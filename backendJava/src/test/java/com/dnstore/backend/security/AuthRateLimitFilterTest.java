package com.dnstore.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    private MutableClock clock;
    private AuthRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        filter = new AuthRateLimitFilter(new ObjectMapper().findAndRegisterModules(), 3, 5, Duration.ofMinutes(5), clock);
    }

    private MockHttpServletResponse call(String method, String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void login_shouldBlockAfterLimitWithRetryAfterAndJsonBody() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("POST", "/api/auth/login", "203.0.113.1").getStatus()).isEqualTo(200);
        }
        clock.advance(Duration.ofSeconds(100));

        MockHttpServletResponse blocked = call("POST", "/api/auth/login", "203.0.113.1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("200");
        assertThat(blocked.getContentType()).startsWith("application/json");
        assertThat(blocked.getContentAsString()).contains("\"status\":429").contains("/api/auth/login");
    }

    @Test
    void limit_shouldResetAfterTheWindow() throws Exception {
        for (int i = 0; i < 3; i++) {
            call("POST", "/api/auth/login", "203.0.113.1");
        }
        assertThat(call("POST", "/api/auth/login", "203.0.113.1").getStatus()).isEqualTo(429);

        clock.advance(Duration.ofMinutes(5));

        assertThat(call("POST", "/api/auth/login", "203.0.113.1").getStatus()).isEqualTo(200);
    }

    @Test
    void differentClients_shouldHaveIndependentBudgets() throws Exception {
        for (int i = 0; i < 3; i++) {
            call("POST", "/api/auth/login", "203.0.113.1");
        }

        assertThat(call("POST", "/api/auth/login", "203.0.113.1").getStatus()).isEqualTo(429);
        assertThat(call("POST", "/api/auth/login", "203.0.113.2").getStatus()).isEqualTo(200);
    }

    @Test
    void loginAndOtherAuthEndpoints_shouldUseSeparateBuckets() throws Exception {
        for (int i = 0; i < 3; i++) {
            call("POST", "/api/auth/login", "203.0.113.1");
        }

        assertThat(call("POST", "/api/auth/login", "203.0.113.1").getStatus()).isEqualTo(429);
        assertThat(call("POST", "/api/auth/forgot-password", "203.0.113.1").getStatus()).isEqualTo(200);
        for (int i = 0; i < 4; i++) {
            call("POST", "/api/auth/forgot-password", "203.0.113.1");
        }
        assertThat(call("POST", "/api/auth/forgot-password", "203.0.113.1").getStatus()).isEqualTo(429);
    }

    @Test
    void otherRequests_shouldNeverBeLimited() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertThat(call("GET", "/api/products", "203.0.113.1").getStatus()).isEqualTo(200);
            assertThat(call("GET", "/api/auth/login", "203.0.113.1").getStatus()).isEqualTo(200);
            assertThat(call("POST", "/api/auth/change-password", "203.0.113.1").getStatus()).isEqualTo(200);
            assertThat(call("POST", "/api/payment/webhook", "203.0.113.1").getStatus()).isEqualTo(200);
        }
    }

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-17T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
