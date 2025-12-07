package com.dnstore.backend.service;

import com.dnstore.backend.model.Cart;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.service.strategy.DeliveryStrategy.DeliveryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 📦 OrderService
 * 
 * Orquestra o fechamento do pedido.
 * Une os produtos do carrinho com o cálculo do frete.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final DeliveryService deliveryService;
    private final Cart cart; // Carrinho injetado (escopo de aplicação para demo, ideal seria Session)
    private final AtomicLong idGenerator = new AtomicLong(1);

    public Order checkout(String zipCode, String shippingType) {
        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("O carrinho está vazio.");
        }

        // 1. Calcular Frete
        DeliveryResult shipping = deliveryService.calculateShipping(
            zipCode, 
            cart.getTotalWeight(), 
            shippingType
        );

        // 2. Criar Pedido
        Order order = Order.builder()
            .id(idGenerator.getAndIncrement())
            .createdAt(LocalDateTime.now())
            .items(new ArrayList<>(cart.getItems())) // Cópia dos itens
            .productsTotal(cart.getTotalPrice())
            .productsTotal(cart.getTotalPrice())
            .entregaCusto(shipping.cost())
            .entregaDias(shipping.deadLineDays())
            .entregaTipo(shipping.typeName())
            .granTotal(cart.getTotalPrice().add(shipping.cost()))
            .status("CONFIRMED")
            .build();

        // 3. Esvaziar carrinho
        cart.clear();

        return order;
    }
}
