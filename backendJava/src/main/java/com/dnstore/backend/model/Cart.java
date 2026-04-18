package com.dnstore.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 🛒 Cart (Carrinho de Compras)
 *
 * JPA Entity that manages a user's shopping cart items.
 * Each user has one cart, and carts contain cart items.
 */
@Entity
@Table(name = "carts")
@Data
@NoArgsConstructor
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CartItem> items = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void addItem(ProductVariant productVariant, int quantity) {
        Optional<CartItem> existing = items.stream()
            .filter(i -> i.getProductVariant().getId().equals(productVariant.getId()))
            .findFirst();

        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + quantity);
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(this);
            newItem.setProductVariant(productVariant);
            newItem.setQuantity(quantity);
            items.add(newItem);
        }
    }

    public void removeItem(UUID productVariantId) {
        items.removeIf(item -> item.getProductVariant().getId().equals(productVariantId));
    }

    public void updateItemQuantity(UUID productVariantId, int quantity) {
        if (quantity <= 0) {
            removeItem(productVariantId);
            return;
        }

        items.stream()
            .filter(item -> item.getProductVariant().getId().equals(productVariantId))
            .findFirst()
            .ifPresent(item -> item.setQuantity(quantity));
    }

    public BigDecimal getTotalPrice() {
        return items.stream()
            .map(CartItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public double getTotalWeight() {
        return items.stream()
            .mapToDouble(CartItem::getTotalWeight)
            .sum();
    }

    public void clear() {
        this.items.clear();
    }
}
