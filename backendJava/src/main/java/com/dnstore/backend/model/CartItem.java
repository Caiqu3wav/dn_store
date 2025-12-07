package com.dnstore.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * 🛒 CartItem (Item do Carrinho)
 * 
 * Representa a associação entre um Produto e a Quantidade desejada.
 * Calcula seu próprio subtotal.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartItem {
    private Product product;
    private int quantity;

    /**
     * Encapsula a lógica de cálculo do subtotal.
     */
    public BigDecimal getSubtotal() {
        if (product == null) return BigDecimal.ZERO;
        return product.getPrice().multiply(new BigDecimal(quantity));
    }
    
    public double getTotalWeight() {
        if (product == null) return 0.0;
        return product.getShippingWeight() * quantity;
    }
}
