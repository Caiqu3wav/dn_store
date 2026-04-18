package com.dnstore.backend.controller;

import com.dnstore.backend.model.Cart;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.CartRepository;
import com.dnstore.backend.repository.ProductVariantRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
        Cart cart = cartRepository.findByUser(user)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setUser(user);
                    return cartRepository.save(newCart);
                });

        return productVariantRepository.findById(request.getProductVariantId())
                .map(productVariant -> {
                    cart.addItem(productVariant, request.getQuantity());
                    cartRepository.save(cart);
                    return ResponseEntity.ok(cart);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/items/{productVariantId}")
    public ResponseEntity<?> updateItem(@AuthenticationPrincipal User user, @PathVariable UUID productVariantId, @RequestBody UpdateQuantityRequest request) {
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
    @Data
    public static class CartItemRequest {
        private UUID productVariantId;
        private int quantity;
    }

    @Data
    public static class UpdateQuantityRequest {
        private int quantity;
    }
}
