package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
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
    private ProductVariantRepository productVariantRepository;

    private OrderInventoryService inventoryService;
    private Order order;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        inventoryService = new OrderInventoryService(productVariantRepository);
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
    }

    @Test
    void reserveReleasedStock_shouldSubtractQuantityAndClearReleasedFlag() {
        order.setStockReleased(true);
        variant.setStock(5);

        inventoryService.reserveReleasedStock(order);

        assertThat(variant.getStock()).isEqualTo(2);
        assertThat(order.isStockReleased()).isFalse();
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
    }
}
