package com.dnstore.backend.controller;

import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.service.PaymentService;
import com.dnstore.backend.service.payment.PaymentGateway;
import jakarta.validation.Valid;
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

    @PostMapping("/init")
    public ResponseEntity<?> initPayment(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PaymentInitRequest request) {

        PaymentGateway.CardData cardData = null;
        if (request.card() != null) {
            cardData = new PaymentGateway.CardData(
                    request.card().holderName(),
                    request.card().number(),
                    request.card().expiryMonth(),
                    request.card().expiryYear(),
                    request.card().ccv());
        }

        try {
            Payment payment = paymentService.initPayment(
                    request.orderId(),
                    user.getId(),
                    request.cpfCnpj(),
                    request.paymentMethod(),
                    request.installments(),
                    cardData);

            log.info("payment.init orderId={} userId={} method={} status={}",
                    request.orderId(), user.getId(), request.paymentMethod(), payment.getStatus());
            return ResponseEntity.ok(PaymentResponse.from(payment));

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("payment.init.rejected orderId={} userId={} reason={}",
                    request.orderId(), user.getId(), e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("payment.init.error orderId={} userId={}", request.orderId(), user.getId(), e);
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Erro ao processar pagamento."));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "asaas-access-token", required = false) String signature) {
        paymentService.handleWebhook(rawPayload, signature);
        log.info("payment.webhook_processed");
        return ResponseEntity.ok().build();
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<?> getPaymentStatus(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User user) {
        try {
            Payment payment = user.getRole() == Role.ADMIN
                    ? paymentService.findByOrderId(orderId)
                    : paymentService.findByOrderIdAndUser(orderId, user.getId());
            return ResponseEntity.ok(PaymentResponse.from(payment));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    public record PaymentInitRequest(
            @NotNull UUID orderId,
            @NotBlank String paymentMethod,
            @NotBlank String cpfCnpj,
            @Min(1) @Max(4) Integer installments,
            @Valid CardRequest card) {}

    public record CardRequest(
            @NotBlank String holderName,
            @NotBlank String number,
            @NotBlank String expiryMonth,
            @NotBlank String expiryYear,
            @NotBlank String ccv) {}

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
            String boletoBarcode) {
        static PaymentResponse from(Payment p) {
            return new PaymentResponse(
                    p.getId(), p.getExternalId(), p.getStatus(),
                    p.getPaymentMethod(), p.getAmount(), p.getInstallments(),
                    p.getPixQrCode(), p.getPixCopyPaste(),
                    p.getBoletoUrl(), p.getBoletoBarcode());
        }
    }

    public record ErrorResponse(String message) {}
}
