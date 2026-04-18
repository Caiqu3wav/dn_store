package com.dnstore.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

/**
 * ⚙️ Configuração do Spring
 *
 * Define Beans que serão gerenciados pelo container do Spring (Injeção de Dependência).
 */
@Configuration
public class BackendConfig {

    /**
     * Disponibiliza o RestTemplate para fazer requisições HTTP externas (ex: ViaCEP).
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
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
