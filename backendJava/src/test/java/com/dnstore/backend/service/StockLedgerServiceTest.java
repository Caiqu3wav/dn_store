package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.enums.StockMovementType;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.dnstore.backend.repository.StockMovementRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StockLedgerServiceTest {

    @Mock
    private StockMovementRepository movementRepository;
    @Mock
    private ProductVariantRepository variantRepository;

    private EntityManager entityManager;
    private Query openingInsert;
    private StockLedgerService ledger;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        openingInsert = mock(Query.class, Mockito.RETURNS_SELF);
        Mockito.lenient().when(entityManager.createNativeQuery(any(String.class))).thenReturn(openingInsert);
        ledger = new StockLedgerService(movementRepository, variantRepository);
        ReflectionTestUtils.setField(ledger, "entityManager", entityManager);

        PhysicalProduct product = new PhysicalProduct();
        product.setName("Camiseta DN");
        variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setSize("M");
        variant.setStock(5);
        Mockito.lenient().when(movementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void applyDelta_whenResultWouldBeNegative_shouldFailWithoutChangingOrRecording() {
        assertThatThrownBy(() -> ledger.applyDelta(variant, -6,
                StockLedgerService.MovementSpec.of(StockMovementType.AJUSTE)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Estoque insuficiente");

        assertThat(variant.getStock()).isEqualTo(5);
        verifyNoInteractions(entityManager);
        verify(variantRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void applyDelta_whenIntOverflows_shouldBeRejectedAsBadRequest() {
        variant.setStock(Integer.MAX_VALUE - 1);

        assertThatThrownBy(() -> ledger.applyDelta(variant, 10,
                StockLedgerService.MovementSpec.of(StockMovementType.ENTRADA_NOTA)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(variant.getStock()).isEqualTo(Integer.MAX_VALUE - 1);
    }

    @Test
    void applyDelta_shouldWriteOpeningBalanceBeforeTheMovementWithBeforeAndAfter() {
        StockMovement movement = ledger.applyDelta(variant, 3, StockLedgerService.MovementSpec
                .of(StockMovementType.ENTRADA_NOTA).withReason("X", "motivo"));

        assertThat(variant.getStock()).isEqualTo(8);
        assertThat(movement.getBalanceBefore()).isEqualTo(5);
        assertThat(movement.getQuantityDelta()).isEqualTo(3);
        assertThat(movement.getBalanceAfter()).isEqualTo(8);
        InOrder order = inOrder(entityManager, openingInsert, movementRepository);
        order.verify(entityManager).createNativeQuery(any(String.class));
        order.verify(openingInsert).executeUpdate();
        order.verify(movementRepository).save(any(StockMovement.class));
    }

    @Test
    void recordApplied_whenVariantHadNoStock_shouldNotWriteOpeningBalance() {
        variant.setStock(4);

        StockMovement movement = ledger.recordApplied(variant, 0,
                StockLedgerService.MovementSpec.of(StockMovementType.EDICAO_CADASTRO));

        assertThat(movement.getBalanceBefore()).isZero();
        assertThat(movement.getBalanceAfter()).isEqualTo(4);
        verifyNoInteractions(entityManager);
    }

    @Test
    void recordApplied_whenStockDidNotChange_shouldRecordNothing() {
        assertThat(ledger.recordApplied(variant, 5, StockLedgerService.MovementSpec.of(StockMovementType.AJUSTE)))
                .isNull();
        verifyNoInteractions(entityManager);
        verify(movementRepository, never()).save(any());
    }

    @Test
    void recordApplied_shouldCarryReferenceReasonAndKey() {
        variant.setStock(4);
        UUID orderId = UUID.randomUUID();

        ledger.recordApplied(variant, 5, StockLedgerService.MovementSpec.of(
                StockMovementType.SAIDA_VENDA, com.dnstore.backend.model.enums.StockReferenceType.ORDER, orderId)
                .withIdempotencyKey("k-1"));

        ArgumentCaptor<StockMovement> saved = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(saved.capture());
        assertThat(saved.getValue().getReferenceId()).isEqualTo(orderId.toString());
        assertThat(saved.getValue().getQuantityDelta()).isEqualTo(-1);
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo("k-1");
    }

    @Test
    void recordApplied_whenVariantIsNotPersisted_shouldFail() {
        variant.setId(null);
        variant.setStock(1);

        assertThatThrownBy(() -> ledger.recordApplied(variant, 0,
                StockLedgerService.MovementSpec.of(StockMovementType.EDICAO_CADASTRO)))
                .isInstanceOf(IllegalStateException.class);
    }
}
