package com.dnstore.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

@Configuration
@EnableConfigurationProperties(MelhorEnvioProperties.class)
public class MelhorEnvioConfiguration {

    private final MelhorEnvioProperties properties;
    private final String gateway;
    private final boolean productionProfile;
    private final boolean homologationProfile;

    public MelhorEnvioConfiguration(
            MelhorEnvioProperties properties,
            @Value("${shipping.gateway:simulated}") String gateway,
            org.springframework.core.env.Environment environment) {
        this.properties = properties;
        this.gateway = gateway;
        this.productionProfile = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
        this.homologationProfile = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("homolog")
                        || profile.equals("homologacao")
                        || profile.equals("staging"));
    }

    @Bean
    @ConditionalOnProperty(name = "shipping.gateway", havingValue = "melhorEnvio")
    RestClientCustomizer melhorEnvioHttpTimeouts(
            @Value("${shipping.http.connect-timeout:PT3S}") Duration connectTimeout,
            @Value("${shipping.http.read-timeout:PT10S}") Duration readTimeout) {
        return builder -> {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(connectTimeout);
            requestFactory.setReadTimeout(readTimeout);
            builder.requestFactory(requestFactory);
        };
    }

    @PostConstruct
    void validateConfiguration() {
        if (productionProfile && !"melhorEnvio".equals(gateway)) {
            throw new IllegalStateException(
                    "Production requires shipping.gateway=melhorEnvio; simulated shipping is disabled.");
        }
        if (productionProfile && "melhorEnvio".equals(gateway)
                && !"production".equals(properties.getEnvironment())) {
            throw new IllegalStateException(
                    "Production profiles require MELHOR_ENVIO_ENV=production.");
        }
        if (homologationProfile && "melhorEnvio".equals(gateway)
                && !"sandbox".equals(properties.getEnvironment())) {
            throw new IllegalStateException(
                    "Homologation profiles require MELHOR_ENVIO_ENV=sandbox.");
        }
        if (!"melhorEnvio".equals(gateway)) {
            return;
        }

        require(properties.getEnvironment(), "MELHOR_ENVIO_ENV");
        if (!properties.getEnvironment().equals("sandbox")
                && !properties.getEnvironment().equals("production")) {
            throw new IllegalStateException("MELHOR_ENVIO_ENV must be sandbox or production.");
        }
        require(properties.getClientId(), "MELHOR_ENVIO_CLIENT_ID");
        require(properties.getClientSecret(), "MELHOR_ENVIO_CLIENT_SECRET");
        require(properties.getRedirectUri(), "MELHOR_ENVIO_REDIRECT_URI");
        require(properties.getApiBaseUrl(), "MELHOR_ENVIO_API_BASE_URL");
        require(properties.getAuthorizationUrl(), "MELHOR_ENVIO_AUTHORIZATION_URL");
        require(properties.getTokenUrl(), "MELHOR_ENVIO_TOKEN_URL");
        require(properties.getScopes(), "MELHOR_ENVIO_SCOPES");
        if (!hasScope(properties.getScopes(), "shipping-calculate")) {
            throw new IllegalStateException(
                    "MELHOR_ENVIO_SCOPES must include shipping-calculate.");
        }
        require(properties.getUserAgent(), "MELHOR_ENVIO_USER_AGENT");
        require(properties.getTokenEncryptionKey(), "MELHOR_ENVIO_TOKEN_ENCRYPTION_KEY");
        validateHttpsUrl(properties.getApiBaseUrl(), "MELHOR_ENVIO_API_BASE_URL");
        validateHttpsUrl(properties.getAuthorizationUrl(), "MELHOR_ENVIO_AUTHORIZATION_URL");
        validateHttpsUrl(properties.getTokenUrl(), "MELHOR_ENVIO_TOKEN_URL");
        validateEnvironmentHost(properties.getApiBaseUrl());
        validateEnvironmentHost(properties.getAuthorizationUrl());
        validateEnvironmentHost(properties.getTokenUrl());
        URI redirect = parseUri(properties.getRedirectUri(), "MELHOR_ENVIO_REDIRECT_URI");
        boolean localRedirect = "localhost".equalsIgnoreCase(redirect.getHost())
                || "127.0.0.1".equals(redirect.getHost());
        if (!"https".equalsIgnoreCase(redirect.getScheme())
                && !(properties.getEnvironment().equals("sandbox")
                        && localRedirect
                        && "http".equalsIgnoreCase(redirect.getScheme()))) {
            throw new IllegalStateException("MELHOR_ENVIO_REDIRECT_URI must use HTTPS.");
        }
        if (!properties.getUserAgent().contains("@")) {
            throw new IllegalStateException("MELHOR_ENVIO_USER_AGENT must include a support email.");
        }
        if (properties.getStateTtl().isNegative() || properties.getStateTtl().isZero()
                || properties.getQuoteTtl().isNegative() || properties.getQuoteTtl().isZero()
                || properties.getRefreshSkew().isNegative()
                || properties.getRefreshTokenTtl().isNegative()
                || properties.getRefreshTokenTtl().isZero()) {
            throw new IllegalStateException("Melhor Envio state, quote, and refresh durations must be valid.");
        }
    }

    private void require(String value, String variable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variable + " is required when the Melhor Envio gateway is enabled.");
        }
    }

    private void validateHttpsUrl(String value, String variable) {
        URI uri = parseUri(value, variable);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalStateException(variable + " must be an absolute HTTPS URL.");
        }
    }

    private void validateEnvironmentHost(String value) {
        String host = URI.create(value).getHost().toLowerCase(Locale.ROOT);
        String expectedHost = properties.getEnvironment().equals("sandbox")
                ? "sandbox.melhorenvio.com.br"
                : "melhorenvio.com.br";
        if (!expectedHost.equals(host)) {
            throw new IllegalStateException(
                    "Melhor Envio URLs must match MELHOR_ENVIO_ENV.");
        }
    }

    private boolean hasScope(String scopes, String requiredScope) {
        return Arrays.stream(scopes.trim().split("\\s+"))
                .anyMatch(requiredScope::equals);
    }

    private URI parseUri(String value, String variable) {
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(variable + " must be a valid absolute URL.");
        }
    }
}
