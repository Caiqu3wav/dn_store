package com.dnstore.backend.service;

import com.dnstore.backend.model.Cart;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.OrderItem;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.CartRepository;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.service.strategy.DeliveryStrategy.DeliveryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 📦 OrderService
 * 
 * Orquestra o fechamento do pedido.
 * Une os produtos do carrinho com o cálculo do frete e persiste no banco.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final DeliveryService deliveryService;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;

    @Transactional
    public Order checkout(User user, String zipCode, String shippingType) {
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("O carrinho está vazio."));

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("O carrinho está vazio.");
        }

        // 1. Calcular Frete
        DeliveryResult shipping = deliveryService.calculateShipping(
                zipCode,
                cart.getTotalWeight(),
                shippingType
        );

        // 2. Criar Entidade Pedido
        Order order = new Order();
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus("CONFIRMED");
        order.setUser(user);

        // Mapear CartItems para OrderItems (Entidades JPA)
        List<OrderItem> orderItems = cart.getItems().stream()
                .map(cartItem -> new OrderItem(order, cartItem.getProductVariant(), cartItem.getQuantity(), cartItem.getSubtotal()))
                .collect(Collectors.toList());

        order.setItems(orderItems);
        order.setTotal(cart.getTotalPrice().add(shipping.cost()));

        // 3. Persistir
        Order savedOrder = orderRepository.save(order);

        // 4. Esvaziar carrinho
        cart.clear();
        cartRepository.save(cart);

        return savedOrder;
    }

    public java.util.Optional<Order> findById(UUID id) {
        return orderRepository.findById(id);
    }
}
