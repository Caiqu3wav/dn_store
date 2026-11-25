package com.dnstore.backend.controller;

import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.User;
import com.dnstore.backend.service.StockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/stock")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /** POST /api/admin/stock/entry — dar entrada em lote */
    @PostMapping("/entry")
    public ResponseEntity<List<StockMovementResponse>> entry(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody EntryRequest request) {
        List<StockService.StockEntryItem> items = request.items().stream()
                .map(i -> new StockService.StockEntryItem(i.variantId(), i.quantity(), i.reason()))
                .toList();
        List<StockMovement> movements = stockService.entry(items, admin.getEmail());
        return ResponseEntity.ok(movements.stream().map(StockMovementResponse::from).toList());
    }

    /** PUT /api/admin/stock/adjust — ajuste manual de uma variação */
    @PutMapping("/adjust")
    public ResponseEntity<StockMovementResponse> adjust(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody AdjustRequest request) {
        StockMovement movement = stockService.adjust(
                request.variantId(), request.delta(), request.reason(), admin.getEmail());
        return ResponseEntity.ok(StockMovementResponse.from(movement));
    }

    /** GET /api/admin/stock/history/product/{productId} */
    @GetMapping("/history/product/{productId}")
    public ResponseEntity<List<StockMovementResponse>> historyByProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(
                stockService.historyByProduct(productId).stream().map(StockMovementResponse::from).toList());
    }

    /** GET /api/admin/stock/history/variant/{variantId} */
    @GetMapping("/history/variant/{variantId}")
    public ResponseEntity<List<StockMovementResponse>> historyByVariant(@PathVariable UUID variantId) {
        return ResponseEntity.ok(
                stockService.historyByVariant(variantId).stream().map(StockMovementResponse::from).toList());
    }

    // ── DTOs ────────────────────────────────────────────────────────────────

    public record EntryItemRequest(
            @NotNull UUID variantId,
            int quantity,
            String reason) {}

    public record EntryRequest(@NotEmpty List<@Valid EntryItemRequest> items) {}

    public record AdjustRequest(
            @NotNull UUID variantId,
            int delta,
            @NotNull String reason) {}

    public record StockMovementResponse(
            UUID id,
            UUID variantId,
            String variantSize,
            String productName,
            int delta,
            int stockAfter,
            String type,
            UUID orderId,
            String reason,
            String performedBy,
            LocalDateTime createdAt) {
        static StockMovementResponse from(StockMovement m) {
            return new StockMovementResponse(
                    m.getId(),
                    m.getVariant().getId(),
                    m.getVariant().getSize(),
                    m.getVariant().getProduct() != null ? m.getVariant().getProduct().getName() : null,
                    m.getDelta(),
                    m.getStockAfter(),
                    m.getType().name(),
                    m.getOrder() != null ? m.getOrder().getId() : null,
                    m.getReason(),
                    m.getPerformedBy(),
                    m.getCreatedAt());
        }
    }
}
