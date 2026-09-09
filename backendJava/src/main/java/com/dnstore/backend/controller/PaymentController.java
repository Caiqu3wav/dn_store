package com.dnstore.backend.controller;

import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.User;
import com.dnstore.backend.service.PaymentService;
import com.dnstore.backend.service.payment.PaymentGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /api/payment/init
     * Inicia o pagamento de um pedido já criado.
     * Retorna os dados necessários para o frontend exibir QR, boleto ou status do cartão.
     */
    @PostMapping("/init")
    public ResponseEntity<?> initPayment(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PaymentInitRequest request
    ) {
        try {
            PaymentGateway.CardData cardData = null;
            if (request.card() != null) {
                cardData = new PaymentGateway.CardData(
                        request.card().holderName(),
                        request.card().number(),
                        request.card().expiryMonth(),
                        request.card().expiryYear(),
                        request.card().ccv()
                );
            }

            PaymentGateway.PaymentRequest gatewayRequest = new PaymentGateway.PaymentRequest(
                    request.orderId(),
                    request.amount(),
                    user.getName(),
                    user.getEmail(),
                    request.cpfCnpj(),
                    "Pedido DN Store #" + request.orderId(),
                    request.paymentMethod(),
                    request.installments(),
                    cardData
            );

            Payment payment = paymentService.initPayment(request.orderId(), user.getId(), gatewayRequest);
            return ResponseEntity.ok(PaymentResponse.from(payment));

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Payment init error for order {}", request.orderId(), e);
            return ResponseEntity.internalServerError().body(new ErrorResponse("Erro ao processar pagamento."));
        }
    }

    /**
     * POST /api/payment/webhook
     * Recebe notificações do Asaas sobre mudanças de status.
     * Endpoint público — o Asaas não manda JWT.
     * A validação de autenticidade é feita dentro do gateway via assinatura.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "asaas-access-token", required = false) String signature
    ) {
        try {
            paymentService.handleWebhook(rawPayload, signature);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Webhook processing error", e);
            // Retorna 200 mesmo em erro para o Asaas não reenviar infinitamente
            return ResponseEntity.ok().build();
        }
    }

    /**
     * GET /api/payment/order/{orderId}
     * Usuário só acessa o pagamento do próprio pedido — previne IDOR.
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<?> getPaymentStatus(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User user
    ) {
        try {
            Payment payment = user.getRole() == com.dnstore.backend.model.enums.Role.ADMIN
                    ? paymentService.findByOrderId(orderId)
                    : paymentService.findByOrderIdAndUser(orderId, user.getId());
            return ResponseEntity.ok(PaymentResponse.from(payment));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record PaymentInitRequest(
            @NotNull UUID orderId,
            @NotNull BigDecimal amount,
            @NotBlank String paymentMethod,
            @NotBlank String cpfCnpj,
            @Min(1) @Max(4) Integer installments,
            CardRequest card
    ) {}

    public record CardRequest(
            @NotBlank String holderName,
            @NotBlank String number,
            @NotBlank String expiryMonth,
            @NotBlank String expiryYear,
            @NotBlank String ccv
    ) {}

    public record PaymentResponse(
            UUID id,
            String externalId,
            String status,
            String paymentMethod,
            BigDecimal amount,
            Integer installments,
            String pixQrCode,
            String pixCopyPaste,
            String boletoUrl,
            String boletoBarcode
    ) {
        static PaymentResponse from(Payment p) {
            return new PaymentResponse(
                    p.getId(), p.getExternalId(), p.getStatus(),
                    p.getPaymentMethod(), p.getAmount(), p.getInstallments(),
                    p.getPixQrCode(), p.getPixCopyPaste(),
                    p.getBoletoUrl(), p.getBoletoBarcode()
            );
        }
    }

    public record ErrorResponse(String message) {}
}
