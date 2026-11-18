package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.repository.ProductRepository;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

/**
 * V27 contra um MySQL real e descartável (127.0.0.1:33316, dn_store_it). O contexto já sobe com o V27
 * aplicado pelo Flyway; o teste devolve a tabela ao estado legado do V1 (DEFAULT 'physical' e linhas
 * 'physical') e executa o arquivo da migration de novo. Nunca aponte este teste para o banco real.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        args = {
                "--spring.config.location=classpath:/application-integration-local.properties",
                "--spring.datasource.password=dn_store_it_test_password"
        })
@ActiveProfiles("integration-local")
class ProductTypeMigrationMySqlIT {

    private static final String MIGRATION = "db/migration/V27__normalize_product_type_discriminator.sql";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private ProductRepository productRepository;

    private String lowercase;
    private String capitalized;
    private String alreadyCorrect;
    private String createdByLegacyDefault;
    private String nullType;

    @BeforeEach
    void restoreLegacyV1State() {
        lowercase = insertProduct("physical");
        capitalized = insertProduct("Physical");
        alreadyCorrect = insertProduct("PhysicalProduct");
        nullType = insertProduct(null);
        jdbc.execute("ALTER TABLE products ALTER COLUMN product_type SET DEFAULT 'physical'");
        createdByLegacyDefault = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO products (id, name, price, active) VALUES (?, 'V27 IT default', 10.00, TRUE)",
                createdByLegacyDefault);
        jdbc.update("INSERT INTO physical_products (id, stock) VALUES (?, 1)", createdByLegacyDefault);
    }

    @AfterEach
    void cleanup() {
        List<String> ids = List.of(lowercase, capitalized, alreadyCorrect, createdByLegacyDefault, nullType);
        for (String id : ids) {
            jdbc.update("DELETE FROM physical_products WHERE id = ?", id);
            jdbc.update("DELETE FROM products WHERE id = ?", id);
        }
        runMigration(); // deixa o schema compartilhado com o DEFAULT definitivo
    }

    @Test
    void legacyDefaultProducesRowsHibernateCannotRead() {
        assertThat(productType(createdByLegacyDefault)).isEqualTo("physical");

        assertThatThrownBy(() -> productRepository.findById(UUID.fromString(lowercase)))
                .hasMessageContaining("physical");
    }

    @Test
    void normalizesLegacyValuesAndLeavesEverythingElseUntouched() {
        long productsBefore = count("SELECT COUNT(*) FROM products");

        runMigration();

        assertThat(productType(lowercase)).isEqualTo("PhysicalProduct");
        assertThat(productType(capitalized)).isEqualTo("PhysicalProduct");
        assertThat(productType(createdByLegacyDefault)).isEqualTo("PhysicalProduct");
        assertThat(productType(alreadyCorrect)).isEqualTo("PhysicalProduct");
        assertThat(productType(nullType)).isNull(); // fora do escopo: nao se adivinha o tipo
        assertThat(count("SELECT COUNT(*) FROM products")).isEqualTo(productsBefore);
        assertThat(count("SELECT COUNT(*) FROM products WHERE product_type = 'physical'")).isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM products WHERE id = ?", String.class, lowercase))
                .isEqualTo("V27 IT physical");
    }

    @Test
    void normalizedRowsAreReadableByHibernate() {
        runMigration();

        assertThat(productRepository.findById(UUID.fromString(lowercase)))
                .get().isInstanceOf(PhysicalProduct.class);
        assertThat(productRepository.findById(UUID.fromString(createdByLegacyDefault)))
                .get().isInstanceOf(PhysicalProduct.class);
    }

    @Test
    void newDefaultIsPhysicalProductAndRowsWithoutATypeAreReadable() {
        runMigration();

        assertThat(jdbc.queryForObject(
                "SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'product_type'",
                String.class)).isEqualTo("PhysicalProduct");

        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO products (id, name, price, active) VALUES (?, 'V27 IT new default', 10.00, TRUE)", id);
            jdbc.update("INSERT INTO physical_products (id, stock) VALUES (?, 1)", id);
            assertThat(productType(id)).isEqualTo("PhysicalProduct");
            assertThat(productRepository.findById(UUID.fromString(id))).isPresent();
        } finally {
            jdbc.update("DELETE FROM physical_products WHERE id = ?", id);
            jdbc.update("DELETE FROM products WHERE id = ?", id);
        }
    }

    @Test
    void runningTheMigrationTwiceChangesNothingTheSecondTime() {
        runMigration();
        long normalized = count("SELECT COUNT(*) FROM products WHERE product_type = 'PhysicalProduct'");

        runMigration();

        assertThat(count("SELECT COUNT(*) FROM products WHERE product_type = 'PhysicalProduct'")).isEqualTo(normalized);
        assertThat(productType(nullType)).isNull();
    }

    @Test
    void flywayAppliedV27Successfully() {
        assertThat(jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '27'", Boolean.class)).isTrue();
    }

    private String insertProduct(String productType) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO products (id, name, price, product_type, active) VALUES (?, ?, 10.00, ?, TRUE)",
                id, "V27 IT " + (productType == null ? "null" : productType), productType);
        jdbc.update("INSERT INTO physical_products (id, stock) VALUES (?, 1)", id);
        return id;
    }

    private String productType(String id) {
        return jdbc.queryForObject("SELECT product_type FROM products WHERE id = ?", String.class, id);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private void runMigration() {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(MIGRATION));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
