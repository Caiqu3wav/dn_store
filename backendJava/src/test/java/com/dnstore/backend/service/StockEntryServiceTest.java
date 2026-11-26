package com.dnstore.backend.service;

import com.dnstore.backend.dto.StockDtos.EntryDetail;
import com.dnstore.backend.service.StockEntryService.CreateCommand;
import com.dnstore.backend.service.StockEntryService.Line;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockEntryServiceTest {

    private final UUID supplier = UUID.randomUUID();
    private final UUID variantA = UUID.randomUUID();
    private final UUID variantB = UUID.randomUUID();

    private CreateCommand command(List<Line> lines) {
        return new CreateCommand(supplier, " 1234 ", null, LocalDate.now(), " key-1 ", "  ", lines, "admin@x");
    }

    @Test
    void normalizeTrimsFieldsAndDefaultsTheSeries() {
        CreateCommand n = StockEntryService.normalize(command(List.of(new Line(variantA, 2, new BigDecimal("10.50")))));

        assertThat(n.invoiceNumber()).isEqualTo("1234");
        assertThat(n.invoiceSeries()).isEmpty();
        assertThat(n.idempotencyKey()).isEqualTo("key-1");
        assertThat(n.notes()).isNull();
    }

    @Test
    void normalizeRejectsInvalidNotes() {
        assertThatThrownBy(() -> StockEntryService.normalize(command(List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ao menos uma linha");
        assertThatThrownBy(() -> StockEntryService.normalize(command(List.of(new Line(variantA, 0, null)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maior que zero");
        assertThatThrownBy(() -> StockEntryService.normalize(command(List.of(new Line(variantA, -2, null)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockEntryService.normalize(
                command(List.of(new Line(variantA, 1, null), new Line(variantA, 2, null)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mais de uma vez");
        assertThatThrownBy(() -> StockEntryService.normalize(
                command(List.of(new Line(variantA, 1, new BigDecimal("-1"))))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Custo");
        assertThatThrownBy(() -> StockEntryService.normalize(
                command(List.of(new Line(variantA, 1, new BigDecimal("1.234"))))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockEntryService.normalize(new CreateCommand(supplier, "  ", "", LocalDate.now(),
                "k", null, List.of(new Line(variantA, 1, null)), "a")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("número da nota");
        assertThatThrownBy(() -> StockEntryService.normalize(new CreateCommand(supplier, "1", "", LocalDate.now().plusDays(5),
                "k", null, List.of(new Line(variantA, 1, null)), "a")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("futuro");
        assertThatThrownBy(() -> StockEntryService.normalize(new CreateCommand(supplier, "1", "", LocalDate.now(),
                " ", null, List.of(new Line(variantA, 1, null)), "a")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("idempotência");
    }

    @Test
    void hashIgnoresLineOrderAndCostFormattingButNotContent() {
        CreateCommand ab = StockEntryService.normalize(command(List.of(
                new Line(variantA, 2, new BigDecimal("10.50")), new Line(variantB, 1, null))));
        CreateCommand ba = StockEntryService.normalize(command(List.of(
                new Line(variantB, 1, null), new Line(variantA, 2, new BigDecimal("10.5")))));
        CreateCommand different = StockEntryService.normalize(command(List.of(
                new Line(variantA, 3, new BigDecimal("10.50")), new Line(variantB, 1, null))));

        assertThat(StockEntryService.hash(ab)).isEqualTo(StockEntryService.hash(ba)).hasSize(64);
        assertThat(StockEntryService.hash(different)).isNotEqualTo(StockEntryService.hash(ab));
    }

    @Test
    void whenARaceBreaksAUniqueKeyTheWinnerIsResolvedAfterTheRollback() {
        StockEntryWriter writer = mock(StockEntryWriter.class);
        StockEntryService service = new StockEntryService(writer);
        DataIntegrityViolationException race = new DataIntegrityViolationException("dup");
        EntryDetail winner = mock(EntryDetail.class);
        when(writer.write(any(), anyString())).thenThrow(race);
        when(writer.resolveAfterConflict(any(), anyString(), any())).thenReturn(winner);

        EntryDetail result = service.create(command(List.of(new Line(variantA, 1, null))));

        assertThat(result).isSameAs(winner);
        verify(writer).resolveAfterConflict(any(), anyString(), any());
    }
}
