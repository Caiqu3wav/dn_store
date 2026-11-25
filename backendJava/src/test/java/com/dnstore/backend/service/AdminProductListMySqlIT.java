package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dnstore.backend.dto.ProductResponse;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * GET /api/products/admin: o DTO é montado dentro da transação do service, então cada coleção LAZY lida
 * ali vira um SELECT. Mede quantos SELECTs a listagem dispara para N produtos. Usa o MySQL descartável
 * do PaymentSchemaMySqlIT (127.0.0.1:33316, dn_store_it) e não roda no `./mvnw test` padrão.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        args = {
                "--spring.config.location=classpath:/application-integration-local.properties",
                "--spring.datasource.password=dn_store_it_test_password",
                "--spring.jpa.properties.hibernate.generate_statistics=true"
        })
@ActiveProfiles("integration-local")
class AdminProductListMySqlIT {

    private static final String NAME_PREFIX = "Lista admin IT ";
    private static final int PRODUCTS = 20;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProductService productService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final List<String> productIds = new ArrayList<>();

    @BeforeEach
    void fixtures() {
        for (int p = 0; p < PRODUCTS; p++) {
            String productId = UUID.randomUUID().toString();
            productIds.add(productId);
            jdbc.update("INSERT INTO products (id, name, price, product_type, active) VALUES (?, ?, ?, ?, TRUE)",
                    productId, NAME_PREFIX + p, new BigDecimal("50.00"), "PhysicalProduct");
            jdbc.update("INSERT INTO physical_products (id, stock) VALUES (?, 10)", productId);
            for (String size : List.of("P", "M", "G")) {
                jdbc.update("INSERT INTO product_variants (id, product_id, size, stock, active) "
                        + "VALUES (?, ?, ?, 5, TRUE)", UUID.randomUUID().toString(), productId, size);
            }
            for (int i = 0; i < 2; i++) {
                jdbc.update("INSERT INTO product_images (id, product_id, image_url, is_main) VALUES (?, ?, ?, ?)",
                        UUID.randomUUID().toString(), productId, "https://example.invalid/" + p + "-" + i + ".jpg", i == 0);
            }
        }
    }

    @AfterEach
    void cleanup() {
        for (String productId : productIds) {
            jdbc.update("DELETE FROM product_images WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM product_variants WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM physical_products WHERE id = ?", productId);
            jdbc.update("DELETE FROM products WHERE id = ?", productId);
        }
        productIds.clear();
    }

    @Test
    void adminListLoadsImagesAndVariantsWithoutOneSelectPerProduct() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        List<ProductResponse> products = productService.searchForAdminResponses(NAME_PREFIX, null, null, null, null);

        long queries = stats.getPrepareStatementCount();
        System.out.println("ADMIN_PRODUCTS products=" + products.size() + " queries=" + queries);
        assertThat(products).hasSize(PRODUCTS);
        assertThat(products).allSatisfy(product -> {
            assertThat(product.images()).hasSize(2);
            assertThat(product.variants()).hasSize(3);
        });
        assertThat(queries).as("SELECTs para listar %d produtos", PRODUCTS).isLessThanOrEqualTo(4);
    }
}
