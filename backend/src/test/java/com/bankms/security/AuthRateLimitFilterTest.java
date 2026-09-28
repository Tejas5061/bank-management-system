package com.bankms.security;

import com.bankms.exception.ApiErrorFactory;
import com.bankms.support.TestProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(
            TestProperties.withRateLimit(3, Duration.ofMinutes(1)),
            new ApiErrorFactory(Clock.systemUTC(), new ObjectMapper().findAndRegisterModules()));

    @Test
    void throttlesLoginAttemptsPerClientIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(login("10.0.0.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = login("10.0.0.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();
        assertThat(blocked.getContentAsString()).contains("\"errorCode\":\"RATE_LIMITED\"");

        // a different client has its own bucket
        assertThat(login("10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void leavesOtherEndpointsAlone() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me/accounts");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse login(String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
