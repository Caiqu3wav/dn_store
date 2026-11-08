package com.dnstore.backend.service.shipping;

import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.model.MelhorEnvioOAuthState;
import com.dnstore.backend.repository.MelhorEnvioOAuthStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MelhorEnvioOAuthServiceTest {

    private static final String AUTHORIZATION_URL = "https://oauth.test/authorize";
    private static final String TOKEN_URL = "https://oauth.test/token";
    private static final String REDIRECT_URI = "https://store.test/api/integrations/melhor-envio/callback";
    private static final String USER_AGENT = "DN Store/1.0 (support@example.com)";

    private MelhorEnvioProperties properties;
    private MelhorEnvioOAuthStateRepository stateRepository;
    private MelhorEnvioTokenStore tokenStore;
    private MelhorEnvioOAuthService service;
    private MockRestServiceServer server;
    private AtomicReference<MelhorEnvioOAuthState> savedState;

    @BeforeEach
    void setUp() {
        properties = new MelhorEnvioProperties();
        properties.setEnvironment("sandbox");
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRedirectUri(REDIRECT_URI);
        properties.setAuthorizationUrl(AUTHORIZATION_URL);
        properties.setTokenUrl(TOKEN_URL);
        properties.setScopes("shipping-calculate");
        properties.setUserAgent(USER_AGENT);

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        stateRepository = mock(MelhorEnvioOAuthStateRepository.class);
        tokenStore = mock(MelhorEnvioTokenStore.class);
        savedState = new AtomicReference<>();
        when(stateRepository.save(any(MelhorEnvioOAuthState.class))).thenAnswer(invocation -> {
            MelhorEnvioOAuthState state = invocation.getArgument(0);
            savedState.set(state);
            return state;
        });

        MelhorEnvioOAuthClient oauthClient = new MelhorEnvioOAuthClient(builder, properties);
        service = new MelhorEnvioOAuthService(properties, stateRepository, oauthClient, tokenStore);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://store.test");
    }

    @Test
    void createAuthorizationUrl_persistsHashedSingleUseStateAndRequestedParameters() {
        String authorizationUrl = service.createAuthorizationUrl();
        URI uri = URI.create(authorizationUrl);
        var query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        String state = query.getFirst("state");

        assertThat(query.getFirst("response_type")).isEqualTo("code");
        assertThat(query.getFirst("client_id")).isEqualTo("client-id");
        assertThat(query.getFirst("redirect_uri")).isEqualTo(REDIRECT_URI);
        assertThat(query.getFirst("scope")).isEqualTo("shipping-calculate");
        assertThat(state).isNotBlank();
        assertThat(savedState.get().getStateHash()).isNotEqualTo(state);
        assertThat(savedState.get().getEnvironment()).isEqualTo("sandbox");
        assertThat(savedState.get().getExpiresAt()).isNotNull();
        assertThat(service.frontendRedirect("connected"))
                .isEqualTo("https://store.test/admin?integration=melhor-envio&status=connected");
    }

    @Test
    void consumeState_dependsOnAtomicDeleteAndCannotBeReplayed() {
        when(stateRepository.consume(anyString(), eq("sandbox"), any()))
                .thenReturn(1)
                .thenReturn(0);

        assertThat(service.consumeState("opaque-state")).isTrue();
        assertThat(service.consumeState("opaque-state")).isFalse();
        verify(stateRepository, times(2)).consume(anyString(), eq("sandbox"), any());
    }

    @Test
    void completeAuthorization_exchangesCodeAndPersistsBothProviderTokens() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("""
                        {
                          "grant_type": "authorization_code",
                          "client_id": "client-id",
                          "client_secret": "client-secret",
                          "redirect_uri": "https://store.test/api/integrations/melhor-envio/callback",
                          "code": "authorization-code"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "token_type": "Bearer",
                          "expires_in": 1800,
                          "access_token": "access-token-value",
                          "refresh_token": "refresh-token-value"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.completeAuthorization("authorization-code", null)).isTrue();
        verify(tokenStore).save(argThat(tokens ->
                tokens.accessToken().equals("access-token-value")
                        && tokens.refreshToken().equals("refresh-token-value")
                        && tokens.expiresIn() == 1800));
        server.verify();
    }

    @Test
    void completeAuthorization_whenProviderDenied_doesNotExchangeOrPersistTokens() {
        assertThat(service.completeAuthorization(null, "access_denied")).isFalse();

        verifyNoInteractions(tokenStore);
        server.verify();
    }

    @Test
    void completeAuthorization_whenProviderRejectsCodeReturnsSafeFailure() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_description\":\"authorization code expired\"}"));

        assertThat(service.completeAuthorization("expired-code", null)).isFalse();

        verifyNoInteractions(tokenStore);
        server.verify();
    }
}
