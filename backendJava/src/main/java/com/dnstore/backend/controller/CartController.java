package com.dnstore.backend.controller;

import com.dnstore.backend.model.Cart;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.User;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.repository.CartRepository;
import com.dnstore.backend.repository.ProductVariantRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
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

        Cart visibleCart = new Cart();
        visibleCart.setId(cart.getId());
        visibleCart.setUser(cart.getUser());
        visibleCart.setCreatedAt(cart.getCreatedAt());
        visibleCart.setItems(cart.getItems().stream()
                .filter(item -> item.getProductVariant() != null
                        && item.getProductVariant().isActive()
                        && item.getProductVariant().getProduct() != null
                        && item.getProductVariant().getProduct().isActive())
                .toList());
        return ResponseEntity.ok(visibleCart);
    }

    @PostMapping("/items")
    @Transactional
    public ResponseEntity<?> addItem(@AuthenticationPrincipal User user, @RequestBody CartItemRequest request) {
        if (request == null || request.getQuantity() <= 0 || request.getQuantity() > 99) {
            throw new IllegalArgumentException("Quantidade inválida para o carrinho.");
        }

        Cart cart = cartRepository.findByUserForUpdate(user)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setUser(user);
                    return cartRepository.save(newCart);
                });

        ProductVariant resolvedVariant = resolveVariant(request);
        ProductVariant productVariant = productVariantRepository.findByIdForUpdate(resolvedVariant.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Variação não encontrada."));
        if (!productVariant.isActive() || !productVariant.getProduct().isActive()) {
            throw new ResourceNotFoundException("Produto não encontrado ou inativo.");
        }
        int existingQuantity = cart.getItems().stream()
                .filter(item -> item.getProductVariant().getId().equals(productVariant.getId()))
                .mapToInt(com.dnstore.backend.model.CartItem::getQuantity)
                .sum();
        if (productVariant.getStock() < existingQuantity + request.getQuantity()) {
            throw new ConflictException("Estoque insuficiente para o tamanho selecionado.");
        }
        cart.addItem(productVariant, request.getQuantity());
        cartRepository.save(cart);
        log.info("cart.item_added userId={} variantId={} qty={}", user.getId(), productVariant.getId(), request.getQuantity());
        return ResponseEntity.ok(cart);
    }

    @PutMapping("/sync")
    @Transactional
    public ResponseEntity<?> synchronizeCart(
            @AuthenticationPrincipal User user,
            @RequestBody List<CartItemRequest> requests) {
        if (requests == null) {
            throw new IllegalArgumentException("Lista de itens é obrigatória.");
        }

        java.util.Map<UUID, CartLine> resolvedLines = new java.util.LinkedHashMap<>();
        for (CartItemRequest request : requests) {
            if (request == null) {
                throw new IllegalArgumentException("Item de carrinho inválido.");
            }
            if (request.getQuantity() < 1 || request.getQuantity() > 99) {
                throw new IllegalArgumentException("Quantidade deve estar entre 1 e 99.");
            }

            ProductVariant variant = resolveVariant(request);
            if (!variant.getProduct().isActive()) {
                throw new ResourceNotFoundException("Produto não encontrado ou inativo.");
            }
            CartLine current = resolvedLines.get(variant.getId());
            int totalQuantity = (current == null ? 0 : current.quantity()) + request.getQuantity();
            resolvedLines.put(variant.getId(), new CartLine(variant, totalQuantity));
        }

        List<UUID> variantIds = resolvedLines.keySet().stream()
                .sorted(java.util.Comparator.comparing(UUID::toString))
                .toList();
        Cart cart = cartRepository.findByUserForUpdate(user).orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.setUser(user);
            return cartRepository.save(newCart);
        });
        java.util.Map<UUID, ProductVariant> lockedVariants = new java.util.HashMap<>();
        if (!variantIds.isEmpty()) {
            for (ProductVariant locked : productVariantRepository.findAllByIdInForUpdate(variantIds)) {
                lockedVariants.put(locked.getId(), locked);
            }
        }
        for (CartLine line : resolvedLines.values()) {
            ProductVariant locked = lockedVariants.get(line.variant().getId());
            if (locked == null || !locked.isActive() || !locked.getProduct().isActive()) {
                throw new ResourceNotFoundException("Produto não encontrado ou inativo.");
            }
            if (locked.getStock() < line.quantity()) {
                throw new ConflictException("Estoque insuficiente para o tamanho selecionado.");
            }
        }

        cart.clear();
        resolvedLines.values().forEach(line -> cart.addItem(
                lockedVariants.get(line.variant().getId()), line.quantity()));
        cartRepository.save(cart);
        return ResponseEntity.noContent().build();
    }

    private ProductVariant resolveVariant(CartItemRequest request) {
        Optional<ProductVariant> variant;
        if (request.getProductVariantId() != null) {
            variant = productVariantRepository.findById(request.getProductVariantId());
        } else if (request.getProductId() != null && request.getSize() != null
                && !request.getSize().isBlank()) {
            variant = productVariantRepository.findByProductIdAndSize(request.getProductId(), request.getSize());
        } else if (request.getProductId() != null) {
            List<ProductVariant> activeVariants = productVariantRepository.findByProductId(request.getProductId())
                    .stream()
                    .filter(ProductVariant::isActive)
                    .toList();
            variant = activeVariants.size() == 1
                    ? Optional.of(activeVariants.get(0))
                    : Optional.empty();
        } else {
            throw new IllegalArgumentException("A variação do produto é obrigatória.");
        }
        ProductVariant resolved = variant.orElseThrow(() -> new ResourceNotFoundException("Variação não encontrada."));
        if (request.getProductId() != null && !request.getProductId().equals(resolved.getProduct().getId())) {
            throw new IllegalArgumentException("A variação não pertence ao produto informado.");
        }
        if (request.getSize() != null && !request.getSize().equals(resolved.getSize())) {
            throw new IllegalArgumentException("O tamanho não corresponde à variação informada.");
        }
        return resolved;
    }

    @PutMapping("/items/{productVariantId}")
    @Transactional
    public ResponseEntity<?> updateItem(@AuthenticationPrincipal User user, @PathVariable UUID productVariantId,
            @RequestBody UpdateQuantityRequest request) {
        Cart cart = cartRepository.findByUserForUpdate(user).orElse(null);
        if (cart == null) {
            throw new ResourceNotFoundException("Carrinho não encontrado.");
        }

        if (request.getQuantity() > 0) {
            if (request.getQuantity() > 99) {
                throw new IllegalArgumentException("Quantidade deve estar entre 1 e 99.");
            }
            ProductVariant variant = productVariantRepository.findByIdForUpdate(productVariantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Variação não encontrada."));
            if (!variant.isActive() || !variant.getProduct().isActive()) {
                throw new ResourceNotFoundException("Produto não encontrado ou inativo.");
            }
            if (variant.getStock() < request.getQuantity()) {
                throw new ConflictException("Estoque insuficiente para o tamanho selecionado.");
            }
        }
        cart.updateItemQuantity(productVariantId, request.getQuantity());
        cartRepository.save(cart);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/items/{productVariantId}")
    @Transactional
    public ResponseEntity<?> removeItem(@AuthenticationPrincipal User user, @PathVariable UUID productVariantId) {
        Cart cart = cartRepository.findByUserForUpdate(user).orElse(null);
        if (cart == null) {
            throw new ResourceNotFoundException("Carrinho não encontrado.");
        }

        cart.removeItem(productVariantId);
        cartRepository.save(cart);
        log.info("cart.item_removed userId={} variantId={}", user.getId(), productVariantId);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping
    public ResponseEntity<?> clearCart(@AuthenticationPrincipal User user) {
        Cart cart = cartRepository.findByUser(user).orElse(null);
        if (cart == null) {
            throw new ResourceNotFoundException("Carrinho não encontrado.");
        }

        cart.clear();
        cartRepository.save(cart);
        log.info("cart.cleared userId={}", user.getId());
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
