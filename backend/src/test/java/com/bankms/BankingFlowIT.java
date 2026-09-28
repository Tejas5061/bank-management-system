package com.bankms;

import com.bankms.entity.AccountStatus;
import com.bankms.entity.KycStatus;
import com.bankms.entity.Transaction;
import com.bankms.entity.TransactionChannel;
import com.bankms.entity.TransactionDirection;
import com.bankms.entity.TransactionStatus;
import com.bankms.entity.TransactionType;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.TransactionRepository;
import com.bankms.support.AbstractIntegrationTest;
import com.bankms.support.TestDataFactory;
import com.bankms.support.TestDataFactory.TestCustomer;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BankingFlowIT extends AbstractIntegrationTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private TransactionRepository transactions;

    private String tellerToken;
    private String adminToken;

    @BeforeEach
    void staff() throws Exception {
        tellerToken = login(data.employee().email(), TestDataFactory.PASSWORD);
        adminToken = login(data.admin().email(), TestDataFactory.PASSWORD);
    }

    // ---------------------------------------------------------------- authorization

    @Test
    void customersSeeOnlyTheirOwnAccountsWhileStaffSeeAll() throws Exception {
        TestCustomer alice = data.customer(KycStatus.VERIFIED, "5000.00");
        TestCustomer bob = data.customer(KycStatus.VERIFIED, "5000.00");
        String aliceToken = login(alice.email(), TestDataFactory.PASSWORD);

        call(get("/api/v1/accounts/" + alice.account(0)), aliceToken).andExpect(status().isOk());
        call(get("/api/v1/accounts/" + bob.account(0)), aliceToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        call(get("/api/v1/accounts/" + bob.account(0) + "/transactions"), aliceToken).andExpect(status().isForbidden());
        call(get("/api/v1/accounts/" + bob.account(0) + "/statement"), aliceToken).andExpect(status().isForbidden());
        call(get("/api/v1/accounts/" + bob.account(0)), tellerToken).andExpect(status().isOk());

        call(get("/api/v1/admin/analytics/overview"), aliceToken).andExpect(status().isForbidden());
        call(get("/api/v1/admin/analytics/overview"), tellerToken).andExpect(status().isForbidden());
        call(get("/api/v1/staff/dashboard"), aliceToken).andExpect(status().isForbidden());
        // cash handling is for tellers only, not admins (separation of duties)
        call(post("/api/v1/staff/cash/deposits").header("Idempotency-Key", TestDataFactory.key())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("accountNumber", accountNumber(alice.account(0)), "amount", 10))), adminToken)
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- KYC gate + cash desk + freeze

    @Test
    void accountsCannotTransactUntilKycIsVerified() throws Exception {
        TestCustomer customer = data.customer(KycStatus.PENDING, "0");
        String number = accountNumber(customer.account(0));

        cash("deposits", number, "2500.00").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("KYC_NOT_VERIFIED"));

        call(patch("/api/v1/staff/customers/" + customer.customerId() + "/kyc").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("status", "VERIFIED", "remarks", "Documents checked"))), tellerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kycStatus").value("VERIFIED"));

        cash("deposits", number, "2500.00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter").value(2500.00));
        cash("withdrawals", number, "1600.00").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
        cash("withdrawals", number, "1500.00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter").value(1000.00));
    }

    @Test
    void frozenAccountRejectsMoneyMovementAndTheFreezeIsAudited() throws Exception {
        TestCustomer customer = data.customer(KycStatus.VERIFIED, "5000.00");
        call(patch("/api/v1/staff/accounts/" + customer.account(0) + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("status", "FROZEN", "reason", "Suspicious activity"))), tellerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        cash("withdrawals", accountNumber(customer.account(0)), "100.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_FROZEN"));

        MvcResult audit = call(get("/api/v1/admin/audit-logs?action=ACCOUNT_STATUS_CHANGED&size=100"), adminToken)
                .andExpect(status().isOk()).andReturn();
        assertThat(body(audit).get("content").findValuesAsText("entityId")).contains(String.valueOf(customer.account(0)));
    }

    // ---------------------------------------------------------------- beneficiaries

    @Test
    void newBeneficiaryHasACoolingPeriodThenBothLegsShareOneReference() throws Exception {
        TestCustomer payer = data.customer(KycStatus.VERIFIED, "20000.00");
        TestCustomer payee = data.customer(KycStatus.VERIFIED, "1000.00");
        String token = login(payer.email(), TestDataFactory.PASSWORD);

        MvcResult added = call(post("/api/v1/me/beneficiaries").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("name", "Payee", "accountNumber", accountNumber(payee.account(0)), "ifsc", "KOSH0000001"))), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.internal").value(true))
                .andExpect(jsonPath("$.active").value(false))
                .andReturn();
        long beneficiaryId = body(added).get("id").asLong();
        Map<String, Object> transfer = Map.of("fromAccountId", payer.account(0), "beneficiaryId", beneficiaryId, "amount", 3000);

        call(post("/api/v1/transfers/beneficiary").header("Idempotency-Key", TestDataFactory.key())
                .contentType(MediaType.APPLICATION_JSON).content(toJson(transfer)), token)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BENEFICIARY_COOLING_PERIOD"));

        jdbc.update("UPDATE beneficiaries SET activated_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE) WHERE id = ?", beneficiaryId);
        MvcResult done = call(post("/api/v1/transfers/beneficiary").header("Idempotency-Key", TestDataFactory.key())
                .contentType(MediaType.APPLICATION_JSON).content(toJson(transfer)), token)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andReturn();
        String reference = body(done).get("referenceNumber").asText();

        assertThat(balance(payer.account(0))).isEqualByComparingTo("17000.00");
        assertThat(balance(payee.account(0))).isEqualByComparingTo("4000.00");
        long legs = transactions.count((root, q, cb) -> cb.equal(root.get("referenceNumber"), reference));
        assertThat(legs).isEqualTo(2);
    }

    @Test
    void cannotSaveYourOwnAccountAsABeneficiary() throws Exception {
        TestCustomer customer = data.customer(KycStatus.VERIFIED, "1000.00");
        call(post("/api/v1/me/beneficiaries").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("name", "Me", "accountNumber", accountNumber(customer.account(0)), "ifsc", "KOSH0000001"))),
                login(customer.email(), TestDataFactory.PASSWORD))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------------------------------------------------------------- loans + EMI job

    @Test
    void loanApprovalDisbursesAndTheEmiJobCollectsDueInstalments() throws Exception {
        TestCustomer borrower = data.customer(KycStatus.VERIFIED, "30000.00");
        String token = login(borrower.email(), TestDataFactory.PASSWORD);

        MvcResult applied = call(post("/api/v1/me/loans").contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of(
                "loanType", "PERSONAL", "amount", 120000, "tenureMonths", 12, "accountId", borrower.account(0),
                "purpose", "Medical expenses"))), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long loanId = body(applied).get("id").asLong();

        MvcResult approved = call(post("/api/v1/staff/loans/" + loanId + "/approve").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("remarks", "Salary slips verified"))), tellerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loan.status").value("ACTIVE"))
                .andReturn();
        JsonNode schedule = body(approved).get("schedule");
        assertThat(schedule).hasSize(12);
        assertThat(schedule.get(11).get("closingPrincipal").decimalValue()).isEqualByComparingTo("0");
        assertThat(balance(borrower.account(0))).isEqualByComparingTo("150000.00");

        // A second decision on the same application is refused.
        call(post("/api/v1/staff/loans/" + loanId + "/reject").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("remarks", "too late"))), tellerToken)
                .andExpect(status().isUnprocessableEntity());

        // Make the first EMI due yesterday and run the job.
        jdbc.update("UPDATE loan_installments SET due_date = ? WHERE loan_id = ? AND installment_number = 1",
                LocalDate.now(IST).minusDays(1), loanId);
        BigDecimal emi = schedule.get(0).get("emiAmount").decimalValue();
        call(post("/api/v1/admin/jobs/emi-debit"), adminToken).andExpect(status().isOk());

        call(get("/api/v1/me/loans/" + loanId), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule[0].status").value("PAID"))
                .andExpect(jsonPath("$.schedule[1].status").value("PENDING"))
                .andExpect(jsonPath("$.paidInstallments").value(1));
        assertThat(balance(borrower.account(0))).isEqualByComparingTo(new BigDecimal("150000.00").subtract(emi));
    }

    @Test
    void loanOutsideProductLimitsIsRejected() throws Exception {
        TestCustomer borrower = data.customer(KycStatus.VERIFIED, "1000.00");
        call(post("/api/v1/me/loans").contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of(
                "loanType", "PERSONAL", "amount", 90000000, "tenureMonths", 12, "accountId", borrower.account(0),
                "purpose", "Yacht"))), login(borrower.email(), TestDataFactory.PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    // ---------------------------------------------------------------- fixed deposits

    @Test
    void fixedDepositIsFundedFromSavingsAndPaysOutOnMaturity() throws Exception {
        TestCustomer saver = data.customer(KycStatus.VERIFIED, "60000.00");
        String token = login(saver.email(), TestDataFactory.PASSWORD);

        MvcResult booked = call(post("/api/v1/me/fixed-deposits").header("Idempotency-Key", TestDataFactory.key())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("sourceAccountId", saver.account(0), "principal", 50000, "tenureMonths", 12))), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        long fdAccountId = body(booked).get("accountId").asLong();
        BigDecimal maturity = body(booked).get("maturityAmount").decimalValue();
        assertThat(balance(saver.account(0))).isEqualByComparingTo("10000.00");

        jdbc.update("UPDATE fixed_deposits SET maturity_date = ? WHERE account_id = ?", LocalDate.now(IST).minusDays(1), fdAccountId);
        call(post("/api/v1/admin/jobs/fd-maturity"), adminToken).andExpect(status().isOk());
        call(post("/api/v1/admin/jobs/fd-maturity"), adminToken).andExpect(status().isOk()); // idempotent

        assertThat(balance(saver.account(0))).isEqualByComparingTo(new BigDecimal("10000.00").add(maturity));
        assertThat(accounts.findById(fdAccountId).orElseThrow().getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(balance(fdAccountId)).isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------- interest job

    @Test
    void monthlyInterestIsCreditedOnceUsingTheDailyProduct() throws Exception {
        TestCustomer saver = data.customer(KycStatus.VERIFIED, "0");
        Long accountId = saver.account(0);
        YearMonth lastMonth = YearMonth.now(IST).minusMonths(1);
        // 3,65,000 held for the whole of last month -> 365000 * days * 3.5% / 365 = 35 per day
        jdbc.update("UPDATE accounts SET balance = 365000.00 WHERE id = ?", accountId);
        transactions.save(Transaction.builder()
                .referenceNumber("TXNTEST" + accountId).account(accounts.getReferenceById(accountId))
                .type(TransactionType.DEPOSIT).direction(TransactionDirection.CREDIT)
                .amount(new BigDecimal("365000.00")).balanceAfter(new BigDecimal("365000.00"))
                .status(TransactionStatus.SUCCESS).channel(TransactionChannel.BRANCH)
                .valueDate(lastMonth.minusMonths(1).atEndOfMonth()).build());

        call(post("/api/v1/admin/jobs/interest?period=" + lastMonth), adminToken).andExpect(status().isOk());
        BigDecimal expected = new BigDecimal(35 * lastMonth.lengthOfMonth()).setScale(2);
        assertThat(balance(accountId)).isEqualByComparingTo(new BigDecimal("365000.00").add(expected));

        call(post("/api/v1/admin/jobs/interest?period=" + lastMonth), adminToken).andExpect(status().isOk());
        assertThat(balance(accountId)).as("second run pays nothing").isEqualByComparingTo(new BigDecimal("365000.00").add(expected));

        call(post("/api/v1/admin/jobs/interest?period=" + YearMonth.now(IST)), adminToken)
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------------------------------------------------------------- statements & history

    @Test
    void historyIsPagedAndStatementsDownloadAsPdfAndCsv() throws Exception {
        TestCustomer customer = data.customer(KycStatus.VERIFIED, "10000.00", "0");
        String token = login(customer.email(), TestDataFactory.PASSWORD);
        for (int i = 0; i < 3; i++) {
            call(post("/api/v1/transfers/internal").header("Idempotency-Key", TestDataFactory.key())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Map.of("fromAccountId", customer.account(0), "toAccountId", customer.account(1), "amount", 100))), token)
                    .andExpect(status().isCreated());
        }
        call(get("/api/v1/accounts/" + customer.account(0) + "/transactions?size=2&type=TRANSFER_OUT"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(9700.00));
        call(get("/api/v1/accounts/" + customer.account(0) + "/transactions?sort=nonsense"), token)
                .andExpect(status().isBadRequest());

        MvcResult pdf = call(get("/api/v1/accounts/" + customer.account(0) + "/statement?format=PDF"), token)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andReturn();
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");
        MvcResult csv = call(get("/api/v1/accounts/" + customer.account(0) + "/statement?format=CSV"), token)
                .andExpect(status().isOk()).andReturn();
        assertThat(csv.getResponse().getContentAsString()).contains("Closing balance").contains("9700.00");
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions cash(String kind, String accountNumber, String amount) throws Exception {
        return call(post("/api/v1/staff/cash/" + kind).header("Idempotency-Key", TestDataFactory.key())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("accountNumber", accountNumber, "amount", new BigDecimal(amount)))), tellerToken);
    }

    private String accountNumber(Long accountId) {
        return accounts.findById(accountId).orElseThrow().getAccountNumber();
    }

    private BigDecimal balance(Long accountId) {
        return accounts.findById(accountId).orElseThrow().getBalance();
    }
}
