package com.dnstore.backend.model;

import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 🛒 Cart (Carrinho de Compras)
 * 
 * Agregado que gerencia uma lista de items.
 * Responsável por garantir a consistência dos dados do pedido antes da finalização.
 */
@Data
public class Cart {
    private List<CartItem> items = new ArrayList<>();

    public void addItem(Product product, int quantity) {
        Optional<CartItem> existing = items.stream()
            .filter(i -> i.getProduct().getId().equals(product.getId()))
            .findFirst();

        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + quantity);
        } else {
            items.add(new CartItem(product, quantity));
        }
    }

    public void removeItem(Long productId) {
        items.removeIf(item -> item.getProduct().getId().equals(productId));
    }

    public void updateItemQuantity(Long productId, int quantity) {
        if (quantity <= 0) {
            removeItem(productId);
            return;
        }
        
        items.stream()
            .filter(item -> item.getProduct().getId().equals(productId))
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
