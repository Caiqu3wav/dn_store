
package com.dnstore.backend.config;

import com.dnstore.backend.controller.MelhorEnvioOAuthController;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MelhorEnvioConfigurationTest {

        @Test
        void productionProfile_rejectsSimulatedShippingGateway() {
                MockEnvironment environment = new MockEnvironment();
                environment.setActiveProfiles("prod");

                MelhorEnvioConfiguration configuration = new MelhorEnvioConfiguration(
                                new MelhorEnvioProperties(), "simulated", environment);

                assertThatThrownBy(configuration::validateConfiguration)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("simulated shipping is disabled");
        }

        @Test
        void productionProfile_rejectsSandboxEnvironment() {
                MockEnvironment environment = new MockEnvironment();
                environment.setActiveProfiles("prod");

                MelhorEnvioConfiguration configuration = new MelhorEnvioConfiguration(
                                configuredProperties(), "melhorEnvio", environment);

                assertThatThrownBy(configuration::validateConfiguration)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("MELHOR_ENVIO_ENV=production");
        }

        @Test
        void productionProfile_acceptsProductionEnvironment() {
                MockEnvironment environment = new MockEnvironment();
                environment.setActiveProfiles("production");

                MelhorEnvioProperties properties = configuredProperties();
                configureProductionUrls(properties);

                MelhorEnvioConfiguration configuration = new MelhorEnvioConfiguration(
                                properties, "melhorEnvio", environment);

                configuration.validateConfiguration();
        }

        @Test
        void homologationProfile_requiresSandboxEnvironment() {
                MockEnvironment environment = new MockEnvironment();
                environment.setActiveProfiles("homolog");

                MelhorEnvioProperties properties = configuredProperties();
                properties.setEnvironment("production");

                MelhorEnvioConfiguration configuration = new MelhorEnvioConfiguration(
                                properties, "melhorEnvio", environment);

                assertThatThrownBy(configuration::validateConfiguration)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("MELHOR_ENVIO_ENV=sandbox");
        }

        @Test
        void homologationProfile_acceptsSandboxEnvironment() {
                MockEnvironment environment = new MockEnvironment();
                environment.setActiveProfiles("staging");

                MelhorEnvioConfiguration configuration = new MelhorEnvioConfiguration(
                                configuredProperties(), "melhorEnvio", environment);

                configuration.validateConfiguration();
        }

        @Test
        void homologacaoProfile_requiresSandboxEnvironment() {
                MockEnvironment environment = new MockEnvironment();
                environment.setActiveProfiles("homologacao");

                MelhorEnvioProperties properties = configuredProperties();
                properties.setEnvironment("production");

                MelhorEnvioConfiguration configuration = new MelhorEnvioConfiguration(
                                properties, "melhorEnvio", environment);

                assertThatThrownBy(configuration::validateConfiguration)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("MELHOR_ENVIO_ENV=sandbox");
        }

        @Test
        void homologationProfile_allowsSimulatedGateway() {
                MockEnvironment environment = new MockEnvironment();
                environment.setActiveProfiles("homolog");

                MelhorEnvioConfiguration configuration = new MelhorEnvioConfiguration(
                                new MelhorEnvioProperties(), "simulated", environment);

                configuration.validateConfiguration();
        }

        @Test
        void realGateway_withoutApplicationCredentialsFailsStartupValidation() {
                MelhorEnvioConfiguration configuration = new MelhorEnvioConfiguration(
                                new MelhorEnvioProperties(), "melhorEnvio", new MockEnvironment());

                assertThatThrownBy(configuration::validateConfiguration)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("MELHOR_ENVIO_CLIENT_ID is required");
        }

        @Test
        void invalidEnvironmentIsRejectedRatherThanFallingBackToSimulatedGateway() {
                MelhorEnvioProperties properties = new MelhorEnvioProperties();
                properties.setEnvironment("staging");

                MelhorEnvioConfiguration configuration = new MelhorEnvioConfiguration(
                                properties, "melhorEnvio", new MockEnvironment());

                assertThatThrownBy(configuration::validateConfiguration)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("MELHOR_ENVIO_ENV must be sandbox or production");
        }

        @Test
        void authorizationStartEndpointRequiresAdministratorRole() throws NoSuchMethodException {
                PreAuthorize annotation = MelhorEnvioOAuthController.class
                                .getMethod("authorize")
                                .getAnnotation(PreAuthorize.class);

                assertThat(annotation)
                                .isNotNull()
                                .extracting(PreAuthorize::value)
                                .isEqualTo("hasRole('ADMIN')");
        }

        @Test
        void sandboxRequiresOfficialHostsAndShippingCalculateScope() {
                MelhorEnvioProperties properties = configuredProperties();

                MelhorEnvioConfiguration configuration = new MelhorEnvioConfiguration(
                                properties, "melhorEnvio", new MockEnvironment());

                configuration.validateConfiguration();

                assertThat(properties.getScopes()).contains("shipping-calculate");

                properties.setApiBaseUrl(
                                "https://sandbox.melhorenvio.com.br.attacker.test");

                assertThatThrownBy(configuration::validateConfiguration)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("URLs must match");
        }

        @Test
        void authorizationUrlRejectsHostOutsideSandbox() {
                MelhorEnvioProperties properties = configuredProperties();
                properties.setAuthorizationUrl(
                                "https://melhorenvio.com.br/oauth/authorize");

                MelhorEnvioConfiguration configuration = new MelhorEnvioConfiguration(
                                properties, "melhorEnvio", new MockEnvironment());

                assertThatThrownBy(configuration::validateConfiguration)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("URLs must match");
        }

        @Test
        void tokenUrlRejectsHostOutsideSandbox() {
                MelhorEnvioProperties properties = configuredProperties();
                properties.setTokenUrl(
                                "https://melhorenvio.com.br/oauth/token");

                MelhorEnvioConfiguration configuration = new MelhorEnvioConfiguration(
                                properties, "melhorEnvio", new MockEnvironment());

                assertThatThrownBy(configuration::validateConfiguration)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("URLs must match");
        }

        private MelhorEnvioProperties configuredProperties() {
                MelhorEnvioProperties properties = new MelhorEnvioProperties();

                properties.setEnvironment("sandbox");
                properties.setClientId("client-id");
                properties.setClientSecret("client-secret");
                properties.setRedirectUri("https://store.test/callback");
                properties.setApiBaseUrl("https://sandbox.melhorenvio.com.br");
                properties.setAuthorizationUrl(
                                "https://sandbox.melhorenvio.com.br/oauth/authorize");
                properties.setTokenUrl(
                                "https://sandbox.melhorenvio.com.br/oauth/token");
                properties.setScopes("shipping-calculate");
                properties.setUserAgent("DN Store (support@example.com)");
                properties.setTokenEncryptionKey("key");

                return properties;
        }

        private void configureProductionUrls(MelhorEnvioProperties properties) {
                properties.setEnvironment("production");
                properties.setApiBaseUrl("https://melhorenvio.com.br");
                properties.setAuthorizationUrl(
                                "https://melhorenvio.com.br/oauth/authorize");
                properties.setTokenUrl(
                                "https://melhorenvio.com.br/oauth/token");
        }
}
