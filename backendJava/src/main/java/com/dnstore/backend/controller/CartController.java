package com.dnstore.backend.controller;

import com.dnstore.backend.model.Cart;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.Product;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.CartRepository;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.dnstore.backend.repository.ProductRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 🛒 CartController
 *
 * API RESTful completa para manipulação do Carrinho do usuário autenticado.
 * Suporta: Ver, Adicionar, Atualizar Qtd, Remover Item, Limpar.
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartRepository cartRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;

    @GetMapping
    public ResponseEntity<Cart> getCart(@AuthenticationPrincipal User user) {
        Cart cart = cartRepository.findByUser(user)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setUser(user);
                    return cartRepository.save(newCart);
                });
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/items")
    public ResponseEntity<?> addItem(@AuthenticationPrincipal User user, @RequestBody CartItemRequest request) {
        if (request == null || request.getQuantity() <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Cart cart = cartRepository.findByUser(user)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setUser(user);
                    return cartRepository.save(newCart);
                });

        java.util.Optional<ProductVariant> variant = request.getProductVariantId() != null
                ? productVariantRepository.findById(request.getProductVariantId())
                : productRepository.findById(request.getProductId())
                        .map(product -> createDefaultVariant(product, request));

        return variant
                .map(productVariant -> {
                    cart.addItem(productVariant, request.getQuantity());
                    cartRepository.save(cart);
                    return ResponseEntity.ok(cart);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/sync")
    @Transactional
    public ResponseEntity<?> synchronizeCart(
            @AuthenticationPrincipal User user,
            @RequestBody List<CartItemRequest> requests) {
        if (requests == null)
            return ResponseEntity.badRequest().build();

        List<CartLine> resolvedLines = new ArrayList<>();
        for (CartItemRequest request : requests) {
            if (request.getQuantity() < 1 || request.getQuantity() > 99) {
                return ResponseEntity.badRequest().build();
            }

            Optional<ProductVariant> variant = request.getProductVariantId() != null
                    ? productVariantRepository.findById(request.getProductVariantId())
                    : productRepository.findById(request.getProductId())
                            .map(product -> productVariantRepository.findByProductId(product.getId()).stream()
                                    .filter(existing -> java.util.Objects.equals(
                                            existing.getSize(), request.getSize() == null || request.getSize().isBlank()
                                                    ? "Único"
                                                    : request.getSize()))
                                    .findFirst()
                                    .orElseGet(() -> createDefaultVariant(product, request)));

            if (variant.isEmpty() || !variant.get().getProduct().isActive()) {
                return ResponseEntity.notFound().build();
            }
            resolvedLines.add(new CartLine(variant.get(), request.getQuantity()));
        }

        Cart cart = cartRepository.findByUser(user).orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.setUser(user);
            return newCart;
        });
        cart.clear();
        resolvedLines.forEach(line -> cart.addItem(line.variant(), line.quantity()));
        cartRepository.save(cart);
        return ResponseEntity.noContent().build();
    }

    private ProductVariant createDefaultVariant(Product product, CartItemRequest request) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSize(request.getSize() == null || request.getSize().isBlank() ? "Único" : request.getSize());
        variant.setColor(product.getColor());
        variant.setStock(product instanceof com.dnstore.backend.model.PhysicalProduct physical
                ? physical.getStock()
                : 0);
        return productVariantRepository.save(variant);
    }

    @PutMapping("/items/{productVariantId}")
    public ResponseEntity<?> updateItem(@AuthenticationPrincipal User user, @PathVariable UUID productVariantId,
            @RequestBody UpdateQuantityRequest request) {
        Cart cart = cartRepository.findByUser(user).orElse(null);
        if (cart == null) {
            return ResponseEntity.notFound().build();
        }

        cart.updateItemQuantity(productVariantId, request.getQuantity());
        cartRepository.save(cart);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/items/{productVariantId}")
    public ResponseEntity<?> removeItem(@AuthenticationPrincipal User user, @PathVariable UUID productVariantId) {
        Cart cart = cartRepository.findByUser(user).orElse(null);
        if (cart == null) {
            return ResponseEntity.notFound().build();
        }

        cart.removeItem(productVariantId);
        cartRepository.save(cart);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping
    public ResponseEntity<?> clearCart(@AuthenticationPrincipal User user) {
        Cart cart = cartRepository.findByUser(user).orElse(null);
        if (cart == null) {
            return ResponseEntity.notFound().build();
        }

        cart.clear();
        cartRepository.save(cart);
        return ResponseEntity.noContent().build();
    }

    // DTOs
    private record CartLine(ProductVariant variant, int quantity) {
    }

    @Data
    public static class CartItemRequest {
        private UUID productVariantId;
        private UUID productId;
        private String size;
        private int quantity;
    }

    @Data
    public static class UpdateQuantityRequest {
        private int quantity;
    }
}
