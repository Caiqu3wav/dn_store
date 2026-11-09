package com.dnstore.backend.service.shipping;

import com.dnstore.backend.config.MelhorEnvioProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MelhorEnvioOAuthClientTest {

    private static final String TOKEN_URL = "https://sandbox.melhorenvio.com.br/oauth/token";
    private MelhorEnvioProperties properties;
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private MelhorEnvioOAuthClient client;

    @BeforeEach
    void setUp() {
        properties = new MelhorEnvioProperties();
        properties.setTokenUrl(TOKEN_URL);
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRedirectUri("https://store.test/callback");
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MelhorEnvioOAuthClient(builder, properties);
    }

    @Test
    void exchangeCode_returnsTokenResponseOnSuccess() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess("""
                {"token_type":"Bearer","expires_in":1800,
                 "access_token":"access","refresh_token":"refresh"}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.exchangeCode("code").accessToken()).isEqualTo("access");
        server.verify();
    }

    @Test
    void refreshToken_invalidGrant_requiresReauthorization() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_grant\",\"error_description\":\"refresh token expired\"}"));

        Throwable thrown = catchThrowable(() -> client.refreshToken("refresh"));
        assertThat(thrown).isInstanceOf(MelhorEnvioOAuthClient.OAuthTokenException.class);
        MelhorEnvioOAuthClient.OAuthTokenException exception =
                (MelhorEnvioOAuthClient.OAuthTokenException) thrown;

        assertThat(exception.isReauthorizationRequired()).isTrue();
        assertThat(exception.isInvalidAuthorizationCode()).isFalse();
        assertThat(exception.getErrorCode()).isEqualTo("invalid_grant");
    }

    @Test
    void exchangeCode_invalidGrant_isInvalidAuthorizationCode() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_grant\"}"));

        assertThatThrownBy(() -> client.exchangeCode("expired-code"))
                .isInstanceOfSatisfying(MelhorEnvioOAuthClient.OAuthTokenException.class,
                        exception -> assertThat(exception.isInvalidAuthorizationCode()).isTrue());
    }

    @Test
    void otherBadRequest_doesNotRequireReauthorization() {
        assertTemporaryFailure(HttpStatus.BAD_REQUEST, "invalid_request");
    }

    @Test
    void unauthorized_doesNotRequireReauthorization() {
        assertTemporaryFailure(HttpStatus.UNAUTHORIZED, "invalid_client");
    }

    @Test
    void serverError_doesNotRequireReauthorization() {
        assertTemporaryFailure(HttpStatus.BAD_GATEWAY, "server_error");
    }

    @Test
    void incompleteResponse_isRejectedWithoutReauthorization() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(
                "{\"token_type\":\"Bearer\",\"expires_in\":1800}",
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.refreshToken("refresh"))
                .isInstanceOf(MelhorEnvioOAuthClient.OAuthTokenException.class)
                .satisfies(error -> assertThat(
                        ((MelhorEnvioOAuthClient.OAuthTokenException) error).isReauthorizationRequired())
                        .isFalse());
    }

    @Test
    void connectionFailure_doesNotRequireReauthorization() {
        MelhorEnvioOAuthClient failingClient = clientWithRequestFailure(new IOException("connection refused"));

        assertThatThrownBy(() -> failingClient.refreshToken("refresh"))
                .isInstanceOf(MelhorEnvioOAuthClient.OAuthTokenException.class)
                .satisfies(error -> assertThat(
                        ((MelhorEnvioOAuthClient.OAuthTokenException) error).isReauthorizationRequired())
                        .isFalse());
    }

    @Test
    void timeout_doesNotRequireReauthorization() {
        MelhorEnvioOAuthClient failingClient = clientWithRequestFailure(
                new SocketTimeoutException("token endpoint timed out"));

        assertThatThrownBy(() -> failingClient.refreshToken("refresh"))
                .isInstanceOf(MelhorEnvioOAuthClient.OAuthTokenException.class)
                .satisfies(error -> assertThat(
                        ((MelhorEnvioOAuthClient.OAuthTokenException) error).isReauthorizationRequired())
                        .isFalse());
    }

    private void assertTemporaryFailure(HttpStatus status, String errorCode) {
        server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"" + errorCode + "\"}"));

        assertThatThrownBy(() -> client.refreshToken("refresh"))
                .isInstanceOfSatisfying(MelhorEnvioOAuthClient.OAuthTokenException.class,
                        exception -> assertThat(exception.isReauthorizationRequired()).isFalse());
    }

    private MelhorEnvioOAuthClient clientWithRequestFailure(IOException failure) {
        ClientHttpRequestFactory factory = (uri, method) -> {
            throw failure;
        };
        return new MelhorEnvioOAuthClient(
                RestClient.builder().requestFactory(factory), properties);
    }
}
