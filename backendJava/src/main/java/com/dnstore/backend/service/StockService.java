package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.OrderItem;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.dnstore.backend.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductVariantRepository variantRepository;
    private final StockMovementRepository movementRepository;

    public record StockEntryItem(UUID variantId, int quantity, String reason) {}

    /** Gives stock in for a list of variants (purchase order / manual entry). */
    @Transactional
    public List<StockMovement> entry(List<StockEntryItem> items, String performedBy) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Nenhum item informado para entrada de estoque.");
        }
        List<StockMovement> movements = new ArrayList<>();
        for (StockEntryItem item : items) {
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
            }
            ProductVariant variant = variantRepository.findByIdForUpdate(item.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variação não encontrada: " + item.variantId()));
            variant.setStock(Math.addExact(variant.getStock(), item.quantity()));
            variantRepository.save(variant);
            movements.add(record(variant, item.quantity(), StockMovement.Type.ENTRY, null,
                    item.reason(), performedBy));
            log.info("stock.entry variantId={} delta={} stockAfter={} by={}",
                    variant.getId(), item.quantity(), variant.getStock(), performedBy);
        }
        return movements;
    }

    /** Manual adjustment — delta can be positive or negative. */
    @Transactional
    public StockMovement adjust(UUID variantId, int delta, String reason, String performedBy) {
        if (delta == 0) throw new IllegalArgumentException("Delta não pode ser zero.");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Motivo é obrigatório.");
        ProductVariant variant = variantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variação não encontrada."));
        int newStock = variant.getStock() + delta;
        if (newStock < 0) {
            throw new ConflictException("Ajuste resultaria em estoque negativo (" + newStock + ").");
        }
        variant.setStock(newStock);
        variantRepository.save(variant);
        log.info("stock.adjust variantId={} delta={} stockAfter={} by={}",
                variantId, delta, newStock, performedBy);
        return record(variant, delta, StockMovement.Type.ADJUSTMENT, null, reason, performedBy);
    }

    /** Called by OrderInventoryService when releasing stock on cancellation. */
    @Transactional
    public void recordRelease(Order order) {
        if (order.getItems() == null) return;
        for (OrderItem item : order.getItems()) {
            ProductVariant variant = item.getProductVariant();
            if (variant == null) continue;
            record(variant, item.getQuantity(), StockMovement.Type.CANCELLATION, order, null, null);
        }
    }

    /** Called by OrderService when reserving stock on checkout. */
    @Transactional
    public void recordSale(Order order) {
        if (order.getItems() == null) return;
        for (OrderItem item : order.getItems()) {
            ProductVariant variant = item.getProductVariant();
            if (variant == null) continue;
            record(variant, -item.getQuantity(), StockMovement.Type.SALE, order, null, null);
        }
    }

    public List<StockMovement> historyByProduct(UUID productId) {
        return movementRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    public List<StockMovement> historyByVariant(UUID variantId) {
        return movementRepository.findByVariantIdOrderByCreatedAtDesc(variantId);
    }

    private StockMovement record(ProductVariant variant, int delta, StockMovement.Type type,
                                  Order order, String reason, String performedBy) {
        StockMovement m = new StockMovement();
        m.setVariant(variant);
        m.setDelta(delta);
        m.setStockAfter(variant.getStock());
        m.setType(type);
        m.setOrder(order);
        m.setReason(reason);
        m.setPerformedBy(performedBy);
        return movementRepository.save(m);
    }
}
