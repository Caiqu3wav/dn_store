package com.dnstore.backend.dto.stock;

import com.dnstore.backend.model.enums.StockAdjustmentReason;
import com.dnstore.backend.model.enums.StockMovementType;
import com.dnstore.backend.model.enums.StockReferenceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Requests e responses do módulo de estoque (admin). */
public final class StockDtos {

    private StockDtos() {
    }

    // ---- Fornecedores ----

    public record SupplierRequest(
            @NotBlank(message = "Informe o nome do fornecedor.")
            @Size(max = 160, message = "O nome deve ter no máximo 160 caracteres.")
            String name,
            @Size(max = 20, message = "O documento deve ter no máximo 20 caracteres.")
            String document,
            Boolean active) {
    }

    public record SupplierResponse(UUID id, String name, String document, boolean active, LocalDateTime createdAt) {
    }

    // ---- Notas de entrada ----

    public record StockEntryItemRequest(
            @NotNull(message = "Informe a variação.")
            UUID variantId,
            @Min(value = 1, message = "A quantidade deve ser maior que zero.")
            @Max(value = 1_000_000, message = "Quantidade acima do limite permitido.")
            int quantity,
            @DecimalMin(value = "0.00", message = "O custo unitário não pode ser negativo.")
            @Digits(integer = 8, fraction = 2, message = "Custo unitário inválido.")
            BigDecimal unitCost) {
    }

    public record StockEntryRequest(
            @NotNull(message = "Informe o fornecedor.")
            UUID supplierId,
            @NotBlank(message = "Informe o número da nota.")
            @Size(max = 40, message = "O número da nota deve ter no máximo 40 caracteres.")
            String invoiceNumber,
            LocalDate issueDate,
            @Size(max = 500, message = "A observação deve ter no máximo 500 caracteres.")
            String notes,
            @NotBlank(message = "Chave de idempotência obrigatória.")
            @Size(max = 64, message = "Chave de idempotência inválida.")
            String idempotencyKey,
            @NotEmpty(message = "A nota precisa ter ao menos um item.")
            @Size(max = 200, message = "A nota pode ter no máximo 200 itens.")
            List<@Valid StockEntryItemRequest> items) {
    }

    public record SupplierRef(UUID id, String name) {
    }

    public record UserRef(UUID id, String name, String email) {
    }

    public record VariantRef(UUID id, UUID productId, String productName, String size, String sku) {
    }

    public record StockEntryItemResponse(
            VariantRef variant, int quantity, BigDecimal unitCost, Integer balanceBefore, Integer balanceAfter) {
    }

    public record StockEntryResponse(
            UUID id, SupplierRef supplier, String invoiceNumber, LocalDate issueDate, LocalDateTime receivedAt,
            String notes, UserRef createdBy, LocalDateTime createdAt, int totalUnits, BigDecimal totalCost,
            List<StockEntryItemResponse> items) {
    }

    public record StockEntrySummary(
            UUID id, SupplierRef supplier, String invoiceNumber, LocalDate issueDate, LocalDateTime receivedAt,
            UserRef createdBy, long itemCount, long totalUnits) {
    }

    // ---- Ajustes ----

    public record StockAdjustmentRequest(
            @NotNull(message = "Informe a variação.")
            UUID variantId,
            @NotNull(message = "Informe a quantidade do ajuste.")
            Integer delta,
            @NotNull(message = "Informe o saldo que você viu na tela.")
            Integer expectedBalance,
            @NotNull(message = "Informe a categoria do ajuste.")
            StockAdjustmentReason reasonCode,
            @NotBlank(message = "Informe o motivo do ajuste.")
            @Size(min = 10, max = 500, message = "O motivo deve ter entre 10 e 500 caracteres.")
            String reason,
            @NotBlank(message = "Chave de idempotência obrigatória.")
            @Size(max = 64, message = "Chave de idempotência inválida.")
            String idempotencyKey) {
    }

    // ---- Livro ----

    public record ReferenceRef(StockReferenceType type, String id, String label) {
    }

    public record StockMovementResponse(
            UUID id, LocalDateTime createdAt, StockMovementType type, VariantRef variant, int quantityDelta,
            int balanceBefore, int balanceAfter, ReferenceRef reference, String reasonCode, String reasonText,
            UserRef actor) {
    }

    // ---- Consultas auxiliares ----

    public record StockVariantResponse(
            UUID id, UUID productId, String productName, String size, String sku, int stock, boolean active,
            boolean productActive) {
    }

    public record SaleOutflowResponse(
            String orderId, String orderLabel, LocalDateTime movedAt, String status, UserRef customer,
            long lines, long units, boolean stockReleased) {
    }

    public record ReconciliationDivergence(VariantRef variant, int currentStock, long ledgerBalance, long difference) {
    }

    public record ReconciliationResponse(long checkedVariants, List<ReconciliationDivergence> divergences) {
    }
}
