package com.dnstore.backend.controller;

import com.dnstore.backend.exception.DeliveryException;
import com.dnstore.backend.model.Cart;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.CartRepository;
import com.dnstore.backend.service.DeliveryService;
import com.dnstore.backend.service.shipping.ShippingQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final DeliveryService deliveryService;
    private final CartRepository cartRepository;

    /**
     * GET /api/shipping/quote?zipCode=01310100
     * Retorna opções de frete para o CEP informado com base no carrinho do usuário.
     */
    @GetMapping("/quote")
    public ResponseEntity<?> quote(
            @RequestParam String zipCode,
            @AuthenticationPrincipal User user
    ) {
        try {
            Cart cart = cartRepository.findByUser(user).orElse(null);
            double weight = (cart != null) ? cart.getTotalWeight() : 0.3;

            List<ShippingQuote> quotes = deliveryService.calculateOptions(
                    zipCode,
                    buildItems(weight)
            );

            log.info("Shipping quoted for user={} zip={} options={}", user.getId(), zipCode, quotes.size());
            return ResponseEntity.ok(quotes);

        } catch (DeliveryException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Shipping quote error for zip={}", zipCode, e);
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Não foi possível cotar o frete. Tente novamente."));
        }
    }

    private List<com.dnstore.backend.service.shipping.ShippingGateway.ShippingItem> buildItems(double totalWeightKg) {
        java.math.BigDecimal weight = java.math.BigDecimal.valueOf(Math.max(totalWeightKg, 0.1));
        return List.of(new com.dnstore.backend.service.shipping.ShippingGateway.ShippingItem(
                weight,
                new java.math.BigDecimal("20"),
                new java.math.BigDecimal("5"),
                new java.math.BigDecimal("30"),
                1
        ));
    }

    public record ErrorResponse(String message) {}
}
