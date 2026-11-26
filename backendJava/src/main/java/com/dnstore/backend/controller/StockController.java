package com.dnstore.backend.controller;

import com.dnstore.backend.dto.StockDtos.AdoptResult;
import com.dnstore.backend.dto.StockDtos.EntryDetail;
import com.dnstore.backend.dto.StockDtos.EntrySummary;
import com.dnstore.backend.dto.StockDtos.MovementView;
import com.dnstore.backend.dto.StockDtos.OrderStockView;
import com.dnstore.backend.dto.StockDtos.PageView;
import com.dnstore.backend.dto.StockDtos.ReconciliationDetail;
import com.dnstore.backend.dto.StockDtos.ReconciliationRow;
import com.dnstore.backend.dto.StockDtos.SupplierView;
import com.dnstore.backend.dto.StockDtos.VariantView;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.StockAdjustmentCategory;
import com.dnstore.backend.service.StockEntryService;
import com.dnstore.backend.service.StockQueryService;
import com.dnstore.backend.service.StockQueryService.MovementFilter;
import com.dnstore.backend.service.StockReconciliationService;
import com.dnstore.backend.service.StockService;
import com.dnstore.backend.service.SupplierService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/stock")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final StockEntryService stockEntryService;
    private final StockQueryService queryService;
    private final StockReconciliationService reconciliationService;
    private final SupplierService supplierService;

    // ── Fornecedores ───────────────────────────────────────────────────────

    @GetMapping("/suppliers")
    public PageView<SupplierView> suppliers(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return supplierService.search(q, includeInactive, page, size);
    }

    @PostMapping("/suppliers")
    public ResponseEntity<SupplierView> createSupplier(@Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierService.create(request.name(), request.document(), request.contact()));
    }

    // ── Notas de entrada ───────────────────────────────────────────────────

    /** 201 na criação; 200 quando é reenvio idêntico (mesma chave e mesmo conteúdo). */
    @PostMapping("/entries")
    public ResponseEntity<EntryDetail> createEntry(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody CreateEntryRequest request) {
        List<StockEntryService.Line> lines = request.lines().stream()
                .map(l -> new StockEntryService.Line(l.variantId(), l.quantity(), l.unitCost()))
                .toList();
        EntryDetail detail = stockEntryService.create(new StockEntryService.CreateCommand(
                request.supplierId(), request.invoiceNumber(), request.invoiceSeries(), request.invoiceDate(),
                request.idempotencyKey(), request.notes(), lines, admin.getEmail()));
        return ResponseEntity.status(detail.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(detail);
    }

    @GetMapping("/entries")
    public PageView<EntrySummary> entries(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.entries(supplierId, q, from, to, page, size);
    }

    @GetMapping("/entries/{id}")
    public EntryDetail entry(@PathVariable UUID id) {
        return queryService.entryDetail(id, false);
    }

    // ── Entrada avulsa (legado) e ajuste ───────────────────────────────────

    /** Legado: entrada sem nota. Prefira POST /entries. */
    @Deprecated
    @PostMapping("/entry")
    public ResponseEntity<List<MovementView>> entry(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody EntryRequest request) {
        List<StockService.StockEntryItem> items = request.items().stream()
                .map(i -> new StockService.StockEntryItem(i.variantId(), i.quantity(), i.reason()))
                .toList();
        return ResponseEntity.ok(stockService.entryViews(items, admin.getEmail()));
    }

    /**
     * PUT /api/admin/stock/adjust. Os campos antigos (variantId, delta, reason) continuam valendo; os novos são
     * opcionais: countedQuantity (contagem), expectedBalance, category e idempotencyKey.
     */
    @PutMapping("/adjust")
    public ResponseEntity<MovementView> adjust(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody AdjustRequest request) {
        StockService.AdjustResult result = stockService.adjust(new StockService.AdjustCommand(
                request.variantId(), request.delta(), request.countedQuantity(), request.expectedBalance(),
                request.category(), request.reason(), request.idempotencyKey(), admin.getEmail()));
        return ResponseEntity.ok(result.movement());
    }

    // ── Log ────────────────────────────────────────────────────────────────

    @GetMapping("/movements")
    public PageView<MovementView> movements(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID variantId,
            @RequestParam(required = false) Set<StockMovement.Type> type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String performedBy,
            @RequestParam(required = false) UUID entryId,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queryService.movements(
                new MovementFilter(productId, variantId, type, from, to, performedBy, entryId, orderId), page, size);
    }

    @GetMapping("/variants/{variantId}/timeline")
    public PageView<MovementView> timeline(@PathVariable UUID variantId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return queryService.movements(
                new MovementFilter(null, variantId, null, null, null, null, null, null), page, size);
    }

    @GetMapping("/variants")
    public PageView<VariantView> variants(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return queryService.variants(q, includeInactive, productId, page, size);
    }

    @GetMapping("/orders")
    public PageView<OrderStockView> orders(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return queryService.orders(page, size);
    }

    /** GET /api/admin/stock/history/product/{productId} (legado) */
    @GetMapping("/history/product/{productId}")
    public ResponseEntity<List<MovementView>> historyByProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(stockService.historyByProduct(productId));
    }

    /** GET /api/admin/stock/history/variant/{variantId} (legado) */
    @GetMapping("/history/variant/{variantId}")
    public ResponseEntity<List<MovementView>> historyByVariant(@PathVariable UUID variantId) {
        return ResponseEntity.ok(stockService.historyByVariant(variantId));
    }

    // ── Reconciliação ──────────────────────────────────────────────────────

    @GetMapping("/reconciliation")
    public PageView<ReconciliationRow> reconciliation(
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "false") boolean onlyIssues,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queryService.reconciliation(productId, onlyIssues, page, size);
    }

    @GetMapping("/reconciliation/variants/{variantId}")
    public ReconciliationDetail reconciliationOf(@PathVariable UUID variantId) {
        return queryService.reconciliationOf(variantId);
    }

    @PostMapping("/reconciliation/adopt")
    public AdoptResult adopt(@AuthenticationPrincipal User admin, @Valid @RequestBody AdoptRequest request) {
        return reconciliationService.adoptBalance(request.variantIds(), admin.getEmail());
    }

    // ── Requests ───────────────────────────────────────────────────────────

    public record SupplierRequest(@NotBlank @Size(max = 150) String name, @Size(max = 20) String document,
                                  @Size(max = 255) String contact) {}

    public record EntryLineRequest(@NotNull UUID variantId, int quantity, BigDecimal unitCost) {}

    public record CreateEntryRequest(
            @NotNull UUID supplierId,
            @NotBlank @Size(max = 40) String invoiceNumber,
            @Size(max = 10) String invoiceSeries,
            @NotNull LocalDate invoiceDate,
            @NotBlank @Size(max = 64) String idempotencyKey,
            @Size(max = 255) String notes,
            @NotEmpty List<@Valid EntryLineRequest> lines) {}

    public record EntryItemRequest(
            @NotNull UUID variantId,
            int quantity,
            String reason) {}

    public record EntryRequest(@NotEmpty List<@Valid EntryItemRequest> items) {}

    public record AdjustRequest(
            @NotNull UUID variantId,
            Integer delta,
            Integer countedQuantity,
            Integer expectedBalance,
            StockAdjustmentCategory category,
            @NotBlank @Size(max = 255) String reason,
            @Size(max = 64) String idempotencyKey) {}

    public record AdoptRequest(@NotEmpty List<@NotNull UUID> variantIds) {}
}
