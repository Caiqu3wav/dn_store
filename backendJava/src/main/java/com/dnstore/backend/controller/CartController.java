package com.dnstore.backend.controller;

import com.dnstore.backend.model.Cart;
import com.dnstore.backend.model.Product;
import com.dnstore.backend.service.ProductService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 🛒 CartController
 * 
 * API RESTful completa para manipulação do Carrinho.
 * Suporta: Ver, Adicionar, Atualizar Qtd, Remover Item, Limpar.
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final Cart cart;
    private final ProductService productService;

    @GetMapping
    public ResponseEntity<Cart> getCart() {
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/items")
    public ResponseEntity<?> addItem(@RequestBody CartItemRequest request) {
        return productService.findById(request.getProductId())
                .map(product -> {
                    cart.addItem(product, request.getQuantity());
                    return ResponseEntity.ok(cart);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<?> updateItem(@PathVariable Long productId, @RequestBody UpdateQuantityRequest request) {
        cart.updateItemQuantity(productId, request.getQuantity());
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<?> removeItem(@PathVariable Long productId) {
        cart.removeItem(productId);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping
    public ResponseEntity<?> clearCart() {
        cart.clear();
        return ResponseEntity.noContent().build();
    }

    // DTOs
    @Data
    public static class CartItemRequest {
        private Long productId;
        private int quantity;
    }

    @Data
    public static class UpdateQuantityRequest {
        private int quantity;
    }
}
