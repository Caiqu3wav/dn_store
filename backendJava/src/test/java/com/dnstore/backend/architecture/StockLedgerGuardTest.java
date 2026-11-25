package com.dnstore.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.repository.StockMovementRepository;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guardas do livro de estoque: o saldo só pode mudar em serviços que também registram a movimentação, e o
 * livro é append-only.
 */
class StockLedgerGuardTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");

    /** Serviços autorizados a escrever em ProductVariant.stock (todos registram no livro). */
    private static final Set<String> STOCK_WRITERS = Set.of(
            "service/OrderService.java",              // checkout (baixa por venda)
            "service/OrderInventoryService.java",     // devolução / reativação
            "service/ProductService.java",            // cadastro, lote e variação legada
            "service/ProductDefaultsService.java",    // padrões de produto (estoque legado)
            "service/StockLedgerService.java");       // nota de entrada e ajuste (applyDelta)

    @Test
    void setStock_isCalledOnlyByServicesThatRecordInTheLedger() throws IOException {
        Set<String> callers = new TreeSet<>();
        for (Path file : javaSources()) {
            String content = Files.readString(file);
            if (content.contains(".setStock(") || content.contains("variant.setStock(")) {
                callers.add(MAIN_SOURCES.relativize(file).toString().replace('\\', '/')
                        .replace("com/dnstore/backend/", ""));
            }
        }

        assertThat(callers)
                .as("setStock fora dos serviços que registram no livro de estoque")
                .isSubsetOf(STOCK_WRITERS);

        for (String writer : STOCK_WRITERS) {
            String content = Files.readString(MAIN_SOURCES.resolve("com/dnstore/backend").resolve(writer));
            assertThat(content.contains("stockLedger") || content.contains("StockLedgerService"))
                    .as("%s altera estoque mas não referencia o livro", writer)
                    .isTrue();
        }
    }

    @Test
    void noQueryOrNativeSqlUpdatesVariantStockBehindTheLedger() throws IOException {
        for (Path file : javaSources()) {
            String content = Files.readString(file);
            String normalized = content.replaceAll("\\s+", " ").toLowerCase();
            assertThat(normalized.contains("update productvariant") || normalized.contains("update product_variants"))
                    .as("%s contém UPDATE direto em variantes (contornaria o livro)", file)
                    .isFalse();
        }
    }

    @Test
    void stockMovementIsAppendOnly() {
        List<String> mutators = Arrays.stream(StockMovement.class.getDeclaredMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("set"))
                .toList();
        assertThat(mutators).as("StockMovement não pode ter setters").isEmpty();
        assertThat(StockMovement.class.isAnnotationPresent(org.hibernate.annotations.Immutable.class)).isTrue();

        Set<String> repositoryMethods = Arrays.stream(StockMovementRepository.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertThat(repositoryMethods)
                .as("o repositório do livro não pode expor remoção/atualização")
                .noneMatch(name -> name.startsWith("delete") || name.startsWith("remove")
                        || name.startsWith("update") || name.equals("saveAll") || name.equals("flush"));
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
