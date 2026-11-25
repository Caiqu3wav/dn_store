package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dnstore.backend.exception.StaleShippingQuoteException;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.UserRepository;
import java.math.BigDecimal;
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
 * Cotação de frete de ponta a ponta contra um MySQL real e descartável (127.0.0.1:33316, dn_store_it),
 * com o gateway simulado: dimensões lidas de physical_products por Hibernate real, consulta de
 * reaproveitamento (JPQL) e rejeição de cotação vencida. Nunca aponte este teste para o banco real.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        args = {
                "--spring.config.location=classpath:/application-integration-local.properties",
                "--spring.datasource.password=dn_store_it_test_password",
                "--shipping.origin-zip=12600100"
        })
@ActiveProfiles("integration-local")
class ShippingQuoteMySqlIT {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrderService orderService;
    @Autowired
    private UserRepository userRepository;

    private String userId;
    private String addressId;
    private String productId;
    private String variantId;
    private String cartId;

    @BeforeEach
    void fixtures() {
        userId = id();
        addressId = id();
        productId = id();
        variantId = id();
        cartId = id();
        jdbc.update("INSERT INTO users (id, name, email, password_hash, role) VALUES (?, ?, ?, ?, 'USER')",
                userId, "Cliente frete IT", userId + "@example.invalid", "not-a-real-password");
        jdbc.update("INSERT INTO addresses (id, user_id, street, city, state, zip_code) VALUES (?, ?, ?, ?, ?, ?)",
                addressId, userId, "Rua IT", "Cidade IT", "TS", "12500-000");
        jdbc.update("INSERT INTO products (id, name, price, product_type, active) VALUES (?, ?, ?, ?, TRUE)",
                productId, "Produto IT frete", new BigDecimal("100.00"), "PhysicalProduct");
        jdbc.update("INSERT INTO physical_products (id, stock, weight, width, height, depth) "
                + "VALUES (?, 10, 0.25, 22, 4, 30)", productId);
        jdbc.update("INSERT INTO product_variants (id, product_id, size, stock, active) VALUES (?, ?, 'M', 5, TRUE)",
                variantId, productId);
        jdbc.update("INSERT INTO carts (id, user_id) VALUES (?, ?)", cartId, userId);
        jdbc.update("INSERT INTO cart_items (id, cart_id, product_variant_id, quantity) VALUES (?, ?, ?, 2)",
                id(), cartId, variantId);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM shipping_quote_options WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM cart_items WHERE cart_id = ?", cartId);
        jdbc.update("DELETE FROM carts WHERE id = ?", cartId);
        jdbc.update("DELETE FROM product_variants WHERE id = ?", variantId);
        jdbc.update("DELETE FROM physical_products WHERE id = ?", productId);
        jdbc.update("DELETE FROM products WHERE id = ?", productId);
        jdbc.update("DELETE FROM addresses WHERE id = ?", addressId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void quotesFromPersistedPackageMeasurementsAndReusesTheRecentQuote() {
        User user = userRepository.findById(UUID.fromString(userId)).orElseThrow();

        List<OrderService.ShippingOption> first = orderService.quoteShipping(user, "12500-000");
        List<OrderService.ShippingOption> second = orderService.quoteShipping(user, "12500-000");

        assertThat(first).isNotEmpty();
        assertThat(second).extracting(OrderService.ShippingOption::id)
                .containsExactlyInAnyOrderElementsOf(first.stream().map(OrderService.ShippingOption::id).toList());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shipping_quote_options WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(first.size());
    }

    @Test
    void differentZipOrChangedCartGetsAFreshQuote() {
        User user = userRepository.findById(UUID.fromString(userId)).orElseThrow();
        List<OrderService.ShippingOption> first = orderService.quoteShipping(user, "12500-000");

        List<OrderService.ShippingOption> otherZip = orderService.quoteShipping(user, "01001-000");
        jdbc.update("UPDATE cart_items SET quantity = 3 WHERE cart_id = ?", cartId);
        List<OrderService.ShippingOption> otherCart = orderService.quoteShipping(user, "12500-000");

        assertThat(otherZip).extracting(OrderService.ShippingOption::id)
                .doesNotContainAnyElementsOf(first.stream().map(OrderService.ShippingOption::id).toList());
        assertThat(otherCart).extracting(OrderService.ShippingOption::id)
                .doesNotContainAnyElementsOf(first.stream().map(OrderService.ShippingOption::id).toList());
    }

    @Test
    void checkoutRejectsAnExpiredQuoteWithTheStaleCode() {
        User user = userRepository.findById(UUID.fromString(userId)).orElseThrow();
        UUID quoteId = orderService.quoteShipping(user, "12500-000").get(0).id();
        jdbc.update("UPDATE shipping_quote_options SET expires_at = ? WHERE id = ?",
                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(5), quoteId.toString());

        assertThatThrownBy(() -> orderService.checkout(
                user, null, quoteId, null, UUID.fromString(addressId)))
                .isInstanceOf(StaleShippingQuoteException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id = ?", Integer.class, userId))
                .isZero();
    }

    private String id() {
        return UUID.randomUUID().toString();
    }
}
