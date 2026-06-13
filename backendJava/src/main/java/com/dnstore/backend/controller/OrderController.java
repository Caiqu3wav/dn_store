package com.dnstore.backend.controller;

import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.User;
import com.dnstore.backend.service.OrderService;
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

    /**
     * Cria um novo pedido (Checkout).
     * POST /api/orders
     */
    @PostMapping
    public ResponseEntity<?> createOrder(@AuthenticationPrincipal User user, @RequestBody CheckoutRequest request) {
        try {
            Order order = orderService.checkout(
                    user,
                    request.getZipCode(),
                    request.getShippingType(),
                    request.getCouponCode(),
                    request.getAddressId()
            );
            return ResponseEntity.status(201).body(order);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            // Em produção logar o erro real
            return ResponseEntity.internalServerError().body(new ErrorResponse("Erro interno ao processar pedido."));
        }
    }

    /**
     * GET /api/orders/{id}
     * Busca um pedido pelo ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(@PathVariable UUID id) {
        return orderService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
        if (status == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Status é obrigatório."));
        }
        return orderService.updateStatus(id, status)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // DTOs auxiliares
    public static class CheckoutRequest {
        private String zipCode;
        private String shippingType;
        private String couponCode;
        private UUID addressId;

        public String getZipCode() {
            return zipCode;
        }

        public void setZipCode(String zipCode) {
            this.zipCode = zipCode;
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

    public static class ErrorResponse {
        private final String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
