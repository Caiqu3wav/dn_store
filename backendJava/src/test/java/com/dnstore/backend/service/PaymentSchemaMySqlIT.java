package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.enums.OrderStatus;
import com.dnstore.backend.model.enums.PaymentStatus;
import com.dnstore.backend.repository.PaymentRepository;
import com.dnstore.backend.service.payment.PaymentGateway;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        args = {
                "--spring.config.location=classpath:/application-integration-local.properties",
                "--spring.datasource.password=dn_store_it_test_password"
        })
@ActiveProfiles("integration-local")
class PaymentSchemaMySqlIT {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Flyway flyway;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentAttemptStateService paymentState;
    @Autowired
    private PaymentExpirationService expirationService;

    private final List<String> webhookEventIds = new ArrayList<>();
    private String userId;
    private String addressId;
    private String orderId;
    private String productId;
    private String variantId;
    private String shippingQuoteOptionId;

    @BeforeEach
    void insertOrderFixture() {
        // Recover artifacts from an interrupted local test run without touching non-test rows.
        jdbc.update("DELETE FROM payment_financial_blocks WHERE payment_id IN "
                + "(SELECT id FROM payments WHERE external_id LIKE 'it-%')");
        jdbc.update("DELETE FROM payment_financial_history WHERE payment_id IN "
                + "(SELECT id FROM payments WHERE external_id LIKE 'it-%')");
        jdbc.update("DELETE FROM payments WHERE external_id LIKE 'it-%'");
        userId = id();
        addressId = id();
        orderId = id();
        jdbc.update(
                "INSERT INTO users (id, name, email, password_hash) VALUES (?, ?, ?, ?)",
                userId, "Integration Test", userId + "@example.invalid", "not-a-real-password");
        jdbc.update(
                "INSERT INTO addresses (id, user_id, street, city, state, zip_code) VALUES (?, ?, ?, ?, ?, ?)",
                addressId, userId, "Local Test Street", "Test City", "TS", "00000-000");
        jdbc.update(
                "INSERT INTO orders (id, user_id, address_id, total, status, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                orderId, userId, addressId, new BigDecimal("25.00"), OrderStatus.PENDING_PAYMENT.name());
    }

    @AfterEach
    void deleteOrderFixture() {
        jdbc.update("DELETE FROM melhor_envio_oauth_states WHERE state_hash = 'it-state-hash'");
        if (orderId == null) {
            return;
        }
        jdbc.update("DELETE FROM payment_webhook_events WHERE event_id LIKE 'it-%'");
        jdbc.update("DELETE FROM payment_financial_blocks WHERE order_id = ?", orderId);
        jdbc.update("DELETE FROM payment_financial_history WHERE order_id = ?", orderId);
        jdbc.update("DELETE FROM payments WHERE order_id = ?", orderId);
        jdbc.update("DELETE FROM order_items WHERE order_id = ?", orderId);
        jdbc.update("DELETE FROM orders WHERE id = ?", orderId);
        if (shippingQuoteOptionId != null) {
            jdbc.update("DELETE FROM shipping_quote_options WHERE id = ?", shippingQuoteOptionId);
        }
        if (variantId != null) {
            jdbc.update("DELETE FROM product_variants WHERE id = ?", variantId);
        }
        if (productId != null) {
            jdbc.update("DELETE FROM physical_products WHERE id = ?", productId);
            jdbc.update("DELETE FROM products WHERE id = ?", productId);
        }
        jdbc.update("DELETE FROM addresses WHERE id = ?", addressId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        webhookEventIds.clear();
    }

    @Test
    void flywayAndHibernateValidateTheCompleteSchemaAgainstLocalMysql8() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .startsWith("jdbc:mysql://127.0.0.1:33316/dn_store_it");
            assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.");
        }

        flyway.validate();
                assertThat(flyway.info().applied())
                .hasSize(22)
                .extracting(info -> info.getVersion().getVersion())
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 22).mapToObj(String::valueOf).toList());
        assertThat(entityManagerFactory).isNotNull();
        assertThat(entityManagerFactory.isOpen()).isTrue();
        java.util.Map<String, ColumnDefinition> eventColumns = jdbc.query(
                        "SELECT column_name, data_type, character_maximum_length, is_nullable "
                                + "FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = 'events'",
                        (resultSet, rowNum) -> java.util.Map.entry(
                                resultSet.getString("column_name"),
                                new ColumnDefinition(
                                        resultSet.getString("data_type"),
                                        resultSet.getObject("character_maximum_length", Integer.class),
                                        resultSet.getString("is_nullable"))))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));
        assertThat(eventColumns)
                .containsEntry("date", new ColumnDefinition("datetime", null, "YES"))
                .containsEntry("event_date", new ColumnDefinition("date", null, "YES"))
                .containsEntry("map_url", new ColumnDefinition("varchar", 500, "YES"))
                .containsEntry("name", new ColumnDefinition("varchar", 150, "NO"))
                .containsEntry("image_url", new ColumnDefinition("varchar", 500, "YES"))
                .containsEntry("registration_url", new ColumnDefinition("varchar", 500, "YES"))
                .containsEntry("active", new ColumnDefinition("tinyint", null, "NO"));
    }

    @Test
    void shippingQuoteOptionsAreOwnedByCustomerAndConsumedByAtMostOneOrder() {
        shippingQuoteOptionId = id();
        jdbc.update(
                "INSERT INTO shipping_quote_options (id, user_id, service_id, service_name, price, delivery_days, "
                        + "source, provider_environment, origin_zip, destination_zip, cart_fingerprint, quoted_at, "
                        + "expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, "
                        + "DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 15 MINUTE))",
                shippingQuoteOptionId, userId, "1:12", "Correios PAC", new BigDecimal("19.90"), 4,
                "MELHOR_ENVIO", "sandbox", "01001000", "20040002", "it-fingerprint");
        jdbc.update(
                "UPDATE orders SET shipping_type = ?, shipping_service_id = ?, shipping_source = ?, "
                        + "shipping_cost = ?, shipping_deadline_days = ?, shipping_quote_option_id = ? WHERE id = ?",
                "Correios PAC", "1:12", "MELHOR_ENVIO", new BigDecimal("19.90"), 4, shippingQuoteOptionId, orderId);

        assertThat(jdbc.queryForObject(
                "SELECT shipping_cost FROM orders WHERE id = ?", BigDecimal.class, orderId))
                .isEqualByComparingTo("19.90");
        assertThat(jdbc.queryForObject(
                "SELECT service_id FROM shipping_quote_options WHERE id = ?", String.class, shippingQuoteOptionId))
                .isEqualTo("1:12");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO orders (id, user_id, address_id, total, status, shipping_quote_option_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id(), userId, addressId, new BigDecimal("19.90"), OrderStatus.PENDING_PAYMENT.name(),
                shippingQuoteOptionId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oauthStateAndEnvironmentTokenRowsHaveDatabaseUniquenessAndChecks() {
        jdbc.update(
                "INSERT INTO melhor_envio_oauth_states (state_hash, environment, expires_at) "
                        + "VALUES (?, ?, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 10 MINUTE))",
                "it-state-hash", "sandbox");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO melhor_envio_oauth_states (state_hash, environment, expires_at) "
                        + "VALUES (?, ?, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 10 MINUTE))",
                "it-state-hash", "sandbox"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO melhor_envio_tokens (environment, access_token_ciphertext, "
                        + "refresh_token_ciphertext, expires_at, refresh_expires_at, token_status) "
                        + "VALUES (?, ?, ?, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 DAY), "
                        + "DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 45 DAY), ?)",
                "sandbox", new byte[] { 1, 2, 3 }, new byte[] { 4, 5, 6 }, "INVALID"))
                .isInstanceOf(DataAccessException.class);

        jdbc.update("DELETE FROM melhor_envio_oauth_states WHERE state_hash = ?", "it-state-hash");
    }

    @Test
    void paymentsAllowMultipleAttemptsAndKeepTheirOrderRelationship() {
        insertPayment("key-1", 1, "it-charge-1");
        insertPayment("key-2", 2, "it-charge-2");

        List<Payment> attempts = paymentRepository.findByOrder_IdOrderByAttemptNumberDesc(uuid(orderId));
        assertThat(attempts).hasSize(2);
        assertThat(attempts).extracting(Payment::getAttemptNumber).containsExactly(2, 1);
        assertThat(attempts).allSatisfy(payment ->
                assertThat(payment.getOrder().getId()).isEqualTo(uuid(orderId)));
    }

    @Test
    void perOrderKeysAttemptNumbersAndExternalPaymentIdsAreUnique() {
        insertPayment("key-1", 1, "it-charge-1");

        assertThatThrownBy(() -> insertPayment("key-2", 1, "it-charge-2"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPayment("key-1", 2, "it-charge-2"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPayment("key-2", 2, "it-charge-1"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ?", Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void paymentForeignKeyRejectsAnUnknownOrder() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO payments (id, order_id, external_reference, idempotency_key, attempt_number, "
                        + "payment_method, amount, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id(), id(), "it-ref", "it-key", 1, "PIX", new BigDecimal("25.00"), "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void webhookEventIdIsPersistentlyUnique() {
        insertWebhookEvent("it-event-unique", "it-charge-unique");

        assertThatThrownBy(() -> insertWebhookEvent("it-event-unique", "it-charge-second"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_webhook_events WHERE event_id = ?",
                Integer.class, "it-event-unique")).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateWebhooksApplyTheFinancialTransitionOnlyOnce() throws Exception {
        insertPayment("key-webhook", 1, "it-charge-webhook");
        String externalReference = "integration-reference";
        jdbc.update(
                "UPDATE payments SET external_reference = ? WHERE order_id = ?",
                externalReference, orderId);
        String eventId = "it-event-" + id();
        webhookEventIds.add(eventId);
        PaymentGateway.WebhookResult event = new PaymentGateway.WebhookResult(
                eventId, "it-charge-webhook", "PAID", externalReference,
                new BigDecimal("25.00"), "BRL", "it-customer");
        PaymentGateway.ProviderPayment provider = new PaymentGateway.ProviderPayment(
                "it-charge-webhook", PaymentStatus.PAID.name(), externalReference,
                new BigDecimal("25.00"), "BRL", "it-customer", "PIX",
                null, null, null, null);

        List<Boolean> results = concurrently(
                () -> paymentState.applyWebhook(event, provider),
                () -> paymentState.applyWebhook(event, provider));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM payments WHERE external_id = ?",
                String.class, "it-charge-webhook")).isEqualTo(PaymentStatus.PAID.name());
        assertThat(jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo(OrderStatus.PAID.name());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_webhook_events WHERE event_id = ?",
                Integer.class, eventId)).isEqualTo(1);
    }

    @Test
    void concurrentRetryAndExpirationCannotCreateAnAttemptForAnExpiredOrder() throws Exception {
        prepareOneReservedInventoryItem();
        jdbc.update("UPDATE orders SET created_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 61 MINUTE) WHERE id = ?",
                orderId);

        List<Object> results = concurrently(
                () -> {
                    try {
                        return paymentState.prepareAttempt(
                                uuid(orderId), uuid(userId), "retry-key", "PIX", null);
                    } catch (ConflictException exception) {
                        return exception;
                    }
                },
                () -> {
                    expirationService.expireIfDue(uuid(orderId));
                    return "expired";
                });

        assertThat(results).hasSize(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ?", Integer.class, orderId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo(OrderStatus.EXPIRED.name());
        assertThat(jdbc.queryForObject(
                "SELECT stock_released FROM orders WHERE id = ?", Boolean.class, orderId)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT stock FROM product_variants WHERE id = ?", Integer.class, variantId)).isEqualTo(5);
    }

    private void insertPayment(String key, int attemptNumber, String externalId) {
        jdbc.update(
                "INSERT INTO payments (id, order_id, external_id, external_reference, idempotency_key, "
                        + "attempt_number, payment_method, amount, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id(), orderId, externalId, "it-ref-" + key, key, attemptNumber,
                "PIX", new BigDecimal("25.00"), PaymentStatus.PENDING.name());
    }

    private void insertWebhookEvent(String eventId, String externalPaymentId) {
        webhookEventIds.add(eventId);
        jdbc.update(
                "INSERT INTO payment_webhook_events "
                        + "(event_id, external_payment_id, event_type, received_at, processing_status, attempt_count) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?)",
                eventId, externalPaymentId, "LEGACY", "PROCESSED", 0);
    }

    private void prepareOneReservedInventoryItem() {
        productId = id();
        variantId = id();
        jdbc.update(
                "INSERT INTO products (id, name, price, product_type, active) VALUES (?, ?, ?, ?, TRUE)",
                productId, "Local Test Product", new BigDecimal("25.00"), "physical");
        jdbc.update("INSERT INTO physical_products (id, stock) VALUES (?, 4)", productId);
        jdbc.update(
                "INSERT INTO product_variants (id, product_id, size, stock, active) VALUES (?, ?, ?, 4, TRUE)",
                variantId, productId, "M");
        jdbc.update(
                "INSERT INTO order_items (id, order_id, product_variant_id, quantity, price) VALUES (?, ?, ?, 1, ?)",
                id(), orderId, variantId, new BigDecimal("25.00"));
    }

    @SafeVarargs
    private final <T> List<T> concurrently(CheckedSupplier<T>... actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.length);
        CountDownLatch ready = new CountDownLatch(actions.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (CheckedSupplier<T> action : actions) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent test did not start.");
                    }
                    return action.get();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private String id() {
        return UUID.randomUUID().toString();
    }

    private UUID uuid(String value) {
        return UUID.fromString(value);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private record ColumnDefinition(String type, Integer length, String nullable) {}
}
