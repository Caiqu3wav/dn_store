package com.dnstore.backend.ledger;

import com.dnstore.backend.dto.StockDtos.EntryDetail;
import com.dnstore.backend.dto.StockDtos.MovementView;
import com.dnstore.backend.dto.StockDtos.PageView;
import com.dnstore.backend.dto.StockDtos.ReconciliationRow;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.exception.StockConflictException;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.Product;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.service.ProductService;
import com.dnstore.backend.service.StockEntryService;
import com.dnstore.backend.service.StockEntryService.CreateCommand;
import com.dnstore.backend.service.StockEntryService.Line;
import com.dnstore.backend.service.StockQueryService;
import com.dnstore.backend.service.StockQueryService.MovementFilter;
import com.dnstore.backend.service.StockReconciliationService;
import com.dnstore.backend.service.StockService;
import com.dnstore.backend.service.SupplierService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fluxos de ponta a ponta do livro e das notas em MySQL 8.0 local (127.0.0.1:33316), com locks e transações
 * reais. Rodar com {@code ./mvnw test -Dtest=StockNoteMySqlIT}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        args = {
                "--spring.config.location=classpath:/application-integration-local.properties",
                "--spring.datasource.password=dn_store_it_test_password"
        })
@ActiveProfiles("integration-local")
class StockNoteMySqlIT {

    private static final String ADMIN = "admin@it.test";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SupplierService supplierService;
    @Autowired private StockEntryService entryService;
    @Autowired private StockService stockService;
    @Autowired private StockQueryService queryService;
    @Autowired private StockReconciliationService reconciliationService;
    @Autowired private ProductService productService;

