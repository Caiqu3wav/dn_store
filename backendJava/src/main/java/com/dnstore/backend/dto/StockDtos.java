package com.dnstore.backend.dto;

import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockEntry;
import com.dnstore.backend.model.StockEntryItem;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.Supplier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Respostas da API de estoque. Nunca expõem entidades JPA. Os mapeamentos devem rodar dentro da transação. */
public final class StockDtos {

    private StockDtos() {
    }

    public record PageView<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public static <T> PageView<T> of(List<T> items, int page, int size, long total) {
            int pages = size == 0 ? 0 : (int) ((total + size - 1) / size);
            return new PageView<>(items, page, size, total, pages);
        }
    }

    public record SupplierView(UUID id, String name, String document, String contact, boolean active,
                               LocalDateTime createdAt) {
        public static SupplierView from(Supplier s) {
            return new SupplierView(s.getId(), s.getName(), s.getDocument(), s.getContact(), s.isActive(),
                    s.getCreatedAt());
        }
    }

    public record VariantView(UUID id, UUID productId, String productName, boolean productActive, String size,
                              String color, String sku, int stock, boolean active) {
        public static VariantView from(ProductVariant v) {
            return new VariantView(v.getId(), v.getProduct().getId(), v.getProduct().getName(),
                    v.getProduct().isActive(), v.getSize(), v.getColor(), v.getSku(), v.getStock(), v.isActive());
        }
    }

    public record MovementView(
            UUID id,
            Long seq,
            UUID variantId,
            UUID productId,
            String productName,
            String variantSize,
            String variantColor,
            int delta,
            Integer stockBefore,
            int stockAfter,
            String type,
            String category,
            UUID orderId,
            UUID entryId,
            String invoiceNumber,
            String supplierName,
            String reason,
            String performedBy,
            LocalDateTime createdAt) {

        public static MovementView from(StockMovement m) {
            ProductVariant v = m.getVariant();
            StockEntry e = m.getStockEntry();
            return new MovementView(
                    m.getId(),
                    m.getSeq(),
                    v.getId(),
                    v.getProduct() != null ? v.getProduct().getId() : null,
                    v.getProduct() != null ? v.getProduct().getName() : null,
                    v.getSize(),
                    v.getColor(),
                    m.getDelta(),
                    m.getStockBefore(),
                    m.getStockAfter(),
                    m.getType().name(),
                    m.getCategory() != null ? m.getCategory().name() : null,
                    m.getOrder() != null ? m.getOrder().getId() : null,
                    e != null ? e.getId() : null,
                    e != null ? e.getInvoiceNumber() : null,
                    e != null && e.getSupplier() != null ? e.getSupplier().getName() : null,
                    m.getReason(),
                    m.getPerformedBy(),
                    m.getCreatedAt());
        }
    }

    public record EntryItemView(UUID variantId, UUID productId, String productName, String size, String color,
                                int quantity, BigDecimal unitCost, BigDecimal lineTotal) {
        public static EntryItemView from(StockEntryItem i) {
            ProductVariant v = i.getVariant();
            BigDecimal total = i.getUnitCost() == null ? null
                    : i.getUnitCost().multiply(BigDecimal.valueOf(i.getQuantity()));
            return new EntryItemView(v.getId(), v.getProduct().getId(), v.getProduct().getName(), v.getSize(),
                    v.getColor(), i.getQuantity(), i.getUnitCost(), total);
        }
    }

    public record EntrySummary(UUID id, UUID supplierId, String supplierName, String invoiceNumber,
                               String invoiceSeries, LocalDate invoiceDate, int itemCount, int totalQuantity,
                               BigDecimal totalCost, String createdBy, LocalDateTime createdAt) {}

    public record EntryDetail(EntrySummary summary, String notes, List<EntryItemView> items,
                              List<MovementView> movements, boolean replayed) {}

    public record OrderStockView(UUID orderId, String status, boolean stockReleased, BigDecimal total,
                                 LocalDateTime orderCreatedAt, int reservedQuantity, int releasedQuantity,
                                 int movementCount, LocalDateTime lastMovementAt) {}

    public record ReconciliationRow(UUID variantId, UUID productId, String productName, String size, String color,
                                    boolean active, int currentStock, Integer ledgerBalance, Integer difference,
                                    String status) {}

    public record ChainBreak(long seq, int stockBefore, int previousStockAfter) {}

    public record ReconciliationDetail(ReconciliationRow row, List<ChainBreak> chainBreaks) {}

    public record AdoptResult(int adopted, List<UUID> skippedVariantIds) {}
}
