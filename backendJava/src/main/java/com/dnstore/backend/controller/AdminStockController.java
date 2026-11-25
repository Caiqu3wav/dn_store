package com.dnstore.backend.controller;

import com.dnstore.backend.dto.stock.StockDtos.PageResponse;
import com.dnstore.backend.dto.stock.StockDtos.ReconciliationResponse;
import com.dnstore.backend.dto.stock.StockDtos.SaleOutflowResponse;
import com.dnstore.backend.dto.stock.StockDtos.StockAdjustmentRequest;
import com.dnstore.backend.dto.stock.StockDtos.StockEntryRequest;
import com.dnstore.backend.dto.stock.StockDtos.StockEntryResponse;
import com.dnstore.backend.dto.stock.StockDtos.StockEntrySummary;
import com.dnstore.backend.dto.stock.StockDtos.StockMovementResponse;
import com.dnstore.backend.dto.stock.StockDtos.StockVariantResponse;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.StockMovementType;
import com.dnstore.backend.model.enums.StockReferenceType;
import com.dnstore.backend.service.StockAdjustmentService;
import com.dnstore.backend.service.StockEntryService;
import com.dnstore.backend.service.StockQueryService;
import com.dnstore.backend.service.StockQueryService.EntryFilter;
import com.dnstore.backend.service.StockQueryService.MovementFilter;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Entradas por nota, ajustes manuais e log de movimentações de estoque. Somente administradores. */
@RestController
@RequestMapping("/api/admin/stock")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminStockController {

    private final StockEntryService entryService;
    private final StockAdjustmentService adjustmentService;
    private final StockQueryService queryService;

    // ---- Notas de entrada ----

    /** 201 quando a nota é criada; 200 quando a mesma chave de idempotência é reenviada (replay). */
    @PostMapping("/entries")
    public ResponseEntity<StockEntryResponse> createEntry(
            @Valid @RequestBody StockEntryRequest request,
            @AuthenticationPrincipal User admin) {
        StockEntryService.EntryResult result = entryService.create(request, admin);
        StockEntryResponse body = queryService.entryDetail(result.entryId());
        if (result.replayed()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create("/api/admin/stock/entries/" + result.entryId())).body(body);
    }

    @GetMapping("/entries")
    public ResponseEntity<PageResponse<StockEntrySummary>> listEntries(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(queryService.searchEntries(
                new EntryFilter(supplierId, invoiceNumber, from, to), page, size)));
    }

    @GetMapping("/entries/{id}")
    public ResponseEntity<StockEntryResponse> getEntry(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.entryDetail(id));
    }

    // ---- Ajustes ----

    @PostMapping("/adjustments")
    public ResponseEntity<Void> adjust(
            @Valid @RequestBody StockAdjustmentRequest request,
            @AuthenticationPrincipal User admin) {
        StockAdjustmentService.AdjustmentResult result = adjustmentService.adjust(request, admin);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).build();
    }

    // ---- Log e consultas ----

    @GetMapping("/movements")
    public ResponseEntity<PageResponse<StockMovementResponse>> movements(
            @RequestParam(required = false) UUID variantId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) StockMovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) StockReferenceType referenceType,
            @RequestParam(required = false) String referenceId,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(PageResponse.of(queryService.searchMovements(new MovementFilter(
                variantId, productId, type, from, to, actorId, referenceType, referenceId, invoiceNumber),
                page, size)));
    }

    @GetMapping("/variants")
    public ResponseEntity<PageResponse<StockVariantResponse>> variants(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(queryService.searchVariants(q, page, size)));
    }

    @GetMapping("/sales-outflows")
    public ResponseEntity<PageResponse<SaleOutflowResponse>> saleOutflows(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(queryService.searchSaleOutflows(from, to, page, size)));
    }

    @GetMapping("/reconciliation")
    public ResponseEntity<ReconciliationResponse> reconciliation() {
        return ResponseEntity.ok(queryService.reconciliation());
    }
}
