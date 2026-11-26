package com.dnstore.backend.ledger;

import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.dnstore.backend.service.StockLedger;
import com.dnstore.backend.service.StockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V29/V30 aplicadas sobre a V28 em MySQL 8.0 local, mais o livro com locks reais. Rodar com -Dtest=StockLedgerMySqlIT. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        args = {
                "--spring.config.location=classpath:/application-integration-local.properties",
                "--spring.datasource.password=dn_store_it_test_password"
        })
@ActiveProfiles("integration-local")
class StockLedgerMySqlIT {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private StockService stockService;
    @Autowired private StockLedger ledger;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private String productId;
    private String variantId;
    private final java.util.List<String> supplierIds = new java.util.ArrayList<>();

    @BeforeEach
    void fixture() {
        productId = id();
        variantId = id();
        jdbc.update("INSERT INTO products (id, name, price, product_type, active) VALUES (?, ?, ?, ?, TRUE)",
                productId, "IT Stock Product", new BigDecimal("25.00"), "PhysicalProduct");
        jdbc.update("INSERT INTO physical_products (id, stock) VALUES (?, 10)", productId);
        jdbc.update("INSERT INTO product_variants (id, product_id, size, stock, active) VALUES (?, ?, ?, 10, TRUE)",
                variantId, productId, "M");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM stock_movements WHERE variant_id = ?", variantId);
        jdbc.update("DELETE FROM stock_entry_item WHERE variant_id = ?", variantId);
        jdbc.update("DELETE FROM stock_entry WHERE supplier_id IN (SELECT id FROM supplier WHERE name LIKE 'it-%')");
        jdbc.update("DELETE FROM supplier WHERE name LIKE 'it-%'");
        jdbc.update("DELETE FROM product_variants WHERE id = ?", variantId);
        jdbc.update("DELETE FROM physical_products WHERE id = ?", productId);
        jdbc.update("DELETE FROM products WHERE id = ?", productId);
    }

    @Test
    void v29AndV30AreAppliedSuccessfullyOnTopOfV28() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IN ('28','29','30') ORDER BY installed_rank");
        assertThat(rows).extracting(r -> r.get("version")).containsExactly("28", "29", "30");
        assertThat(rows).extracting(r -> r.get("success")).containsOnly(true);
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'stock_movements'", String.class))
                .contains("seq", "stock_before", "category", "stock_entry_id", "idempotency_key");
    }

    @Test
    void adjustGoesThroughTheLedgerWithBeforeAfterAndSequence() {
        StockMovement movement = stockService.adjust(UUID.fromString(variantId), -3, "Avaria", "admin@it.test");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT stock_before, stock_after, delta, type, category, performed_by, seq FROM stock_movements WHERE id = ?",
                movement.getId().toString());
        assertThat(row.get("stock_before")).isEqualTo(10);
        assertThat(row.get("stock_after")).isEqualTo(7);
        assertThat(row.get("delta")).isEqualTo(-3);
        assertThat(row.get("type")).isEqualTo("ADJUSTMENT");
        assertThat(row.get("category")).isEqualTo("OTHER");
        assertThat(row.get("performed_by")).isEqualTo("admin@it.test");
        assertThat(((Number) row.get("seq")).longValue()).isPositive();
        assertThat(jdbc.queryForObject("SELECT stock FROM product_variants WHERE id = ?", Integer.class, variantId))
                .isEqualTo(7);
    }

    @Test
    void ledgerRefusesToMoveStockOfAVariantThatIsNotPessimisticallyLocked() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            ProductVariant unlocked = variantRepository.findById(UUID.fromString(variantId)).orElseThrow();
            ledger.post(unlocked, -1, StockMovement.Type.ADJUSTMENT,
                    StockLedger.Context.manual("sem lock", null, "admin@it.test", null));
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("PESSIMISTIC_WRITE");

        assertThat(jdbc.queryForObject("SELECT stock FROM product_variants WHERE id = ?", Integer.class, variantId))
                .isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE variant_id = ?", Integer.class,
                variantId)).isZero();
    }

    @Test
    void rowsInsertedByTheOldCodeStillWorkAndGetASequence() {
        // O código antigo (V28) insere sem as colunas novas.
        jdbc.update("INSERT INTO stock_movements (id, variant_id, delta, stock_after, type, created_at) "
                + "VALUES (?, ?, 5, 15, 'ENTRY', NOW(6))", id(), variantId);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT stock_before, seq FROM stock_movements WHERE variant_id = ?", variantId);
        assertThat(row.get("stock_before")).isNull();
        assertThat(((Number) row.get("seq")).longValue()).isPositive();
    }

    @Test
    void databaseRejectsInconsistentBalanceDuplicateKeysAndNonPositiveQuantities() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO stock_movements (id, variant_id, delta, stock_before, stock_after, type, created_at) "
                        + "VALUES (?, ?, 1, 5, 9, 'ENTRY', NOW(6))", id(), variantId))
                .as("ck_sm_balance")
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("ck_sm_balance");

        String key = "it-key-" + id();
        jdbc.update("INSERT INTO stock_movements (id, variant_id, delta, stock_before, stock_after, type, "
                + "idempotency_key, created_at) VALUES (?, ?, 1, 10, 11, 'ADJUSTMENT', ?, NOW(6))", id(), variantId, key);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO stock_movements (id, variant_id, delta, stock_before, "
                + "stock_after, type, idempotency_key, created_at) VALUES (?, ?, 1, 11, 12, 'ADJUSTMENT', ?, NOW(6))",
                id(), variantId, key)).isInstanceOf(DataIntegrityViolationException.class);

        String supplierId = supplier("it-supplier-" + id());
        String entryId = entry(supplierId, "123", "1", "it-entry-" + id());
        assertThatThrownBy(() -> entry(supplierId, "123", "1", "it-entry-" + id()))
                .as("mesma nota (fornecedor + série + número)")
                .isInstanceOf(DataIntegrityViolationException.class);
        entry(supplierId, "123", "2", "it-entry-" + id()); // série diferente é outra nota
        assertThatThrownBy(() -> jdbc.update("INSERT INTO stock_entry_item (id, entry_id, variant_id, quantity) "
                + "VALUES (?, ?, ?, 0)", id(), entryId, variantId))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("ck_sei_quantity");
        jdbc.update("INSERT INTO stock_entry_item (id, entry_id, variant_id, quantity) VALUES (?, ?, ?, 3)",
                id(), entryId, variantId);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO stock_entry_item (id, entry_id, variant_id, quantity) "
                + "VALUES (?, ?, ?, 4)", id(), entryId, variantId))
                .as("mesma variante duas vezes na mesma nota")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String supplier(String name) {
        String id = id();
        jdbc.update("INSERT INTO supplier (id, name, active, created_at) VALUES (?, ?, TRUE, NOW(6))", id, name);
        supplierIds.add(id);
        return id;
    }

    private String entry(String supplierId, String number, String series, String key) {
        String id = id();
        jdbc.update("INSERT INTO stock_entry (id, supplier_id, invoice_number, invoice_series, invoice_date, "
                + "idempotency_key, request_hash, created_by, created_at) VALUES (?, ?, ?, ?, CURRENT_DATE, ?, ?, "
                + "'admin@it.test', NOW(6))", id, supplierId, number, series, key, "0".repeat(64));
        return id;
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }
}
