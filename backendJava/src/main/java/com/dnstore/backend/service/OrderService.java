package com.dnstore.backend.service;

import com.dnstore.backend.model.Address;
import com.dnstore.backend.model.Cart;
import com.dnstore.backend.model.CartItem;
import com.dnstore.backend.model.Coupon;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.OrderItem;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.AddressRepository;
import com.dnstore.backend.repository.CartRepository;
import com.dnstore.backend.repository.CouponRepository;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.dnstore.backend.service.strategy.DeliveryStrategy.DeliveryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.List;
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
    private final ProductVariantRepository productVariantRepository;

    public record AddressData(
            String street, String number, String complement,
            String neighborhood, String city, String state, String zipCode
    ) {}

        public record ShippingOption(String typeName, BigDecimal cost, int deadLineDays, String source) {}

        @Transactional(readOnly = true)
        public List<ShippingOption> quoteShipping(User user, String zipCode) {
        Cart cart = cartRepository.findByUser(user)
            .orElseThrow(() -> new IllegalStateException("O carrinho está vazio."));
        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("O carrinho está vazio.");
        }

        return deliveryService.calculateOptions(zipCode, cart.getTotalWeight()).stream()
            .map(option -> new ShippingOption(option.typeName(), option.cost(), option.deadLineDays(), "SIMULATED"))
            .toList();
        }

    @Transactional
    public Order checkout(User user, AddressData addressData, String shippingType, String couponCode, UUID addressId) {
        String zipCode = addressData != null ? addressData.zipCode() : null;
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("O carrinho está vazio."));

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("O carrinho está vazio.");
        }

        // 1. Calcular Frete
        DeliveryResult shipping = deliveryService.calculateShipping(
                zipCode,
                cart.getTotalWeight(),
                shippingType);

        // 2. Resolver Endereço — prioridade: addressId salvo > dados do formulário
        Address address;
        if (addressId != null) {
            address = addressRepository.findByIdAndUser_Id(addressId, user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Endereço não encontrado."));
        } else if (addressData != null) {
            address = new Address();
            address.setUser(user);
            address.setStreet(addressData.street());
            address.setNumber(addressData.number());
            address.setComplement(addressData.complement());
            address.setNeighborhood(addressData.neighborhood());
            address.setCity(addressData.city());
            address.setState(addressData.state());
            address.setZipCode(addressData.zipCode());
            address = addressRepository.save(address);
        } else {
            throw new IllegalArgumentException("Endereço de entrega é obrigatório.");
        }

        // 3. Aplicar Cupom se existir
        BigDecimal discount = BigDecimal.ZERO;
        Coupon appliedCoupon = null;

        if (couponCode != null && !couponCode.trim().isEmpty()) {
            Coupon coupon = couponService.validateCoupon(couponCode)
                    .orElseThrow(() -> new IllegalArgumentException("Cupom inválido ou expirado."));

            BigDecimal cartTotal = cart.getTotalPrice();
            if (coupon.getMinCartValue() != null && cartTotal.compareTo(coupon.getMinCartValue()) < 0) {
                throw new IllegalArgumentException(
                        "O valor mínimo do carrinho para usar este cupom é R$ " + coupon.getMinCartValue());
            }

            if (coupon.getMaxUsage() != null && coupon.getCurrentUsage() != null
                    && coupon.getCurrentUsage() >= coupon.getMaxUsage()) {
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
        order.setStatus("PENDING_PAYMENT");
        order.setUser(user);
        order.setAddress(address);
        order.setCoupon(appliedCoupon);
        order.setDiscountAmount(discount);

        // 4b. Validar estoque e decrementar
        for (CartItem cartItem : cart.getItems()) {
            var variant = cartItem.getProductVariant();
            if (variant.getStock() < cartItem.getQuantity()) {
                throw new IllegalStateException(
                        "Estoque insuficiente para o produto: " + variant.getProduct().getName()
                                + " (" + variant.getSize() + ")"  
                );
            }
            variant.setStock(variant.getStock() - cartItem.getQuantity());
            productVariantRepository.save(variant);
        }

        // Mapear CartItems para OrderItems
        List<OrderItem> orderItems = cart.getItems().stream()
                .map(cartItem -> new OrderItem(order, cartItem.getProductVariant(), cartItem.getQuantity(),
                        cartItem.getSubtotal()))
                .collect(Collectors.toList());

        order.setItems(orderItems);
        order.setShippingCost(shipping.cost());
        order.setShippingType(shipping.typeName());
        order.setShippingDeadlineDays(shipping.deadLineDays());

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

    private static final java.util.Set<String> VALID_STATUSES = java.util.Set.of(
            "PENDING_PAYMENT", "PAID", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED", "PAYMENT_FAILED");

    public java.util.Optional<Order> findById(UUID id) {
        return orderRepository.findById(id);
    }

    /** Busca pedido garantindo que pertence ao usuário — previne IDOR. */
    public java.util.Optional<Order> findByIdAndUser(UUID id, UUID userId) {
        return orderRepository.findById(id)
                .filter(order -> order.getUser().getId().equals(userId));
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public List<Order> findByUser(UUID userId) {
        return orderRepository.findByUserId(userId);
    }

    public java.util.Optional<Order> updateStatus(UUID id, String status) {
        if (!VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Status inválido: " + status);
        }
        return orderRepository.findById(id).map(order -> {
            order.setStatus(status);
            return orderRepository.save(order);
        });
    }
}
