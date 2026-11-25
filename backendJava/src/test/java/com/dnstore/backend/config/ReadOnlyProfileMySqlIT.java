package com.dnstore.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dnstore.backend.model.Category;
import com.dnstore.backend.repository.CategoryRepository;
import com.dnstore.backend.service.PaymentExpirationScheduler;
import com.dnstore.backend.service.PaymentWebhookWorker;
import com.dnstore.backend.service.shipping.ShippingDataCleanupScheduler;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * O perfil "readonly" existe para inspecionar um banco real (Aiven) sem escrever nele.
 * Este teste prova, contra MySQL de verdade, que o caminho real de escrita da aplicação
 * (repositório JPA) é recusado pelo servidor e que a leitura continua funcionando.
 *
 * Usa o mesmo MySQL local dos demais ITs (127.0.0.1:33316, banco descartável dn_store_it).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        args = {
                "--spring.config.location=classpath:/application-integration-local.properties,"
                        + "classpath:/application-readonly.properties",
                "--spring.datasource.password=" + ReadOnlyProfileMySqlIT.PASSWORD
        })
@ActiveProfiles({"integration-local", "readonly"})
class ReadOnlyProfileMySqlIT {

    static final String PASSWORD = "dn_store_it_test_password";
    private static final String URL =
            "jdbc:mysql://127.0.0.1:33316/dn_store_it?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String USER = "dn_store_it";

    static {
        // Com o perfil readonly o Flyway fica desligado; deixa o schema pronto no banco descartável.
        Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Autowired
    private ApplicationContext context;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void writersAreNotLoadedAndFlywayIsOff() {
        assertThat(context.getBeanNamesForType(DatabaseSeeder.class)).isEmpty();
        assertThat(context.getBeanNamesForType(PaymentExpirationScheduler.class)).isEmpty();
        assertThat(context.getBeanNamesForType(PaymentWebhookWorker.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ShippingDataCleanupScheduler.class)).isEmpty();
        assertThat(context.getBeanNamesForType(Flyway.class)).isEmpty();
    }

    @Test
    void pooledSessionsAreReadOnlyOnTheServerToo() {
        // Camada do servidor (connection-init-sql), independente da checagem do driver.
        Integer readOnly = jdbc.queryForObject("SELECT @@session.transaction_read_only", Integer.class);
        assertThat(readOnly).isEqualTo(1);
    }

    @Test
    void readsWorkButJpaWritesAreRejected() throws SQLException {
        assertThat(categoryRepository.count()).isGreaterThanOrEqualTo(0);

        // Controle: o mesmo INSERT é válido em uma conexão normal (senão o teste passaria pelo motivo errado).
        String slug = "it-readonly-control";
        try (Connection normal = DriverManager.getConnection(URL, USER, PASSWORD);
                Statement statement = normal.createStatement()) {
            statement.executeUpdate("DELETE FROM categories WHERE slug = '" + slug + "'");
            assertThat(statement.executeUpdate(
                    "INSERT INTO categories (id, name, slug) VALUES (UUID(), '" + slug + "', '" + slug + "')"))
                    .isEqualTo(1);
            statement.executeUpdate("DELETE FROM categories WHERE slug = '" + slug + "'");
        }

        Category category = Category.builder().name("it-readonly-denied").slug("it-readonly-denied").build();
        assertThatThrownBy(() -> categoryRepository.saveAndFlush(category))
                .hasStackTraceContaining("read-only");

        try (Connection normal = DriverManager.getConnection(URL, USER, PASSWORD);
                Statement statement = normal.createStatement()) {
            var result = statement.executeQuery(
                    "SELECT COUNT(*) FROM categories WHERE slug = 'it-readonly-denied'");
            result.next();
            assertThat(result.getInt(1)).isZero();
        }
    }
}
