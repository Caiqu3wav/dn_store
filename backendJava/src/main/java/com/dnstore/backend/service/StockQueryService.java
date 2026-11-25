package com.dnstore.backend.service;

import com.dnstore.backend.dto.stock.StockDtos.ReconciliationDivergence;
import com.dnstore.backend.dto.stock.StockDtos.ReconciliationResponse;
import com.dnstore.backend.dto.stock.StockDtos.ReferenceRef;
import com.dnstore.backend.dto.stock.StockDtos.SaleOutflowResponse;
import com.dnstore.backend.dto.stock.StockDtos.StockEntryItemResponse;
import com.dnstore.backend.dto.stock.StockDtos.StockEntryResponse;
import com.dnstore.backend.dto.stock.StockDtos.StockEntrySummary;
import com.dnstore.backend.dto.stock.StockDtos.StockMovementResponse;
import com.dnstore.backend.dto.stock.StockDtos.StockVariantResponse;
import com.dnstore.backend.dto.stock.StockDtos.SupplierRef;
import com.dnstore.backend.dto.stock.StockDtos.UserRef;
import com.dnstore.backend.dto.stock.StockDtos.VariantRef;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockEntry;
import com.dnstore.backend.model.StockEntryItem;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.StockMovementType;
import com.dnstore.backend.model.enums.StockReferenceType;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.dnstore.backend.repository.StockEntryItemRepository;
import com.dnstore.backend.repository.StockEntryRepository;
import com.dnstore.backend.repository.StockMovementRepository;
import com.dnstore.backend.repository.StockMovementRepository.SaleOutflowRow;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas do módulo de estoque (log, notas, pedidos que baixaram estoque, reconciliação). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockQueryService {

    public static final int MAX_PAGE_SIZE = 100;

    public record MovementFilter(
            UUID variantId, UUID productId, StockMovementType type, LocalDate from, LocalDate to, UUID actorId,
            StockReferenceType referenceType, String referenceId, String invoiceNumber) {
    }

    public record EntryFilter(UUID supplierId, String invoiceNumber, LocalDate from, LocalDate to) {
    }

    private final StockMovementRepository movementRepository;
    private final StockEntryRepository entryRepository;
    private final StockEntryItemRepository entryItemRepository;
    private final ProductVariantRepository variantRepository;
    private final OrderRepository orderRepository;

    // ---- Log ----

    public Page<StockMovementResponse> searchMovements(MovementFilter filter, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "seq")));
        Set<String> invoiceEntryIds = null;
        if (filter.invoiceNumber() != null && !filter.invoiceNumber().isBlank()) {
            invoiceEntryIds = entryRepository.findAll(entrySpec(
                    new EntryFilter(null, filter.invoiceNumber(), null, null)), PageRequest.of(0, MAX_PAGE_SIZE))
                    .map(entry -> entry.getId().toString()).stream().collect(Collectors.toSet());
            if (invoiceEntryIds.isEmpty()) {
                return Page.empty(pageable);
            }
        }
        Page<StockMovement> result = movementRepository.findAll(movementSpec(filter, invoiceEntryIds), pageable);
        Map<String, ReferenceRef> references = resolveReferences(result.getContent());
        return result.map(movement -> toResponse(movement, references));
    }

    private Specification<StockMovement> movementSpec(MovementFilter filter, Set<String> invoiceEntryIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.variantId() != null) {
                predicates.add(cb.equal(root.get("productVariant").get("id"), filter.variantId()));
            }
            if (filter.productId() != null) {
                predicates.add(cb.equal(root.get("productVariant").get("product").get("id"), filter.productId()));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from().atStartOfDay()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.to().plusDays(1).atStartOfDay()));
            }
            if (filter.actorId() != null) {
                predicates.add(cb.equal(root.get("actor").get("id"), filter.actorId()));
            }
            if (filter.referenceType() != null) {
                predicates.add(cb.equal(root.get("referenceType"), filter.referenceType()));
            }
            if (filter.referenceId() != null && !filter.referenceId().isBlank()) {
                predicates.add(cb.equal(root.get("referenceId"), filter.referenceId().trim()));
            }
            if (invoiceEntryIds != null) {
                predicates.add(cb.equal(root.get("referenceType"), StockReferenceType.STOCK_ENTRY));
                predicates.add(root.get("referenceId").in(invoiceEntryIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Map<String, ReferenceRef> resolveReferences(List<StockMovement> movements) {
        Set<UUID> entryIds = new java.util.HashSet<>();
        for (StockMovement movement : movements) {
            if (movement.getReferenceType() == StockReferenceType.STOCK_ENTRY && movement.getReferenceId() != null) {
                entryIds.add(UUID.fromString(movement.getReferenceId()));
            }
        }
        Map<String, ReferenceRef> labels = new HashMap<>();
        if (!entryIds.isEmpty()) {
            for (StockEntry entry : entryRepository.findAllWithSupplierByIdIn(entryIds)) {
                labels.put(entry.getId().toString(), new ReferenceRef(StockReferenceType.STOCK_ENTRY,
                        entry.getId().toString(),
                        "Nota " + entry.getInvoiceNumber() + " · " + entry.getSupplier().getName()));
            }
        }
        return labels;
    }

    private StockMovementResponse toResponse(StockMovement movement, Map<String, ReferenceRef> references) {
        ReferenceRef reference = null;
        if (movement.getReferenceType() != null && movement.getReferenceId() != null) {
            reference = references.get(movement.getReferenceId());
            if (reference == null) {
                reference = new ReferenceRef(movement.getReferenceType(), movement.getReferenceId(),
                        movement.getReferenceType() == StockReferenceType.ORDER
                                ? "Pedido #" + shortId(movement.getReferenceId())
                                : movement.getReferenceId());
            }
        }
        return new StockMovementResponse(movement.getId(), movement.getCreatedAt(), movement.getType(),
                variantRef(movement.getProductVariant()), movement.getQuantityDelta(), movement.getBalanceBefore(),
                movement.getBalanceAfter(), reference, movement.getReasonCode(), movement.getReasonText(),
                userRef(movement.getActor()));
    }

    // ---- Notas ----

    public Page<StockEntrySummary> searchEntries(EntryFilter filter, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "receivedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<StockEntry> entries = entryRepository.findAll(entrySpec(filter), pageable);
        Map<UUID, StockEntryItemRepository.EntryTotals> totals = entries.isEmpty()
                ? Map.of()
                : entryItemRepository.totalsByEntry(entries.map(StockEntry::getId).getContent()).stream()
                        .collect(Collectors.toMap(StockEntryItemRepository.EntryTotals::getEntryId, t -> t));
        return entries.map(entry -> {
            StockEntryItemRepository.EntryTotals total = totals.get(entry.getId());
            return new StockEntrySummary(entry.getId(),
                    new SupplierRef(entry.getSupplier().getId(), entry.getSupplier().getName()),
                    entry.getInvoiceNumber(), entry.getIssueDate(), entry.getReceivedAt(),
                    userRef(entry.getCreatedBy()), total == null ? 0 : total.getItemCount(),
                    total == null ? 0 : total.getTotalUnits());
        });
    }

    private Specification<StockEntry> entrySpec(EntryFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.supplierId() != null) {
                predicates.add(cb.equal(root.get("supplier").get("id"), filter.supplierId()));
            }
            if (filter.invoiceNumber() != null && !filter.invoiceNumber().isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("invoiceNumber")),
                        "%" + escapeLike(filter.invoiceNumber().trim().toUpperCase()) + "%", '\\'));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("receivedAt"), filter.from().atStartOfDay()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("receivedAt"), filter.to().plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public StockEntryResponse entryDetail(UUID id) {
        StockEntry entry = entryRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nota de entrada não encontrada."));
        Map<UUID, StockMovement> movementByVariant = movementRepository
                .findByReference(StockReferenceType.STOCK_ENTRY, entry.getId().toString()).stream()
                .collect(Collectors.toMap(m -> m.getProductVariant().getId(), m -> m, (a, b) -> a));
        List<StockEntryItemResponse> items = new ArrayList<>();
        int totalUnits = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        boolean hasCost = false;
        for (StockEntryItem item : entry.getItems()) {
            StockMovement movement = movementByVariant.get(item.getProductVariant().getId());
            items.add(new StockEntryItemResponse(variantRef(item.getProductVariant()), item.getQuantity(),
                    item.getUnitCost(), movement == null ? null : movement.getBalanceBefore(),
                    movement == null ? null : movement.getBalanceAfter()));
            totalUnits += item.getQuantity();
            if (item.getUnitCost() != null) {
                hasCost = true;
                totalCost = totalCost.add(item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        return new StockEntryResponse(entry.getId(),
                new SupplierRef(entry.getSupplier().getId(), entry.getSupplier().getName()),
                entry.getInvoiceNumber(), entry.getIssueDate(), entry.getReceivedAt(), entry.getNotes(),
                userRef(entry.getCreatedBy()), entry.getCreatedAt(), totalUnits, hasCost ? totalCost : null, items);
    }

    // ---- Variantes ----

    public Page<StockVariantResponse> searchVariants(String q, int page, int size) {
        String term = q == null || q.isBlank() ? null : q.trim();
        return variantRepository.searchForStock(term, PageRequest.of(Math.max(page, 0), clampSize(size)))
                .map(variant -> new StockVariantResponse(variant.getId(), variant.getProduct().getId(),
                        variant.getProduct().getName(), variant.getSize(), variant.getSku(), variant.getStock(),
                        variant.isActive(), variant.getProduct().isActive()));
    }

    // ---- Pedidos que baixaram estoque ----

    public Page<SaleOutflowResponse> searchSaleOutflows(LocalDate from, LocalDate to, int page, int size) {
        Page<SaleOutflowRow> rows = movementRepository.findSaleOutflows(
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(),
                PageRequest.of(Math.max(page, 0), clampSize(size)));
        List<UUID> orderIds = rows.getContent().stream().map(row -> UUID.fromString(row.getOrderId())).toList();
        Map<UUID, Order> orders = orderIds.isEmpty()
                ? Map.of()
                : orderRepository.findAllWithUserByIdIn(orderIds).stream()
                        .collect(Collectors.toMap(Order::getId, order -> order));
        return rows.map(row -> {
            Order order = orders.get(UUID.fromString(row.getOrderId()));
            return new SaleOutflowResponse(row.getOrderId(), "Pedido #" + shortId(row.getOrderId()),
                    row.getMovedAt(), order == null ? null : order.getStatus(),
                    order == null ? null : userRef(order.getUser()), row.getLines(), row.getUnits(),
                    order != null && order.isStockReleased());
        });
    }

    // ---- Reconciliação ----

    public ReconciliationResponse reconciliation() {
        List<StockMovementRepository.ReconciliationRow> rows = movementRepository.findDivergences();
        Map<UUID, ProductVariant> variants = rows.isEmpty()
                ? Map.of()
                : variantRepository.findAllById(rows.stream().map(StockMovementRepository.ReconciliationRow::getVariantId)
                        .toList()).stream().collect(Collectors.toMap(ProductVariant::getId, v -> v));
        List<ReconciliationDivergence> divergences = rows.stream()
                .map(row -> new ReconciliationDivergence(variantRef(variants.get(row.getVariantId())),
                        row.getStock(), row.getLedgerBalance(), row.getStock() - row.getLedgerBalance()))
                .toList();
        return new ReconciliationResponse(movementRepository.countVariantsWithMovements(), divergences);
    }

    // ---- Helpers ----

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static String shortId(String id) {
        return id == null || id.length() < 8 ? id : id.substring(0, 8);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static VariantRef variantRef(ProductVariant variant) {
        if (variant == null) {
            return null;
        }
        return new VariantRef(variant.getId(), variant.getProduct().getId(), variant.getProduct().getName(),
                variant.getSize(), variant.getSku());
    }

    private static UserRef userRef(User user) {
        return user == null ? null : new UserRef(user.getId(), user.getName(), user.getEmail());
    }
}
