package com.dnstore.backend.service.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.dnstore.backend.model.enums.PaymentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service("asaas")
@RequiredArgsConstructor
public class AsaasGatewayService implements PaymentGateway {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${asaas.api-key}")
    private String apiKey;

    @Value("${asaas.sandbox:true}")
    private boolean sandbox;

    @Value("${asaas.webhook-token:}")
    private String webhookToken;

    private String baseUrl() {
        return sandbox
                ? "https://sandbox.asaas.com/api/v3"
                : "https://api.asaas.com/v3";
    }

    private HttpHeaders headers() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new PaymentGatewayException("Integração Asaas não configurada");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("access_token", apiKey);
        return headers;
    }

    // ── Criar ou recuperar customer no Asaas ─────────────────────────────────

    @SuppressWarnings("rawtypes")
    private String getOrCreateCustomer(String name, String email, String cpfCnpj) {
        String document = normalizeAndValidateDocument(cpfCnpj);
        String searchUrl = UriComponentsBuilder.fromUriString(baseUrl() + "/customers")
                .queryParam("cpfCnpj", document)
                .build()
                .encode()
                .toUriString();

        try {
            ResponseEntity<Map> searchResp = restTemplate.exchange(
                    searchUrl, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);

            Map<?, ?> responseBody = searchResp.getBody();
            var data = responseBody == null ? null : (java.util.List<?>) responseBody.get("data");
            if (data != null && !data.isEmpty()) {
                Object customerId = ((Map<?, ?>) data.get(0)).get("id");
                if (customerId instanceof String id && !id.isBlank()) {
                    return id;
                }
                throw new PaymentGatewayException("Resposta inválida ao consultar customer Asaas");
            }
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentGatewayException("Não foi possível consultar customer no Asaas");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("cpfCnpj", document);

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl() + "/customers",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers()),
                    Map.class);
            Object customerId = resp.getBody() == null ? null : resp.getBody().get("id");
            if (!(customerId instanceof String id) || id.isBlank()) {
                throw new PaymentGatewayException("Resposta inválida ao criar customer Asaas");
            }
            return id;
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentGatewayException("Não foi possível criar customer no Asaas");
        }
    }

    // ── createPayment ─────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public PaymentResult createPayment(PaymentRequest request) {
        String document = normalizeAndValidateDocument(request.payerCpfCnpj());
        String billingType = asaasBillingType(request.paymentMethod());
        if (("CREDIT_CARD".equals(billingType) || "DEBIT_CARD".equals(billingType)) && request.card() == null) {
            throw new IllegalArgumentException("Dados do cartão são obrigatórios para este método");
        }
        PaymentResult existingPayment = findPaymentByOrderReference(request.orderId().toString());
        if (existingPayment != null) {
            return existingPayment;
        }

        String customerId = getOrCreateCustomer(
                request.payerName(), request.payerEmail(), document);

        Map<String, Object> body = new HashMap<>();
        body.put("customer", customerId);
        body.put("billingType", billingType);
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
            holderInfo.put("cpfCnpj", document);
            body.put("creditCardHolderInfo", holderInfo);
        }

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl() + "/payments",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers()),
                    Map.class);
            return parsePaymentResponse(resp.getBody());
        } catch (Exception e) {
            log.error("Asaas createPayment failed");
            throw new PaymentGatewayException("Não foi possível criar a cobrança no Asaas");
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private PaymentResult findPaymentByOrderReference(String orderReference) {
        String url = UriComponentsBuilder.fromUriString(baseUrl() + "/payments")
                .queryParam("externalReference", orderReference)
                .build()
                .encode()
                .toUriString();
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
            Map<String, Object> body = response.getBody();
            Object data = body == null ? null : body.get("data");
            if (!(data instanceof java.util.List<?> payments) || payments.isEmpty()) {
                return null;
            }
            if (payments.size() != 1 || !(payments.get(0) instanceof Map<?, ?> payment)) {
                throw new PaymentGatewayException("Referência do pedido não identifica uma cobrança única");
            }
            return parsePaymentResponse((Map<String, Object>) payment);
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentGatewayException("Não foi possível verificar cobrança existente no Asaas");
        }
    }

    // ── processWebhook ────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public WebhookResult processWebhook(String rawPayload, String signatureHeader) {
        if (webhookToken == null || webhookToken.isBlank() || signatureHeader == null
                || !java.security.MessageDigest.isEqual(
                        webhookToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        signatureHeader.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new WebhookAuthenticationException();
        }

        try {
            Object decodedPayload = objectMapper.readValue(rawPayload, Object.class);
            if (!(decodedPayload instanceof Map<?, ?> payload)
                    || !(payload.get("payment") instanceof Map<?, ?> paymentData)) {
                throw new IllegalArgumentException("Webhook sem dados de cobrança válidos");
            }
            Map<String, Object> payment = (Map<String, Object>) paymentData;
            if (payment == null || !(payment.get("id") instanceof String externalId) || externalId.isBlank()
                    || !(payment.get("status") instanceof String asaasStatus)) {
                throw new IllegalArgumentException("Webhook sem dados de cobrança válidos");
            }

            return new WebhookResult(externalId, PaymentStatus.fromAsaas(asaasStatus).name());
        } catch (JsonProcessingException | ClassCastException e) {
            throw new IllegalArgumentException("Webhook inválido");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("rawtypes")
    private PaymentResult parsePaymentResponse(Map<String, Object> body) {
        Object id = body == null ? null : body.get("id");
        Object statusValue = body == null ? null : body.get("status");
        Object methodValue = body == null ? null : body.get("billingType");
        if (!(id instanceof String externalId) || externalId.isBlank()
                || !(statusValue instanceof String asaasStatus)
                || !(methodValue instanceof String paymentMethod)) {
            throw new PaymentGatewayException("Resposta inválida ao criar cobrança no Asaas");
        }
        String status;
        try {
            status = PaymentStatus.fromAsaas(asaasStatus).name();
        } catch (IllegalArgumentException e) {
            throw new PaymentGatewayException("Status inesperado recebido do Asaas");
        }

        String pixQrCode = null;
        String pixCopyPaste = null;
        String boletoUrl = null;
        String boletoBarcode = null;

        if ("PIX".equals(paymentMethod)) {
            try {
                ResponseEntity<Map> pixResp = restTemplate.exchange(
                        baseUrl() + "/payments/" + externalId + "/pixQrCode",
                        HttpMethod.GET,
                        new HttpEntity<>(headers()),
                        Map.class);
                pixQrCode = (String) pixResp.getBody().get("encodedImage");
                pixCopyPaste = (String) pixResp.getBody().get("payload");
            } catch (Exception e) {
                log.warn("Asaas: could not fetch Pix QR code", e);
            }
        }

        if ("BOLETO".equals(paymentMethod)) {
            boletoUrl = (String) body.get("bankSlipUrl");
            boletoBarcode = (String) body.get("identificationField");
        }

        return new PaymentResult(externalId, status, paymentMethod, pixQrCode, pixCopyPaste,
                boletoUrl, boletoBarcode);
    }

    private String asaasBillingType(String method) {
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "CREDIT_CARD" -> "CREDIT_CARD";
            case "DEBIT_CARD" -> "DEBIT_CARD";
            case "PIX" -> "PIX";
            case "BOLETO" -> "BOLETO";
            default -> throw new IllegalArgumentException("Método de pagamento inválido: " + method);
        };
    }

    private String normalizeAndValidateDocument(String document) {
        if (document == null) {
            throw new IllegalArgumentException("CPF/CNPJ inválido");
        }
        String digits = document.replaceAll("\\D", "");
        if (digits.length() == 11 && validCpf(digits))
            return digits;
        if (digits.length() == 14 && validCnpj(digits))
            return digits;
        throw new IllegalArgumentException("CPF/CNPJ inválido");
    }

    private boolean validCpf(String cpf) {
        if (cpf.chars().distinct().count() == 1)
            return false;
        int first = cpfDigit(cpf.substring(0, 9), 10);
        int second = cpfDigit(cpf.substring(0, 9) + first, 11);
        return cpf.charAt(9) - '0' == first && cpf.charAt(10) - '0' == second;
    }

    private int cpfDigit(String digits, int weight) {
        int sum = 0;
        for (int index = 0; index < digits.length(); index++) {
            sum += (digits.charAt(index) - '0') * (weight - index);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }

    private boolean validCnpj(String cnpj) {
        if (cnpj.chars().distinct().count() == 1)
            return false;
        int first = cnpjDigit(cnpj.substring(0, 12), new int[] { 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 });
        int second = cnpjDigit(cnpj.substring(0, 12) + first,
                new int[] { 6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 });
        return cnpj.charAt(12) - '0' == first && cnpj.charAt(13) - '0' == second;
    }

    private int cnpjDigit(String digits, int[] weights) {
        int sum = 0;
        for (int index = 0; index < digits.length(); index++) {
            sum += (digits.charAt(index) - '0') * weights[index];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
