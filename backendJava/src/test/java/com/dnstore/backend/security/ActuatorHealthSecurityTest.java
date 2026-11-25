package com.dnstore.backend.security;

import com.dnstore.backend.config.SecurityConfiguration;
import com.dnstore.backend.repository.UserRepository;
import com.dnstore.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Garante que a cadeia real de segurança deixa o health check público para a plataforma de
 * hospedagem, sem abrir mais nada do Actuator. O handler stub só existe para que as rotas
 * públicas respondam 200 (o Actuator em si não faz parte do slice WebMvc).
 */
@WebMvcTest(controllers = ActuatorHealthSecurityTest.StubController.class)
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, AuthRateLimitFilter.class,
        ApiSecurityExceptionHandler.class,
        ActuatorHealthSecurityTest.StubController.class})
class ActuatorHealthSecurityTest {

    @RestController
    static class StubController {
        @GetMapping({"/actuator/health", "/actuator/health/liveness", "/actuator/env", "/api/orders"})
        String ok() {
            return "ok";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @Test
    void health_shouldBePublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void healthProbes_shouldBePublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void otherActuatorEndpoints_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void nonGetHealthRequests_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/actuator/health")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedApi_shouldStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }
}
