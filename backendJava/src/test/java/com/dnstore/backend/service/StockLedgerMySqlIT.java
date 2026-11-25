package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dnstore.backend.dto.stock.StockDtos.ReconciliationResponse;
import com.dnstore.backend.dto.stock.StockDtos.StockAdjustmentRequest;
import com.dnstore.backend.dto.stock.StockDtos.StockEntryItemRequest;
import com.dnstore.backend.dto.stock.StockDtos.StockEntryRequest;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.DuplicateStockEntryException;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.ProductRemovalOutcome;
import com.dnstore.backend.model.enums.StockAdjustmentReason;
import com.dnstore.backend.model.enums.StockMovementType;
import com.dnstore.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Livro de estoque contra um MySQL 8 real e DESCARTÁVEL (127.0.0.1:33316, dn_store_it): constraints da V28,
 * nota de entrada (várias linhas, atomicidade, duplicidade, idempotência), ajuste manual, saldo inicial lazy e
 * concorrência de checkouts reais. Nunca aponte este teste para o banco real. Não roda no {@code mvn test}
 * (sufixo IT): {@code ./mvnw test -Dtest=StockLedgerMySqlIT}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        args = {
                "--spring.config.location=classpath:/application-integration-local.properties",
                "--spring.datasource.password=dn_store_it_test_password",
                "--shipping.origin-zip=12600100"
        })
@ActiveProfiles("integration-local")
class StockLedgerMySqlIT {

    private static final String PREFIX = "IT-STOCK";
    private static final String V1 = "00000000-0000-0000-0000-0000000000a1";
    private static final String V2 = "00000000-0000-0000-0000-0000000000a2";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private StockEntryService entryService;
    @Autowired private StockAdjustmentService adjustmentService;
    @Autowired private StockQueryService queryService;
    @Autowired private OrderService orderService;
    @Autowired private ProductService productService;

    private User admin;
    private String supplierId;

