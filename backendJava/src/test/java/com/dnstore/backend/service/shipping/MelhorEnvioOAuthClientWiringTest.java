package com.dnstore.backend.service.shipping;

import com.dnstore.backend.config.MelhorEnvioProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os testes unitários constroem o cliente à mão; este sobe um contexto Spring para garantir
 * que o container consegue escolher o construtor (em produção shipping.gateway=melhorEnvio).
 */
class MelhorEnvioOAuthClientWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(MelhorEnvioProperties.class, MelhorEnvioProperties::new)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(MelhorEnvioOAuthClient.class);

    @Test
    void melhorEnvioGateway_shouldCreateOAuthClientBean() {
        runner.withPropertyValues("shipping.gateway=melhorEnvio")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MelhorEnvioOAuthClient.class);
                });
    }

    @Test
    void simulatedGateway_shouldNotCreateOAuthClientBean() {
        runner.withPropertyValues("shipping.gateway=simulated")
                .run(context -> assertThat(context).doesNotHaveBean(MelhorEnvioOAuthClient.class));
    }
}
