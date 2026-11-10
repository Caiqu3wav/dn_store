package com.dnstore.backend.config;

import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "asaas")
public class AsaasProperties {

    public static final String SANDBOX_URL = "https://api-sandbox.asaas.com/v3";
    public static final String PRODUCTION_URL = "https://api.asaas.com/v3";

    private String environment = "sandbox";
    private String apiKey = "";
    private String webhookToken = "";
    private String baseUrl = SANDBOX_URL;
    private String userAgent = "DN-Store/1.0";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(10);

    public void validate() {
        String normalizedEnvironment = environment == null ? "" : environment.trim().toLowerCase(Locale.ROOT);
        if (!normalizedEnvironment.equals("sandbox") && !normalizedEnvironment.equals("production")) {
            throw new IllegalStateException("ASAAS_ENVIRONMENT deve ser sandbox ou production.");
        }

        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String expectedBaseUrl = normalizedEnvironment.equals("production") ? PRODUCTION_URL : SANDBOX_URL;
        if (!expectedBaseUrl.equals(normalizedBaseUrl)) {
            throw new IllegalStateException("ASAAS_BASE_URL não corresponde ao ASAAS_ENVIRONMENT configurado.");
        }

        validateDuration(connectTimeout, "ASAAS_HTTP_CONNECT_TIMEOUT");
        validateDuration(readTimeout, "ASAAS_HTTP_READ_TIMEOUT");
        if (!StringUtils.hasText(userAgent)) {
            throw new IllegalStateException("ASAAS_USER_AGENT é obrigatório.");
        }

        if (normalizedEnvironment.equals("production")) {
            if (!StringUtils.hasText(apiKey)) {
                throw new IllegalStateException("ASAAS_API_KEY é obrigatório em production.");
            }
            if (!StringUtils.hasText(webhookToken)) {
                throw new IllegalStateException("ASAAS_WEBHOOK_TOKEN é obrigatório em production.");
            }
            if (!apiKey.startsWith("$aact_prod_")) {
                throw new IllegalStateException("ASAAS_API_KEY não corresponde ao ambiente production.");
            }
        } else if (StringUtils.hasText(apiKey) && !apiKey.startsWith("$aact_hmlg_")) {
            throw new IllegalStateException("ASAAS_API_KEY não corresponde ao ambiente sandbox.");
        }
    }

    public String normalizedBaseUrl() {
        return normalizeBaseUrl(baseUrl);
    }

    private String normalizeBaseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("/+$", "");
    }

    private void validateDuration(Duration value, String propertyName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(propertyName + " deve ser maior que zero.");
        }
    }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getWebhookToken() { return webhookToken; }
    public void setWebhookToken(String webhookToken) { this.webhookToken = webhookToken; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
}
