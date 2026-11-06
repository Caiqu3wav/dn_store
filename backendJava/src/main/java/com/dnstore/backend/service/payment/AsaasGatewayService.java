package com.dnstore.backend.service.payment;

import com.dnstore.backend.model.enums.PaymentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service("asaas")
public class AsaasGatewayService implements PaymentGateway {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestTemplate restTemplate;

    @Value("${asaas.api-key}")
    private String apiKey;

    @Value("${asaas.base-url:https://api-sandbox.asaas.com/v3}")
    private String configuredBaseUrl;

    @Value("${asaas.webhook-token:}")
    private String webhookToken;

    public AsaasGatewayService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("access_token", apiKey);
        return headers;
    }

    @Override
    public PaymentResult createPayment(PaymentRequest request) {
        String cpfCnpj = normalizeCpfCnpj(request.payerCpfCnpj());
        if (!"PIX".equalsIgnoreCase(request.paymentMethod()) && !"BOLETO".equalsIgnoreCase(request.paymentMethod())) {
            throw new IllegalArgumentException("Método de pagamento temporariamente indisponível.");
        }
        if (request.externalReference() == null || request.externalReference().isBlank()
                || request.amount() == null || request.amount().signum() <= 0) {
            throw new IllegalArgumentException("Referência ou valor do pagamento inválido.");
        }

        ProviderPayment existing = findPaymentByReference(request.externalReference());
        if (existing != null) {
            verifyExpectedCharge(existing, request.externalReference(), request.amount());
            return enrichPaymentResult(existing);
        }

        String customerId = getOrCreateCustomer(request.payerName(), request.payerEmail(), cpfCnpj);
        String billingType = request.paymentMethod().toUpperCase(Locale.ROOT);
        Map<String, Object> body = new HashMap<>();
        body.put("customer", customerId);
        body.put("billingType", billingType);
        body.put("value", request.amount());
        body.put("dueDate", LocalDate.now().plusDays(1).toString());
        body.put("description", request.description());
        body.put("externalReference", request.externalReference());

        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                    baseUrl() + "/payments",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers()),
                    Map.class);
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().is5xxServerError()) {
                throw new PaymentCreationUncertainException(
                        "Resultado da criação da cobrança é incerto; reconciliação necessária.", exception);
            }
            throw new PaymentGatewayException("O provedor recusou a criação da cobrança.");
        } catch (RestClientException exception) {
            throw new PaymentCreationUncertainException(
                    "Resultado da criação da cobrança é incerto; reconciliação necessária.", exception);
        }

        PaymentResult result;
        try {
            result = parsePaymentResponse(response.getBody());
        } catch (RuntimeException exception) {
            throw new PaymentCreationUncertainException(
                    "Cobrança possivelmente criada; reconciliação necessária.", exception);
        }
        verifyExpectedCharge(result, request.externalReference(), request.amount());
        return result;
    }

    @Override
    public WebhookResult processWebhook(String rawPayload, String signatureHeader) {
        if (webhookToken == null || webhookToken.isBlank()
                || signatureHeader == null
                || !MessageDigest.isEqual(
                        webhookToken.getBytes(StandardCharsets.UTF_8),
                        signatureHeader.getBytes(StandardCharsets.UTF_8))) {
            throw new WebhookAuthenticationException();
        }

        try {
            Map<?, ?> payload = MAPPER.readValue(rawPayload, Map.class);
            Map<?, ?> payment = requireMap(payload.get("payment"), "payment");
            String eventId = requireString(payload.get("id"), "id");
            String externalId = requireString(payment.get("id"), "payment.id");
            String status = requireString(payment.get("status"), "payment.status");
            PaymentStatus.fromAsaas(status);
            return new WebhookResult(
                    eventId,
                    externalId,
                    PaymentStatus.fromAsaas(status).name(),
                    optionalString(payment.get("externalReference")),
                    decimalValue(payment.get("value")),
                    optionalString(payment.get("currency")),
                    optionalString(payment.get("customer")));
        } catch (JsonProcessingException | ClassCastException exception) {
            throw new PaymentGatewayException("Webhook inválido.", exception);
        }
    }

    @Override
    public ProviderPayment findPaymentByReference(String externalReference) {
        Map<?, ?> response;
        try {
            ResponseEntity<Map> entity = restTemplate.exchange(
                    baseUrl() + "/payments?externalReference=" + externalReference,
                    HttpMethod.GET,
                    new HttpEntity<>(headers()),
                    Map.class);
            response = entity.getBody();
        } catch (RestClientException exception) {
            throw new PaymentGatewayException("Não foi possível reconciliar a cobrança no Asaas.", exception);
        }
        if (response == null || !(response.get("data") instanceof List<?> data)) {
            throw new PaymentGatewayException("Resposta inválida ao reconciliar cobrança no Asaas.");
        }
        if (data.isEmpty()) {
            return null;
        }
        if (data.size() != 1 || !(data.get(0) instanceof Map<?, ?> payment)) {
            throw new PaymentGatewayException("Mais de uma cobrança encontrada para a tentativa.");
        }
        ProviderPayment result = parseProviderPayment(payment);
        if (!externalReference.equals(result.externalReference())) {
            throw new PaymentGatewayException("Referência da cobrança não corresponde à tentativa.");
        }
        return result;
    }

    @Override
    public ProviderPayment getPayment(String externalId) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl() + "/payments/" + externalId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers()),
                    Map.class);
            if (response.getBody() == null) {
                throw new PaymentGatewayException("Resposta inválida ao consultar cobrança no Asaas.");
            }
            return parseProviderPayment(response.getBody());
        } catch (PaymentGatewayException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new PaymentGatewayException("Não foi possível consultar a cobrança no Asaas.", exception);
        }
    }

    @Override
    public ProviderPayment cancelPayment(String externalId) {
        try {
            restTemplate.exchange(
                    baseUrl() + "/payments/" + externalId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers()),
                    Map.class);
        } catch (RestClientException exception) {
            throw new PaymentGatewayException("Não foi possível solicitar o cancelamento da cobrança.", exception);
        }
        return getPayment(externalId);
    }

    private String baseUrl() {
        if (configuredBaseUrl == null || configuredBaseUrl.isBlank()) {
            return "https://api-sandbox.asaas.com/v3";
        }
        return configuredBaseUrl.replaceAll("/+$", "");
    }

    private String getOrCreateCustomer(String name, String email, String cpfCnpj) {
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                    baseUrl() + "/customers?cpfCnpj=" + cpfCnpj,
                    HttpMethod.GET,
                    new HttpEntity<>(headers()),
                    Map.class);
        } catch (RestClientException exception) {
            throw new PaymentGatewayException("Falha ao buscar cliente no gateway de pagamento.", exception);
        }
        if (response.getBody() == null || !(response.getBody().get("data") instanceof List<?> data)) {
            throw new PaymentGatewayException("Resposta inválida ao buscar cliente no gateway.");
        }
        if (!data.isEmpty() && data.get(0) instanceof Map<?, ?> customer) {
            return requireString(customer.get("id"), "customer.id");
        }

        Map<String, Object> body = Map.of("name", name, "email", email, "cpfCnpj", cpfCnpj);
        try {
            ResponseEntity<Map> created = restTemplate.exchange(
                    baseUrl() + "/customers",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers()),
                    Map.class);
            return requireString(created.getBody().get("id"), "customer.id");
        } catch (RestClientException exception) {
            throw new PaymentGatewayException("Falha ao criar cliente no gateway de pagamento.", exception);
        }
    }

    private PaymentResult parsePaymentResponse(Map<?, ?> body) {
        if (body == null) {
            throw new PaymentGatewayException("Resposta inválida ao criar cobrança.");
        }
        return enrichPaymentResult(parseProviderPayment(body));
    }

    private PaymentResult enrichPaymentResult(ProviderPayment payment) {
        String pixQrCode = payment.pixQrCode();
        String pixCopyPaste = payment.pixCopyPaste();
        if ("PIX".equals(payment.paymentMethod()) && (pixQrCode == null || pixCopyPaste == null)) {
            try {
                ResponseEntity<Map> response = restTemplate.exchange(
                        baseUrl() + "/payments/" + payment.externalId() + "/pixQrCode",
                        HttpMethod.GET,
                        new HttpEntity<>(headers()),
                        Map.class);
                Map<?, ?> body = response.getBody();
                if (body == null) {
                    throw new PaymentGatewayException("Resposta Pix inválida.");
                }
                pixQrCode = optionalString(body.get("encodedImage"));
                pixCopyPaste = optionalString(body.get("payload"));
            } catch (RestClientException exception) {
                throw new PaymentGatewayException("Não foi possível obter o QR Code Pix.", exception);
            }
        }
        return new PaymentResult(
                payment.externalId(),
                payment.status(),
                payment.paymentMethod(),
                pixQrCode,
                pixCopyPaste,
                payment.boletoUrl(),
                payment.boletoBarcode(),
                payment.amount(),
                payment.externalReference(),
                payment.customerId(),
                payment.currency());
    }

    private ProviderPayment parseProviderPayment(Map<?, ?> body) {
        return new ProviderPayment(
                requireString(body.get("id"), "payment.id"),
                PaymentStatus.fromAsaas(requireString(body.get("status"), "payment.status")).name(),
                optionalString(body.get("externalReference")),
                decimalValue(body.get("value")),
                optionalString(body.get("currency")),
                optionalString(body.get("customer")),
                optionalString(body.get("billingType")),
                null,
                null,
                optionalString(body.get("bankSlipUrl")),
                optionalString(body.get("identificationField") != null
                        ? body.get("identificationField") : body.get("barCode")));
    }

    private void verifyExpectedCharge(PaymentResult result, String reference, BigDecimal amount) {
        verifyExpectedCharge(
                new ProviderPayment(result.externalId(), result.status(), result.externalReference(),
                        result.amount(), result.currency(), result.customerId(), result.paymentMethod(),
                        result.pixQrCode(), result.pixCopyPaste(), result.boletoUrl(), result.boletoBarcode()),
                reference, amount);
    }

    private void verifyExpectedCharge(ProviderPayment result, String reference, BigDecimal amount) {
        if (!reference.equals(result.externalReference())
                || result.amount() == null
                || result.amount().compareTo(amount) != 0
                || (result.currency() != null && !"BRL".equalsIgnoreCase(result.currency()))) {
            throw new PaymentCreationUncertainException(
                    "Cobrança divergente da tentativa registrada; reconciliação necessária.", null);
        }
    }

    private String normalizeCpfCnpj(String cpfCnpj) {
        if (cpfCnpj == null) {
            throw new IllegalArgumentException("CPF/CNPJ é obrigatório.");
        }
        String digits = cpfCnpj.replaceAll("[.\\-/]", "");
        if ((digits.length() != 11 && digits.length() != 14) || digits.chars().distinct().count() == 1) {
            throw new IllegalArgumentException("CPF/CNPJ inválido.");
        }
        return digits;
    }

    private Map<?, ?> requireMap(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new PaymentGatewayException("Campo obrigatório ausente no webhook: " + field);
        }
        return map;
    }

    private String requireString(Object value, String field) {
        String stringValue = optionalString(value);
        if (stringValue == null || stringValue.isBlank()) {
            throw new PaymentGatewayException("Campo obrigatório ausente na resposta do Asaas: " + field);
        }
        return stringValue;
    }

    private String optionalString(Object value) {
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : null;
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return null;
    }
}