    @BeforeEach
    void setUp() {
        cleanup();
        admin = createUser("ADMIN");
        supplierId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO supplier (id, name, active, created_at) VALUES (?, ?, TRUE, NOW())",
                supplierId, PREFIX + " Fornecedor " + supplierId);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM stock_movement WHERE product_variant_id IN (SELECT pv.id FROM product_variants pv "
                + "JOIN products p ON p.id = pv.product_id WHERE p.name LIKE ?)", PREFIX + "%");
        jdbc.update("DELETE FROM stock_movement WHERE actor_user_id IN (SELECT id FROM users WHERE email LIKE ?)",
                "it-stock-%");
        jdbc.update("DELETE FROM stock_entry_item WHERE entry_id IN (SELECT id FROM stock_entry WHERE supplier_id IN "
                + "(SELECT id FROM supplier WHERE name LIKE ?))", PREFIX + "%");
        jdbc.update("DELETE FROM stock_entry WHERE supplier_id IN (SELECT id FROM supplier WHERE name LIKE ?)",
                PREFIX + "%");
        jdbc.update("DELETE FROM supplier WHERE name LIKE ?", PREFIX + "%");
        jdbc.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE user_id IN "
                + "(SELECT id FROM users WHERE email LIKE ?))", "it-stock-%");
        jdbc.update("DELETE FROM orders WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "it-stock-%");
        jdbc.update("DELETE FROM shipping_quote_options WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)",
                "it-stock-%");
        jdbc.update("DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE user_id IN "
                + "(SELECT id FROM users WHERE email LIKE ?))", "it-stock-%");
        jdbc.update("DELETE FROM carts WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "it-stock-%");
        jdbc.update("DELETE FROM addresses WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "it-stock-%");
        jdbc.update("DELETE FROM users WHERE email LIKE ?", "it-stock-%");
        jdbc.update("DELETE FROM product_variants WHERE product_id IN (SELECT id FROM products WHERE name LIKE ?)",
                PREFIX + "%");
        jdbc.update("DELETE FROM physical_products WHERE id IN (SELECT id FROM products WHERE name LIKE ?)",
                PREFIX + "%");
        jdbc.update("DELETE FROM products WHERE name LIKE ?", PREFIX + "%");
    }

    // ---------------------------------------------------------------- schema

    @Test
    void schemaRejectsInvalidLedgerRowsAndDuplicates() {
        String variant = createVariant(5);
        String insert = "INSERT INTO stock_movement (id, product_variant_id, movement_type, quantity_delta, "
                + "balance_before, balance_after, opening_marker, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, NOW(3))";

        assertThatThrownBy(() -> jdbc.update(insert, id(), variant, "AJUSTE", 0, 5, 5, null))
                .as("delta zero").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(insert, id(), variant, "AJUSTE", 2, 5, 9, null))
                .as("after != before + delta").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(insert, id(), variant, "AJUSTE", -6, 5, -1, null))
                .as("saldo negativo").isInstanceOf(DataAccessException.class);

        jdbc.update(insert, id(), variant, "SALDO_INICIAL", 5, 0, 5, 1);
        assertThatThrownBy(() -> jdbc.update(insert, id(), variant, "SALDO_INICIAL", 5, 0, 5, 1))
                .as("dois saldos iniciais da mesma variante").isInstanceOf(DataAccessException.class);

        String entryId = id();
        jdbc.update("INSERT INTO stock_entry (id, supplier_id, invoice_number, received_at, idempotency_key, "
                + "created_by, created_at) VALUES (?, ?, 'NF-1', NOW(), ?, ?, NOW())",
                entryId, supplierId, id(), admin.getId().toString());
        assertThatThrownBy(() -> jdbc.update("INSERT INTO stock_entry (id, supplier_id, invoice_number, "
                + "received_at, idempotency_key, created_by, created_at) VALUES (?, ?, 'NF-1', NOW(), ?, ?, NOW())",
                id(), supplierId, id(), admin.getId().toString()))
                .as("mesma nota do mesmo fornecedor").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO stock_entry_item (id, entry_id, product_variant_id, "
                + "quantity) VALUES (?, ?, ?, 0)", id(), entryId, variant))
                .as("quantidade zero").isInstanceOf(DataAccessException.class);
    }

    // ---------------------------------------------------------------- nota de entrada

    @Test
    void entryWithSeveralLinesRaisesStockAndWritesTheLedgerWithLazyOpeningBalance() {
        String withStock = createVariant(5);
        String empty = createVariant(0);

        var result = entryService.create(entry("NF-100", UUID.randomUUID().toString(),
                line(withStock, 10, "12.50"), line(empty, 4, null)), admin);

        assertThat(result.replayed()).isFalse();
        assertThat(stockOf(withStock)).isEqualTo(15);
        assertThat(stockOf(empty)).isEqualTo(4);

        // Variante que já tinha estoque: SALDO_INICIAL (0→5) e depois ENTRADA_NOTA (5→15), na ordem.
        assertThat(ledger(withStock)).extracting(m -> m.get("movement_type") + ":" + m.get("balance_before") + ">"
                + m.get("balance_after")).containsExactly("SALDO_INICIAL:0>5", "ENTRADA_NOTA:5>15");
        // Variante zerada: sem saldo inicial (nada a abrir).
        assertThat(ledger(empty)).extracting(m -> m.get("movement_type") + ":" + m.get("balance_before") + ">"
                + m.get("balance_after")).containsExactly("ENTRADA_NOTA:0>4");

        var detail = queryService.entryDetail(result.entryId());
        assertThat(detail.invoiceNumber()).isEqualTo("NF-100");
        assertThat(detail.items()).hasSize(2);
        assertThat(detail.totalUnits()).isEqualTo(14);
        assertThat(detail.totalCost()).isEqualByComparingTo("125.00");
        assertThat(reconcilesFor(withStock, empty)).isTrue();
    }

    @Test
    void entryIsAtomicWhenALaterLineFails() {
        createVariantWithId(V1, 3);
        createVariantWithId(V2, Integer.MAX_VALUE - 1); // +10 estoura o int: a 2ª linha falha depois da 1ª aplicada

        assertThatThrownBy(() -> entryService.create(entry("NF-ATOMIC", UUID.randomUUID().toString(),
                line(V1, 5, null), line(V2, 10, null)), admin)).isInstanceOf(IllegalArgumentException.class);

        assertThat(stockOf(V1)).as("1ª linha desfeita").isEqualTo(3);
        assertThat(stockOf(V2)).isEqualTo(Integer.MAX_VALUE - 1);
        assertThat(count("SELECT COUNT(*) FROM stock_entry WHERE supplier_id = ?", supplierId)).isZero();
        assertThat(ledger(V1)).as("nem o saldo inicial sobrevive ao rollback").isEmpty();
    }

    @Test
    void duplicateInvoiceIsRejectedAndSameKeyReplaysWithoutApplyingTwice() {
        String variant = createVariant(0);
        String key = UUID.randomUUID().toString();
        var first = entryService.create(entry("nf-200", key, line(variant, 7, null)), admin);

        var replay = entryService.create(entry("NF-200", key, line(variant, 7, null)), admin);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.entryId()).isEqualTo(first.entryId());

        assertThatThrownBy(() -> entryService.create(
                entry("NF-200", UUID.randomUUID().toString(), line(variant, 7, null)), admin))
                .isInstanceOfSatisfying(DuplicateStockEntryException.class,
                        e -> assertThat(e.getExistingEntryId()).isEqualTo(first.entryId()));
        assertThatThrownBy(() -> entryService.create(entry("NF-200", key, line(variant, 8, null)), admin))
                .as("mesma chave com conteúdo diferente").isInstanceOf(ConflictException.class);

        assertThat(stockOf(variant)).isEqualTo(7);
        assertThat(count("SELECT COUNT(*) FROM stock_movement WHERE product_variant_id = ?", variant)).isEqualTo(1);
    }

    @Test
    void simultaneousSubmissionsOfTheSameInvoiceApplyStockOnlyOnce() throws Exception {
        String variant = createVariant(0);
        // Mesma nota com chaves diferentes: exatamente uma vence, a outra recebe duplicidade.
        List<Object> outcomes = runConcurrently(2, index -> () -> entryService.create(
                entry("NF-RACE", UUID.randomUUID().toString(), line(variant, 5, null)), admin));
        assertThat(outcomes.stream().filter(o -> o instanceof StockEntryService.EntryResult)).hasSize(1);
        assertThat(outcomes.stream().filter(o -> o instanceof DuplicateStockEntryException)).hasSize(1);
        assertThat(stockOf(variant)).isEqualTo(5);

        // Mesma chave: uma cria, a outra é replay.
        String key = UUID.randomUUID().toString();
        List<Object> sameKey = runConcurrently(2, index -> () -> entryService.create(
                entry("NF-RACE-2", key, line(variant, 3, null)), admin));
        assertThat(sameKey).allMatch(o -> o instanceof StockEntryService.EntryResult);
        assertThat(sameKey.stream().map(o -> ((StockEntryService.EntryResult) o).replayed()).sorted())
                .containsExactly(false, true);
        assertThat(stockOf(variant)).isEqualTo(8);
    }

    // ---------------------------------------------------------------- ajuste

    @Test
    void adjustmentRequiresReasonKeepsStockNonNegativeAndChecksTheExpectedBalance() {
        String variant = createVariant(4);

        assertThatThrownBy(() -> adjustmentService.adjust(adjustment(variant, -5, 4,
                StockAdjustmentReason.PERDA, UUID.randomUUID().toString()), admin))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Estoque insuficiente");
        assertThatThrownBy(() -> adjustmentService.adjust(adjustment(variant, -1, 9,
                StockAdjustmentReason.PERDA, UUID.randomUUID().toString()), admin))
                .isInstanceOf(ConflictException.class).hasMessageContaining("O estoque mudou");
        assertThatThrownBy(() -> adjustmentService.adjust(adjustment(variant, 2, 4,
                StockAdjustmentReason.AVARIA, UUID.randomUUID().toString()), admin))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(stockOf(variant)).isEqualTo(4);
        assertThat(ledger(variant)).isEmpty();

        String key = UUID.randomUUID().toString();
        assertThat(adjustmentService.adjust(adjustment(variant, -3, 4, StockAdjustmentReason.PERDA, key), admin)
                .replayed()).isFalse();
        assertThat(adjustmentService.adjust(adjustment(variant, -3, 4, StockAdjustmentReason.PERDA, key), admin)
                .replayed()).isTrue();

        assertThat(stockOf(variant)).isEqualTo(1);
        assertThat(ledger(variant)).extracting(m -> m.get("movement_type") + ":" + m.get("balance_before") + ">"
                + m.get("balance_after")).containsExactly("SALDO_INICIAL:0>4", "AJUSTE:4>1");
        assertThat(ledger(variant).get(1)).containsEntry("reason_code", "PERDA")
                .containsEntry("actor_user_id", admin.getId().toString());
    }

    // ---------------------------------------------------------------- venda real (checkout) e devolução

    @Test
    void concurrentCheckoutsOnTheFirstMovementCreateASingleOpeningBalanceAndNeverOversell() throws Exception {
        String variant = createVariant(3);
        List<String> userIds = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            userIds.add(prepareBuyer(variant, 1));
        }
        Map<String, UUID> quotes = new java.util.HashMap<>();
        for (String userId : userIds) {
            User user = userRepository.findById(UUID.fromString(userId)).orElseThrow();
            quotes.put(userId, orderService.quoteShipping(user, "12500-000").get(0).id());
        }

        List<Object> outcomes = runConcurrently(6, index -> () -> {
            String userId = userIds.get(index);
            User user = userRepository.findById(UUID.fromString(userId)).orElseThrow();
            return orderService.checkout(user, null, quotes.get(userId), null,
                    UUID.fromString(addressOf(userId)));
        });

        long sold = outcomes.stream().filter(o -> o instanceof Order).count();
        assertThat(sold).as("só 3 unidades existiam: %s", outcomes).isEqualTo(3);
        assertThat(outcomes.stream().filter(o -> o instanceof ConflictException).count()).isEqualTo(3);
        assertThat(stockOf(variant)).isZero();

        List<Map<String, Object>> movements = ledger(variant);
        assertThat(movements.stream().filter(m -> "SALDO_INICIAL".equals(m.get("movement_type"))))
                .as("exatamente um saldo inicial, mesmo com 6 checkouts na primeira movimentação").hasSize(1);
        assertThat(movements.stream().filter(m -> "SAIDA_VENDA".equals(m.get("movement_type")))).hasSize(3)
                .allSatisfy(m -> assertThat(m.get("quantity_delta")).isEqualTo(-1));
        assertChainIsContinuous(movements, 0, 0);
        assertThat(reconcilesFor(variant)).isTrue();
    }

    @Test
    void cancellingAnOrderReturnsStockOnceAndRecordsTheReturnInTheLedger() {
        String variant = createVariant(5);
        String userId = prepareBuyer(variant, 2);
        User user = userRepository.findById(UUID.fromString(userId)).orElseThrow();
        UUID quote = orderService.quoteShipping(user, "12500-000").get(0).id();
        Order order = orderService.checkout(user, null, quote, null, UUID.fromString(addressOf(userId)));
        assertThat(stockOf(variant)).isEqualTo(3);

        orderService.updateStatus(order.getId(), "CANCELLED");
        orderService.updateStatus(order.getId(), "CANCELLED"); // repetido: não devolve em dobro

        assertThat(stockOf(variant)).isEqualTo(5);
        assertThat(ledger(variant)).extracting(m -> m.get("movement_type") + ":" + m.get("balance_before") + ">"
                + m.get("balance_after")).containsExactly(
                "SALDO_INICIAL:0>5", "SAIDA_VENDA:5>3", "DEVOLUCAO_PEDIDO:3>5");
        assertThat(ledger(variant).get(1)).containsEntry("reference_type", "ORDER")
                .containsEntry("reference_id", order.getId().toString());
        assertThat(queryService.searchSaleOutflows(null, null, 0, 20).getContent())
                .anySatisfy(o -> {
                    assertThat(o.orderId()).isEqualTo(order.getId().toString());
                    assertThat(o.units()).isEqualTo(2);
                    assertThat(o.stockReleased()).isTrue();
                });
        assertThat(reconcilesFor(variant)).isTrue();
    }

    // ---------------------------------------------------------------- cadastro de produto (D2/D6) e reconciliação

    @Test
    void productCreateAndEditRecordTheStockDeltaAndProductWithMovementsIsArchivedNotDeleted() {
        PhysicalProduct product = new PhysicalProduct();
        product.setName(PREFIX + " Produto cadastro");
        product.setPrice(new BigDecimal("50.00"));
        product.setActive(true);
        product.setVariants(new ArrayList<>(List.of(variantDraft("M", 4))));
        PhysicalProduct saved = (PhysicalProduct) productService.create(product);
        String variantId = saved.getVariants().get(0).getId().toString();

        assertThat(ledger(variantId)).extracting(m -> m.get("movement_type") + ":" + m.get("balance_before") + ">"
                + m.get("balance_after")).containsExactly("EDICAO_CADASTRO:0>4");

        // Venda concorrente "por fora" (ex.: instância antiga) não é perdida de vista: o merge re-lê do banco.
        jdbc.update("UPDATE product_variants SET stock = 2 WHERE id = ?", variantId);
        PhysicalProduct edit = new PhysicalProduct();
        edit.setName(saved.getName());
        edit.setPrice(saved.getPrice());
        edit.setActive(true);
        edit.setVariants(new ArrayList<>(List.of(variantDraft("M", 9))));
        productService.update(saved.getId(), edit);

        assertThat(stockOf(variantId)).isEqualTo(9);
        List<Map<String, Object>> movements = ledger(variantId);
        assertThat(movements.get(movements.size() - 1)).containsEntry("movement_type", "EDICAO_CADASTRO")
                .containsEntry("balance_before", 2).containsEntry("balance_after", 9);

        assertThat(productService.delete(saved.getId())).contains(ProductRemovalOutcome.ARCHIVED);
        assertThat(count("SELECT COUNT(*) FROM products WHERE id = ?", saved.getId().toString())).isEqualTo(1);
    }

    @Test
    void reconciliationFlagsAStockChangedBehindTheLedger() {
        String variant = createVariant(5);
        adjustmentService.adjust(adjustment(variant, -1, 5, StockAdjustmentReason.CORRECAO_INVENTARIO,
                UUID.randomUUID().toString()), admin);
        assertThat(reconcilesFor(variant)).isTrue();

        // Simula a janela de deploy: a instância antiga vendeu 2 unidades sem registrar no livro.
        jdbc.update("UPDATE product_variants SET stock = stock - 2 WHERE id = ?", variant);

        ReconciliationResponse report = queryService.reconciliation();
        assertThat(report.divergences()).anySatisfy(d -> {
            assertThat(d.variant().id().toString()).isEqualTo(variant);
            assertThat(d.currentStock()).isEqualTo(2);
            assertThat(d.ledgerBalance()).isEqualTo(4);
            assertThat(d.difference()).isEqualTo(-2);
        });
    }

    @Test
    void queryEndpointsFilterAndPageAgainstTheRealSchema() {
        String withStock = createVariant(5);
        String other = createVariant(0);
        var result = entryService.create(entry("NF-Q-1", UUID.randomUUID().toString(),
                line(withStock, 10, null), line(other, 4, null)), admin);
        adjustmentService.adjust(adjustment(withStock, -2, 15, StockAdjustmentReason.AVARIA,
                UUID.randomUUID().toString()), admin);

        var all = queryService.searchMovements(movementFilter(withStock, null, null, null, null), 0, 10);
        assertThat(all.getContent()).extracting(m -> m.type().name())
                .as("mais recente primeiro")
                .containsExactly("AJUSTE", "ENTRADA_NOTA", "SALDO_INICIAL");
        assertThat(all.getContent().get(0).actor().id()).isEqualTo(admin.getId());
        assertThat(all.getContent().get(1).reference().label()).startsWith("Nota NF-Q-1");

        assertThat(queryService.searchMovements(movementFilter(null, StockMovementType.ENTRADA_NOTA, null, null, null),
                0, 10).getContent()).extracting(m -> m.variant().id().toString())
                .containsExactlyInAnyOrder(withStock, other);
        assertThat(queryService.searchMovements(movementFilter(withStock, null,
                com.dnstore.backend.model.enums.StockReferenceType.STOCK_ENTRY, result.entryId().toString(), null),
                0, 10).getContent()).hasSize(1);
        assertThat(queryService.searchMovements(movementFilter(null, null, null, null, "nf-q-1"), 0, 10)
                .getTotalElements()).isEqualTo(2);
        assertThat(queryService.searchMovements(movementFilter(null, null, null, null, "NF-NAO-EXISTE"), 0, 10)
                .getContent()).isEmpty();
        assertThat(queryService.searchMovements(new StockQueryService.MovementFilter(null,
                null, null, java.time.LocalDate.now(), java.time.LocalDate.now(), admin.getId(), null, null, null),
                0, 100).getContent()).extracting(m -> m.actor().id()).containsOnly(admin.getId());
        assertThat(queryService.searchMovements(movementFilter(withStock, null, null, null, null), 1, 2)
                .getContent()).hasSize(1);

        assertThat(queryService.searchEntries(new StockQueryService.EntryFilter(
                UUID.fromString(supplierId), "q-1", null, null), 0, 10).getContent())
                .singleElement().satisfies(entry -> {
                    assertThat(entry.itemCount()).isEqualTo(2);
                    assertThat(entry.totalUnits()).isEqualTo(14);
                    assertThat(entry.supplier().id().toString()).isEqualTo(supplierId);
                });
        assertThat(queryService.searchEntries(new StockQueryService.EntryFilter(
                UUID.fromString(supplierId), "NF_Q%", null, null), 0, 10).getContent())
                .as("curingas do LIKE são escapados").isEmpty();

        assertThat(queryService.variantDetail(UUID.fromString(withStock)).stock()).isEqualTo(13);
        assertThat(queryService.searchVariants(PREFIX.toLowerCase() + " produto", 0, 10).getContent())
                .extracting(v -> v.id().toString()).contains(withStock, other);
    }

    private StockQueryService.MovementFilter movementFilter(String variantId, StockMovementType type,
            com.dnstore.backend.model.enums.StockReferenceType referenceType, String referenceId, String invoice) {
        return new StockQueryService.MovementFilter(variantId == null ? null : UUID.fromString(variantId), null, type,
                null, null, null, referenceType, referenceId, invoice);
    }

    // ---------------------------------------------------------------- helpers

    private User createUser(String role) {
        String userId = id();
        jdbc.update("INSERT INTO users (id, name, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
                userId, PREFIX + " " + role, "it-stock-" + userId + "@example.invalid", "not-a-real-password", role);
        return userRepository.findById(UUID.fromString(userId)).orElseThrow();
    }

    private String createVariant(int stock) {
        return createVariantWithId(id(), stock);
    }

    private String createVariantWithId(String variantId, int stock) {
        String productId = id();
        jdbc.update("INSERT INTO products (id, name, price, product_type, active) VALUES (?, ?, ?, ?, TRUE)",
                productId, PREFIX + " Produto " + productId, new BigDecimal("100.00"), "PhysicalProduct");
        jdbc.update("INSERT INTO physical_products (id, stock, weight, width, height, depth) "
                + "VALUES (?, 0, 0.25, 22, 4, 30)", productId);
        jdbc.update("INSERT INTO product_variants (id, product_id, size, stock, active) VALUES (?, ?, 'M', ?, TRUE)",
                variantId, productId, stock);
        return variantId;
    }

    /** Cliente com endereço e carrinho de {@code quantity} unidades da variante. */
    private String prepareBuyer(String variantId, int quantity) {
        User buyer = createUser("USER");
        String userId = buyer.getId().toString();
        String cartId = id();
        jdbc.update("INSERT INTO addresses (id, user_id, street, city, state, zip_code) VALUES (?, ?, ?, ?, ?, ?)",
                id(), userId, "Rua IT", "Cidade IT", "TS", "12500-000");
        jdbc.update("INSERT INTO carts (id, user_id) VALUES (?, ?)", cartId, userId);
        jdbc.update("INSERT INTO cart_items (id, cart_id, product_variant_id, quantity) VALUES (?, ?, ?, ?)",
                id(), cartId, variantId, quantity);
        return userId;
    }

    private String addressOf(String userId) {
        return jdbc.queryForObject("SELECT id FROM addresses WHERE user_id = ?", String.class, userId);
    }

    private ProductVariant variantDraft(String size, int stock) {
        ProductVariant variant = new ProductVariant();
        variant.setSize(size);
        variant.setStock(stock);
        variant.setActive(true);
        return variant;
    }

    private StockEntryRequest entry(String invoice, String key, StockEntryItemRequest... items) {
        return new StockEntryRequest(UUID.fromString(supplierId), invoice, null, null, key, List.of(items));
    }

    private StockEntryItemRequest line(String variantId, int quantity, String cost) {
        return new StockEntryItemRequest(UUID.fromString(variantId), quantity, cost == null ? null : new BigDecimal(cost));
    }

    private StockAdjustmentRequest adjustment(String variantId, int delta, int expected,
            StockAdjustmentReason reason, String key) {
        return new StockAdjustmentRequest(UUID.fromString(variantId), delta, expected, reason,
                "Contagem física do depósito", key);
    }

    private int stockOf(String variantId) {
        return jdbc.queryForObject("SELECT stock FROM product_variants WHERE id = ?", Integer.class, variantId);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private List<Map<String, Object>> ledger(String variantId) {
        return jdbc.queryForList("SELECT * FROM stock_movement WHERE product_variant_id = ? "
                + "ORDER BY created_at, seq", variantId);
    }

    /** Estoque atual == soma dos deltas e cadeia before/after contínua, para cada variante informada. */
    private boolean reconcilesFor(String... variantIds) {
        for (String variantId : variantIds) {
            List<Map<String, Object>> movements = ledger(variantId);
            long sum = movements.stream().mapToLong(m -> ((Number) m.get("quantity_delta")).longValue()).sum();
            if (sum != stockOf(variantId)) {
                return false;
            }
            assertChainIsContinuous(movements, 0, 0);
        }
        return queryService.reconciliation().divergences().stream()
                .noneMatch(d -> List.of(variantIds).contains(d.variant().id().toString()));
    }

    private void assertChainIsContinuous(List<Map<String, Object>> movements, int ignoredA, int ignoredB) {
        int previousAfter = 0;
        for (Map<String, Object> movement : movements) {
            assertThat(((Number) movement.get("balance_before")).intValue())
                    .as("balance_before de %s", movement.get("movement_type")).isEqualTo(previousAfter);
            previousAfter = ((Number) movement.get("balance_after")).intValue();
        }
    }

    /** Dispara {@code threads} tarefas ao mesmo tempo; devolve o resultado ou a exceção de cada uma. */
    private List<Object> runConcurrently(int threads, java.util.function.IntFunction<Callable<Object>> task)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Object> body = task.apply(i);
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return body.call();
                } catch (Throwable throwable) {
                    return throwable;
                }
            }));
        }
        ready.await();
        go.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        return results;
    }

    private String id() {
        return UUID.randomUUID().toString();
    }
}
