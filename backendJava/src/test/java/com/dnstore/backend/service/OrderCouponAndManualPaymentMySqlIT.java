package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dnstore.backend.dto.AdminDashboardDTO;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.model.Coupon;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Regressões de cupom (limite sob concorrência, devolução em cancelamento/expiração), receita do dashboard e
 * confirmação manual de pagamento, contra um MySQL 8 real e descartável (127.0.0.1:33316, dn_store_it).
 * Nunca aponte este teste para o banco real.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        args = {
                "--spring.config.location=classpath:/application-integration-local.properties",
                "--spring.datasource.password=dn_store_it_test_password"
        })
@ActiveProfiles("integration-local")
class OrderCouponAndManualPaymentMySqlIT {

    private static final String REASON = "Transferência bancária conferida no extrato";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private CouponService couponService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentExpirationService expirationService;
    @Autowired
    private OrderManualPaymentService manualPaymentService;
    @Autowired
    private AdminDashboardService dashboardService;
    @Autowired
    private UserRepository userRepository;

    private final List<String> orderIds = new ArrayList<>();
    private final List<String> couponIds = new ArrayList<>();
    private String customerId;
    private String adminId;
    private String addressId;
    private String productId;
    private String variantId;

    @BeforeEach
    void fixtures() {
        customerId = id();
        adminId = id();
        addressId = id();
        productId = id();
        variantId = id();
        jdbc.update("INSERT INTO users (id, name, email, password_hash, role) VALUES (?, ?, ?, ?, 'USER')",
                customerId, "Cliente IT", customerId + "@example.invalid", "not-a-real-password");
        jdbc.update("INSERT INTO users (id, name, email, password_hash, role) VALUES (?, ?, ?, ?, 'ADMIN')",
                adminId, "Admin IT", adminId + "@example.invalid", "not-a-real-password");
        jdbc.update("INSERT INTO addresses (id, user_id, street, city, state, zip_code) VALUES (?, ?, ?, ?, ?, ?)",
                addressId, customerId, "Rua IT", "Cidade IT", "TS", "00000-000");
        jdbc.update("INSERT INTO products (id, name, price, product_type, active) VALUES (?, ?, ?, ?, TRUE)",
                productId, "Produto IT cupom", new BigDecimal("100.00"), "PhysicalProduct");
        jdbc.update("INSERT INTO physical_products (id, stock) VALUES (?, 10)", productId);
        // estoque 4 já considera 1 unidade reservada por pedido aberto
        jdbc.update("INSERT INTO product_variants (id, product_id, size, stock, active) VALUES (?, ?, 'M', 4, TRUE)",
                variantId, productId);
    }

    @AfterEach
    void cleanup() {
        for (String orderId : orderIds) {
            jdbc.update("DELETE FROM order_status_audit WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM payment_financial_blocks WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM payment_financial_history WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM payments WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM order_items WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM orders WHERE id = ?", orderId);
        }
        for (String couponId : couponIds) {
            jdbc.update("DELETE FROM coupons WHERE id = ?", couponId);
        }
        // O livro de estoque referencia a variação (FK): devolução/cancelamento gravam movimentos.
        jdbc.update("DELETE FROM stock_movement WHERE product_variant_id = ?", variantId);
        jdbc.update("DELETE FROM product_variants WHERE id = ?", variantId);
        jdbc.update("DELETE FROM physical_products WHERE id = ?", productId);
        jdbc.update("DELETE FROM products WHERE id = ?", productId);
        jdbc.update("DELETE FROM addresses WHERE id = ?", addressId);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", customerId, adminId);
        orderIds.clear();
        couponIds.clear();
    }

    // ---------------------------------------------------------------- cupom: concorrência

