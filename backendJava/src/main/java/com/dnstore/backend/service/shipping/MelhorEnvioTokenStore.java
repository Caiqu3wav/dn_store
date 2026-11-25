package com.dnstore.backend.service.shipping;

import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.exception.ShippingGatewayException;
import com.dnstore.backend.model.MelhorEnvioToken;
import com.dnstore.backend.repository.MelhorEnvioTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Guarda os tokens OAuth do Melhor Envio (cifrados) e os renova.
 *
 * <p><b>Validade.</b> O Melhor Envio informa a validade do access token ({@code expires_in}, 30 dias)
 * e documenta 45 dias para o refresh token, mas nenhum prazo local bloqueia a renovação: quem decide
 * é o provedor. A autorização só passa a {@code REAUTH_REQUIRED} quando ele recusa o refresh token
 * com {@code invalid_grant}. Qualquer outra falha (rede, 5xx, 429, resposta incompleta) é temporária
 * e deixa tokens e status intactos.
 *
 * <p><b>Rotação.</b> Cada refresh devolve um novo refresh token "para o próximo refresh", então o
 * anterior deve ser tratado como invalidado. Por isso:
 * <ul>
 *   <li>a renovação roda sob o lock da linha do banco (vale entre threads e instâncias): agendador e
 *       cotação nunca usam o mesmo refresh token ao mesmo tempo;</li>
 *   <li>uma resposta incompleta é rejeitada antes de tocar no que está gravado;</li>
 *   <li>se o provedor devolveu tokens novos e a gravação falhou, eles ficam em memória
 *       ({@link PendingPersistence}), são usados nas requisições seguintes e regravados com novas
 *       tentativas; enquanto isso o refresh token antigo do banco não é usado, para não gerar um
 *       {@code invalid_grant} falso. O marcador é posto sob o lock, antes da gravação, e só sai depois
 *       do commit; por isso uma thread que esperava a linha após um rollback também o encontra;</li>
 *   <li>o lock e a garantia acima valem dentro de um processo (instância única, premissa atual). Entre
 *       instâncias, o lock do banco impede renovações simultâneas, mas o marcador em memória não é
 *       visível às outras: uma instância pode ler o refresh token antigo após o rollback de outra.</li>
 * </ul>
 * O que continua sem solução: se o processo morrer antes de conseguir gravar, os tokens novos se
 * perdem e a conexão precisa ser refeita pelo painel.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "shipping.gateway", havingValue = "melhorEnvio")
public class MelhorEnvioTokenStore {

    public static final String AUTHORIZED = "AUTHORIZED";
    public static final String REAUTHORIZATION_REQUIRED = "REAUTH_REQUIRED";

    private static final int PERSIST_RETRY_ATTEMPTS = 3;
    private static final long PERSIST_RETRY_BACKOFF_MILLIS = 200;
    private static final String TEMPORARY_FAILURE = "Melhor Envio token refresh failed temporarily. Please try again.";

    private final MelhorEnvioTokenRepository tokenRepository;
    private final MelhorEnvioOAuthClient oauthClient;
    private final TokenCipher tokenCipher;
    private final MelhorEnvioProperties properties;
    private final TransactionOperations transactions;
    private final Sleeper sleeper;

    /** Última falha de renovação desde que o processo subiu; só em memória, nunca contém token. */
    private final AtomicReference<RefreshFailure> lastRefreshFailure = new AtomicReference<>();
    /** Tokens já emitidos pelo provedor que ainda não foram gravados no banco. */
    private final AtomicReference<PendingPersistence> pendingPersistence = new AtomicReference<>();

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public enum RenewalOutcome { NOT_CONNECTED, REAUTH_REQUIRED, NOT_DUE, RENEWED, FAILED }

    public enum ConnectionState { NOT_CONNECTED, CONNECTED, RENEWAL_FAILING, REAUTH_REQUIRED }

    private enum Trigger { ON_DEMAND, AFTER_UNAUTHORIZED, SCHEDULED }

    public record RefreshFailure(LocalDateTime at, String category, Integer httpStatus, String errorCode) {
    }

    /** {@code persistencePending}: há tokens novos só em memória, aguardando gravação. */
    public record ConnectionStatus(
            ConnectionState state,
            String environment,
            LocalDateTime accessTokenExpiresAt,
            LocalDateTime renewalStartsAt,
            RefreshFailure lastRefreshFailure,
            boolean persistencePending) {
    }

