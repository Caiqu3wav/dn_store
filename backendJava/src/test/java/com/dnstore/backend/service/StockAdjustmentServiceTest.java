package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dnstore.backend.dto.stock.StockDtos.StockAdjustmentRequest;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.model.enums.StockAdjustmentReason;
import com.dnstore.backend.model.enums.StockMovementType;
import com.dnstore.backend.repository.StockMovementRepository;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class StockAdjustmentServiceTest {

    @Mock
    private StockMovementRepository movementRepository;
    @Mock
    private StockLedgerService ledger;
    @Mock
    private PlatformTransactionManager transactionManager;

    private StockAdjustmentService service;
    private User admin;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        service = new StockAdjustmentService(movementRepository, ledger, transactionManager);
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));
        admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setRole(Role.ADMIN);
        variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setStock(10);
    }

    @Test
    void adjust_whenActorIsNotAdmin_shouldBeDenied() {
        User customer = new User();
        customer.setRole(Role.USER);

        assertThatThrownBy(() -> service.adjust(request(-1, 10, StockAdjustmentReason.PERDA, "Contagem física"), customer))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adjust_shouldRejectZeroDeltaAndWrongSignForTheCategory() {
        assertThatThrownBy(() -> service.adjust(request(0, 10, StockAdjustmentReason.CORRECAO_INVENTARIO,
                "Contagem física"), admin)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.adjust(request(2, 10, StockAdjustmentReason.PERDA, "Contagem física"), admin))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reduzir");
        assertThatThrownBy(() -> service.adjust(request(2, 10, StockAdjustmentReason.AVARIA, "Contagem física"), admin))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.adjust(request(-2, 10, StockAdjustmentReason.DEVOLUCAO_CLIENTE,
                "Contagem física"), admin)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("aumentar");
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void adjust_shouldRequireAMeaningfulReason() {
        assertThatThrownBy(() -> service.adjust(request(-1, 10, StockAdjustmentReason.PERDA, "curto"), admin))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivo");
    }

    @Test
    void adjust_whenBalanceChangedSinceTheScreenWasLoaded_shouldConflictWithoutApplying() {
        when(movementRepository.findByIdempotencyKey("k")).thenReturn(java.util.Optional.empty());
        when(ledger.lockVariants(any())).thenReturn(Map.of(variant.getId(), variant));

        assertThatThrownBy(() -> service.adjust(request(-1, 12, StockAdjustmentReason.PERDA, "Contagem física"), admin))
                .isInstanceOf(ConflictException.class).hasMessageContaining("O estoque mudou");

        verify(ledger, never()).applyDelta(any(), anyInt(), any());
        verify(transactionManager).rollback(any());
    }

    @Test
    void adjust_shouldApplyTheDeltaThroughTheLedgerWithReasonAndKey() {
        when(movementRepository.findByIdempotencyKey("k")).thenReturn(java.util.Optional.empty());
        when(ledger.lockVariants(any())).thenReturn(Map.of(variant.getId(), variant));
        StockMovement movement = mock(StockMovement.class);
        when(movement.getId()).thenReturn(UUID.randomUUID());
        when(ledger.applyDelta(eq(variant), eq(-3), any())).thenReturn(movement);

        StockAdjustmentService.AdjustmentResult result =
                service.adjust(request(-3, 10, StockAdjustmentReason.AVARIA, "Caixa molhada no depósito"), admin);

        assertThat(result.replayed()).isFalse();
        verify(ledger).applyDelta(eq(variant), eq(-3), org.mockito.ArgumentMatchers.argThat(spec ->
                spec.type() == StockMovementType.AJUSTE
                        && "AVARIA".equals(spec.reasonCode())
                        && "Caixa molhada no depósito".equals(spec.reasonText())
                        && "k".equals(spec.idempotencyKey())
                        && spec.actor() == admin));
        verify(transactionManager).commit(any());
    }

    @Test
    void adjust_whenKeyWasAlreadyUsedForTheSameAdjustment_shouldReplay() {
        StockMovement stored = mock(StockMovement.class);
        when(stored.getType()).thenReturn(StockMovementType.AJUSTE);
        when(stored.getProductVariant()).thenReturn(variant);
        when(stored.getQuantityDelta()).thenReturn(-3);
        when(stored.getId()).thenReturn(UUID.randomUUID());
        when(movementRepository.findByIdempotencyKey("k")).thenReturn(java.util.Optional.of(stored));

        assertThat(service.adjust(request(-3, 10, StockAdjustmentReason.PERDA, "Contagem física"), admin).replayed())
                .isTrue();
        verify(ledger, never()).applyDelta(any(), anyInt(), any());
    }

    @Test
    void adjust_whenKeyWasUsedForAnotherAdjustment_shouldConflict() {
        StockMovement stored = mock(StockMovement.class);
        when(stored.getType()).thenReturn(StockMovementType.AJUSTE);
        when(stored.getProductVariant()).thenReturn(variant);
        when(stored.getQuantityDelta()).thenReturn(-9);
        when(movementRepository.findByIdempotencyKey("k")).thenReturn(java.util.Optional.of(stored));

        assertThatThrownBy(() -> service.adjust(request(-3, 10, StockAdjustmentReason.PERDA, "Contagem física"), admin))
                .isInstanceOf(ConflictException.class);
    }

    private StockAdjustmentRequest request(int delta, int expected, StockAdjustmentReason reason, String text) {
        return new StockAdjustmentRequest(variant.getId(), delta, expected, reason, text, "k");
    }
}
