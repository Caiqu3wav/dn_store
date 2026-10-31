package com.dnstore.backend.controller;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.Cart;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.CartRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    private CartController controller;
    private User user;
    private Cart cart;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        controller = new CartController(cartRepository, productVariantRepository);
        user = new User();
        user.setId(UUID.randomUUID());
        cart = new Cart();
        cart.setUser(user);
        variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setSize("BL P");
        variant.setStock(4);
        PhysicalProduct product = new PhysicalProduct();
        product.setId(UUID.randomUUID());
        product.setName("Camiseta DN");
        product.setActive(true);
        variant.setProduct(product);
        when(cartRepository.findByUserForUpdate(user)).thenReturn(Optional.of(cart));
        lenient().when(productVariantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));
        lenient().when(productVariantRepository.findByIdForUpdate(variant.getId())).thenReturn(Optional.of(variant));
        lenient().when(productVariantRepository.save(any(ProductVariant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void addItem_withAvailableVariant_shouldAddTheSelectedSize() {
        CartController.CartItemRequest request = request(variant.getId(), null, "BL P", 2);

        controller.addItem(user, request);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getProductVariant()).isSameAs(variant);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void addItem_whenRequestedQuantityExceedsStock_shouldReject() {
        CartController.CartItemRequest request = request(variant.getId(), null, "BL P", 5);

        assertThatThrownBy(() -> controller.addItem(user, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Estoque insuficiente");

        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void synchronizeCart_withDuplicateLines_shouldValidateTheirCombinedQuantity() {
        CartController.CartItemRequest first = request(variant.getId(), null, "BL P", 3);
        CartController.CartItemRequest second = request(variant.getId(), null, "BL P", 2);
        when(productVariantRepository.findAllByIdInForUpdate(anyCollection())).thenReturn(List.of(variant));

        assertThatThrownBy(() -> controller.synchronizeCart(user, List.of(first, second)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Estoque insuficiente");

        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void addItem_withDifferentSizes_shouldKeepSeparateCartLines() {
        ProductVariant secondVariant = new ProductVariant();
        secondVariant.setId(UUID.randomUUID());
        secondVariant.setProduct(variant.getProduct());
        secondVariant.setSize("BL M");
        secondVariant.setStock(2);
        when(productVariantRepository.findById(secondVariant.getId())).thenReturn(Optional.of(secondVariant));
        when(productVariantRepository.findByIdForUpdate(secondVariant.getId())).thenReturn(Optional.of(secondVariant));

        controller.addItem(user, request(variant.getId(), null, "BL P", 1));
        controller.addItem(user, request(secondVariant.getId(), null, "BL M", 1));

        assertThat(cart.getItems()).hasSize(2);
        assertThat(cart.getItems()).extracting(item -> item.getProductVariant().getSize())
                .containsExactly("BL P", "BL M");
    }

    @Test
    void addItem_withUnknownSize_shouldReject() {
        CartController.CartItemRequest request = request(null, variant.getProduct().getId(), "XXL", 1);
        when(productVariantRepository.findByProductIdAndSize(variant.getProduct().getId(), "XXL"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.addItem(user, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Variação");
    }

    @Test
    void addItem_withoutVariantOrResolvableSize_shouldReject() {
        CartController.CartItemRequest request = request(null, variant.getProduct().getId(), null, 1);
        ProductVariant secondVariant = new ProductVariant();
        secondVariant.setId(UUID.randomUUID());
        secondVariant.setProduct(variant.getProduct());
        secondVariant.setSize("M");
        secondVariant.setStock(2);
        when(productVariantRepository.findByProductId(variant.getProduct().getId()))
                .thenReturn(List.of(variant, secondVariant));

        assertThatThrownBy(() -> controller.addItem(user, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Variação");
    }

    private CartController.CartItemRequest request(UUID variantId, UUID productId, String size, int quantity) {
        CartController.CartItemRequest request = new CartController.CartItemRequest();
        request.setProductVariantId(variantId);
        request.setProductId(productId);
        request.setSize(size);
        request.setQuantity(quantity);
        return request;
    }
}
