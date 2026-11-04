package com.dnstore.backend.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service("asaas")
public class AsaasGatewayService implements PaymentGateway {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;

    @Value("${asaas.api-key}")
    private String apiKey;

    @Value("${asaas.sandbox:true}")
    private boolean sandbox;

    @Value("${asaas.webhook-token:}")
    private String webhookToken;

    public AsaasGatewayService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private String baseUrl() {
        return sandbox
                ? "https://sandbox.asaas.com/api/v3"
                : "https://api.asaas.com/v3";
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("access_token", apiKey);
        return h;
    }

    // ── Customer ─────────────────────────────────────────────────────────────

    private String getOrCreateCustomer(String name, String email, String cpfCnpj) {
        ResponseEntity<Map> resp;
        try {
            resp = restTemplate.exchange(
                    baseUrl() + "/customers?cpfCnpj=" + cpfCnpj,
                    HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
        } catch (Exception e) {
            log.warn("Asaas: customer search failed", e);
            throw new PaymentGatewayException("Falha ao buscar cliente no gateway de pagamento");
        }
        var data = (java.util.List<?>) resp.getBody().get("data");
        if (data != null && !data.isEmpty()) {
            return (String) ((Map<?, ?>) data.get(0)).get("id");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("cpfCnpj", cpfCnpj);

        ResponseEntity<Map> createResp = restTemplate.exchange(
                baseUrl() + "/customers",
                HttpMethod.POST,
                new HttpEntity<>(body, headers()),
                Map.class);
        return (String) createResp.getBody().get("id");
    }

    // ── createPayment ─────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public PaymentResult createPayment(PaymentRequest request) {
        String cpfCnpj = normalizeCpfCnpj(request.payerCpfCnpj());

        // Recover existing charge for idempotency (e.g. retry after timeout)
        PaymentResult existing = findExistingCharge(request.orderId());
        if (existing != null) return existing;

        String customerId = getOrCreateCustomer(
                request.payerName(), request.payerEmail(), cpfCnpj);

        String billingType = asaasBillingType(request.paymentMethod());

        Map<String, Object> body = new HashMap<>();
        body.put("customer", customerId);
        body.put("billingType", billingType);
        body.put("value", request.amount());
        body.put("dueDate", java.time.LocalDate.now().plusDays(1).toString());
        body.put("description", request.description());
        body.put("externalReference", request.orderId().toString());

        // Parcelas — apenas crédito
        if ("CREDIT_CARD".equals(billingType)
                && request.installments() != null && request.installments() > 1) {
            body.put("installmentCount", request.installments());
            body.put("installmentValue",
                    request.amount().divide(
                            java.math.BigDecimal.valueOf(request.installments()),
                            2, java.math.RoundingMode.HALF_UP));
        }

        // Dados do cartão
        if (request.card() != null) {
            Map<String, Object> card = new HashMap<>();
            card.put("holderName", request.card().holderName());
            card.put("number", request.card().number());
            card.put("expiryMonth", request.card().expiryMonth());
            card.put("expiryYear", request.card().expiryYear());
            card.put("ccv", request.card().ccv());
            body.put("creditCard", card);

            Map<String, Object> holderInfo = new HashMap<>();
            holderInfo.put("name", request.payerName());
            holderInfo.put("email", request.payerEmail());
            holderInfo.put("cpfCnpj", cpfCnpj);
            body.put("creditCardHolderInfo", holderInfo);
        }

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl() + "/payments",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers()),
                    Map.class);
            return parsePaymentResponse(resp.getBody());
        } catch (HttpClientErrorException e) {
            log.error("Asaas createPayment error: {}", e.getResponseBodyAsString(), e);
            throw new PaymentGatewayException("Erro ao criar cobrança: " + e.getResponseBodyAsString());
        }
    }

    // ── processWebhook ────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public WebhookResult processWebhook(String rawPayload, String signatureHeader) {
        if (webhookToken == null || webhookToken.isBlank()
                || signatureHeader == null
                || !MessageDigest.isEqual(
                        webhookToken.getBytes(StandardCharsets.UTF_8),
                        signatureHeader.getBytes(StandardCharsets.UTF_8))) {
            throw new WebhookAuthenticationException();
        }

        try {
            Map<String, Object> payload = MAPPER.readValue(rawPayload, Map.class);
            Map<String, Object> payment = (Map<String, Object>) payload.get("payment");
            String externalId = (String) payment.get("id");
            String asaasStatus = (String) payment.get("status");
            return new WebhookResult(externalId, normalizeStatus(asaasStatus));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Asaas webhook parse error", e);
            throw new PaymentGatewayException("Webhook inválido");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private PaymentResult parsePaymentResponse(Map<String, Object> body) {
        String externalId = (String) body.get("id");
        String status = normalizeStatus((String) body.get("status"));
        String billingType = (String) body.get("billingType");

        String pixQrCode = null;
        String pixCopyPaste = null;
        String boletoUrl = null;
        String boletoBarcode = null;

        if ("PIX".equals(billingType)) {
            try {
                ResponseEntity<Map> pixResp = restTemplate.exchange(
                        baseUrl() + "/payments/" + externalId + "/pixQrCode",
                        HttpMethod.GET,
                        new HttpEntity<>(headers()),
                        Map.class);
                Map<String, Object> pixBody = pixResp.getBody();
                if (pixBody != null) {
                    pixQrCode = (String) pixBody.get("encodedImage");
                    pixCopyPaste = (String) pixBody.get("payload");
                }
            } catch (Exception e) {
                log.error("Asaas: could not fetch Pix QR code for {}", externalId, e);
                throw new PaymentGatewayException("Não foi possível obter o QR Code PIX");
            }
        }

        if ("BOLETO".equals(billingType)) {
            boletoUrl = (String) body.get("bankSlipUrl");
            // identificationField = linha digitável do boleto
            boletoBarcode = (String) body.get("identificationField");
            if (boletoBarcode == null) {
                boletoBarcode = (String) body.get("barCode");
            }
        }

        return new PaymentResult(externalId, status, billingType, pixQrCode, pixCopyPaste, boletoUrl, boletoBarcode);
    }

    @SuppressWarnings("unchecked")
    private PaymentResult findExistingCharge(java.util.UUID orderId) {
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl() + "/payments?externalReference=" + orderId,
                    HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
            var data = (java.util.List<?>) resp.getBody().get("data");
            if (data != null && !data.isEmpty()) {
                return parsePaymentResponse((Map<String, Object>) data.get(0));
            }
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Asaas: could not check existing charge for orderId={}", orderId, e);
        }
        return null;
    }

    private String normalizeCpfCnpj(String cpfCnpj) {
        if (cpfCnpj == null) throw new IllegalArgumentException("CPF/CNPJ é obrigatório");
        String digits = cpfCnpj.replaceAll("[.\\-/]", "");
        if (digits.length() != 11 && digits.length() != 14)
            throw new IllegalArgumentException("CPF/CNPJ inválido");
        if (digits.chars().distinct().count() == 1)
            throw new IllegalArgumentException("CPF/CNPJ inválido");
        return digits;
    }

    private String asaasBillingType(String method) {
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "CREDIT_CARD" -> "CREDIT_CARD";
            case "DEBIT_CARD"  -> "DEBIT_CARD";
            case "PIX"         -> "PIX";
            case "BOLETO"      -> "BOLETO";
            default -> throw new IllegalArgumentException("Método de pagamento inválido: " + method);
        };
    }

    private String normalizeStatus(String asaasStatus) {
        if (asaasStatus == null) return "PENDING";
        return switch (asaasStatus.toUpperCase(Locale.ROOT)) {
            case "RECEIVED", "CONFIRMED"          -> "PAID";
            case "PENDING", "AWAITING_RISK_ANALYSIS",
                 "AWAITING_CHARGEBACK_REVERSAL"   -> "PENDING";
            case "REFUNDED", "REFUND_REQUESTED",
                 "PARTIALLY_REFUNDED"             -> "REFUNDED";
            case "OVERDUE", "DUNNING_REQUESTED",
                 "DUNNING_RECEIVED"               -> "OVERDUE";
            default -> throw new IllegalArgumentException("Status Asaas inválido: " + asaasStatus);
        };
    }
}
