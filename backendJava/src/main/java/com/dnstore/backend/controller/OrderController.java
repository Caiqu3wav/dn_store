package com.dnstore.backend.controller;

import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.model.User;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.service.OrderService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 📦 OrderController - REST Completo
 *
 * Gerencia o ciclo de vida dos pedidos: Criação e Consulta.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/shipping-quotes")
    public ResponseEntity<?> quoteShipping(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ShippingQuoteRequest request) {
        return ResponseEntity.ok(orderService.quoteShipping(user, request.getZipCode()));
    }

    /**
     * Cria um novo pedido (Checkout).
     * POST /api/orders
     */
    @PostMapping
    public ResponseEntity<?> createOrder(@AuthenticationPrincipal User user, @RequestBody CheckoutRequest request) {
        OrderService.AddressData addressData = request.getAddressId() == null
                ? new OrderService.AddressData(
                        request.getStreet(), request.getNumber(), request.getComplement(),
                        request.getNeighborhood(), request.getCity(), request.getState(),
                        request.getZipCode())
                : null;

        Order order = orderService.checkout(
                user,
                addressData,
                request.getShippingType(),
                request.getCouponCode(),
                request.getAddressId());
        return ResponseEntity.status(201).body(order);
    }

    /**
     * GET /api/orders/{id}
     * Usuário só acessa o próprio pedido. ADMIN acessa qualquer um.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        Order order = (user.getRole() == Role.ADMIN
                ? orderService.findById(id)
                : orderService.findByIdAndUser(id, user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        return ResponseEntity.ok(order);
    }

    /**
     * GET /api/orders/my
     * Lista os pedidos do usuário logado.
     */
    @GetMapping("/my")
    public ResponseEntity<List<Order>> myOrders(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(orderService.findByUser(user.getId()));
    }

    /**
     * GET /api/orders/all
     * Lista todos os pedidos (apenas ADMIN).
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Order>> listAll() {
        return ResponseEntity.ok(orderService.findAll());
    }

    /**
     * PUT /api/orders/{id}/status
     * Atualiza o status do pedido (apenas ADMIN).
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (body == null || status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status é obrigatório.");
        }
        Order order = orderService.updateStatus(id, status)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        return ResponseEntity.ok(order);
    }

    // DTOs auxiliares
    public static class ShippingQuoteRequest {
        @NotBlank
        private String zipCode;

        public String getZipCode() {
            return zipCode;
        }

        public void setZipCode(String zipCode) {
            this.zipCode = zipCode;
        }
    }

    public static class CheckoutRequest {
        private String zipCode;
        private String street;
        private String number;
        private String complement;
        private String neighborhood;
        private String city;
        private String state;
        private String shippingType;
        private String couponCode;
        private UUID addressId;

        public String getZipCode() {
            return zipCode;
        }

        public void setZipCode(String zipCode) {
            this.zipCode = zipCode;
        }

        public String getStreet() {
            return street;
        }

        public void setStreet(String street) {
            this.street = street;
        }

        public String getNumber() {
            return number;
        }

        public void setNumber(String number) {
            this.number = number;
        }

        public String getComplement() {
            return complement;
        }

        public void setComplement(String complement) {
            this.complement = complement;
        }

        public String getNeighborhood() {
            return neighborhood;
        }

        public void setNeighborhood(String neighborhood) {
            this.neighborhood = neighborhood;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getShippingType() {
            return shippingType;
        }

        public void setShippingType(String shippingType) {
            this.shippingType = shippingType;
        }

        public String getCouponCode() {
            return couponCode;
        }

        public void setCouponCode(String couponCode) {
            this.couponCode = couponCode;
        }

        public UUID getAddressId() {
            return addressId;
        }

        public void setAddressId(UUID addressId) {
            this.addressId = addressId;
        }
    }

}
