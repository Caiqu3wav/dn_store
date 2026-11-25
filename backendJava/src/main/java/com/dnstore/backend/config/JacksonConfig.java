package com.dnstore.backend.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Vários endpoints devolvem entidades JPA. Sem este módulo, o Jackson falha com
 * "No serializer found for class ByteBuddyInterceptor" ao encontrar proxies lazy do Hibernate
 * (ex.: GET /api/cart com itens). Associações lazy ainda não carregadas são serializadas como null.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Module hibernate6Module() {
        return new Hibernate6Module();
    }
}
