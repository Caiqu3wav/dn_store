package com.dnstore.backend.service.shipping;

import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.exception.ShippingGatewayException;
import com.dnstore.backend.model.MelhorEnvioToken;
import com.dnstore.backend.repository.MelhorEnvioTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * O repositório é um "banco" em memória: cada leitura devolve uma cópia e só {@code saveAndFlush}
 * bem-sucedido altera o que está gravado, como uma transação que sofre rollback.
 */
class MelhorEnvioTokenStoreTest {

        private static final String ACCESS_AAD = "melhor-envio:sandbox:access";
        private static final String REFRESH_AAD = "melhor-envio:sandbox:refresh";

        private MelhorEnvioTokenRepository repository;
        private MelhorEnvioOAuthClient oauthClient;
        private MelhorEnvioProperties properties;
        private TokenCipher cipher;
        private MelhorEnvioTokenStore tokenStore;
        private final AtomicReference<MelhorEnvioToken> db = new AtomicReference<>();
        private final AtomicInteger saveFailures = new AtomicInteger();
        private final List<Long> sleeps = new ArrayList<>();

        @BeforeEach
        void setUp() {
                properties = new MelhorEnvioProperties();
                properties.setEnvironment("sandbox");
                properties.setTokenEncryptionKey(Base64.getEncoder().encodeToString(
                                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
                properties.setRefreshSkew(Duration.ofMinutes(5));
                properties.setProactiveRefreshWindow(Duration.ofDays(7));
                repository = mock(MelhorEnvioTokenRepository.class);
                oauthClient = mock(MelhorEnvioOAuthClient.class);
                cipher = new TokenCipher(properties);
                tokenStore = new MelhorEnvioTokenStore(repository, oauthClient, cipher, properties,
                                TransactionOperations.withoutTransaction(), sleeps::add);
                wireRepository();
        }

        private void wireRepository() {
                when(repository.findByEnvironmentForUpdate("sandbox"))
                                .thenAnswer(invocation -> Optional.ofNullable(db.get()).map(this::copy));
                when(repository.findById("sandbox"))
                                .thenAnswer(invocation -> Optional.ofNullable(db.get()).map(this::copy));
                when(repository.save(any(MelhorEnvioToken.class))).thenAnswer(invocation -> {
                        MelhorEnvioToken saved = invocation.getArgument(0);
                        db.set(copy(saved));
                        return saved;
                });
                when(repository.saveAndFlush(any(MelhorEnvioToken.class))).thenAnswer(invocation -> {
                        if (saveFailures.getAndUpdate(remaining -> Math.max(0, remaining - 1)) > 0) {
                                throw new DataAccessResourceFailureException("database unavailable");
                        }
                        MelhorEnvioToken saved = invocation.getArgument(0);
                        db.set(copy(saved));
                        return saved;
                });
        }

        // ---- gravação e leitura ----

        @Test
        void save_encryptsAccessAndRefreshTokensBeforePersistence() {
                tokenStore.save(tokenResponse("access-secret-value", "refresh-secret-value", 3600));

                MelhorEnvioToken saved = db.get();
                assertThat(saved.getStatus()).isEqualTo("AUTHORIZED");
                assertThat(saved.getAccessTokenCiphertext())
                                .isNotEqualTo("access-secret-value".getBytes(StandardCharsets.UTF_8));
                assertThat(saved.getRefreshTokenCiphertext())
                                .isNotEqualTo("refresh-secret-value".getBytes(StandardCharsets.UTF_8));
                assertThat(cipher.decrypt(saved.getAccessTokenCiphertext(), ACCESS_AAD)).isEqualTo("access-secret-value");
                assertThat(cipher.decrypt(saved.getRefreshTokenCiphertext(), REFRESH_AAD)).isEqualTo("refresh-secret-value");
                assertThat(saved.getExpiresAt()).isAfter(now());
        }

        @Test
        void getValidAccessToken_returnsUnexpiredTokenWithoutRefreshing() {
                seed("active-access", "active-refresh", now().plusHours(1));

                assertThat(tokenStore.getValidAccessToken()).isEqualTo("active-access");

                verifyNoInteractions(oauthClient);
        }

        @Test
        void getValidAccessToken_withAValidTokenNeedsNeitherANewTransactionNorTheRowLock() {
                // Cada cotação já segura uma conexão; abrir outra transação (REQUIRES_NEW) com FOR UPDATE a
                // cada leitura esgotaria o pool sob carga. O caso comum lê sem lock e sem transação própria.
                AtomicInteger transactionsOpened = new AtomicInteger();
                tokenStore = new MelhorEnvioTokenStore(repository, oauthClient, cipher, properties,
                                new TransactionOperations() {
                                        @Override
                                        public <T> T execute(TransactionCallback<T> action) {
                                                transactionsOpened.incrementAndGet();
                                                return action.doInTransaction(mock(TransactionStatus.class));
                                        }
                                }, sleeps::add);
                seed("active-access", "active-refresh", now().plusHours(1));

                assertThat(tokenStore.getValidAccessToken()).isEqualTo("active-access");

                assertThat(transactionsOpened.get()).isZero();
                verify(repository, never()).findByEnvironmentForUpdate(any());
        }

        @Test
        void getValidAccessToken_nearExpiryStillRefreshesUnderTheRowLock() {
                AtomicInteger transactionsOpened = new AtomicInteger();
                tokenStore = new MelhorEnvioTokenStore(repository, oauthClient, cipher, properties,
                                new TransactionOperations() {
                                        @Override
                                        public <T> T execute(TransactionCallback<T> action) {
                                                transactionsOpened.incrementAndGet();
                                                return action.doInTransaction(mock(TransactionStatus.class));
                                        }
                                }, sleeps::add);
                seed("expiring-access", "old-refresh", now().plusMinutes(1));
                when(oauthClient.refreshToken("old-refresh"))
                                .thenReturn(tokenResponse("new-access", "new-refresh", 3600));

                assertThat(tokenStore.getValidAccessToken()).isEqualTo("new-access");

                assertThat(transactionsOpened.get()).isEqualTo(1);
                verify(repository).findByEnvironmentForUpdate("sandbox");
        }

        @Test
        void getValidAccessToken_withoutStoredTokenFailsClosed() {
                assertThatThrownBy(tokenStore::getValidAccessToken)
                                .isInstanceOf(ShippingGatewayException.class)
                                .hasMessageContaining("not been authorized");
        }

        // ---- renovação sob demanda ----

        @Test
        void getValidAccessToken_refreshesAndReplacesBothTokensBeforeExpiry() {
                seed("expiring-access", "old-refresh", now().plusMinutes(1));
                when(oauthClient.refreshToken("old-refresh"))
                                .thenReturn(tokenResponse("new-access", "new-refresh", 3600));

                assertThat(tokenStore.getValidAccessToken()).isEqualTo("new-access");

                assertThat(storedAccess()).isEqualTo("new-access");
                assertThat(storedRefresh()).isEqualTo("new-refresh");
                assertThat(db.get().getExpiresAt()).isAfter(now());
        }

        @Test
        void refreshIsAttemptedEvenWhenLocalRefreshEstimateHasPassed() {
                // O Melhor Envio decide a validade do refresh token; o campo local é só informativo.
                seed("expiring-access", "old-refresh", now().plusMinutes(1));
                db.get().setRefreshExpiresAt(now().minusDays(1));
                when(oauthClient.refreshToken("old-refresh"))
                                .thenReturn(tokenResponse("new-access", "new-refresh", 2592000));

                assertThat(tokenStore.getValidAccessToken()).isEqualTo("new-access");
                assertThat(db.get().getStatus()).isEqualTo("AUTHORIZED");
        }

        @Test
        void staleUnauthorizedResponseUsesTokenAlreadyRefreshedByConcurrentRequest() {
                seed("new-access", "new-refresh", now().plusHours(1));

                assertThat(tokenStore.refreshAfterUnauthorized("old-access")).isEqualTo("new-access");

                verifyNoInteractions(oauthClient);
        }

        @Test
        void invalidRefreshMarksAuthorizationForReconnectionAndDoesNotRetry() {
                seed("expiring-access", "revoked-refresh", now().minusMinutes(1));
                byte[] refreshBefore = db.get().getRefreshTokenCiphertext();
                when(oauthClient.refreshToken("revoked-refresh"))
                                .thenThrow(new MelhorEnvioOAuthClient.OAuthTokenException(true, false));

                assertThatThrownBy(tokenStore::getValidAccessToken)
                                .isInstanceOf(ShippingGatewayException.class)
                                .hasMessageContaining("authorization was revoked");

                assertThat(db.get().getStatus()).isEqualTo("REAUTH_REQUIRED");
                assertThat(db.get().getRefreshTokenCiphertext()).isEqualTo(refreshBefore);
                assertThatThrownBy(tokenStore::getValidAccessToken).isInstanceOf(ShippingGatewayException.class);
                verify(oauthClient, times(1)).refreshToken(any());
        }

        @Test
        void temporaryRefreshFailureKeepsAuthorizationActive() {
                seed("expiring-access", "active-refresh", now().minusMinutes(1));
                when(oauthClient.refreshToken("active-refresh"))
                                .thenThrow(new MelhorEnvioOAuthClient.OAuthTokenException(false, false));

                assertThatThrownBy(tokenStore::getValidAccessToken)
                                .isInstanceOf(ShippingGatewayException.class)
                                .hasMessageContaining("failed temporarily");

                assertThat(db.get().getStatus()).isEqualTo("AUTHORIZED");
                verify(repository, never()).saveAndFlush(any());
        }

        // ---- agendador ----

        @Test
        void renewIfDue_withoutStoredTokenReportsNotConnected() {
                assertThat(tokenStore.renewIfDue()).isEqualTo(MelhorEnvioTokenStore.RenewalOutcome.NOT_CONNECTED);
                verifyNoInteractions(oauthClient);
        }

        @Test
        void renewIfDue_doesNothingWhileAccessTokenIsOutsideTheRenewalWindow() {
                seed("access", "refresh", now().plusDays(20));

                assertThat(tokenStore.renewIfDue()).isEqualTo(MelhorEnvioTokenStore.RenewalOutcome.NOT_DUE);
                verifyNoInteractions(oauthClient);
        }

        @Test
        void renewIfDue_renewsProactivelyDaysBeforeExpiry() {
                seed("access", "old-refresh", now().plusDays(3));
                when(oauthClient.refreshToken("old-refresh"))
                                .thenReturn(tokenResponse("new-access", "new-refresh", 2592000));

                assertThat(tokenStore.renewIfDue()).isEqualTo(MelhorEnvioTokenStore.RenewalOutcome.RENEWED);

                assertThat(storedAccess()).isEqualTo("new-access");
                assertThat(storedRefresh()).isEqualTo("new-refresh");
                assertThat(db.get().getExpiresAt()).isAfter(now().plusDays(29));
        }

        @Test
        void renewIfDue_temporaryFailureKeepsCredentialsAndStatusAndReportsFailingState() {
                seed("access", "old-refresh", now().plusDays(3));
                byte[] accessBefore = db.get().getAccessTokenCiphertext();
                byte[] refreshBefore = db.get().getRefreshTokenCiphertext();
                when(oauthClient.refreshToken("old-refresh"))
                                .thenThrow(new MelhorEnvioOAuthClient.OAuthTokenException(false, false, null, 503));

                assertThat(tokenStore.renewIfDue()).isEqualTo(MelhorEnvioTokenStore.RenewalOutcome.FAILED);

                assertThat(db.get().getStatus()).isEqualTo("AUTHORIZED");
                assertThat(db.get().getAccessTokenCiphertext()).isEqualTo(accessBefore);
                assertThat(db.get().getRefreshTokenCiphertext()).isEqualTo(refreshBefore);
                verify(repository, never()).saveAndFlush(any());
                var status = tokenStore.status();
                assertThat(status.state()).isEqualTo(MelhorEnvioTokenStore.ConnectionState.RENEWAL_FAILING);
                assertThat(status.lastRefreshFailure().httpStatus()).isEqualTo(503);
        }

        @Test
        void renewIfDue_invalidGrantMarksReauthButKeepsStoredCredentials() {
                seed("access", "old-refresh", now().plusDays(3));
                byte[] refreshBefore = db.get().getRefreshTokenCiphertext();
                when(oauthClient.refreshToken("old-refresh")).thenThrow(
                                new MelhorEnvioOAuthClient.OAuthTokenException(true, false, "invalid_grant", 400));

                assertThat(tokenStore.renewIfDue()).isEqualTo(MelhorEnvioTokenStore.RenewalOutcome.REAUTH_REQUIRED);

                assertThat(db.get().getStatus()).isEqualTo("REAUTH_REQUIRED");
                assertThat(db.get().getRefreshTokenCiphertext()).isEqualTo(refreshBefore);
                assertThat(tokenStore.status().state())
                                .isEqualTo(MelhorEnvioTokenStore.ConnectionState.REAUTH_REQUIRED);
                // Já marcado: o agendador não insiste com um token recusado.
                assertThat(tokenStore.renewIfDue()).isEqualTo(MelhorEnvioTokenStore.RenewalOutcome.REAUTH_REQUIRED);
                verify(oauthClient, times(1)).refreshToken(any());
        }

        // ---- rotação e resposta incompleta ----

        @Test
        void incompleteRefreshResponseNeverOverwritesValidCredentials() {
                seed("access", "old-refresh", now().plusMinutes(1));
                byte[] accessBefore = db.get().getAccessTokenCiphertext();
                byte[] refreshBefore = db.get().getRefreshTokenCiphertext();
                when(oauthClient.refreshToken("old-refresh"))
                                .thenReturn(new MelhorEnvioOAuthClient.TokenResponse("Bearer", 3600L, "new-access", " "));

                assertThatThrownBy(tokenStore::getValidAccessToken)
                                .isInstanceOf(ShippingGatewayException.class)
                                .hasMessageContaining("failed temporarily");

                assertThat(db.get().getAccessTokenCiphertext()).isEqualTo(accessBefore);
                assertThat(db.get().getRefreshTokenCiphertext()).isEqualTo(refreshBefore);
                assertThat(db.get().getStatus()).isEqualTo("AUTHORIZED");
                verify(repository, never()).saveAndFlush(any());
        }

        @Test
        void incompleteRefreshResponseMissingAccessTokenOrExpiryIsAlsoRejected() {
                seed("access", "old-refresh", now().plusMinutes(1));
                when(oauthClient.refreshToken("old-refresh"))
                                .thenReturn(new MelhorEnvioOAuthClient.TokenResponse("Bearer", null, "new-access", "new-refresh"))
                                .thenReturn(new MelhorEnvioOAuthClient.TokenResponse("Bearer", 3600L, null, "new-refresh"));

                assertThatThrownBy(tokenStore::getValidAccessToken).isInstanceOf(ShippingGatewayException.class);
                assertThatThrownBy(tokenStore::getValidAccessToken).isInstanceOf(ShippingGatewayException.class);

                assertThat(storedRefresh()).isEqualTo("old-refresh");
        }

        // ---- falha ao gravar tokens que o provedor já emitiu ----

        @Test
        void persistenceFailure_keepsNewTokensInMemoryAndRetriesUntilTheyAreStored() {
                seed("expiring-access", "old-refresh", now().plusMinutes(1));
                when(oauthClient.refreshToken("old-refresh"))
                                .thenReturn(tokenResponse("new-access", "new-refresh", 3600));
                saveFailures.set(2); // a gravação da renovação e a primeira nova tentativa falham

                String token = tokenStore.getValidAccessToken();

                assertThat(token).isEqualTo("new-access");
                assertThat(storedAccess()).isEqualTo("new-access");
                assertThat(storedRefresh()).isEqualTo("new-refresh");
                assertThat(sleeps).hasSize(2);
                assertThat(tokenStore.status().persistencePending()).isFalse();
                assertThat(tokenStore.status().state()).isEqualTo(MelhorEnvioTokenStore.ConnectionState.CONNECTED);
                verify(oauthClient, times(1)).refreshToken(any());
        }

        @Test
        void persistenceFailure_whileDatabaseStaysDown_neverReusesTheRotatedRefreshToken() {
                seed("expiring-access", "old-refresh", now().plusMinutes(1));
                when(oauthClient.refreshToken("old-refresh"))
                                .thenReturn(tokenResponse("new-access", "new-refresh", 3600));
                saveFailures.set(100);

                assertThat(tokenStore.getValidAccessToken()).isEqualTo("new-access");
                assertThat(storedRefresh()).isEqualTo("old-refresh"); // o banco ainda tem o token antigo

                // Requisições seguintes usam o access token em memória e NÃO chamam o provedor com o
                // refresh token antigo, que a rotação já invalidou (daria invalid_grant falso).
                assertThat(tokenStore.getValidAccessToken()).isEqualTo("new-access");
                assertThat(tokenStore.renewIfDue()).isEqualTo(MelhorEnvioTokenStore.RenewalOutcome.FAILED);
                assertThatThrownBy(() -> tokenStore.refreshAfterUnauthorized("new-access"))
                                .isInstanceOf(ShippingGatewayException.class);
                verify(oauthClient, times(1)).refreshToken(any());
                assertThat(db.get().getStatus()).isEqualTo("AUTHORIZED");

                var status = tokenStore.status();
                assertThat(status.persistencePending()).isTrue();
                assertThat(status.state()).isEqualTo(MelhorEnvioTokenStore.ConnectionState.RENEWAL_FAILING);
                assertThat(status.lastRefreshFailure().category()).isEqualTo("persistence_failed");

                // O banco volta: o próximo uso grava os tokens pendentes, sem nova chamada ao provedor.
                saveFailures.set(0);
                assertThat(tokenStore.getValidAccessToken()).isEqualTo("new-access");
                assertThat(storedRefresh()).isEqualTo("new-refresh");
                assertThat(tokenStore.status().persistencePending()).isFalse();
                verify(oauthClient, times(1)).refreshToken(any());
        }

        @Test
        void persistenceFailure_doesNotOverwriteANewerTokenStoredByAnotherInstance() {
                seed("expiring-access", "old-refresh", now().plusMinutes(1));
                when(oauthClient.refreshToken("old-refresh"))
                                .thenReturn(tokenResponse("new-access", "new-refresh", 3600));
                saveFailures.set(100);
                tokenStore.getValidAccessToken();

                // Outra instância renovou depois e gravou um token que vale mais.
                seed("newer-access", "newer-refresh", now().plusDays(30));
                saveFailures.set(0);
                tokenStore.renewIfDue();

                assertThat(storedAccess()).isEqualTo("newer-access");
                assertThat(storedRefresh()).isEqualTo("newer-refresh");
                assertThat(tokenStore.status().persistencePending()).isFalse();
        }

        @Test
        void persistenceFailure_isNeverLoggedWithTheTokenValues() {
                seed("expiring-access", "old-refresh", now().plusMinutes(1));
                when(oauthClient.refreshToken("old-refresh"))
                                .thenReturn(tokenResponse("new-access-secret", "new-refresh-secret", 3600));
                saveFailures.set(100);

                tokenStore.getValidAccessToken();

                assertThat(tokenStore.status().toString())
                                .doesNotContain("new-access-secret").doesNotContain("new-refresh-secret");
        }

        // ---- concorrência ----

        @Test
        void schedulerAndQuoteRequestRunningTogetherRefreshExactlyOnce() throws Exception {
                // O lock da linha é emulado por um lock de JVM ao redor de cada transação.
                ReentrantLock rowLock = new ReentrantLock();
                TransactionOperations lockingTransactions = new TransactionOperations() {
                        @Override
                        public <T> T execute(TransactionCallback<T> action) {
                                rowLock.lock();
                                try {
                                        return action.doInTransaction(mock(TransactionStatus.class));
                                } finally {
                                        rowLock.unlock();
                                }
                        }
                };
                tokenStore = new MelhorEnvioTokenStore(repository, oauthClient, cipher, properties,
                                lockingTransactions, sleeps::add);
                seed("expiring-access", "old-refresh", now().plusMinutes(1)); // dentro da janela e do skew
                AtomicInteger providerCalls = new AtomicInteger();
                when(oauthClient.refreshToken(any())).thenAnswer(invocation -> {
                        providerCalls.incrementAndGet();
                        Thread.sleep(200); // dá tempo para a outra thread tentar entrar
                        if (!"old-refresh".equals(invocation.getArgument(0))) {
                                throw new MelhorEnvioOAuthClient.OAuthTokenException(true, false, "invalid_grant", 400);
                        }
                        return tokenResponse("new-access", "new-refresh", 2592000);
                });

                ExecutorService executor = Executors.newFixedThreadPool(2);
                CountDownLatch start = new CountDownLatch(1);
                Future<MelhorEnvioTokenStore.RenewalOutcome> scheduler = executor.submit(() -> {
                        start.await();
                        return tokenStore.renewIfDue();
                });
                Future<String> quote = executor.submit(() -> {
                        start.await();
                        return tokenStore.getValidAccessToken();
                });
                start.countDown();

                assertThat(quote.get(5, TimeUnit.SECONDS)).isEqualTo("new-access");
                assertThat(scheduler.get(5, TimeUnit.SECONDS))
                                .isIn(MelhorEnvioTokenStore.RenewalOutcome.RENEWED,
                                                MelhorEnvioTokenStore.RenewalOutcome.NOT_DUE);
                executor.shutdownNow();

                assertThat(providerCalls.get()).isEqualTo(1);
                assertThat(db.get().getStatus()).isEqualTo("AUTHORIZED");
                assertThat(storedRefresh()).isEqualTo("new-refresh");
        }

        @Test
        void commitFailureAfterRotation_doesNotLetAThreadWaitingOnTheLockReuseTheRotatedRefreshToken() throws Exception {
                // A primeira transação falha no commit (rollback da linha) depois de o provedor rotacionar o
                // refresh token. A thread que já esperava o lock não pode chamar o provedor com o token
                // antigo: seria um invalid_grant falso e marcaria REAUTH_REQUIRED sem necessidade.
                ReentrantLock rowLock = new ReentrantLock();
                AtomicInteger transactionsStarted = new AtomicInteger();
                TransactionOperations lockingTransactions = new TransactionOperations() {
                        @Override
                        public <T> T execute(TransactionCallback<T> action) {
                                boolean first = transactionsStarted.incrementAndGet() == 1;
                                rowLock.lock();
                                MelhorEnvioToken snapshot = db.get() == null ? null : copy(db.get());
                                boolean committed = false;
                                try {
                                        T result = action.doInTransaction(mock(TransactionStatus.class));
                                        if (first) {
                                                throw new DataAccessResourceFailureException("commit failed");
                                        }
                                        committed = true;
                                        return result;
                                } finally {
                                        if (!committed) {
                                                db.set(snapshot);
                                        }
                                        rowLock.unlock();
                                        if (!committed && first) {
                                                pause(150); // a exceção só chega ao chamador depois de a linha ser liberada
                                        }
                                }
                        }
                };
                tokenStore = new MelhorEnvioTokenStore(repository, oauthClient, cipher, properties,
                                lockingTransactions, sleeps::add);
                seed("expiring-access", "old-refresh", now().plusMinutes(1));
                AtomicInteger providerCalls = new AtomicInteger();
                CountDownLatch providerEntered = new CountDownLatch(1);
                when(oauthClient.refreshToken(any())).thenAnswer(invocation -> {
                        if (providerCalls.incrementAndGet() > 1 || !"old-refresh".equals(invocation.getArgument(0))) {
                                throw new MelhorEnvioOAuthClient.OAuthTokenException(true, false, "invalid_grant", 400);
                        }
                        providerEntered.countDown();
                        Thread.sleep(300); // a segunda thread chega ao lock enquanto esta ainda renova
                        return tokenResponse("new-access", "new-refresh", 2592000);
                });

                ExecutorService executor = Executors.newFixedThreadPool(2);
                try {
                        Future<String> first = executor.submit(() -> tokenStore.getValidAccessToken());
                        assertThat(providerEntered.await(5, TimeUnit.SECONDS)).isTrue();
                        Future<String> second = executor.submit(() -> tokenStore.getValidAccessToken());

                        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("new-access");
                        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("new-access");
                } finally {
                        executor.shutdownNow();
                }

                assertThat(providerCalls.get()).isEqualTo(1);
                assertThat(db.get().getStatus()).isEqualTo("AUTHORIZED");
                assertThat(storedRefresh()).isEqualTo("new-refresh");
                assertThat(tokenStore.status().persistencePending()).isFalse();
        }

        // ---- status ----

        @Test
        void status_reportsNotConnectedConnectedAndExpiryWithoutExposingTokens() {
                assertThat(tokenStore.status().state())
                                .isEqualTo(MelhorEnvioTokenStore.ConnectionState.NOT_CONNECTED);

                seed("secret-access-value", "secret-refresh-value", now().plusDays(25));
                var status = tokenStore.status();
                assertThat(status.state()).isEqualTo(MelhorEnvioTokenStore.ConnectionState.CONNECTED);
                assertThat(status.accessTokenExpiresAt()).isEqualTo(db.get().getExpiresAt());
                assertThat(status.renewalStartsAt()).isEqualTo(db.get().getExpiresAt().minusDays(7));
                assertThat(status.toString()).doesNotContain("secret-access-value").doesNotContain("secret-refresh-value");
        }

        // ---- helpers ----

        private MelhorEnvioOAuthClient.TokenResponse tokenResponse(
                        String accessToken, String refreshToken, long expiresIn) {
                return new MelhorEnvioOAuthClient.TokenResponse("Bearer", expiresIn, accessToken, refreshToken);
        }

        private void seed(String accessToken, String refreshToken, LocalDateTime expiresAt) {
                MelhorEnvioToken token = new MelhorEnvioToken();
                token.setEnvironment("sandbox");
                token.setAccessTokenCiphertext(cipher.encrypt(accessToken, ACCESS_AAD));
                token.setRefreshTokenCiphertext(cipher.encrypt(refreshToken, REFRESH_AAD));
                token.setExpiresAt(expiresAt);
                token.setRefreshExpiresAt(now().plusDays(60));
                token.setStatus("AUTHORIZED");
                db.set(token);
        }

        private String storedAccess() {
                return cipher.decrypt(db.get().getAccessTokenCiphertext(), ACCESS_AAD);
        }

        private String storedRefresh() {
                return cipher.decrypt(db.get().getRefreshTokenCiphertext(), REFRESH_AAD);
        }

        private MelhorEnvioToken copy(MelhorEnvioToken source) {
                MelhorEnvioToken copy = new MelhorEnvioToken();
                copy.setEnvironment(source.getEnvironment());
                copy.setAccessTokenCiphertext(source.getAccessTokenCiphertext());
                copy.setRefreshTokenCiphertext(source.getRefreshTokenCiphertext());
                copy.setExpiresAt(source.getExpiresAt());
                copy.setRefreshExpiresAt(source.getRefreshExpiresAt());
                copy.setStatus(source.getStatus());
                return copy;
        }

        private static void pause(long millis) {
                try {
                        Thread.sleep(millis);
                } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                }
        }

        private LocalDateTime now() {
                return LocalDateTime.now(ZoneOffset.UTC);
        }
}
