package com.dnstore.backend.service.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.dnstore.backend.model.MelhorEnvioToken;
import com.dnstore.backend.repository.MelhorEnvioTokenRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Renovação de token contra um MySQL real e descartável (127.0.0.1:33316, dn_store_it): o lock da
 * linha serializa agendador e cotação, e os tokens rotacionados sobrevivem ao rollback da transação
 * da cotação. O provedor é simulado; nenhuma chamada real ao Melhor Envio. Nunca aponte para o banco real.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        args = {
                "--spring.config.location=classpath:/application-integration-local.properties",
                "--spring.datasource.password=dn_store_it_test_password",
                "--shipping.gateway=melhorEnvio",
                "--shipping.origin-zip=12600100",
                "--melhor-envio.client-id=it-client",
                "--melhor-envio.client-secret=it-secret",
                "--melhor-envio.token-encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "--melhor-envio.renewal-initial-delay-ms=3600000"
        })
@ActiveProfiles("integration-local")
class MelhorEnvioTokenStoreMySqlIT {

    @Autowired
    private MelhorEnvioTokenStore tokenStore;
    @Autowired
    private MelhorEnvioTokenRepository tokenRepository;
    @Autowired
    private TokenCipher cipher;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoBean
    private MelhorEnvioOAuthClient oauthClient;

    @BeforeEach
    void seedExpiringToken() {
        jdbc.update("DELETE FROM melhor_envio_tokens");
        // 60 s de validade: já está dentro do skew de 5 min e da janela de renovação proativa.
        tokenStore.save(new MelhorEnvioOAuthClient.TokenResponse("Bearer", 60L, "old-access", "old-refresh"));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM melhor_envio_tokens");
    }

    @Test
    void schedulerAndQuoteRequestRunningTogetherRefreshExactlyOnceUnderTheRowLock() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        when(oauthClient.refreshToken(any())).thenAnswer(invocation -> {
            providerCalls.incrementAndGet();
            Thread.sleep(400);
            if (!"old-refresh".equals(invocation.getArgument(0))) {
                // Com rotação, reusar o refresh token antigo é recusado pelo provedor.
                throw new MelhorEnvioOAuthClient.OAuthTokenException(true, false, "invalid_grant", 400);
            }
            return new MelhorEnvioOAuthClient.TokenResponse("Bearer", 2592000L, "new-access", "new-refresh");
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

        assertThat(quote.get(15, TimeUnit.SECONDS)).isEqualTo("new-access");
        assertThat(scheduler.get(15, TimeUnit.SECONDS)).isIn(
                MelhorEnvioTokenStore.RenewalOutcome.RENEWED, MelhorEnvioTokenStore.RenewalOutcome.NOT_DUE);
        executor.shutdownNow();

        assertThat(providerCalls.get()).isEqualTo(1);
        MelhorEnvioToken stored = tokenRepository.findById("sandbox").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("AUTHORIZED");
        assertThat(cipher.decrypt(stored.getRefreshTokenCiphertext(), "melhor-envio:sandbox:refresh"))
                .isEqualTo("new-refresh");
    }

    @Test
    void rotatedTokensSurviveRollbackOfTheQuoteTransactionThatTriggeredTheRefresh() {
        when(oauthClient.refreshToken("old-refresh")).thenReturn(
                new MelhorEnvioOAuthClient.TokenResponse("Bearer", 2592000L, "new-access", "new-refresh"));

        // OrderService.quoteShipping é @Transactional e chama o gateway; aqui a transação externa dá rollback.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(tokenStore.getValidAccessToken()).isEqualTo("new-access");
            status.setRollbackOnly();
        });

        MelhorEnvioToken stored = tokenRepository.findById("sandbox").orElseThrow();
        assertThat(cipher.decrypt(stored.getRefreshTokenCiphertext(), "melhor-envio:sandbox:refresh"))
                .isEqualTo("new-refresh");
    }
}
