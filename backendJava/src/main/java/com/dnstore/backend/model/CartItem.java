package com.dnstore.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 🛒 CartItem (Item do Carrinho)
 *
 * JPA Entity representing an item in a shopping cart.
 * Associates a product variant with quantity and belongs to a cart.
 */
@Entity
@Table(name = "cart_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(nullable = false)
    private int quantity;

    /**
     * Encapsula a lógica de cálculo do subtotal.
     */
    public BigDecimal getSubtotal() {
        if (productVariant == null || productVariant.getProduct() == null) return BigDecimal.ZERO;
        return productVariant.getProduct().getPrice().multiply(new BigDecimal(quantity));
    }

    public double getTotalWeight() {
        if (productVariant == null || productVariant.getProduct() == null) return 0.0;
        return productVariant.getProduct().getShippingWeight() * quantity;
    }
}
