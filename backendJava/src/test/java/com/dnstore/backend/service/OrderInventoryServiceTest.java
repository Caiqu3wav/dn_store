package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.model.Coupon;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.OrderItem;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OrderInventoryServiceTest {

    @Mock
    private StockLedgerService stockLedger;

    @Mock
    private CouponService couponService;

    @Mock
    private ProductVariantRepository productVariantRepository;

    private OrderInventoryService inventoryService;
    private Order order;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        inventoryService = new OrderInventoryService(productVariantRepository, couponService, stockLedger);
        variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setStock(2);
        variant.setSize("M");
        PhysicalProduct product = new PhysicalProduct();
        product.setName("Camiseta DN");
        variant.setProduct(product);

        OrderItem item = new OrderItem();
        item.setProductVariant(variant);
        item.setQuantity(3);
        order = new Order();
        order.setId(UUID.randomUUID());
        order.setItems(List.of(item));
        when(productVariantRepository.findAllByIdInForUpdate(anyCollection()))
                .thenReturn(List.of(variant));
        lenient().when(productVariantRepository.save(any(ProductVariant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void releaseReservedStock_shouldRestoreQuantityOnlyOnce() {
        inventoryService.releaseReservedStock(order);
        inventoryService.releaseReservedStock(order);

        assertThat(variant.getStock()).isEqualTo(5);
        assertThat(order.isStockReleased()).isTrue();
        verify(productVariantRepository, times(1)).save(variant);
        // Livro: uma única devolução, com o saldo anterior (2) e a referência ao pedido.
        verify(stockLedger, times(1)).recordApplied(eq(variant), eq(2), argThat(spec ->
                spec.type() == com.dnstore.backend.model.enums.StockMovementType.DEVOLUCAO_PEDIDO
                        && spec.referenceType() == com.dnstore.backend.model.enums.StockReferenceType.ORDER
                        && order.getId().toString().equals(spec.referenceId())));
    }

    @Test
    void reserveReleasedStock_shouldSubtractQuantityAndClearReleasedFlag() {
        order.setStockReleased(true);
        variant.setStock(5);

        inventoryService.reserveReleasedStock(order);

        assertThat(variant.getStock()).isEqualTo(2);
        assertThat(order.isStockReleased()).isFalse();
        verify(stockLedger).recordApplied(eq(variant), eq(5), argThat(spec ->
                spec.type() == com.dnstore.backend.model.enums.StockMovementType.SAIDA_VENDA));
    }

    @Test
    void reserveReleasedStock_whenInsufficient_shouldNotChangeStock() {
        order.setStockReleased(true);
        variant.setStock(2);

        assertThatThrownBy(() -> inventoryService.reserveReleasedStock(order))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Estoque insuficiente");

        assertThat(variant.getStock()).isEqualTo(2);
        verify(productVariantRepository, never()).save(any(ProductVariant.class));
        verifyNoInteractions(stockLedger);
    }

    @Test
    void releaseReservedStock_shouldReturnCouponUsageTogetherWithStockAndOnlyOnce() {
        Coupon coupon = new Coupon();
        coupon.setId(UUID.randomUUID());
        order.setCoupon(coupon);

        inventoryService.releaseReservedStock(order);
        inventoryService.releaseReservedStock(order); // cancelamento + expiração repetidos

        verify(couponService, times(1)).releaseUsage(coupon);
    }

    @Test
    void releaseReservedStock_withoutCoupon_shouldNotTouchCoupons() {
        inventoryService.releaseReservedStock(order);

        verifyNoInteractions(couponService);
    }

    @Test
    void reserveReleasedStock_shouldReapplyCouponUsage() {
        Coupon coupon = new Coupon();
        coupon.setId(UUID.randomUUID());
        order.setCoupon(coupon);
        order.setStockReleased(true);
        variant.setStock(5);

        inventoryService.reserveReleasedStock(order);

        verify(couponService).reapplyUsage(coupon);
    }

    @Test
    void reserveReleasedStock_whenStockIsInsufficient_shouldNotReapplyCouponUsage() {
        Coupon coupon = new Coupon();
        coupon.setId(UUID.randomUUID());
        order.setCoupon(coupon);
        order.setStockReleased(true);

        assertThatThrownBy(() -> inventoryService.reserveReleasedStock(order))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(couponService);
    }
}
