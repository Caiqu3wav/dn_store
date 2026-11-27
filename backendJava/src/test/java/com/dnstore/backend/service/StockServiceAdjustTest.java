package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.StockConflictException;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.enums.StockAdjustmentCategory;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.dnstore.backend.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class StockServiceAdjustTest {

    @Mock private ProductVariantRepository variantRepository;
    @Mock private StockMovementRepository movementRepository;

    private final List<StockMovement> ledgerRows = new ArrayList<>();
    private StockService service;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        StockLedger ledger = TestStockLedger.create(ledgerRows);
        service = new StockService(variantRepository, movementRepository, ledger);
        PhysicalProduct product = new PhysicalProduct();
        product.setName("Camiseta");
        variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setSize("M");
        variant.setStock(10);
        lenient().when(variantRepository.findByIdForUpdate(variant.getId())).thenReturn(Optional.of(variant));
        lenient().when(variantRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(variant));
        lenient().when(movementRepository.findByIdempotencyKey(any())).thenAnswer(inv -> ledgerRows.stream()
                .filter(m -> inv.getArgument(0).equals(m.getIdempotencyKey())).findFirst());
    }

    private StockService.AdjustCommand cmd(Integer delta, Integer counted, Integer expected, String reason, String key) {
        return new StockService.AdjustCommand(variant.getId(), delta, counted, expected,
                StockAdjustmentCategory.LOSS, reason, key, "admin@x");
    }

    @Test
    void adjustWithoutReasonIsRejected() {
        assertThatThrownBy(() -> service.adjust(cmd(-1, null, null, "  ", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Motivo");
        assertThat(variant.getStock()).isEqualTo(10);
        assertThat(ledgerRows).isEmpty();
    }

    @Test
    void adjustNeedsExactlyOneOfDeltaAndCountedQuantity() {
        assertThatThrownBy(() -> service.adjust(cmd(null, null, null, "x", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.adjust(cmd(1, 11, null, "x", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.adjust(cmd(0, null, null, "x", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void countedQuantityBecomesTheDeltaComputedUnderLock() {
        var result = service.adjust(cmd(null, 7, 10, "Contagem de março", null));

        assertThat(variant.getStock()).isEqualTo(7);
        assertThat(result.movement().delta()).isEqualTo(-3);
        assertThat(result.movement().stockBefore()).isEqualTo(10);
        assertThat(result.movement().stockAfter()).isEqualTo(7);
        assertThat(result.movement().category()).isEqualTo("LOSS");
        assertThat(result.replayed()).isFalse();
    }

    @Test
    void staleExpectedBalanceIsRefusedWithTheCurrentBalance() {
        assertThatThrownBy(() -> service.adjust(cmd(-1, null, 12, "x", null)))
                .isInstanceOfSatisfying(StockConflictException.class, e -> {
                    assertThat(e.getDetails()).containsEntry("currentBalance", "10").containsEntry("expectedBalance", "12");
                });
        assertThat(variant.getStock()).isEqualTo(10);
        assertThat(ledgerRows).isEmpty();
    }

    @Test
    void negativeResultIsRefused() {
        assertThatThrownBy(() -> service.adjust(cmd(-11, null, null, "x", null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("estoque negativo");
        assertThat(variant.getStock()).isEqualTo(10);
    }

    @Test
    void sameIdempotencyKeyReturnsTheOriginalMovementWithoutAdjustingAgain() {
        var first = service.adjust(cmd(-2, null, null, "Avaria", "key-1"));
        var second = service.adjust(cmd(-2, null, null, "Avaria", "key-1"));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(variant.getStock()).isEqualTo(8);
        assertThat(ledgerRows).hasSize(1);
    }

    @Test
    void reusingAKeyForAnotherOperationIsAConflict() {
        service.adjust(cmd(-2, null, null, "Avaria", "key-1"));

        assertThatThrownBy(() -> service.adjust(cmd(-5, null, null, "Outra coisa", "key-1")))
                .isInstanceOf(ConflictException.class);
        assertThat(variant.getStock()).isEqualTo(8);
    }
}
