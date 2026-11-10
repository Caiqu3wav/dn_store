package com.dnstore.backend.controller;

import com.dnstore.backend.service.shipping.MelhorEnvioOAuthClient;
import com.dnstore.backend.service.shipping.MelhorEnvioOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;

class MelhorEnvioOAuthControllerTest {

    private MelhorEnvioOAuthService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(MelhorEnvioOAuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MelhorEnvioOAuthController(service)).build();
    }

    @Test
    void authorize_returnsProviderUrlWithoutOAuthTokens() throws Exception {
        when(service.createAuthorizationUrl())
                .thenReturn("https://sandbox.melhorenvio.com.br/oauth/authorize?state=opaque-state");

        mockMvc.perform(get("/api/integrations/melhor-envio/authorize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl")
                        .value("https://sandbox.melhorenvio.com.br/oauth/authorize?state=opaque-state"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void callback_withInvalidState_returnsBadRequest() throws Exception {
        when(service.consumeState("invalid-state")).thenReturn(false);

        mockMvc.perform(get("/api/integrations/melhor-envio/callback")
                        .param("state", "invalid-state")
                        .param("code", "authorization-code"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void callback_withDeniedAuthorization_redirectsDenied() throws Exception {
        when(service.consumeState("valid-state")).thenReturn(true);
        when(service.frontendRedirect("denied"))
                .thenReturn("https://store.test/admin?integration=melhor-envio&status=denied");

        mockMvc.perform(get("/api/integrations/melhor-envio/callback")
                        .param("state", "valid-state")
                        .param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "https://store.test/admin?integration=melhor-envio&status=denied"));
    }

    @Test
    void callback_withInvalidCode_redirectsFailed() throws Exception {
        when(service.consumeState("valid-state")).thenReturn(true);
        when(service.completeAuthorization("invalid-code", null)).thenReturn(false);
        when(service.frontendRedirect("failed"))
                .thenReturn("https://store.test/admin?integration=melhor-envio&status=failed");

        mockMvc.perform(get("/api/integrations/melhor-envio/callback")
                        .param("state", "valid-state")
                        .param("code", "invalid-code"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "https://store.test/admin?integration=melhor-envio&status=failed"));
    }

    @Test
    void callback_whenTokenExchangeFails_redirectsFailedWithoutLeakingException() throws Exception {
        when(service.consumeState("valid-state")).thenReturn(true);
        when(service.completeAuthorization("authorization-code", null))
                .thenThrow(new MelhorEnvioOAuthClient.OAuthTokenException(false, false));
        when(service.frontendRedirect("failed"))
                .thenReturn("https://store.test/admin?integration=melhor-envio&status=failed");

        mockMvc.perform(get("/api/integrations/melhor-envio/callback")
                        .param("state", "valid-state")
                        .param("code", "authorization-code"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "https://store.test/admin?integration=melhor-envio&status=failed"));
    }
}
