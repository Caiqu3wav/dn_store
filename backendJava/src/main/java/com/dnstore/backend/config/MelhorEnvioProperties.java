package com.dnstore.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "melhor-envio")
public class MelhorEnvioProperties {

    private String environment = "sandbox";
    private String clientId = "";
    private String clientSecret = "";
    private String redirectUri = "";
    private String apiBaseUrl = "";
    private String authorizationUrl = "";
    private String tokenUrl = "";
    private String scopes = "";
    private String userAgent = "";
    private String tokenEncryptionKey = "";
    private Duration stateTtl = Duration.ofMinutes(10);
    private Duration quoteTtl = Duration.ofMinutes(15);
    private Duration refreshSkew = Duration.ofMinutes(5);
    /**
     * Apenas informativo (coluna refresh_expires_at): o FAQ do Melhor Envio fala em 45 dias, mas a
     * resposta OAuth só traz {@code expires_in} do access token. Não bloqueia nenhuma renovação.
     */
    private Duration refreshTokenTtl = Duration.ofDays(45);
    /** Antecedência, antes do vencimento do access token, em que a renovação proativa começa. */
    private Duration proactiveRefreshWindow = Duration.ofDays(7);
    /** Janela em que uma cotação recente do mesmo carrinho e CEP é reaproveitada sem chamar o provedor. */
    private Duration quoteReuseWindow = Duration.ofMinutes(5);
    private Retry retry = new Retry();

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getAuthorizationUrl() {
        return authorizationUrl;
    }

    public void setAuthorizationUrl(String authorizationUrl) {
        this.authorizationUrl = authorizationUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getTokenEncryptionKey() {
        return tokenEncryptionKey;
    }

    public void setTokenEncryptionKey(String tokenEncryptionKey) {
        this.tokenEncryptionKey = tokenEncryptionKey;
    }

    public Duration getStateTtl() {
        return stateTtl;
    }

    public void setStateTtl(Duration stateTtl) {
        this.stateTtl = stateTtl;
    }

    public Duration getQuoteTtl() {
        return quoteTtl;
    }

    public void setQuoteTtl(Duration quoteTtl) {
        this.quoteTtl = quoteTtl;
    }

    public Duration getRefreshSkew() {
        return refreshSkew;
    }

    public void setRefreshSkew(Duration refreshSkew) {
        this.refreshSkew = refreshSkew;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public Duration getProactiveRefreshWindow() {
        return proactiveRefreshWindow;
    }

    public void setProactiveRefreshWindow(Duration proactiveRefreshWindow) {
        this.proactiveRefreshWindow = proactiveRefreshWindow;
    }

    public Duration getQuoteReuseWindow() {
        return quoteReuseWindow;
    }

    public void setQuoteReuseWindow(Duration quoteReuseWindow) {
        this.quoteReuseWindow = quoteReuseWindow;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    /** Política de tentativas para a cotação (erros 5xx, 429 e falhas de rede). */
    public static class Retry {
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(300);
        private Duration maxBackoff = Duration.ofSeconds(2);
        /** Maior {@code Retry-After} que ainda vale esperar dentro de uma requisição do cliente. */
        private Duration maxRetryAfter = Duration.ofSeconds(3);
        /** Tempo total máximo gasto em tentativas e esperas de uma cotação. */
        private Duration totalBudget = Duration.ofSeconds(15);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }

        public Duration getMaxRetryAfter() {
            return maxRetryAfter;
        }

        public void setMaxRetryAfter(Duration maxRetryAfter) {
            this.maxRetryAfter = maxRetryAfter;
        }

        public Duration getTotalBudget() {
            return totalBudget;
        }

        public void setTotalBudget(Duration totalBudget) {
            this.totalBudget = totalBudget;
        }
    }
}