    @Test
    void couponUsageNeverExceedsTheLimitWhenManyCheckoutsRaceForTheLastSlots() throws Exception {
        Coupon coupon = coupon(3, 0);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int threads = 16;
        CountDownLatch startGate = new CountDownLatch(1);

        List<Callable<Boolean>> checkouts = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            checkouts.add(() -> {
                startGate.await();
                try {
                    tx.executeWithoutResult(status -> {
                        couponService.registerUsage(coupon);
                        pause(60); // mantém a transação aberta para forçar a disputa pela mesma linha
                    });
                    return true;
                } catch (IllegalArgumentException limitReached) {
                    return false;
                }
            });
        }
        List<Boolean> results = runAll(checkouts, startGate);

        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(3);
        assertThat(usageOf(coupon)).isEqualTo(3);
    }

    @Test
    void anUnlimitedCouponKeepsCountingAndNeverRejects() {
        Coupon coupon = coupon(null, 0);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        for (int i = 0; i < 5; i++) {
            tx.executeWithoutResult(status -> couponService.registerUsage(coupon));
        }

        assertThat(usageOf(coupon)).isEqualTo(5);
    }

    @Test
    void aFailedCheckoutRollsTheCouponUsageBack() {
        Coupon coupon = coupon(3, 0);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            couponService.registerUsage(coupon);
            throw new IllegalStateException("falha depois de consumir o cupom (estoque, frete, banco...)");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(usageOf(coupon)).isZero();
    }

    // ---------------------------------------------------------------- cupom: cancelamento e expiração

    @Test
    void cancellingAnOrderReturnsTheCouponUsageAndTheStockExactlyOnce_evenWithRepeatedAndConcurrentCancels()
            throws Exception {
        Coupon coupon = coupon(5, 1);
        String orderId = order("PENDING_PAYMENT", coupon, 5);
        CountDownLatch startGate = new CountDownLatch(1);

        List<Callable<Boolean>> cancels = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            cancels.add(() -> {
                startGate.await();
                orderService.updateStatus(UUID.fromString(orderId), "CANCELLED");
                return true;
            });
        }
        runAll(cancels, startGate);
        orderService.updateStatus(UUID.fromString(orderId), "CANCELLED"); // e mais um clique depois

        assertThat(usageOf(coupon)).isZero();
        assertThat(stock()).isEqualTo(5); // 4 + 1 devolvido, uma única vez
        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
    }

    @Test
    void expiringAnUnpaidOrderReturnsTheCouponUsage() {
        Coupon coupon = coupon(5, 1);
        String orderId = order("PENDING_PAYMENT", coupon, 180);

        expirationService.expireIfDue(UUID.fromString(orderId));
        expirationService.expireIfDue(UUID.fromString(orderId)); // varredura repetida do agendador

        assertThat(orderStatus(orderId)).isEqualTo("EXPIRED");
        assertThat(usageOf(coupon)).isZero();
        assertThat(stock()).isEqualTo(5);
    }

    @Test
    void cancellationRacingWithExpirationStillReturnsTheCouponOnlyOnce() throws Exception {
        Coupon coupon = coupon(5, 1);
        String orderId = order("PENDING_PAYMENT", coupon, 180);
        CountDownLatch startGate = new CountDownLatch(1);

        List<Callable<Boolean>> racers = List.of(
                () -> {
                    startGate.await();
                    expirationService.expireIfDue(UUID.fromString(orderId));
                    return true;
                },
                () -> {
                    startGate.await();
                    orderService.updateStatus(UUID.fromString(orderId), "CANCELLED");
                    return true;
                });
        runAll(racers, startGate);

        assertThat(usageOf(coupon)).isZero();
        assertThat(stock()).isEqualTo(5);
        assertThat(orderStatus(orderId)).isIn("EXPIRED", "CANCELLED");
    }

    @Test
    void returningTheCouponNeverGoesBelowZero() {
        Coupon coupon = coupon(5, 0); // contador já em zero (histórico anterior à correção)
        String orderId = order("PENDING_PAYMENT", coupon, 5);

        orderService.updateStatus(UUID.fromString(orderId), "CANCELLED");

        assertThat(usageOf(coupon)).isZero();
    }

    // ---------------------------------------------------------------- dashboard

    @Test
    void dashboardRevenueCountsOnlyPaidOrdersNetOfRefunds() {
        AdminDashboardDTO before = dashboardService.dashboard();

        String paid = order("PAID", null, 5, new BigDecimal("100.00"));
        order("PROCESSING", null, 5, new BigDecimal("50.00"));
        order("SHIPPED", null, 5, new BigDecimal("25.00"));
        order("DELIVERED", null, 5, new BigDecimal("25.00"));
        for (String ignored : List.of("PENDING_PAYMENT", "PAYMENT_FAILED", "EXPIRED", "CANCELLED", "PAYMENT_REVIEW")) {
            order(ignored, null, 5, new BigDecimal("1000.00"));
        }
        jdbc.update("INSERT INTO payments (id, order_id, external_reference, idempotency_key, attempt_number, "
                        + "payment_method, amount, status, refunded_amount) VALUES (?, ?, ?, ?, 1, 'PIX', ?, 'PARTIALLY_REFUNDED', ?)",
                id(), paid, "it-ref-" + paid, "it-key-" + paid, new BigDecimal("100.00"), new BigDecimal("30.00"));

        AdminDashboardDTO after = dashboardService.dashboard();

        assertThat(after.getTotalRevenue().subtract(before.getTotalRevenue())).isEqualByComparingTo("170.00");
        assertThat(after.getPaidOrders() - before.getPaidOrders()).isEqualTo(4);
        assertThat(after.getTotalOrders() - before.getTotalOrders()).isEqualTo(9);
    }

    // ---------------------------------------------------------------- confirmação manual

    @Test
    void manualConfirmationPersistsWhoWhenAndWhat_andADoubleClickCreatesOneAuditRow() throws Exception {
        String orderId = order("PENDING_PAYMENT", null, 5);
        User admin = userRepository.findById(UUID.fromString(adminId)).orElseThrow();
        CountDownLatch startGate = new CountDownLatch(1);

        List<Callable<Boolean>> clicks = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            clicks.add(() -> {
                startGate.await();
                try {
                    manualPaymentService.confirmPayment(UUID.fromString(orderId), admin, REASON);
                    return true;
                } catch (ConflictException alreadyConfirmed) {
                    return false;
                }
            });
        }
        List<Boolean> results = runAll(clicks, startGate);

        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT manual_payment_confirmed FROM orders WHERE id = ?",
                Boolean.class, orderId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_status_audit WHERE order_id = ?",
                Integer.class, orderId)).isEqualTo(1);
        var row = jdbc.queryForMap("SELECT * FROM order_status_audit WHERE order_id = ?", orderId);
        assertThat(row.get("actor_user_id")).isEqualTo(adminId);
        assertThat(row.get("previous_status")).isEqualTo("PENDING_PAYMENT");
        assertThat(row.get("new_status")).isEqualTo("PAID");
        assertThat(row.get("change_type")).isEqualTo("MANUAL_PAYMENT_CONFIRMATION");
        assertThat(row.get("reason")).isEqualTo(REASON);
        assertThat(row.get("created_at")).isNotNull();
        assertThat(manualPaymentService.listAudit(UUID.fromString(orderId)))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.actorName()).isEqualTo("Admin IT");
                    assertThat(entry.reason()).isEqualTo(REASON);
                });
    }

    @Test
    void manualConfirmationIsRefusedWhileAnAsaasChargeIsPending_soTheCustomerIsNotChargedTwice() {
        String orderId = order("PENDING_PAYMENT", null, 5);
        jdbc.update("INSERT INTO payments (id, order_id, external_reference, idempotency_key, attempt_number, "
                        + "payment_method, amount, status) VALUES (?, ?, ?, ?, 1, 'PIX', ?, 'PENDING')",
                id(), orderId, "it-ref-" + orderId, "it-key-" + orderId, new BigDecimal("100.00"));
        User admin = userRepository.findById(UUID.fromString(adminId)).orElseThrow();

        assertThatThrownBy(() -> manualPaymentService.confirmPayment(UUID.fromString(orderId), admin, REASON))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("duplicar a cobrança");

        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_status_audit WHERE order_id = ?",
                Integer.class, orderId)).isZero();
    }

    @Test
    void theGenericStatusEndpointCanNoLongerMarkAnOrderAsPaid() {
        String orderId = order("PENDING_PAYMENT", null, 5);

        assertThatThrownBy(() -> orderService.updateStatus(UUID.fromString(orderId), "PAID"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
    }

    // ---------------------------------------------------------------- helpers

    private Coupon coupon(Integer maxUsage, int currentUsage) {
        String couponId = id();
        couponIds.add(couponId);
        jdbc.update("INSERT INTO coupons (id, code, discount_percentage, max_usage, current_usage, active) "
                        + "VALUES (?, ?, 10, ?, ?, TRUE)",
                couponId, "IT" + couponId.substring(0, 8).toUpperCase(), maxUsage, currentUsage);
        Coupon coupon = new Coupon();
        coupon.setId(UUID.fromString(couponId));
        return coupon;
    }

    private String order(String status, Coupon coupon, int createdMinutesAgo) {
        return order(status, coupon, createdMinutesAgo, new BigDecimal("100.00"));
    }

    private String order(String status, Coupon coupon, int createdMinutesAgo, BigDecimal total) {
        String orderId = id();
        orderIds.add(orderId);
        jdbc.update("INSERT INTO orders (id, user_id, address_id, total, status, created_at, coupon_id) "
                        + "VALUES (?, ?, ?, ?, ?, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL ? MINUTE), ?)",
                orderId, customerId, addressId, total, status, createdMinutesAgo,
                coupon == null ? null : coupon.getId().toString());
        jdbc.update("INSERT INTO order_items (id, order_id, product_variant_id, quantity, price) VALUES (?, ?, ?, 1, ?)",
                id(), orderId, variantId, new BigDecimal("100.00"));
        return orderId;
    }

    private int usageOf(Coupon coupon) {
        return jdbc.queryForObject("SELECT current_usage FROM coupons WHERE id = ?", Integer.class,
                coupon.getId().toString());
    }

    private int stock() {
        return jdbc.queryForObject("SELECT stock FROM product_variants WHERE id = ?", Integer.class, variantId);
    }

    private String orderStatus(String orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static <T> List<T> runAll(List<Callable<T>> actions, CountDownLatch startGate) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> action : actions) {
                futures.add(executor.submit(action));
            }
            pause(200); // todas as threads aguardando na largada
            startGate.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
