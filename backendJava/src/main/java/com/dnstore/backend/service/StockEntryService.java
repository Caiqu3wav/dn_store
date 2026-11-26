package com.dnstore.backend.service;

import com.dnstore.backend.dto.StockDtos.EntryDetail;
import com.dnstore.backend.exception.StockConflictException;
import com.dnstore.backend.model.StockEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Entrada de estoque por nota de fornecedor. Esta classe NÃO é transacional de propósito: a transação fica em
 * {@link StockEntryWriter}. Se uma corrida estourar uma unicidade (chave de idempotência ou nota), a transação já
 * foi desfeita quando chegamos ao {@code catch}, e podemos consultar a nota vencedora.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockEntryService {

    public record Line(UUID variantId, int quantity, BigDecimal unitCost) {}

    public record CreateCommand(UUID supplierId, String invoiceNumber, String invoiceSeries, LocalDate invoiceDate,
                                String idempotencyKey, String notes, List<Line> lines, String performedBy) {}

    static final int MAX_LINES = 200;

    private final StockEntryWriter writer;

    public EntryDetail create(CreateCommand raw) {
        CreateCommand cmd = normalize(raw);
        String hash = hash(cmd);
        try {
            return writer.write(cmd, hash);
        } catch (DataIntegrityViolationException race) {
            log.info("stock.entry.race idempotencyKey={} invoice={}/{}", cmd.idempotencyKey(), cmd.invoiceSeries(),
                    cmd.invoiceNumber());
            return writer.resolveAfterConflict(cmd, hash, race);
        }
    }

    static CreateCommand normalize(CreateCommand c) {
        if (c.supplierId() == null) {
            throw new IllegalArgumentException("Informe o fornecedor.");
        }
        String number = c.invoiceNumber() == null ? "" : c.invoiceNumber().trim();
        if (number.isEmpty() || number.length() > 40) {
            throw new IllegalArgumentException("Informe o número da nota (até 40 caracteres).");
        }
        String series = c.invoiceSeries() == null ? "" : c.invoiceSeries().trim();
        if (series.length() > 10) {
            throw new IllegalArgumentException("A série deve ter até 10 caracteres.");
        }
        if (c.invoiceDate() == null) {
            throw new IllegalArgumentException("Informe a data da nota.");
        }
        if (c.invoiceDate().isAfter(LocalDate.now().plusDays(1))) {
            throw new IllegalArgumentException("A data da nota não pode estar no futuro.");
        }
        String key = c.idempotencyKey() == null ? "" : c.idempotencyKey().trim();
        if (key.isEmpty() || key.length() > 64) {
            throw new IllegalArgumentException("Informe a chave de idempotência (até 64 caracteres).");
        }
        String notes = c.notes() == null || c.notes().isBlank() ? null : c.notes().trim();
        if (notes != null && notes.length() > 255) {
            throw new IllegalArgumentException("As observações devem ter até 255 caracteres.");
        }
        List<Line> lines = c.lines();
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A nota precisa ter ao menos uma linha.");
        }
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("A nota pode ter no máximo " + MAX_LINES + " linhas.");
        }
        Set<UUID> seen = new HashSet<>();
        for (Line line : lines) {
            if (line.variantId() == null) {
                throw new IllegalArgumentException("Toda linha precisa de uma variação.");
            }
            if (!seen.add(line.variantId())) {
                throw new IllegalArgumentException("A mesma variação aparece mais de uma vez na nota.");
            }
            if (line.quantity() <= 0) {
                throw new IllegalArgumentException("A quantidade de cada linha deve ser maior que zero.");
            }
            if (line.unitCost() != null && (line.unitCost().signum() < 0 || line.unitCost().scale() > 2
                    || line.unitCost().precision() - line.unitCost().scale() > 10)) {
                throw new IllegalArgumentException("Custo unitário inválido (máximo 2 casas decimais, sem negativos).");
            }
        }
        return new CreateCommand(c.supplierId(), number, series, c.invoiceDate(), key, notes, List.copyOf(lines),
                c.performedBy());
    }

    /** SHA-256 do conteúdo normalizado da nota; permite distinguir reenvio idêntico de chave reutilizada. */
    static String hash(CreateCommand c) {
        StringBuilder sb = new StringBuilder()
                .append(c.supplierId()).append('|').append(c.invoiceSeries()).append('|')
                .append(c.invoiceNumber()).append('|').append(c.invoiceDate()).append('|')
                .append(c.notes() == null ? "" : c.notes());
        c.lines().stream()
                .sorted(Comparator.comparing(l -> l.variantId().toString()))
                .forEach(l -> sb.append('|').append(l.variantId()).append(':').append(l.quantity()).append(':')
                        .append(l.unitCost() == null ? "" : l.unitCost().stripTrailingZeros().toPlainString()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static StockConflictException duplicateInvoice(StockEntry existing) {
        return new StockConflictException("Esta nota já foi registrada para o fornecedor.",
                Map.of("existingEntryId", existing.getId().toString(),
                        "invoiceNumber", existing.getInvoiceNumber()));
    }
}
