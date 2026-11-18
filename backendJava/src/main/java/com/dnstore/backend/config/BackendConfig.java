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
                        .allowedOrigins(parseAllowedOrigins(allowedOrigins))
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .maxAge(3600);
            }
        };
    }

    /**
     * Converte a lista separada por vírgulas em origens exatas. Remove espaços e barra final
     * (o navegador envia a origem sem barra) para que um valor de ambiente como
     * "https://loja.vercel.app/, https://www.loja.com" continue funcionando.
     */
    static String[] parseAllowedOrigins(String allowedOrigins) {
        return java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }
}
