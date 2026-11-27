package com.dnstore.backend.ledger;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste-guarda: quem escreve saldo de estoque tem que passar pelo livro. Qualquer {@code setStock(} novo fora
 * da lista abaixo, ou um UPDATE de estoque em JPQL/SQL, quebra este teste. Se a mudança for legítima, ela
 * precisa gravar o movimento na mesma transação e a lista deve ser atualizada na revisão.
 */
class StockWriteGuardTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Pattern SET_STOCK = Pattern.compile("\\.setStock\\(");
    private static final Pattern UPDATE_STOCK = Pattern.compile("(?is)update\\s+\\w+(\\s+\\w+)?\\s+set\\s[^\"]*\\bstock\\b");

    /** arquivo -> quantidade exata de chamadas a setStock( e por quê. */
    private static final Map<String, Integer> ALLOWED_SET_STOCK = Map.of(
            // O livro é quem altera o saldo de variantes existentes.
            "service/StockLedger.java", 1,
            // Checkout: a baixa continua onde estava; recordSale grava o livro logo após o save, na mesma transação.
            "service/OrderService.java", 1,
            // Agregado desnormalizado do produto (physical_products.stock) e variante "Único" nova (INITIAL).
            "service/ProductService.java", 2,
            // Variantes novas criadas pelos padrões de produto (INITIAL registrado após o save).
            "service/ProductDefaultsService.java", 2);

    @Test
    void setStock_onlyAppearsInServicesThatGoThroughTheLedger() throws IOException {
        Map<String, Integer> found = new TreeMap<>();
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                int count = count(SET_STOCK, Files.readString(file));
                if (count > 0) {
                    found.put(MAIN_JAVA.resolve("com/dnstore/backend").relativize(file).toString(), count);
                }
            }
        }

        assertThat(found).containsExactlyInAnyOrderEntriesOf(ALLOWED_SET_STOCK);
    }

    @Test
    void noJpqlOrNativeUpdateChangesStock() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                assertThat(count(UPDATE_STOCK, Files.readString(file)))
                        .as("UPDATE de estoque fora do livro em %s", file)
                        .isZero();
            }
        }
    }

    @Test
    void movementRepositoryAndEntityAreAppendOnly() throws Exception {
        Class<?> repository = Class.forName("com.dnstore.backend.repository.StockMovementRepository");
        assertThat(repository.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("delete") || name.equals("saveAll") || name.equals("flush"));

        Class<?> entity = Class.forName("com.dnstore.backend.model.StockMovement");
        assertThat(entity.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("set"));
        assertThat(entity.isAnnotationPresent(org.hibernate.annotations.Immutable.class)).isTrue();
    }

    private static int count(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
