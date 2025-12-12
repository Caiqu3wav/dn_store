package com.dnstore.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    private int quantity;
    private BigDecimal priceAtPurchase;
    
    // Construtor auxiliar para facilitar criação
    public OrderItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
        this.priceAtPurchase = product.getPrice();
    }
    
    public BigDecimal getSubtotal() {
        if (priceAtPurchase == null) return BigDecimal.ZERO;
        return priceAtPurchase.multiply(new BigDecimal(quantity));
    }
    
    public Double getTotalWeight() {
        if (product instanceof PhysicalProduct) {
             return ((PhysicalProduct) product).getWeight() * quantity;
        }
        return 0.0;
    }
}
