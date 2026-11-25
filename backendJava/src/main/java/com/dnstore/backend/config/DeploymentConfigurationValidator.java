package com.dnstore.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recusa iniciar em staging/produção com configuração insegura ou de exemplo: banco sem TLS,
 * segredos fracos ou conhecidos, e URLs do frontend/CORS em HTTP ou localhost. As mensagens citam
 * apenas o nome da variável, nunca o valor.
 */
@Component
@Profile({"prod", "production", "staging", "homolog", "homologacao"})
class DeploymentConfigurationValidator {

    private static final int MIN_JWT_SECRET_BYTES = 32;
    private static final int MIN_CHALLENGE_SECRET_CHARS = 32;
    private static final Set<String> STRICT_SSL_MODES = Set.of("REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY");
    private static final Set<String> KNOWN_EXAMPLE_JWT_SECRETS =
            Set.of("c2VjcmV0LWRldi1kbi1zdG9yZS1jaGF2ZS1sb2NhbC0yMDI1");
    private static final Pattern SSL_MODE = Pattern.compile("[?&;]ssl-?mode=([^&;]+)", Pattern.CASE_INSENSITIVE);

    private final String dbUrl;
    private final String jwtSecret;
    private final String challengeSecret;
    private final String frontendUrl;
    private final String corsOrigins;

    DeploymentConfigurationValidator(
            @Value("${spring.datasource.url:}") String dbUrl,
            @Value("${jwt.secret:}") String jwtSecret,
            @Value("${auth.challenge-secret:}") String challengeSecret,
            @Value("${app.frontend-url:}") String frontendUrl,
            @Value("${cors.allowed-origins:}") String corsOrigins) {
        this.dbUrl = dbUrl;
        this.jwtSecret = jwtSecret;
        this.challengeSecret = challengeSecret;
        this.frontendUrl = frontendUrl;
        this.corsOrigins = corsOrigins;
    }

    @PostConstruct
    void validate() {
        validateDatabaseTls();
        validateJwtSecret();
        validateChallengeSecret();
        requirePublicHttps(frontendUrl, "FRONTEND_URL");
        validateCorsOrigins();
    }

    private void validateDatabaseTls() {
        Matcher matcher = SSL_MODE.matcher(dbUrl == null ? "" : dbUrl);
        if (!matcher.find() || !STRICT_SSL_MODES.contains(matcher.group(1).toUpperCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "DB_URL deve exigir TLS (ssl-mode=REQUIRED, VERIFY_CA ou VERIFY_IDENTITY).");
        }
    }

    private void validateJwtSecret() {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(jwtSecret == null ? "" : jwtSecret.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET deve ser um valor aleatório em Base64.");
        }
        if (decoded.length < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET deve ter pelo menos " + MIN_JWT_SECRET_BYTES + " bytes (gere com: openssl rand -base64 32).");
        }
        if (KNOWN_EXAMPLE_JWT_SECRETS.contains(jwtSecret.trim())) {
            throw new IllegalStateException("JWT_SECRET é um valor de exemplo/desenvolvimento; gere um novo.");
        }
    }

    private void validateChallengeSecret() {
        String secret = challengeSecret == null ? "" : challengeSecret.trim();
        if (secret.length() < MIN_CHALLENGE_SECRET_CHARS
                || secret.toLowerCase(Locale.ROOT).contains("replace-with")
                || secret.toLowerCase(Locale.ROOT).contains("changeme")) {
            throw new IllegalStateException(
                    "AUTH_CHALLENGE_SECRET deve ser um valor aleatório com pelo menos "
                            + MIN_CHALLENGE_SECRET_CHARS + " caracteres (gere com: openssl rand -hex 32).");
        }
    }

    private void validateCorsOrigins() {
        String[] origins = BackendConfig.parseAllowedOrigins(corsOrigins == null ? "" : corsOrigins);
        if (origins.length == 0) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS é obrigatório (URL pública do frontend).");
        }
        for (String origin : origins) {
            requirePublicHttps(origin, "CORS_ALLOWED_ORIGINS");
        }
    }

    private void requirePublicHttps(String value, String variable) {
        URI uri;
        try {
            uri = URI.create(value == null ? "" : value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(variable + " deve ser uma URL HTTPS pública válida.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")
                || host.equals("[::1]") || host.endsWith(".localhost");
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host.isEmpty() || host.contains("*") || local) {
            throw new IllegalStateException(variable + " deve ser uma URL HTTPS pública (sem localhost nem curinga).");
        }
    }
}