    private final List<String> productIds = new ArrayList<>();
    private final List<String> variantIds = new ArrayList<>();
    private final List<String> orderIds = new ArrayList<>();
    private final List<String> userIds = new ArrayList<>();
    private final List<String> addressIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String variantId : variantIds) {
            jdbc.update("DELETE FROM stock_movements WHERE variant_id = ?", variantId);
            jdbc.update("DELETE FROM stock_entry_item WHERE variant_id = ?", variantId);
        }
        jdbc.update("DELETE FROM stock_entry WHERE invoice_number LIKE 'IT-%'");
        jdbc.update("DELETE FROM supplier WHERE name LIKE 'it-%'");
        orderIds.forEach(id -> jdbc.update("DELETE FROM stock_movements WHERE order_id = ?", id));
        orderIds.forEach(id -> jdbc.update("DELETE FROM orders WHERE id = ?", id));
        addressIds.forEach(id -> jdbc.update("DELETE FROM addresses WHERE id = ?", id));
        userIds.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
        for (String productId : productIds) {
            jdbc.update("DELETE FROM stock_movements WHERE variant_id IN (SELECT id FROM product_variants WHERE product_id = ?)", productId);
            jdbc.update("DELETE FROM product_variants WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM physical_products WHERE id = ?", productId);
            jdbc.update("DELETE FROM products WHERE id = ?", productId);
        }
    }

    // ── notas ──────────────────────────────────────────────────────────────

    @Test
    void oneNoteWithThreeProductsPostsOneMovementPerLineAndKeepsTheTotals() {
        UUID a = variant("it-A", "M", 10, true), b = variant("it-B", "P", 0, true), c = variant("it-C", "G", 4, true);
        UUID supplier = supplier();

        EntryDetail detail = entryService.create(note(supplier, "IT-1001", "key-" + id(),
                new Line(a, 5, new BigDecimal("20.00")), new Line(b, 3, new BigDecimal("10.50")),
                new Line(c, 2, new BigDecimal("7.25"))));

        assertThat(detail.replayed()).isFalse();
        assertThat(detail.summary().itemCount()).isEqualTo(3);
        assertThat(detail.summary().totalQuantity()).isEqualTo(10);
        assertThat(detail.summary().totalCost()).isEqualByComparingTo("146.00"); // 100 + 31,50 + 14,50 → 146,00
        assertThat(detail.items()).extracting(i -> i.quantity()).containsExactlyInAnyOrder(5, 3, 2);
        assertThat(detail.movements()).hasSize(3)
                .allSatisfy(m -> {
                    assertThat(m.type()).isEqualTo("ENTRY");
                    assertThat(m.entryId()).isEqualTo(detail.summary().id());
                    assertThat(m.invoiceNumber()).isEqualTo("IT-1001");
                    assertThat(m.stockBefore() + m.delta()).isEqualTo(m.stockAfter());
                });
        assertThat(stock(a)).isEqualTo(15);
        assertThat(stock(b)).isEqualTo(3);
        assertThat(stock(c)).isEqualTo(6);
        assertThat(queryService.entries(null, "IT-1001", null, null, 0, 20).items()).hasSize(1);
    }

    @Test
    void duplicateInvoiceIsRefusedPointingToTheExistingNoteAndStockMovesOnce() {
        UUID a = variant("it-A", "M", 10, true);
        UUID supplier = supplier();
        EntryDetail first = entryService.create(note(supplier, "IT-2001", "key-" + id(), new Line(a, 5, null)));

        assertThatThrownBy(() -> entryService.create(note(supplier, "IT-2001", "key-" + id(), new Line(a, 5, null))))
                .isInstanceOfSatisfying(StockConflictException.class, e ->
                        assertThat(e.getDetails()).containsEntry("existingEntryId", first.summary().id().toString()));

        assertThat(stock(a)).isEqualTo(15);
        assertThat(count("SELECT COUNT(*) FROM stock_movements WHERE variant_id = ?", a)).isEqualTo(1);
    }

    @Test
    void sameKeyAndSameContentReplaysTheNoteWithoutPostingTwiceAndDifferentContentIsRefused() {
        UUID a = variant("it-A", "M", 10, true);
        UUID supplier = supplier();
        String key = "key-" + id();
        EntryDetail first = entryService.create(note(supplier, "IT-3001", key, new Line(a, 5, null)));

        EntryDetail again = entryService.create(note(supplier, "IT-3001", key, new Line(a, 5, null)));
        assertThat(again.replayed()).isTrue();
        assertThat(again.summary().id()).isEqualTo(first.summary().id());
        assertThat(stock(a)).isEqualTo(15);

        assertThatThrownBy(() -> entryService.create(note(supplier, "IT-3001", key, new Line(a, 9, null))))
                .isInstanceOf(StockConflictException.class);
        assertThat(stock(a)).isEqualTo(15);
    }

    @Test
    void aFailingLineRollsBackTheWholeNote() {
        UUID ok = variant("it-A", "M", 10, true);
        UUID nearLimit = variant("it-B", "P", Integer.MAX_VALUE, true);
        UUID supplier = supplier();

        assertThatThrownBy(() -> entryService.create(note(supplier, "IT-4001", "key-" + id(),
                new Line(ok, 5, null), new Line(nearLimit, 1, null))))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> entryService.create(note(supplier, "IT-4002", "key-" + id(),
                new Line(ok, 5, null), new Line(UUID.randomUUID(), 1, null))))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(stock(ok)).isEqualTo(10);
        assertThat(stock(nearLimit)).isEqualTo(Integer.MAX_VALUE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_entry WHERE invoice_number LIKE 'IT-400%'", Integer.class))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM stock_movements WHERE variant_id = ?", ok)).isZero();
    }

    @Test
    void parallelSubmissionsOfTheSameNoteCreateExactlyOne() throws Exception {
        UUID a = variant("it-A", "M", 10, true);
        UUID supplier = supplier();
        String key = "key-" + id();

        List<EntryDetail> results = inParallel(8, () ->
                entryService.create(note(supplier, "IT-5001", key, new Line(a, 5, null))));

        assertThat(results).extracting(r -> r.summary().id()).containsOnly(results.get(0).summary().id());
        assertThat(results.stream().filter(r -> !r.replayed()).count()).isEqualTo(1);
        assertThat(stock(a)).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_entry WHERE invoice_number = 'IT-5001'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void parallelAttemptsToRegisterTheSameInvoiceWithDifferentKeysAndVariantsYieldOneWinner() throws Exception {
        UUID supplier = supplier();
        List<UUID> variants = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            variants.add(variant("it-V" + i, "M", 0, true));
        }
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(variants.size());
        List<Future<Object>> futures = new ArrayList<>();
        for (UUID v : variants) {
            futures.add(pool.submit(() -> {
                go.await();
                try {
                    return entryService.create(note(supplier, "IT-6001", "key-" + id(), new Line(v, 4, null)));
                } catch (StockConflictException e) {
                    return e;
                }
            }));
        }
        go.countDown();
        int created = 0, refused = 0;
        for (Future<Object> f : futures) {
            Object r = f.get(60, TimeUnit.SECONDS);
            if (r instanceof EntryDetail) created++; else refused++;
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(refused).isEqualTo(variants.size() - 1);
        int total = variants.stream().mapToInt(this::stock).sum();
        assertThat(total).as("só a nota vencedora mexeu no saldo").isEqualTo(4);
    }

    // ── caminhos de produto ────────────────────────────────────────────────

    @Test
    void productCreationAndEditingRegisterTheirStockInTheLedger() {
        PhysicalProduct created = (PhysicalProduct) productService.create(product("it-Produto",
                variantWith("M", 5), variantWith("P", 0)));
        productIds.add(created.getId().toString());
        UUID m = variantOf(created, "M"), p = variantOf(created, "P");
        variantIds.add(m.toString());
        variantIds.add(p.toString());

        List<MovementView> afterCreate = queryService.movements(MovementFilter.none(), 0, 100).items().stream()
                .filter(v -> v.variantId().equals(m) || v.variantId().equals(p)).toList();
        assertThat(afterCreate).singleElement().satisfies(mv -> {
            assertThat(mv.type()).isEqualTo("INITIAL");
            assertThat(mv.stockBefore()).isZero();
            assertThat(mv.delta()).isEqualTo(5);
            assertThat(mv.stockAfter()).isEqualTo(5);
        });

        productService.update(created.getId(), product("it-Produto", variantWith("M", 8), variantWith("G", 2)));
        UUID g = jdbc.queryForObject("SELECT id FROM product_variants WHERE product_id = ? AND size = 'G'",
                UUID.class, created.getId().toString());
        variantIds.add(g.toString());

        List<MovementView> edits = queryService.movements(
                new MovementFilter(null, m, null, null, null, null, null, null), 0, 10).items();
        assertThat(edits.get(0).type()).isEqualTo("ADJUSTMENT");
        assertThat(edits.get(0).category()).isEqualTo("PRODUCT_EDIT");
        assertThat(edits.get(0).stockBefore()).isEqualTo(5);
        assertThat(edits.get(0).delta()).isEqualTo(3);
        assertThat(edits.get(0).stockAfter()).isEqualTo(8);
        assertThat(queryService.movements(new MovementFilter(null, g, null, null, null, null, null, null), 0, 10).items())
                .singleElement().satisfies(mv -> {
                    assertThat(mv.type()).isEqualTo("INITIAL");
                    assertThat(mv.stockAfter()).isEqualTo(2);
                });
        assertThat(stock(m)).isEqualTo(8);
    }

    // ── reconciliação ──────────────────────────────────────────────────────

    @Test
    void reconciliationFlagsMissingLedgerAdoptsBalanceAndDetectsBypassedWrites() {
        UUID v = variant("it-R", "M", 7, true);

        ReconciliationRow before = rowOf(v);
        assertThat(before.status()).isEqualTo("NO_LEDGER");

        var adopted = reconciliationService.adoptBalance(List.of(v), ADMIN);
        assertThat(adopted.adopted()).isEqualTo(1);
        MovementView opening = queryService.movements(
                new MovementFilter(null, v, null, null, null, null, null, null), 0, 10).items().get(0);
        assertThat(opening.type()).isEqualTo("OPENING");
        assertThat(opening.delta()).isZero();
        assertThat(opening.stockBefore()).isEqualTo(7);
        assertThat(opening.stockAfter()).isEqualTo(7);
        assertThat(rowOf(v).status()).isEqualTo("OK");
        assertThat(reconciliationService.adoptBalance(List.of(v), ADMIN).skippedVariantIds()).containsExactly(v);

        jdbc.update("UPDATE product_variants SET stock = stock + 1 WHERE id = ?", v.toString()); // escrita fora do livro
        ReconciliationRow diverged = rowOf(v);
        assertThat(diverged.status()).isEqualTo("DIVERGENT");
        assertThat(diverged.difference()).isEqualTo(1);
        assertThat(queryService.reconciliation(null, true, 0, 100).items()).extracting(ReconciliationRow::variantId)
                .contains(v);
    }

    // ── log e consultas ────────────────────────────────────────────────────

    @Test
    void logFiltersPaginationCapAndVariantSearchWork() {
        UUID a = variant("it-Camiseta Log", "M", 10, true);
        UUID inactive = variant("it-Inativa Log", "M", 3, false);
        UUID supplier = supplier();
        EntryDetail entry = entryService.create(note(supplier, "IT-7001", "key-" + id(), new Line(a, 5, null)));
        stockService.adjust(new StockService.AdjustCommand(a, -2, null, null,
                com.dnstore.backend.model.enums.StockAdjustmentCategory.DAMAGE, "Avaria", null, "outro@it.test"));

        assertThat(queryService.movements(new MovementFilter(null, a, null, null, null, null, null, null), 0, 50)
                .items()).extracting(MovementView::type).containsExactly("ADJUSTMENT", "ENTRY");
        assertThat(queryService.movements(new MovementFilter(null, a, java.util.Set.of(StockMovement.Type.ENTRY), null,
                null, null, null, null), 0, 50).items()).hasSize(1);
        assertThat(queryService.movements(new MovementFilter(null, a, null, null, null, "OUTRO@", null, null), 0, 50)
                .items()).extracting(MovementView::type).containsExactly("ADJUSTMENT");
        assertThat(queryService.movements(new MovementFilter(null, null, null, null, null, null,
                entry.summary().id(), null), 0, 50).items()).hasSize(1);
        assertThat(queryService.movements(new MovementFilter(null, a, null, LocalDate.now().plusDays(1), null, null,
                null, null), 0, 50).items()).isEmpty();
        PageView<MovementView> capped = queryService.movements(MovementFilter.none(), 0, 100_000);
        assertThat(capped.size()).isEqualTo(100);
        assertThat(capped.items().size()).isLessThanOrEqualTo(100);

        assertThat(queryService.variants("it-Camiseta", false, null, 0, 20).items()).extracting(v -> v.id()).contains(a);
        assertThat(queryService.variants("it-Inativa", false, null, 0, 20).items()).isEmpty();
        assertThat(queryService.variants("it-Inativa", true, null, 0, 20).items()).extracting(v -> v.id())
                .containsExactly(inactive);
    }

    @Test
    void ordersViewListsOrdersThatMovedStock() {
        UUID v = variant("it-O", "M", 10, true);
        String orderId = orderFixture();
        jdbc.update("INSERT INTO stock_movements (id, variant_id, delta, stock_before, stock_after, type, order_id, "
                + "created_at) VALUES (?, ?, -2, 10, 8, 'SALE', ?, NOW(6))", id(), v.toString(), orderId);

        var orders = queryService.orders(0, 100).items();

        assertThat(orders).filteredOn(o -> o.orderId().toString().equals(orderId)).singleElement().satisfies(o -> {
            assertThat(o.reservedQuantity()).isEqualTo(2);
            assertThat(o.releasedQuantity()).isZero();
            assertThat(o.movementCount()).isEqualTo(1);
        });
    }

    // ── concorrência ───────────────────────────────────────────────────────

    @Test
    void concurrentAdjustmentsAndNotesOnOneVariantKeepTheLedgerExactAndChained() throws Exception {
        UUID v = variant("it-Conc", "M", 100, true);
        UUID supplier = supplier();
        int adders = 10, removers = 10, notes = 6;
        List<Callable<Object>> work = new ArrayList<>();
        for (int i = 0; i < adders; i++) {
            work.add(() -> stockService.adjust(adjust(v, +3)));
        }
        for (int i = 0; i < removers; i++) {
            work.add(() -> stockService.adjust(adjust(v, -2)));
        }
        for (int i = 0; i < notes; i++) {
            int n = i;
            work.add(() -> entryService.create(note(supplier, "IT-9" + n, "key-" + id(), new Line(v, 5, null))));
        }
        runAll(work);

        int expected = 100 + adders * 3 - removers * 2 + notes * 5;
        assertThat(stock(v)).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(delta),0) FROM stock_movements WHERE variant_id = ?",
                Integer.class, v.toString())).isEqualTo(expected - 100);
        assertThat(count("SELECT COUNT(*) FROM stock_movements WHERE variant_id = ?", v)).isEqualTo(adders + removers + notes);
        var detail = queryService.reconciliationOf(v);
        assertThat(detail.chainBreaks()).as("stock_before de cada linha = stock_after da anterior").isEmpty();
        assertThat(detail.row().status()).isEqualTo("OK");
        assertThat(detail.row().ledgerBalance()).isEqualTo(expected);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private StockService.AdjustCommand adjust(UUID v, int delta) {
        return new StockService.AdjustCommand(v, delta, null, null, null, "Teste de concorrência", null, ADMIN);
    }

    private CreateCommand note(UUID supplier, String number, String key, Line... lines) {
        return new CreateCommand(supplier, number, "", LocalDate.now(), key, null, List.of(lines), ADMIN);
    }

    private UUID supplier() {
        return supplierService.create("it-fornecedor-" + id(), null, null).id();
    }

    private UUID variant(String productName, String size, int stock, boolean active) {
        String productId = id(), variantId = id();
        jdbc.update("INSERT INTO products (id, name, price, product_type, active) VALUES (?, ?, 25.00, 'PhysicalProduct', ?)",
                productId, productName, active);
        jdbc.update("INSERT INTO physical_products (id, stock) VALUES (?, ?)", productId, stock);
        jdbc.update("INSERT INTO product_variants (id, product_id, size, stock, active) VALUES (?, ?, ?, ?, ?)",
                variantId, productId, size, stock, active);
        productIds.add(productId);
        variantIds.add(variantId);
        return UUID.fromString(variantId);
    }

    private Product product(String name, ProductVariant... variants) {
        PhysicalProduct p = new PhysicalProduct();
        p.setName(name);
        p.setPrice(new BigDecimal("49.90"));
        p.setActive(true);
        p.setVariants(new ArrayList<>(List.of(variants)));
        return p;
    }

    private ProductVariant variantWith(String size, int stock) {
        ProductVariant v = new ProductVariant();
        v.setSize(size);
        v.setStock(stock);
        v.setActive(true);
        return v;
    }

    private UUID variantOf(Product product, String size) {
        return product.getVariants().stream().filter(v -> size.equals(v.getSize())).findFirst().orElseThrow().getId();
    }

    private String orderFixture() {
        String userId = id(), addressId = id(), orderId = id();
        jdbc.update("INSERT INTO users (id, name, email, password_hash) VALUES (?, 'IT', ?, 'x')", userId,
                userId + "@example.invalid");
        jdbc.update("INSERT INTO addresses (id, user_id, street, city, state, zip_code) VALUES (?, ?, 'Rua', 'Cidade', 'TS', '00000-000')",
                addressId, userId);
        jdbc.update("INSERT INTO orders (id, user_id, address_id, total, status, created_at) VALUES (?, ?, ?, 25.00, 'PENDING_PAYMENT', CURRENT_TIMESTAMP)",
                orderId, userId, addressId);
        userIds.add(userId);
        addressIds.add(addressId);
        orderIds.add(orderId);
        return orderId;
    }

    private ReconciliationRow rowOf(UUID variantId) {
        return queryService.reconciliationOf(variantId).row();
    }

    private int stock(UUID variantId) {
        return jdbc.queryForObject("SELECT stock FROM product_variants WHERE id = ?", Integer.class, variantId.toString());
    }

    private int count(String sql, UUID variantId) {
        return jdbc.queryForObject(sql, Integer.class, variantId.toString());
    }

    private <T> List<T> inParallel(int threads, Callable<T> task) throws Exception {
        return runAll(Collections.nCopies(threads, task));
    }

    private <T> List<T> runAll(List<? extends Callable<? extends T>> tasks) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(tasks.size(), 16));
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<? extends T> task : tasks) {
            futures.add(pool.submit(() -> {
                go.await();
                return task.call();
            }));
        }
        go.countDown();
        List<T> results = new ArrayList<>();
        try {
            for (Future<T> f : futures) {
                results.add(f.get(90, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        return results;
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }
}
