package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.exception.StockConflictException;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.enums.StockAdjustmentCategory;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.dnstore.backend.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Entrada avulsa (legada), ajuste manual e consultas simples do livro. Toda escrita passa por {@link StockLedger}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductVariantRepository variantRepository;
    private final StockMovementRepository movementRepository;
    private final StockLedger ledger;

    public record StockEntryItem(UUID variantId, int quantity, String reason) {}

    /**
     * Ajuste manual. Informe {@code delta} OU {@code countedQuantity} (contagem física; o delta é calculado
     * sob lock). {@code expectedBalance} é o saldo que o admin viu na tela; se mudou, o ajuste é recusado.
     */
    public record AdjustCommand(UUID variantId, Integer delta, Integer countedQuantity, Integer expectedBalance,
                                StockAdjustmentCategory category, String reason, String idempotencyKey,
                                String performedBy) {}

    public record AdjustResult(StockMovement movement, boolean replayed) {}

    /** Entrada avulsa, sem nota (endpoint legado /entry). Notas de fornecedor usam {@code StockEntryService}. */
    @Transactional
    public List<StockMovement> entry(List<StockEntryItem> items, String performedBy) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Nenhum item informado para entrada de estoque.");
        }
        for (StockEntryItem item : items) {
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
            }
        }
        Map<UUID, ProductVariant> variants = lockVariants(items.stream().map(StockEntryItem::variantId).toList());
        List<StockMovement> movements = new ArrayList<>();
        for (StockEntryItem item : items) {
            ProductVariant variant = variants.get(item.variantId());
            movements.add(ledger.post(variant, item.quantity(), StockMovement.Type.ENTRY,
                    StockLedger.Context.manual(item.reason(), null, performedBy, null)));
            variantRepository.save(variant);
            log.info("stock.entry variantId={} delta={} stockAfter={} by={}",
                    variant.getId(), item.quantity(), variant.getStock(), performedBy);
        }
        return movements;
    }

    /** Compatível com o contrato antigo: delta + motivo. */
    @Transactional
    public StockMovement adjust(UUID variantId, int delta, String reason, String performedBy) {
        return adjust(new AdjustCommand(variantId, delta, null, null, null, reason, null, performedBy)).movement();
    }

    @Transactional
    public AdjustResult adjust(AdjustCommand cmd) {
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new IllegalArgumentException("Motivo é obrigatório.");
        }
        if ((cmd.delta() == null) == (cmd.countedQuantity() == null)) {
            throw new IllegalArgumentException("Informe o delta ou a quantidade contada (apenas um).");
        }
        if (cmd.countedQuantity() != null && cmd.countedQuantity() < 0) {
            throw new IllegalArgumentException("A quantidade contada não pode ser negativa.");
        }
        ProductVariant variant = variantRepository.findByIdForUpdate(cmd.variantId())
                .orElseThrow(() -> new ResourceNotFoundException("Variação não encontrada."));

        // A chave é conferida depois do lock da variante: duas requisições iguais em paralelo se serializam aqui.
        if (cmd.idempotencyKey() != null) {
            var existing = movementRepository.findByIdempotencyKey(cmd.idempotencyKey());
            if (existing.isPresent()) {
                return new AdjustResult(sameAdjustmentOrFail(existing.get(), cmd), true);
            }
        }
        if (cmd.expectedBalance() != null && cmd.expectedBalance() != variant.getStock()) {
            throw new StockConflictException("O saldo mudou desde que você abriu a tela.",
                    Map.of("currentBalance", Integer.toString(variant.getStock()),
                            "expectedBalance", Integer.toString(cmd.expectedBalance())));
        }
        int delta = cmd.delta() != null ? cmd.delta() : cmd.countedQuantity() - variant.getStock();
        if (delta == 0) {
            throw new IllegalArgumentException("O ajuste não altera o saldo (delta zero).");
        }
        if (variant.getStock() + delta < 0) {
            throw new ConflictException("Ajuste resultaria em estoque negativo (" + (variant.getStock() + delta) + ").");
        }
        StockAdjustmentCategory category = cmd.category() != null ? cmd.category() : StockAdjustmentCategory.OTHER;
        StockMovement movement = ledger.post(variant, delta, StockMovement.Type.ADJUSTMENT,
                StockLedger.Context.manual(cmd.reason().trim(), category, cmd.performedBy(), cmd.idempotencyKey()));
        variantRepository.save(variant);
        log.info("stock.adjust variantId={} delta={} stockAfter={} category={} by={}",
                variant.getId(), delta, variant.getStock(), category, cmd.performedBy());
        return new AdjustResult(movement, false);
    }

    public List<StockMovement> historyByProduct(UUID productId) {
        return movementRepository.findByProductIdOrderBySeqDesc(productId);
    }

    public List<StockMovement> historyByVariant(UUID variantId) {
        return movementRepository.findByVariantIdOrderBySeqDesc(variantId);
    }

    private StockMovement sameAdjustmentOrFail(StockMovement existing, AdjustCommand cmd) {
        boolean same = existing.getType() == StockMovement.Type.ADJUSTMENT
                && existing.getVariant().getId().equals(cmd.variantId())
                && (cmd.delta() == null || existing.getDelta() == cmd.delta())
                && (cmd.countedQuantity() == null || existing.getStockAfter() == cmd.countedQuantity());
        if (!same) {
            throw new ConflictException("A chave de idempotência já foi usada em outra operação.");
        }
        return existing;
    }

    private Map<UUID, ProductVariant> lockVariants(List<UUID> variantIds) {
        List<UUID> ordered = variantIds.stream().distinct()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, ProductVariant> variants = variantRepository.findAllByIdInForUpdate(ordered).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        if (variants.size() != ordered.size()) {
            throw new ResourceNotFoundException("Uma ou mais variações não foram encontradas.");
        }
        return variants;
    }
}
