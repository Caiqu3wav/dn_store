package com.dnstore.backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AsaasPropertiesTest {

    @Test
    void validSandboxConfiguration_shouldPassWithoutRealCredentials() {
        AsaasProperties properties = properties("sandbox", AsaasProperties.SANDBOX_URL, "");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void validProductionConfiguration_shouldPassWithoutCallingProvider() {
        AsaasProperties properties = properties("production", AsaasProperties.PRODUCTION_URL, "$aact_prod_test");
        properties.setWebhookToken("production-webhook-token");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void productionWithoutApiKey_shouldFailWithoutExposingSecret() {
        AsaasProperties properties = properties("production", AsaasProperties.PRODUCTION_URL, "");

        assertThatThrownBy(properties::validate)
                .hasMessage("ASAAS_API_KEY é obrigatório em production.");
    }

    @Test
    void productionWithoutWebhookToken_shouldFail() {
        AsaasProperties properties = properties("production", AsaasProperties.PRODUCTION_URL, "$aact_prod_test");

        assertThatThrownBy(properties::validate)
                .hasMessage("ASAAS_WEBHOOK_TOKEN é obrigatório em production.");
    }

    @Test
    void productionWithSandboxUrl_shouldFail() {
        AsaasProperties properties = properties("production", AsaasProperties.SANDBOX_URL, "$aact_prod_test");
        properties.setWebhookToken("production-webhook-token");

        assertThatThrownBy(properties::validate)
                .hasMessage("ASAAS_BASE_URL não corresponde ao ASAAS_ENVIRONMENT configurado.");
    }

    @Test
    void sandboxWithProductionUrl_shouldFail() {
        AsaasProperties properties = properties("sandbox", AsaasProperties.PRODUCTION_URL, "");

        assertThatThrownBy(properties::validate)
                .hasMessage("ASAAS_BASE_URL não corresponde ao ASAAS_ENVIRONMENT configurado.");
    }

    @Test
    void sandboxWithProductionApiKey_shouldFail() {
        AsaasProperties properties = properties("sandbox", AsaasProperties.SANDBOX_URL, "$aact_prod_test");

        assertThatThrownBy(properties::validate)
                .hasMessage("ASAAS_API_KEY não corresponde ao ambiente sandbox.");
    }

    @Test
    void nonPositiveTimeout_shouldFail() {
        AsaasProperties properties = properties("sandbox", AsaasProperties.SANDBOX_URL, "");
        properties.setConnectTimeout(Duration.ZERO);

        assertThatThrownBy(properties::validate)
                .hasMessage("ASAAS_HTTP_CONNECT_TIMEOUT deve ser maior que zero.");
    }

    @Test
    void defaultHttpTimeouts_shouldBeSafeAndIndependent() {
        AsaasProperties properties = new AsaasProperties();

        assertThatCode(properties::validate).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(properties.getConnectTimeout())
                .isEqualTo(Duration.ofSeconds(3));
        org.assertj.core.api.Assertions.assertThat(properties.getReadTimeout())
                .isEqualTo(Duration.ofSeconds(10));
        org.assertj.core.api.Assertions.assertThat(properties.getConnectTimeout())
                .isNotEqualTo(properties.getReadTimeout());
    }

    private AsaasProperties properties(String environment, String baseUrl, String apiKey) {
        AsaasProperties properties = new AsaasProperties();
        properties.setEnvironment(environment);
        properties.setBaseUrl(baseUrl);
        properties.setApiKey(apiKey);
        properties.setUserAgent("DN-Store Test/1.0");
        return properties;
    }
}
