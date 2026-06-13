package com.dnstore.backend.service;

import com.dnstore.backend.model.Address;
import com.dnstore.backend.model.Cart;
import com.dnstore.backend.model.Coupon;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.OrderItem;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.AddressRepository;
import com.dnstore.backend.repository.CartRepository;
import com.dnstore.backend.repository.CouponRepository;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.service.strategy.DeliveryStrategy.DeliveryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final AddressRepository addressRepository;
    private final CouponService couponService;
    private final CouponRepository couponRepository;

    @Transactional
    public Order checkout(User user, String zipCode, String shippingType, String couponCode, UUID addressId) {
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

        // 2. Resolver Endereço
        Address address;
        if (addressId != null) {
            address = addressRepository.findById(addressId)
                    .orElseThrow(() -> new IllegalArgumentException("Endereço não encontrado."));
        } else {
            List<Address> userAddresses = addressRepository.findByUser_Id(user.getId());
            if (!userAddresses.isEmpty()) {
                address = userAddresses.get(0);
            } else {
                address = new Address();
                address.setUser(user);
                address.setStreet("Rua Simulação de Compra");
                address.setNumber("10");
                address.setNeighborhood("Centro");
                address.setCity("Cidade DN Store");
                address.setState("SP");
                address.setZipCode(zipCode != null ? zipCode : "01001-000");
                address = addressRepository.save(address);
            }
        }

        // 3. Aplicar Cupom se existir
        BigDecimal discount = BigDecimal.ZERO;
        Coupon appliedCoupon = null;

        if (couponCode != null && !couponCode.trim().isEmpty()) {
            Coupon coupon = couponService.validateCoupon(couponCode)
                    .orElseThrow(() -> new IllegalArgumentException("Cupom inválido ou expirado."));

            BigDecimal cartTotal = cart.getTotalPrice();
            if (coupon.getMinCartValue() != null && cartTotal.compareTo(coupon.getMinCartValue()) < 0) {
                throw new IllegalArgumentException("O valor mínimo do carrinho para usar este cupom é R$ " + coupon.getMinCartValue());
            }

            if (coupon.getMaxUsage() != null && coupon.getCurrentUsage() != null && coupon.getCurrentUsage() >= coupon.getMaxUsage()) {
                throw new IllegalArgumentException("Este cupom já atingiu o limite máximo de uso.");
            }

            if (coupon.getDiscountPercentage() != null) {
                discount = cartTotal.multiply(coupon.getDiscountPercentage())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            } else if (coupon.getDiscountValue() != null) {
                discount = coupon.getDiscountValue();
            }

            if (discount.compareTo(cartTotal) > 0) {
                discount = cartTotal;
            }

            coupon.setCurrentUsage((coupon.getCurrentUsage() != null ? coupon.getCurrentUsage() : 0) + 1);
            couponRepository.save(coupon);
            appliedCoupon = coupon;
        }

        // 4. Criar Entidade Pedido
        Order order = new Order();
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus("CONFIRMADO");
        order.setUser(user);
        order.setAddress(address);
        order.setCoupon(appliedCoupon);
        order.setDiscountAmount(discount);

        // Mapear CartItems para OrderItems (Entidades JPA)
        List<OrderItem> orderItems = cart.getItems().stream()
                .map(cartItem -> new OrderItem(order, cartItem.getProductVariant(), cartItem.getQuantity(), cartItem.getSubtotal()))
                .collect(Collectors.toList());

        order.setItems(orderItems);

        // Total = total produtos + frete - desconto
        BigDecimal total = cart.getTotalPrice().add(shipping.cost()).subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }
        order.setTotal(total);

        // 5. Persistir
        Order savedOrder = orderRepository.save(order);

        // 6. Esvaziar carrinho
        cart.clear();
        cartRepository.save(cart);

        return savedOrder;
    }

    public java.util.Optional<Order> findById(UUID id) {
        return orderRepository.findById(id);
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public java.util.Optional<Order> updateStatus(UUID id, String status) {
        return orderRepository.findById(id).map(order -> {
            order.setStatus(status);
            return orderRepository.save(order);
        });
    }
}
