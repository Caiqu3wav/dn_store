package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.OrderItem;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.StockMovement.Type;
import com.dnstore.backend.repository.StockMovementRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockLedgerTest {

    @Mock private StockMovementRepository repository;
    @Mock private EntityManager entityManager;

    private StockLedger ledger;
    private final List<StockMovement> saved = new ArrayList<>();
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        ledger = new StockLedger(repository, entityManager);
        lenient().when(repository.save(any(StockMovement.class))).thenAnswer(inv -> {
            StockMovement m = inv.getArgument(0);
            saved.add(m);
            return m;
        });
        lenient().when(entityManager.getLockMode(any())).thenReturn(LockModeType.PESSIMISTIC_WRITE);

        PhysicalProduct product = new PhysicalProduct();
        product.setName("Camiseta");
        variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setSize("M");
        variant.setStock(10);
    }

    @Test
    void post_changesBalanceAndRecordsBeforeAndAfter() {
        StockMovement m = ledger.post(variant, -3, Type.ADJUSTMENT,
                StockLedger.Context.manual("Avaria", null, "admin@x", null));

        assertThat(variant.getStock()).isEqualTo(7);
        assertThat(m.getStockBefore()).isEqualTo(10);
        assertThat(m.getDelta()).isEqualTo(-3);
        assertThat(m.getStockAfter()).isEqualTo(7);
        assertThat(m.getPerformedBy()).isEqualTo("admin@x");
        assertThat(saved).containsExactly(m);
    }

    @Test
    void post_neverLeavesNegativeStockAndWritesNothing() {
        assertThatThrownBy(() -> ledger.post(variant, -11, Type.ADJUSTMENT,
                StockLedger.Context.manual("x", null, "admin@x", null)))
                .isInstanceOf(ConflictException.class);

        assertThat(variant.getStock()).isEqualTo(10);
        verify(repository, never()).save(any());
    }

    @Test
    void post_refusesVariantsThatAreNotPessimisticallyLocked() {
        when(entityManager.getLockMode(any())).thenReturn(LockModeType.NONE);

        assertThatThrownBy(() -> ledger.post(variant, 1, Type.ENTRY, StockLedger.Context.manual("x", null, null, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(variant.getStock()).isEqualTo(10);
        verify(repository, never()).save(any());
    }

    @Test
    void post_refusesDetachedVariants() {
        when(entityManager.getLockMode(any())).thenThrow(new IllegalArgumentException("not managed"));

        assertThatThrownBy(() -> ledger.post(variant, 1, Type.ENTRY, StockLedger.Context.manual("x", null, null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void post_propagatesLedgerFailureSoTheWholeTransactionRollsBack() {
        doAnswer(inv -> {
            throw new IllegalStateException("db down");
        }).when(repository).save(any(StockMovement.class));

        assertThatThrownBy(() -> ledger.post(variant, 1, Type.ENTRY, StockLedger.Context.manual("x", null, null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessage("db down");
    }

    @Test
    void recordSale_usesCurrentBalancePlusQuantityAsBeforeAndMergesRepeatedVariants() {
        variant.setStock(6); // o checkout já baixou 4
        Order order = new Order();
        order.setItems(List.of(item(order, 3), item(order, 1)));

        ledger.recordSale(order);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getType()).isEqualTo(Type.SALE);
        assertThat(saved.get(0).getStockBefore()).isEqualTo(10);
        assertThat(saved.get(0).getDelta()).isEqualTo(-4);
        assertThat(saved.get(0).getStockAfter()).isEqualTo(6);
        assertThat(variant.getStock()).isEqualTo(6);
    }

    @Test
    void recordInitial_startsFromZeroAndSkipsEmptyVariants() {
        StockMovement m = ledger.recordInitial(variant, "admin@x");
        assertThat(m.getType()).isEqualTo(Type.INITIAL);
        assertThat(m.getStockBefore()).isZero();
        assertThat(m.getDelta()).isEqualTo(10);
        assertThat(m.getStockAfter()).isEqualTo(10);

        variant.setStock(0);
        assertThat(ledger.recordInitial(variant, "admin@x")).isNull();
        assertThat(saved).hasSize(1);
    }

    @Test
    void recordInitial_requiresAPersistedVariant() {
        variant.setId(null);
        assertThatThrownBy(() -> ledger.recordInitial(variant, "admin@x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordOpening_isAMarkerWithZeroDelta() {
        StockMovement m = ledger.recordOpening(variant, "admin@x");

        assertThat(m.getType()).isEqualTo(Type.OPENING);
        assertThat(m.getDelta()).isZero();
        assertThat(m.getStockBefore()).isEqualTo(10);
        assertThat(m.getStockAfter()).isEqualTo(10);
        assertThat(variant.getStock()).isEqualTo(10);
    }

    @Test
    void postSetTo_rereadsTheLockedRowAndRecordsTheDifference() {
        doAnswer(inv -> {
            variant.setStock(8); // uma venda concorrente confirmada antes do lock
            return null;
        }).doNothing().when(entityManager).refresh(variant, LockModeType.PESSIMISTIC_WRITE);

        StockMovement m = ledger.postSetTo(variant, 12, StockLedger.Context.manual("Edição", null, "a@x", null));

        assertThat(m.getStockBefore()).isEqualTo(8);
        assertThat(m.getDelta()).isEqualTo(4);
        assertThat(variant.getStock()).isEqualTo(12);
        assertThat(ledger.postSetTo(variant, 12, StockLedger.Context.manual("Edição", null, "a@x", null))).isNull();
    }

    private OrderItem item(Order order, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductVariant(variant);
        item.setQuantity(quantity);
        return item;
    }
}
