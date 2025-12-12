package com.dnstore.backend.controller;

import com.dnstore.backend.model.Order;
import com.dnstore.backend.service.OrderService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<?> createOrder(@RequestBody CheckoutRequest request) {
        try {
            Order order = orderService.checkout(request.getZipCode(), request.getShippingType());
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
    public ResponseEntity<?> getOrder(@PathVariable Long id) {
        return orderService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // DTOs auxiliares
    @Data
    public static class CheckoutRequest {
        private String zipCode;
        private String shippingType;
    }

    @Data
    public static class ErrorResponse {
        private final String message;
    }
}
