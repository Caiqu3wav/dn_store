package com.dnstore.backend.model;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Getter;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name="UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    // Construtor auxiliar para facilitar criação
    public OrderItem(Order order, ProductVariant productVariant, int quantity, BigDecimal price) {
        this.order = order;
        this.productVariant = productVariant;
        this.quantity = quantity;
        this.price = price;
    }

    public Double getTotalWeight() {
        if (productVariant != null && productVariant.getProduct() instanceof PhysicalProduct) {
             return ((PhysicalProduct) productVariant.getProduct()).getWeight() * quantity;
        }
        return 0.0;
    }

    public BigDecimal getTotalPrice() {
        if (price != null) {
            return price.multiply(BigDecimal.valueOf(quantity));
        }
        return BigDecimal.ZERO;
    }
}