    /** Exclui os tokens do toString para que nunca cheguem a logs. */
    private record PendingPersistence(
            MelhorEnvioOAuthClient.TokenResponse tokens, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        @Override
        public String toString() {
            return "PendingPersistence[expiresAt=" + expiresAt + "]";
        }
    }

    private record Result(RenewalOutcome outcome, String accessToken, ShippingGatewayException failure) {
        static Result token(RenewalOutcome outcome, String accessToken) {
            return new Result(outcome, accessToken, null);
        }

        static Result failure(RenewalOutcome outcome, ShippingGatewayException failure) {
            return new Result(outcome, null, failure);
        }
    }

    @Autowired
    public MelhorEnvioTokenStore(
            MelhorEnvioTokenRepository tokenRepository,
            MelhorEnvioOAuthClient oauthClient,
            TokenCipher tokenCipher,
            MelhorEnvioProperties properties,
            PlatformTransactionManager transactionManager) {
        this(tokenRepository, oauthClient, tokenCipher, properties,
                independentTransactions(transactionManager), Thread::sleep);
    }

    /**
     * REQUIRES_NEW: a cotação que dispara a renovação já roda numa transação (OrderService). Com
     * REQUIRED, os tokens rotacionados só seriam gravados no commit dela, e um rollback da cotação
     * perderia o refresh token novo depois de o provedor já ter invalidado o antigo.
     */
    private static TransactionTemplate independentTransactions(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    MelhorEnvioTokenStore(
            MelhorEnvioTokenRepository tokenRepository,
            MelhorEnvioOAuthClient oauthClient,
            TokenCipher tokenCipher,
            MelhorEnvioProperties properties,
            TransactionOperations transactions,
            Sleeper sleeper) {
        this.tokenRepository = tokenRepository;
        this.oauthClient = oauthClient;
        this.tokenCipher = tokenCipher;
        this.properties = properties;
        this.transactions = transactions;
        this.sleeper = sleeper;
    }

    /** Grava tokens de uma nova autorização (callback OAuth). Substitui qualquer pendência anterior. */
    @Transactional
    public void save(MelhorEnvioOAuthClient.TokenResponse tokens) {
        validateTokens(tokens);

        String environment = properties.getEnvironment();
        MelhorEnvioToken token = tokenRepository.findById(environment)
                .orElseGet(MelhorEnvioToken::new);

        applyTokens(token, tokens, now());
        tokenRepository.save(token);
        pendingPersistence.set(null);
        lastRefreshFailure.set(null);
        log.info("melhor_envio.token.stored environment={} accessExpiresAt={}",
                environment, token.getExpiresAt());
    }

    public String getValidAccessToken() {
        return tokenOrThrow(execute(Trigger.ON_DEMAND, null));
    }

    public String refreshAfterUnauthorized(String rejectedAccessToken) {
        return tokenOrThrow(execute(Trigger.AFTER_UNAUTHORIZED, rejectedAccessToken));
    }

    /**
     * Renovação proativa chamada pelo agendador. Nunca lança exceção: falhas temporárias são
     * registradas e a próxima execução tenta de novo, bem antes do vencimento do access token.
     */
    public RenewalOutcome renewIfDue() {
        return execute(Trigger.SCHEDULED, null).outcome();
    }

    @Transactional(readOnly = true)
    public ConnectionStatus status() {
        String environment = properties.getEnvironment();
        boolean pending = pendingPersistence.get() != null;
        Optional<MelhorEnvioToken> stored = tokenRepository.findById(environment);
        if (stored.isEmpty()) {
            return new ConnectionStatus(
                    ConnectionState.NOT_CONNECTED, environment, null, null, lastRefreshFailure.get(), pending);
        }
        MelhorEnvioToken token = stored.get();
        LocalDateTime renewalStartsAt = token.getExpiresAt().minus(properties.getProactiveRefreshWindow());
        ConnectionState state;
        if (REAUTHORIZATION_REQUIRED.equals(token.getStatus())) {
            state = ConnectionState.REAUTH_REQUIRED;
        } else if (pending || (lastRefreshFailure.get() != null && !renewalStartsAt.isAfter(now()))) {
            state = ConnectionState.RENEWAL_FAILING;
        } else {
            state = ConnectionState.CONNECTED;
        }
        return new ConnectionStatus(
                state, environment, token.getExpiresAt(), renewalStartsAt, lastRefreshFailure.get(), pending);
    }

    public String get() {
        return getValidAccessToken();
    }

    private String tokenOrThrow(Result result) {
        if (result.failure() != null) {
            throw result.failure();
        }
        return result.accessToken();
    }

    private Result execute(Trigger trigger, String rejectedAccessToken) {
        if (pendingPersistence.get() != null && !tryPersistPending()) {
            return fromPending(trigger);
        }
        if (trigger == Trigger.ON_DEMAND) {
            String valid = validStoredAccessToken();
            if (valid != null) {
                return Result.token(RenewalOutcome.NOT_DUE, valid);
            }
        }
        return refreshUnderLock(trigger, rejectedAccessToken);
    }

    /**
     * Caso comum (token longe do vencimento): lê sem lock e sem transação própria. A cotação já segura
     * uma conexão do pool; abrir outra transação com {@code FOR UPDATE} a cada leitura faria N cotações
     * simultâneas disputarem N conexões. Qualquer dúvida (sem token, vencendo, reautorização, erro de
     * leitura ou de decifragem) cai no caminho com lock, que decide e reporta o erro.
     */
    private String validStoredAccessToken() {
        try {
            Optional<MelhorEnvioToken> stored = tokenRepository.findById(properties.getEnvironment());
            if (stored.isPresent()
                    && AUTHORIZED.equals(stored.get().getStatus())
                    && stored.get().getExpiresAt().isAfter(now().plus(properties.getRefreshSkew()))) {
                return decryptAccessToken(stored.get());
            }
        } catch (RuntimeException ignored) {
            // o caminho com lock repete a leitura e trata a falha
        }
        return null;
    }

    /**
     * Há tokens novos que não couberam no banco. O refresh token gravado provavelmente já foi
     * invalidado pela rotação, então não se chama o provedor: usa-se o access token em memória
     * enquanto ele valer.
     */
    private Result fromPending(Trigger trigger) {
        PendingPersistence pending = pendingPersistence.get();
        if (pending == null) {
            return Result.failure(RenewalOutcome.FAILED, new ShippingGatewayException(TEMPORARY_FAILURE));
        }
        if (trigger == Trigger.ON_DEMAND && pending.expiresAt().isAfter(now())) {
            return Result.token(RenewalOutcome.FAILED, pending.tokens().accessToken());
        }
        return Result.failure(RenewalOutcome.FAILED, new ShippingGatewayException(TEMPORARY_FAILURE));
    }

    private Result refreshUnderLock(Trigger trigger, String rejectedAccessToken) {
        AtomicReference<PendingPersistence> issued = new AtomicReference<>();
        try {
            Result result = transactions.execute(status -> locked(trigger, rejectedAccessToken, issued));
            PendingPersistence committed = issued.get();
            if (committed != null) {
                pendingPersistence.compareAndSet(committed, null);
            }
            return result;
        } catch (RuntimeException failure) {
            PendingPersistence tokens = issued.get();
            if (tokens != null) {
                return keepInMemoryAndRetry(trigger, tokens, failure);
            }
            if (failure instanceof ShippingGatewayException gatewayFailure) {
                return Result.failure(RenewalOutcome.FAILED, gatewayFailure);
            }
            log.error("melhor_envio.token.transaction_failed trigger={} type={}",
                    trigger, failure.getClass().getSimpleName());
            return Result.failure(RenewalOutcome.FAILED, new ShippingGatewayException(TEMPORARY_FAILURE));
        }
    }

    /** Roda dentro da transação, com a linha do token travada. Devolve o resultado em vez de lançar, para o commit acontecer. */
    private Result locked(
            Trigger trigger, String rejectedAccessToken, AtomicReference<PendingPersistence> issued) {
        Optional<MelhorEnvioToken> stored = tokenRepository.findByEnvironmentForUpdate(properties.getEnvironment());
        if (stored.isEmpty()) {
            return Result.failure(RenewalOutcome.NOT_CONNECTED,
                    new ShippingGatewayException("Melhor Envio has not been authorized."));
        }
        MelhorEnvioToken token = stored.get();
        PendingPersistence pending = pendingPersistence.get();
        if (pending != null && isSuperseded(token, pending)) {
            // Outra thread renovou e a gravação dela falhou depois de o lock já estar liberado: a linha
            // ainda tem o refresh token que a rotação invalidou. Não se chama o provedor com ele.
            return fromPending(trigger);
        }
        if (REAUTHORIZATION_REQUIRED.equals(token.getStatus())) {
            return Result.failure(RenewalOutcome.REAUTH_REQUIRED,
                    new ShippingGatewayException("Melhor Envio authorization must be renewed."));
        }

        switch (trigger) {
            case ON_DEMAND -> {
                if (token.getExpiresAt().isAfter(now().plus(properties.getRefreshSkew()))) {
                    return Result.token(RenewalOutcome.NOT_DUE, decryptAccessToken(token));
                }
            }
            case AFTER_UNAUTHORIZED -> {
                String current = decryptAccessToken(token);
                // Outra requisição (ou o agendador) já renovou enquanto esta esperava o lock.
                if (!current.equals(rejectedAccessToken) && token.getExpiresAt().isAfter(now())) {
                    return Result.token(RenewalOutcome.NOT_DUE, current);
                }
            }
            case SCHEDULED -> {
                if (token.getExpiresAt().isAfter(now().plus(properties.getProactiveRefreshWindow()))) {
                    return Result.token(RenewalOutcome.NOT_DUE, null);
                }
            }
        }

        MelhorEnvioOAuthClient.TokenResponse refreshed;
        try {
            refreshed = oauthClient.refreshToken(decryptRefreshToken(token));
        } catch (MelhorEnvioOAuthClient.OAuthTokenException exception) {
            if (exception.isReauthorizationRequired()) {
                // Os tokens ficam guardados; só o status muda, para o painel pedir a reconexão.
                token.setStatus(REAUTHORIZATION_REQUIRED);
                tokenRepository.saveAndFlush(token);
                recordFailure("reauthorization_required", exception);
                log.error("melhor_envio.token.reauthorization_required trigger={} httpStatus={} errorCode={}",
                        trigger, exception.getHttpStatus(), exception.getErrorCode());
                return Result.failure(RenewalOutcome.REAUTH_REQUIRED,
                        new ShippingGatewayException("Melhor Envio authorization was revoked or expired."));
            }
            recordFailure("temporary", exception);
            log.warn("melhor_envio.token.refresh_failed trigger={} category=temporary httpStatus={} errorCode={}",
                    trigger, exception.getHttpStatus(), exception.getErrorCode());
            return Result.failure(RenewalOutcome.FAILED, new ShippingGatewayException(TEMPORARY_FAILURE));
        }

        if (!isComplete(refreshed)) {
            // Nunca sobrescreve credenciais válidas com uma resposta incompleta.
            lastRefreshFailure.set(new RefreshFailure(now(), "incomplete_response", null, null));
            log.warn("melhor_envio.token.refresh_failed trigger={} category=incomplete_response", trigger);
            return Result.failure(RenewalOutcome.FAILED, new ShippingGatewayException(TEMPORARY_FAILURE));
        }

        // A partir daqui o provedor já invalidou o refresh token anterior (rotação). O marcador é posto
        // ainda sob o lock: quem esperar a linha depois de um rollback já o encontra.
        LocalDateTime issuedAt = now();
        PendingPersistence rotated = new PendingPersistence(refreshed, issuedAt, issuedAt.plusSeconds(refreshed.expiresIn()));
        issued.set(rotated);
        pendingPersistence.set(rotated);
        applyTokens(token, refreshed, issuedAt);
        tokenRepository.saveAndFlush(token);
        lastRefreshFailure.set(null);
        log.info("melhor_envio.token.refreshed trigger={} accessExpiresAt={}", trigger, token.getExpiresAt());
        return Result.token(RenewalOutcome.RENEWED, refreshed.accessToken());
    }

    /** A gravação (ou o commit) falhou depois de o provedor emitir tokens novos: não os perde. */
    private Result keepInMemoryAndRetry(Trigger trigger, PendingPersistence issued, RuntimeException failure) {
        lastRefreshFailure.set(new RefreshFailure(now(), "persistence_failed", null, null));
        log.error("melhor_envio.token.persist_failed trigger={} type={} action=kept_in_memory_and_retrying",
                trigger, failure.getClass().getSimpleName());

        for (int attempt = 1; attempt <= PERSIST_RETRY_ATTEMPTS; attempt++) {
            try {
                sleeper.sleep(PERSIST_RETRY_BACKOFF_MILLIS * attempt);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
            if (tryPersistPending()) {
                break;
            }
        }
        // O access token novo vale de qualquer forma; a gravação continua sendo tentada depois.
        return Result.token(RenewalOutcome.RENEWED, issued.tokens().accessToken());
    }

    /** Regrava os tokens pendentes. Não sobrescreve um token mais novo que já esteja no banco. */
    private boolean tryPersistPending() {
        PendingPersistence pending = pendingPersistence.get();
        if (pending == null) {
            return true;
        }
        try {
            transactions.executeWithoutResult(status -> {
                Optional<MelhorEnvioToken> stored =
                        tokenRepository.findByEnvironmentForUpdate(properties.getEnvironment());
                if (stored.isPresent() && !isSuperseded(stored.get(), pending)) {
                    return; // já gravado (commit de resultado incerto) ou outra instância renovou depois
                }
                MelhorEnvioToken token = stored.orElseGet(MelhorEnvioToken::new);
                applyTokens(token, pending.tokens(), pending.issuedAt());
                tokenRepository.saveAndFlush(token);
            });
        } catch (RuntimeException failure) {
            log.warn("melhor_envio.token.persist_retry_failed type={}", failure.getClass().getSimpleName());
            return false;
        }
        if (pendingPersistence.compareAndSet(pending, null)) {
            lastRefreshFailure.set(null);
            log.info("melhor_envio.token.persisted_after_failure accessExpiresAt={}", pending.expiresAt());
        }
        return true;
    }

    /** A linha gravada é mais antiga que os tokens pendentes (a folga de 1 s cobre o arredondamento do DATETIME). */
    private boolean isSuperseded(MelhorEnvioToken stored, PendingPersistence pending) {
        return stored.getExpiresAt().isBefore(pending.expiresAt().minusSeconds(1));
    }

    private void applyTokens(
            MelhorEnvioToken token, MelhorEnvioOAuthClient.TokenResponse tokens, LocalDateTime issuedAt) {
        String environment = properties.getEnvironment();
        token.setEnvironment(environment);
        token.setAccessTokenCiphertext(
                tokenCipher.encrypt(tokens.accessToken(), aad(environment, "access")));
        token.setRefreshTokenCiphertext(
                tokenCipher.encrypt(tokens.refreshToken(), aad(environment, "refresh")));
        token.setExpiresAt(issuedAt.plusSeconds(tokens.expiresIn()));
        // Informativo (coluna obrigatória): nenhuma decisão usa este valor.
        token.setRefreshExpiresAt(issuedAt.plus(properties.getRefreshTokenTtl()));
        token.setStatus(AUTHORIZED);
    }

    private void recordFailure(String category, MelhorEnvioOAuthClient.OAuthTokenException exception) {
        lastRefreshFailure.set(new RefreshFailure(
                now(), category, exception.getHttpStatus(), exception.getErrorCode()));
    }

    private String decryptAccessToken(MelhorEnvioToken token) {
        return tokenCipher.decrypt(
                token.getAccessTokenCiphertext(),
                aad(properties.getEnvironment(), "access"));
    }

    private String decryptRefreshToken(MelhorEnvioToken token) {
        return tokenCipher.decrypt(
                token.getRefreshTokenCiphertext(),
                aad(properties.getEnvironment(), "refresh"));
    }

    private String aad(String environment, String tokenType) {
        return "melhor-envio:" + environment + ":" + tokenType;
    }

    private boolean isComplete(MelhorEnvioOAuthClient.TokenResponse tokens) {
        return tokens != null
                && tokens.accessToken() != null && !tokens.accessToken().isBlank()
                && tokens.refreshToken() != null && !tokens.refreshToken().isBlank()
                && tokens.expiresIn() != null && tokens.expiresIn() > 0;
    }

    private void validateTokens(MelhorEnvioOAuthClient.TokenResponse tokens) {
        if (!isComplete(tokens)) {
            throw new ShippingGatewayException(
                    "Melhor Envio returned incomplete OAuth credentials.");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
