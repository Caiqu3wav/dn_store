package com.dnstore.backend.service.payment;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Contrato de integração com gateways de pagamento.
 *
 * Para trocar de gateway (ex: PagSeguro, Pagar.me, Stripe):
 * 1. Crie uma nova classe que implemente esta interface
 * 2. Altere PAYMENT_GATEWAY no .env para o nome do novo bean
 * 3. Nenhum outro arquivo precisa mudar
 */
public interface PaymentGateway {

        PaymentResult createPayment(PaymentRequest request);

        WebhookResult processWebhook(String rawPayload, String signatureHeader);

        ProviderPayment findPaymentByReference(String externalReference);

        ProviderPayment getPayment(String externalId);

        ProviderPayment cancelPayment(String externalId);

        // ── DTOs ─────────────────────────────────────────────────────────────────

        record PaymentRequest(
                        UUID orderId,
                        String externalReference,
                        BigDecimal amount,
                        String payerName,
                        String payerEmail,
                        String payerCpfCnpj,
                        String description,
                        String paymentMethod, // CREDIT_CARD | DEBIT_CARD | PIX | BOLETO
                        Integer installments,
                        CardData card, // null se não for cartão
                        String payerPhone,
                        String payerPostalCode,
                        String payerAddressNumber,
                        String payerAddressComplement,
                        String remoteIp
        ) {
                public PaymentRequest(
                                UUID orderId,
                                BigDecimal amount,
                                String payerName,
                                String payerEmail,
                                String payerCpfCnpj,
                                String description,
                                String paymentMethod,
                                Integer installments,
                                CardData card) {
                        this(orderId, orderId.toString(), amount, payerName, payerEmail, payerCpfCnpj,
                                        description, paymentMethod, installments, card, null, null, null, null, null);
                }

                public PaymentRequest(
                                UUID orderId,
                                String externalReference,
                                BigDecimal amount,
                                String payerName,
                                String payerEmail,
                                String payerCpfCnpj,
                                String description,
                                String paymentMethod,
                                Integer installments,
                                CardData card) {
                        this(orderId, externalReference, amount, payerName, payerEmail, payerCpfCnpj,
                                        description, paymentMethod, installments, card, null, null, null, null, null);
                }

                @Override
                public String toString() {
                        return "PaymentRequest[orderId=" + orderId + ", externalReference=" + externalReference
                                        + ", amount=" + amount + ", paymentMethod=" + paymentMethod + "]";
                }
        }

        record CardData(
                        @NotBlank @Size(max = 100)
                        String holderName,
                        @NotBlank @Size(min = 13, max = 23)
                        String number,
                        @NotBlank @Size(min = 2, max = 2)
                        String expiryMonth,
                        @NotBlank @Size(min = 4, max = 4)
                        String expiryYear,
                        @NotBlank @Size(min = 3, max = 4)
                        String ccv) {
                @Override
                public String toString() {
                        return "CardData[holderName=[REDACTED], number=[REDACTED], expiryMonth=[REDACTED], "
                                        + "expiryYear=[REDACTED], ccv=[REDACTED]]";
                }
        }

        record PaymentResult(
                        String externalId,
                        String status,
                        String paymentMethod,
                        String pixQrCode,
                        String pixCopyPaste,
                        String boletoUrl,
                        String boletoBarcode,
                        BigDecimal amount,
                        String externalReference,
                        String customerId,
                        String currency) {
                public PaymentResult(
                                String externalId,
                                String status,
                                String paymentMethod,
                                String pixQrCode,
                                String pixCopyPaste,
                                String boletoUrl,
                                String boletoBarcode) {
                        this(externalId, status, paymentMethod, pixQrCode, pixCopyPaste, boletoUrl, boletoBarcode,
                                        null, null, null, null);
                }
        }

        record WebhookResult(
                        String eventId,
                        String externalId,
                        String eventType,
                        String normalizedStatus,
                        String externalReference,
                        BigDecimal amount,
                        String currency,
                        String customerId
        ) {
                public WebhookResult(String eventId, String externalId, String normalizedStatus,
                                String externalReference, BigDecimal amount, String currency, String customerId) {
                        this(eventId, externalId, null, normalizedStatus, externalReference, amount, currency, customerId);
                }

                public WebhookResult(String externalId, String normalizedStatus) {
                        this("legacy:" + externalId + ":" + normalizedStatus, externalId, null, normalizedStatus,
                                        null, null, null, null);
                }
        }

        record ProviderPayment(
                        String externalId,
                        String status,
                        String externalReference,
                        BigDecimal amount,
                        String currency,
                        String customerId,
                        String paymentMethod,
                        String pixQrCode,
                        String pixCopyPaste,
                        String boletoUrl,
                        String boletoBarcode) {
                public PaymentResult toPaymentResult() {
                        return new PaymentResult(externalId, status, paymentMethod, pixQrCode, pixCopyPaste,
                                        boletoUrl, boletoBarcode, amount, externalReference, customerId, currency);
                }
        }
}
