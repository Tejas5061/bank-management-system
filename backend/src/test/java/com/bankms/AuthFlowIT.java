package com.bankms;

import com.bankms.entity.KycStatus;
import com.bankms.entity.PasswordResetOtp;
import com.bankms.repository.PasswordResetOtpRepository;
import com.bankms.support.AbstractIntegrationTest;
import com.bankms.support.TestDataFactory;
import com.bankms.support.TestDataFactory.TestCustomer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordResetOtpRepository otps;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registrationCreatesAPendingKycCustomerWithASavingsAccount() throws Exception {
        String email = "new." + UUID.randomUUID().toString().substring(0, 8) + "@test.example";
        Map<String, Object> request = Map.ofEntries(
                Map.entry("fullName", "Neha Kulkarni"), Map.entry("email", email), Map.entry("phone", "9812345678"),
                Map.entry("password", "Str0ng!Pass"), Map.entry("dateOfBirth", "1996-05-14"),
                Map.entry("panNumber", randomPan()), Map.entry("aadhaarNumber", randomAadhaar()),
                Map.entry("addressLine", "9 MG Road"), Map.entry("city", "Pune"), Map.entry("state", "Maharashtra"),
                Map.entry("pincode", "411001"), Map.entry("branchCode", "000001"));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerNumber").value(org.hamcrest.Matchers.startsWith("CIF")))
                .andExpect(jsonPath("$.accountNumber").value(org.hamcrest.Matchers.matchesPattern("\\d{12}")));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));

        String token = login(email, "Str0ng!Pass");
        mvc.perform(get("/api/v1/me/profile").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kycStatus").value("PENDING"))
                .andExpect(jsonPath("$.maskedPan").value(org.hamcrest.Matchers.startsWith("XXXXXX")));
    }

    @Test
    void weakPasswordAndUnderageApplicantAreRejectedWithFieldErrors() throws Exception {
        Map<String, Object> request = Map.ofEntries(
                Map.entry("fullName", "Too Young"), Map.entry("email", "young@test.example"), Map.entry("phone", "9812345678"),
                Map.entry("password", "password"), Map.entry("dateOfBirth", "2015-01-01"),
                Map.entry("panNumber", "BAD"), Map.entry("aadhaarNumber", "123"),
                Map.entry("addressLine", "x"), Map.entry("city", "Pune"), Map.entry("state", "MH"),
                Map.entry("pincode", "411001"), Map.entry("branchCode", "000001"));

        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();
        assertThat(body(result).get("fieldErrors").findValuesAsText("field"))
                .contains("password", "dateOfBirth", "panNumber", "aadhaarNumber");
    }

    @Test
    void fiveWrongPasswordsLockTheAccount() throws Exception {
        TestCustomer customer = data.customer(KycStatus.VERIFIED, "0");
        for (int attempt = 1; attempt <= 4; attempt++) {
            loginAttempt(customer.email(), "wrong").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        }
        loginAttempt(customer.email(), "wrong").andExpect(status().isLocked())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
        // the correct password does not get through while locked
        loginAttempt(customer.email(), TestDataFactory.PASSWORD).andExpect(status().isLocked());
    }

    @Test
    void unknownEmailGetsTheSameAnswerAsAWrongPassword() throws Exception {
        loginAttempt("nobody@test.example", "whatever")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshTokensRotateAndReuseRevokesTheWholeSession() throws Exception {
        TestCustomer customer = data.customer(KycStatus.VERIFIED, "0");
        MvcResult login = loginAttempt(customer.email(), TestDataFactory.PASSWORD).andExpect(status().isOk()).andReturn();
        Cookie first = login.getResponse().getCookie("bms_refresh");
        assertThat(first).isNotNull();
        assertThat(first.isHttpOnly()).isTrue();
        assertThat(login.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Strict").contains("Path=/api/v1/auth");
        assertThat(body(login).has("refreshToken")).as("refresh token never appears in the JSON body").isFalse();

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = refreshed.getResponse().getCookie("bms_refresh");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Pretend the first token was stolen and replayed a minute after it was rotated.
        jdbc.update("UPDATE refresh_tokens SET revoked_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE) WHERE user_id = ? AND revoked_at IS NOT NULL",
                customer.userId());
        mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
        // ...and the legitimate newer token is revoked with it.
        mvc.perform(post("/api/v1/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        TestCustomer customer = data.customer(KycStatus.VERIFIED, "0");
        Cookie cookie = loginAttempt(customer.email(), TestDataFactory.PASSWORD).andReturn().getResponse().getCookie("bms_refresh");
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetWithOtp() throws Exception {
        TestCustomer customer = data.customer(KycStatus.VERIFIED, "0");
        mvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", customer.email()))))
                .andExpect(status().isAccepted());
        // Same answer for an unknown email: no account enumeration.
        mvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", "ghost@test.example"))))
                .andExpect(status().isAccepted());

        // The OTP is only in the email; in the test we overwrite its hash with a known code.
        PasswordResetOtp otp = otps.findAll().stream().filter(o -> o.getUser().getId().equals(customer.userId()))
                .findFirst().orElseThrow();
        jdbc.update("UPDATE password_reset_otps SET otp_hash = ? WHERE id = ?", passwordEncoder.encode("424242"), otp.getId());

        Map<String, String> wrong = Map.of("email", customer.email(), "otp", "000000", "newPassword", "N3w!Password");
        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content(toJson(wrong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_OTP"));

        Map<String, String> right = Map.of("email", customer.email(), "otp", "424242", "newPassword", "N3w!Password");
        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content(toJson(right)))
                .andExpect(status().isOk());
        assertThat(login(customer.email(), "N3w!Password")).isNotBlank();
        // a code works once
        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content(toJson(right)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointsAnswer401InTheStandardShape() throws Exception {
        mvc.perform(get("/api/v1/me/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/me/accounts"));
        mvc.perform(get("/api/v1/me/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    private org.springframework.test.web.servlet.ResultActions loginAttempt(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", email, "password", password))));
    }

    private static String randomPan() {
        return "ZZ" + (char) ('A' + (int) (Math.random() * 26)) + "P" + (char) ('A' + (int) (Math.random() * 26))
                + String.format("%04d", (int) (Math.random() * 10000)) + "Q";
    }

    private static String randomAadhaar() {
        return String.valueOf(300_000_000_000L + (long) (Math.random() * 600_000_000_000L));
    }
}
