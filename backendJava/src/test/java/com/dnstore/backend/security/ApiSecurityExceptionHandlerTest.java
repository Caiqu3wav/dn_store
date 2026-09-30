package com.dnstore.backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiSecurityExceptionHandlerTest {

    private ApiSecurityExceptionHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        handler = new ApiSecurityExceptionHandler(objectMapper);
    }

    @Test
    void unauthenticatedRequest_shouldReturnStandard401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/account/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response, new BadCredentialsException("invalid credentials"));

        assertBody(response, 401, "Unauthorized", "/api/account/me");
    }

    @Test
    void forbiddenRequest_shouldReturnStandard403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("not allowed"));

        assertBody(response, 403, "Forbidden", "/api/admin/dashboard");
    }

    private void assertBody(MockHttpServletResponse response, int status, String error, String path) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("error").asText()).isEqualTo(error);
        assertThat(body.get("path").asText()).isEqualTo(path);
        assertThat(body.has("trace")).isFalse();
    }
}