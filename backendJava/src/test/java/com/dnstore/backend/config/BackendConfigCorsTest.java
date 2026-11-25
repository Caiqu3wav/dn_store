package com.dnstore.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BackendConfigCorsTest {

    @Test
    void parseAllowedOrigins_shouldTrimWhitespaceAndTrailingSlash() {
        String[] origins = BackendConfig.parseAllowedOrigins(
                " https://loja.vercel.app/ , https://www.loja.com,, ");

        assertThat(origins).containsExactly("https://loja.vercel.app", "https://www.loja.com");
    }

    @Test
    void corsMapping_shouldAllowConfiguredOriginsAndNoOthers() {
        WebMvcConfigurer configurer = new BackendConfig(new AsaasProperties())
                .corsConfigurer("https://loja.vercel.app/, https://www.loja.com");

        class Registry extends CorsRegistry {
            Map<String, CorsConfiguration> configurations() {
                return getCorsConfigurations();
            }
        }
        Registry registry = new Registry();
        configurer.addCorsMappings(registry);

        CorsConfiguration config = registry.configurations().get("/**");
        assertThat(config.getAllowedOrigins()).containsExactly("https://loja.vercel.app", "https://www.loja.com");
        assertThat(config.checkOrigin("https://loja.vercel.app")).isEqualTo("https://loja.vercel.app");
        assertThat(config.checkOrigin("https://evil.example")).isNull();
        assertThat(config.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
