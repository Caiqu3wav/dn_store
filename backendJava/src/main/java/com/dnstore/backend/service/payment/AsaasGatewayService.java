package com.dnstore.backend.service.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementação do PaymentGateway para o Asaas.
 * Usa RestTemplate puro — sem SDK proprietário — para facilitar troca futura.
 *
 * Sandbox:  https://sandbox.asaas.com/api/v3
 * Produção: https://api.asaas.com/v3
 *
 * Chaves necessárias no .env:
 *   ASAAS_API_KEY   → $aact_... (token do sandbox ou produção)
 *   ASAAS_SANDBOX   → true | false
 */
@Slf4j
@Service("asaas")
@RequiredArgsConstructor
public class AsaasGatewayService implements PaymentGateway {

    private final RestTemplate restTemplate;

    @Value("${asaas.api-key}")
    private String apiKey;

    @Value("${asaas.sandbox:true}")
    private boolean sandbox;

    private String baseUrl() {
        return sandbox
                ? "https://sandbox.asaas.com/api/v3"
                : "https://api.asaas.com/v3";
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("access_token", apiKey);
        return headers;
    }

    // ── Criar ou recuperar customer no Asaas ─────────────────────────────────

    private String getOrCreateCustomer(String name, String email, String cpfCnpj) {
        try {
            // Tenta buscar customer existente pelo CPF/CNPJ
            String searchUrl = baseUrl() + "/customers?cpfCnpj=" + cpfCnpj;
            ResponseEntity<Map> searchResp = restTemplate.exchange(
                    searchUrl, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);

            var data = (java.util.List<?>) searchResp.getBody().get("data");
            if (data != null && !data.isEmpty()) {
                return (String) ((Map<?, ?>) data.get(0)).get("id");
            }
        } catch (Exception e) {
            log.warn("Asaas: customer search failed, creating new one. {}", e.getMessage());
        }

        // Cria novo customer
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("cpfCnpj", cpfCnpj);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/customers",
                HttpMethod.POST,
                new HttpEntity<>(body, headers()),
                Map.class
        );
        return (String) resp.getBody().get("id");
    }

    // ── createPayment ─────────────────────────────────────────────────────────

    @Override
    public PaymentResult createPayment(PaymentRequest request) {
        String customerId = getOrCreateCustomer(
                request.payerName(), request.payerEmail(), request.payerCpfCnpj());

        Map<String, Object> body = new HashMap<>();
        body.put("customer", customerId);
        body.put("billingType", asaasBillingType(request.paymentMethod()));
        body.put("value", request.amount());
        body.put("dueDate", java.time.LocalDate.now().plusDays(1).toString());
        body.put("description", request.description());
        body.put("externalReference", request.orderId().toString());

        if (request.installments() != null && request.installments() > 1) {
            body.put("installmentCount", request.installments());
            body.put("installmentValue",
                    request.amount().divide(java.math.BigDecimal.valueOf(request.installments()),
                            2, java.math.RoundingMode.HALF_UP));
        }

        if (request.card() != null) {
            Map<String, Object> cardMap = new HashMap<>();
            cardMap.put("holderName", request.card().holderName());
            cardMap.put("number", request.card().number());
            cardMap.put("expiryMonth", request.card().expiryMonth());
            cardMap.put("expiryYear", request.card().expiryYear());
            cardMap.put("ccv", request.card().ccv());
            body.put("creditCard", cardMap);

            Map<String, Object> holderInfo = new HashMap<>();
            holderInfo.put("name", request.payerName());
            holderInfo.put("email", request.payerEmail());
            holderInfo.put("cpfCnpj", request.payerCpfCnpj());
            body.put("creditCardHolderInfo", holderInfo);
        }

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl() + "/payments",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers()),
                    Map.class
            );
            return parsePaymentResponse(resp.getBody());
        } catch (HttpClientErrorException e) {
            log.error("Asaas createPayment error: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Erro ao criar cobrança: " + e.getResponseBodyAsString());
        }
    }

    // ── processWebhook ────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public WebhookResult processWebhook(String rawPayload, String signatureHeader) {
        // Asaas envia JSON com { "event": "PAYMENT_RECEIVED", "payment": { "id": "...", "status": "..." } }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> payload = mapper.readValue(rawPayload, Map.class);

            Map<String, Object> payment = (Map<String, Object>) payload.get("payment");
            String externalId = (String) payment.get("id");
            String asaasStatus = (String) payment.get("status");

            return new WebhookResult(externalId, normalizeStatus(asaasStatus));
        } catch (Exception e) {
            log.error("Asaas webhook parse error: {}", e.getMessage());
            throw new RuntimeException("Webhook inválido");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private PaymentResult parsePaymentResponse(Map<String, Object> body) {
        String externalId = (String) body.get("id");
        String status = normalizeStatus((String) body.get("status"));

        String pixQrCode = null;
        String pixCopyPaste = null;
        String boletoUrl = null;
        String boletoBarcode = null;

        // Busca dados de Pix se for o caso
        if ("PIX".equals(body.get("billingType"))) {
            try {
                ResponseEntity<Map> pixResp = restTemplate.exchange(
                        baseUrl() + "/payments/" + externalId + "/pixQrCode",
                        HttpMethod.GET,
                        new HttpEntity<>(headers()),
                        Map.class
                );
                pixQrCode = (String) pixResp.getBody().get("encodedImage");
                pixCopyPaste = (String) pixResp.getBody().get("payload");
            } catch (Exception e) {
                log.warn("Asaas: could not fetch Pix QR code: {}", e.getMessage());
            }
        }

        if ("BOLETO".equals(body.get("billingType"))) {
            boletoUrl = (String) body.get("bankSlipUrl");
            boletoBarcode = (String) body.get("nossoNumero");
        }

        return new PaymentResult(externalId, status, pixQrCode, pixCopyPaste, boletoUrl, boletoBarcode);
    }

    private String asaasBillingType(String method) {
        return switch (method.toUpperCase()) {
            case "CREDIT_CARD" -> "CREDIT_CARD";
            case "DEBIT_CARD"  -> "DEBIT_CARD";
            case "PIX"         -> "PIX";
            case "BOLETO"      -> "BOLETO";
            default -> throw new IllegalArgumentException("Método de pagamento inválido: " + method);
        };
    }

    private String normalizeStatus(String asaasStatus) {
        if (asaasStatus == null) return "PENDING";
        return switch (asaasStatus.toUpperCase()) {
            case "RECEIVED", "CONFIRMED" -> "PAID";
            case "PENDING", "AWAITING_RISK_ANALYSIS" -> "PENDING";
            case "REFUNDED", "REFUND_REQUESTED" -> "REFUNDED";
            default -> "FAILED";
        };
    }
}
