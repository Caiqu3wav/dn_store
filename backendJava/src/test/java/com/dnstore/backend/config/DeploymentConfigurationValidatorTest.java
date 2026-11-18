package com.dnstore.backend.config;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentConfigurationValidatorTest {

    private static final String DB_URL = "jdbc:mysql://mysql.example.com:3306/dn_store?ssl-mode=REQUIRED";
    private static final String JWT_SECRET = Base64.getEncoder().encodeToString(new byte[32]).replace('A', 'q');
    private static final String CHALLENGE_SECRET = "0123456789abcdef0123456789abcdef-challenge";
    private static final String FRONTEND_URL = "https://loja.vercel.app";
    private static final String CORS = "https://loja.vercel.app";

    private static DeploymentConfigurationValidator validator(
            String dbUrl, String jwtSecret, String challengeSecret, String frontendUrl, String cors) {
        return new DeploymentConfigurationValidator(dbUrl, jwtSecret, challengeSecret, frontendUrl, cors);
    }

    @Test
    void validConfiguration_shouldPass() {
        assertThatCode(() -> validator(DB_URL, JWT_SECRET, CHALLENGE_SECRET, FRONTEND_URL, CORS).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void databaseWithoutTls_shouldFail() {
        for (String url : new String[] {
                "jdbc:mysql://host:3306/db",
                "jdbc:mysql://host:3306/db?ssl-mode=DISABLED",
                "jdbc:mysql://host:3306/db?ssl-mode=PREFERRED",
                "jdbc:mysql://host:3306/db?useSSL=false"}) {
            assertThatThrownBy(() -> validator(url, JWT_SECRET, CHALLENGE_SECRET, FRONTEND_URL, CORS).validate())
                    .hasMessageContaining("DB_URL");
        }
    }

    @Test
    void databaseWithStrictTlsModes_shouldPass() {
        for (String mode : new String[] {"REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY"}) {
            assertThatCode(() -> validator("jdbc:mysql://h:3306/db?ssl-mode=" + mode + "&x=1",
                    JWT_SECRET, CHALLENGE_SECRET, FRONTEND_URL, CORS).validate())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void weakOrExampleJwtSecret_shouldFailWithoutLeakingValue() {
        String shortSecret = Base64.getEncoder().encodeToString("curto".getBytes());
        String devSecret = "c2VjcmV0LWRldi1kbi1zdG9yZS1jaGF2ZS1sb2NhbC0yMDI1";
        for (String secret : new String[] {shortSecret, devSecret, "nao-e-base64!!", ""}) {
            assertThatThrownBy(() -> validator(DB_URL, secret, CHALLENGE_SECRET, FRONTEND_URL, CORS).validate())
                    .hasMessageContaining("JWT_SECRET")
                    .satisfies(error -> {
                        if (!secret.isEmpty()) {
                            org.assertj.core.api.Assertions.assertThat(error.getMessage()).doesNotContain(secret);
                        }
                    });
        }
    }

    @Test
    void weakOrExampleChallengeSecret_shouldFail() {
        for (String secret : new String[] {"curto", "replace-with-a-long-random-secret-xxxxxxxxxxxx", ""}) {
            assertThatThrownBy(() -> validator(DB_URL, JWT_SECRET, secret, FRONTEND_URL, CORS).validate())
                    .hasMessageContaining("AUTH_CHALLENGE_SECRET");
        }
    }

    @Test
    void frontendUrl_mustBePublicHttps() {
        for (String url : new String[] {"http://loja.vercel.app", "https://localhost:3000", "http://127.0.0.1:3000", ""}) {
            assertThatThrownBy(() -> validator(DB_URL, JWT_SECRET, CHALLENGE_SECRET, url, CORS).validate())
                    .hasMessageContaining("FRONTEND_URL");
        }
    }

    @Test
    void corsOrigins_mustBePublicHttpsAndNeverWildcard() {
        for (String cors : new String[] {
                "http://loja.vercel.app", "*", "https://loja.vercel.app, http://localhost:3000", "https://*.vercel.app", ""}) {
            assertThatThrownBy(() -> validator(DB_URL, JWT_SECRET, CHALLENGE_SECRET, FRONTEND_URL, cors).validate())
                    .hasMessageContaining("CORS_ALLOWED_ORIGINS");
        }
    }

    @Test
    void corsOrigins_shouldAcceptSeveralHttpsOriginsWithSpacesAndTrailingSlash() {
        assertThatCode(() -> validator(DB_URL, JWT_SECRET, CHALLENGE_SECRET, FRONTEND_URL,
                "https://loja.vercel.app/, https://www.loja.com").validate())
                .doesNotThrowAnyException();
    }
}
