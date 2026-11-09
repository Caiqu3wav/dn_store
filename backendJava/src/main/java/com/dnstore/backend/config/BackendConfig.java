package com.dnstore.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * ⚙️ Configuração do Spring
 *
 * Define Beans que serão gerenciados pelo container do Spring (Injeção de Dependência).
 */
@Configuration
@EnableConfigurationProperties(AsaasProperties.class)
@org.springframework.scheduling.annotation.EnableScheduling
public class BackendConfig {

    private final AsaasProperties asaasProperties;

    public BackendConfig(AsaasProperties asaasProperties) {
        this.asaasProperties = asaasProperties;
        asaasProperties.validate();
    }

    /**
     * Disponibiliza o RestTemplate para fazer requisições HTTP externas (ex: ViaCEP).
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean(name = "asaasRestTemplate")
    public RestTemplate asaasRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(asaasProperties.getConnectTimeout());
        requestFactory.setReadTimeout(asaasProperties.getReadTimeout());
        return new RestTemplate(requestFactory);
    }

    /**
     * Password encoder for user authentication
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configuração Global de CORS.
     * Lê a URL permitida do application.properties (ou variável de ambiente).
     */
    @Bean
    public org.springframework.web.servlet.config.annotation.WebMvcConfigurer corsConfigurer(
            @org.springframework.beans.factory.annotation.Value("${cors.allowed-origins}") String allowedOrigins) {
        return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer() {
            @Override
            public void addCorsMappings(org.springframework.web.servlet.config.annotation.CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(allowedOrigins.split(","))
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
            }
        };
    }
}
