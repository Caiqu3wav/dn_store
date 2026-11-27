package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dnstore.backend.model.Order;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * OrderService.findRecent (GET /api/orders/all?limit=&excludeStatus=): ordem, filtro de status e LIMIT
 * reais no MySQL. Usa o MySQL descartável do PaymentSchemaMySqlIT (127.0.0.1:33316, dn_store_it) e não roda
 * no `./mvnw test` padrão. Nunca aponte este teste para o banco real.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        args = {
                "--spring.config.location=classpath:/application-integration-local.properties",
                "--spring.datasource.password=dn_store_it_test_password"
        })
@ActiveProfiles("integration-local")
class AdminRecentOrdersMySqlIT {

    private static final List<String> UNPAID = List.of("PENDING_PAYMENT", "PAYMENT_FAILED", "EXPIRED");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrderService orderService;

    private final List<String> orderIds = new ArrayList<>();
    private final List<String> addressIds = new ArrayList<>();
    private String userId;

    @BeforeEach
    void fixtures() {
        userId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users (id, name, email, password_hash, role) VALUES (?, ?, ?, ?, 'USER')",
                userId, "Cliente recentes IT", userId + "@example.invalid", "not-a-real-password");
        // minutos atrás: quanto menor, mais recente
        seedOrder("PAID", 50);
        seedOrder("PENDING_PAYMENT", 40);
        seedOrder("SHIPPED", 30);
        seedOrder("EXPIRED", 20);
        seedOrder("DELIVERED", 10);
        seedOrder("PAID", 5);
    }

    @AfterEach
    void cleanup() {
        orderIds.forEach(id -> jdbc.update("DELETE FROM stock_movements WHERE order_id = ?", id));
        orderIds.forEach(id -> jdbc.update("DELETE FROM orders WHERE id = ?", id));
        addressIds.forEach(id -> jdbc.update("DELETE FROM addresses WHERE id = ?", id));
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        orderIds.clear();
        addressIds.clear();
    }

    @Test
    void returnsNewestPaidOrdersFirstAndAppliesTheLimitInSql() {
        List<Order> recent = mine(orderService.findRecent(3, UNPAID));

        assertThat(recent).extracting(Order::getStatus).containsExactly("PAID", "DELIVERED", "SHIPPED");
        assertThat(recent.get(0).getUser().getName()).isEqualTo("Cliente recentes IT");
    }

    @Test
    void withoutLimitReturnsEveryNonExcludedOrderNewestFirst() {
        List<Order> recent = mine(orderService.findRecent(null, UNPAID));

        assertThat(recent).extracting(Order::getStatus)
                .containsExactly("PAID", "DELIVERED", "SHIPPED", "PAID");
    }

    @Test
    void withoutExcludedStatusesKeepsEveryOrder() {
        List<Order> recent = mine(orderService.findRecent(null, List.of()));

        assertThat(recent).hasSize(6);
        assertThat(recent.get(0).getStatus()).isEqualTo("PAID");
    }

    /** O banco de teste pode ter pedidos de outros ITs; considera só os criados aqui. */
    private List<Order> mine(List<Order> orders) {
        return orders.stream().filter(order -> orderIds.contains(order.getId().toString())).toList();
    }

    private void seedOrder(String status, int minutesAgo) {
        String addressId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        addressIds.add(addressId);
        orderIds.add(orderId);
        jdbc.update("INSERT INTO addresses (id, user_id, street, city, state, zip_code) VALUES (?, ?, ?, ?, ?, ?)",
                addressId, userId, "Rua IT", "Cidade IT", "TS", "00000-000");
        jdbc.update("INSERT INTO orders (id, user_id, address_id, total, status, created_at) "
                + "VALUES (?, ?, ?, ?, ?, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL ? MINUTE))",
                orderId, userId, addressId, new BigDecimal("100.00"), status, minutesAgo);
    }
}
