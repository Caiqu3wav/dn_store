package com.dnstore.backend.controller;

import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

        try {
            Payment payment = paymentService.initPayment(
                    request.orderId(),
                    user.getId(),
                    request.idempotencyKey(),
                    request.cpfCnpj(),
                    request.paymentMethod(),
                    request.installments());

            log.info("payment.init orderId={} userId={} method={} status={}",
                    request.orderId(), user.getId(), request.paymentMethod(), payment.getStatus());
            ResponseEntity.BodyBuilder response = "RECONCILIATION_REQUIRED".equals(payment.getStatus())
                    || "CREATING".equals(payment.getStatus())
                            ? ResponseEntity.accepted()
                            : ResponseEntity.ok();
            return response.body(PaymentResponse.from(payment));

        } catch (IllegalArgumentException | IllegalStateException | ConflictException e) {
            log.warn("payment.init.rejected orderId={} userId={} reason={}",
                    request.orderId(), user.getId(), e.getMessage());
            return ResponseEntity.status(e instanceof ConflictException ? 409 : 400)
                    .body(new ErrorResponse(e.getMessage()));
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
            @Size(max = 64) @NotBlank String idempotencyKey,
            Integer installments) {}

    public record PaymentResponse(
            UUID id,
            String externalId,
            String status,
            String orderStatus,
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
                    p.getOrder().getStatus(),
                    p.getPaymentMethod(), p.getAmount(), p.getInstallments(),
                    p.getPixQrCode(), p.getPixCopyPaste(),
                    p.getBoletoUrl(), p.getBoletoBarcode());
        }
    }

    public record ErrorResponse(String message) {}
}
