package com.dnstore.backend.service.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Contrato de integração com gateways de pagamento.
 *
 * Para trocar de gateway (ex: PagSeguro, Pagar.me, Stripe):
 *   1. Crie uma nova classe que implemente esta interface
 *   2. Altere PAYMENT_GATEWAY no .env para o nome do novo bean
 *   3. Nenhum outro arquivo precisa mudar
 */
public interface PaymentGateway {

    PaymentResult createPayment(PaymentRequest request);

    WebhookResult processWebhook(String rawPayload, String signatureHeader);

    // ── DTOs ─────────────────────────────────────────────────────────────────

    record PaymentRequest(
            UUID orderId,
            BigDecimal amount,
            String payerName,
            String payerEmail,
            String payerCpfCnpj,
            String description,
            String paymentMethod,   // CREDIT_CARD | DEBIT_CARD | PIX | BOLETO
            Integer installments,
            CardData card           // null se não for cartão
    ) {}

    record CardData(
            String holderName,
            String number,
            String expiryMonth,
            String expiryYear,
            String ccv
    ) {}

    record PaymentResult(
            String externalId,
            String status,
            String pixQrCode,
            String pixCopyPaste,
            String boletoUrl,
            String boletoBarcode
    ) {}

    record WebhookResult(
            String externalId,
            String normalizedStatus  // PAID | PENDING | FAILED | REFUNDED
    ) {}
}
