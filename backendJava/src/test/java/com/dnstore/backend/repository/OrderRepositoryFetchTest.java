package com.dnstore.backend.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRepositoryFetchTest {

    /**
     * GET /api/orders/all devolve entidades; Order.user é LAZY e o Hibernate6Module serializa
     * associações não inicializadas como null, então o painel admin perdia nome/email do cliente.
     */
    @Test
    void findAll_fetchesUserSoAdminListShowsCustomer() throws NoSuchMethodException {
        EntityGraph graph = OrderRepository.class.getMethod("findAll").getAnnotation(EntityGraph.class);

        assertThat(graph).isNotNull();
        assertThat(graph.attributePaths()).contains("user");
    }
}
