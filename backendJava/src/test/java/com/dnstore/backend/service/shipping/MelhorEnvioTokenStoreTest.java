package com.dnstore.backend.service.shipping;

import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.exception.ShippingGatewayException;
import com.dnstore.backend.model.MelhorEnvioToken;
import com.dnstore.backend.repository.MelhorEnvioTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MelhorEnvioTokenStoreTest {

        private MelhorEnvioTokenRepository repository;
        private MelhorEnvioOAuthClient oauthClient;
        private MelhorEnvioProperties properties;
        private TokenCipher cipher;
        private MelhorEnvioTokenStore tokenStore;

        @BeforeEach
        void setUp() {
                properties = new MelhorEnvioProperties();
                properties.setEnvironment("sandbox");
                properties.setTokenEncryptionKey(Base64.getEncoder().encodeToString(
                                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
                properties.setRefreshSkew(java.time.Duration.ofMinutes(5));
                repository = mock(MelhorEnvioTokenRepository.class);
                oauthClient = mock(MelhorEnvioOAuthClient.class);
                cipher = new TokenCipher(properties);
                tokenStore = new MelhorEnvioTokenStore(repository, oauthClient, cipher, properties);
                when(repository.save(any(MelhorEnvioToken.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        void save_encryptsAccessAndRefreshTokensBeforePersistence() {
                when(repository.findById("sandbox")).thenReturn(Optional.empty());

                tokenStore.save(tokenResponse("access-secret-value", "refresh-secret-value", 3600));

                var captor = org.mockito.ArgumentCaptor.forClass(MelhorEnvioToken.class);
                verify(repository).save(captor.capture());
                MelhorEnvioToken saved = captor.getValue();
                assertThat(saved.getStatus()).isEqualTo("AUTHORIZED");
                assertThat(saved.getAccessTokenCiphertext())
                                .isNotEqualTo("access-secret-value".getBytes(StandardCharsets.UTF_8));
                assertThat(saved.getRefreshTokenCiphertext())
                                .isNotEqualTo("refresh-secret-value".getBytes(StandardCharsets.UTF_8));
                assertThat(cipher.decrypt(saved.getAccessTokenCiphertext(), "melhor-envio:sandbox:access"))
                                .isEqualTo("access-secret-value");
                assertThat(cipher.decrypt(saved.getRefreshTokenCiphertext(), "melhor-envio:sandbox:refresh"))
                                .isEqualTo("refresh-secret-value");
                assertThat(saved.getExpiresAt()).isAfter(now());
                assertThat(saved.getRefreshExpiresAt()).isAfter(saved.getExpiresAt());
        }

        @Test
        void getValidAccessToken_returnsUnexpiredTokenWithoutRefreshing() {
                MelhorEnvioToken token = storedToken("active-access", "active-refresh", now().plusHours(1));
                when(repository.findByEnvironmentForUpdate("sandbox")).thenReturn(Optional.of(token));

                assertThat(tokenStore.getValidAccessToken()).isEqualTo("active-access");

                verifyNoInteractions(oauthClient);
        }

        @Test
        void getValidAccessToken_refreshesAndAtomicallyReplacesBothTokensBeforeExpiry() {
                MelhorEnvioToken token = storedToken("expiring-access", "old-refresh", now().plusMinutes(1));
                when(repository.findByEnvironmentForUpdate("sandbox")).thenReturn(Optional.of(token));
                when(oauthClient.refreshToken("old-refresh"))
                                .thenReturn(tokenResponse("new-access", "new-refresh", 3600));

                assertThat(tokenStore.getValidAccessToken()).isEqualTo("new-access");
                assertThat(cipher.decrypt(token.getAccessTokenCiphertext(), "melhor-envio:sandbox:access"))
                                .isEqualTo("new-access");
                assertThat(cipher.decrypt(token.getRefreshTokenCiphertext(), "melhor-envio:sandbox:refresh"))
                                .isEqualTo("new-refresh");
                assertThat(token.getExpiresAt()).isAfter(now());
                verify(repository).save(token);
        }

        @Test
        void staleUnauthorizedResponseUsesTokenAlreadyRefreshedByConcurrentRequest() {
                MelhorEnvioToken token = storedToken("new-access", "new-refresh", now().plusHours(1));
                when(repository.findByEnvironmentForUpdate("sandbox")).thenReturn(Optional.of(token));

                assertThat(tokenStore.refreshAfterUnauthorized("old-access")).isEqualTo("new-access");

                verifyNoInteractions(oauthClient);
        }

        @Test
        void invalidRefreshMarksAuthorizationForReconnectionAndDoesNotRetry() {
                MelhorEnvioToken token = storedToken("expiring-access", "revoked-refresh", now().minusMinutes(1));
                when(repository.findByEnvironmentForUpdate("sandbox")).thenReturn(Optional.of(token));
                when(oauthClient.refreshToken("revoked-refresh"))
                                .thenThrow(new MelhorEnvioOAuthClient.OAuthTokenException(true, false));

                assertThatThrownBy(tokenStore::getValidAccessToken)
                                .isInstanceOf(ShippingGatewayException.class)
                                .hasMessageContaining("authorization was revoked");

                assertThat(token.getStatus()).isEqualTo("REAUTH_REQUIRED");
                verify(repository).save(token);
        }

        private MelhorEnvioOAuthClient.TokenResponse tokenResponse(
                        String accessToken, String refreshToken, long expiresIn) {
                return new MelhorEnvioOAuthClient.TokenResponse("Bearer", expiresIn, accessToken, refreshToken);
        }

        private MelhorEnvioToken storedToken(String accessToken, String refreshToken, LocalDateTime expiresAt) {
                MelhorEnvioToken token = new MelhorEnvioToken();
                token.setEnvironment("sandbox");
                token.setAccessTokenCiphertext(cipher.encrypt(accessToken, "melhor-envio:sandbox:access"));
                token.setRefreshTokenCiphertext(cipher.encrypt(refreshToken, "melhor-envio:sandbox:refresh"));
                token.setExpiresAt(expiresAt);
                token.setRefreshExpiresAt(now().plusDays(60));
                token.setStatus("AUTHORIZED");
                return token;
        }

        private LocalDateTime now() {
                return LocalDateTime.now(ZoneOffset.UTC);
        }

        @Test
        void temporaryRefreshFailureKeepsAuthorizationActive() {
                MelhorEnvioToken token = storedToken(
                                "expiring-access",
                                "active-refresh",
                                now().minusMinutes(1));

                when(repository.findByEnvironmentForUpdate("sandbox"))
                                .thenReturn(Optional.of(token));

                when(oauthClient.refreshToken("active-refresh"))
                                .thenThrow(new MelhorEnvioOAuthClient.OAuthTokenException(false, false));

                assertThatThrownBy(tokenStore::getValidAccessToken)
                                .isInstanceOf(ShippingGatewayException.class)
                                .hasMessageContaining("failed temporarily");

                assertThat(token.getStatus()).isEqualTo("AUTHORIZED");

                verify(repository, never()).save(token);
        }
}
