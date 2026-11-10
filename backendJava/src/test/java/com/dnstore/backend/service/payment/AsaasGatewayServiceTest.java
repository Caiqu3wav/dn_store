package com.dnstore.backend.service.payment;

import com.dnstore.backend.config.AsaasProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

class AsaasGatewayServiceTest {

    private RestTemplate restTemplate;
    private AsaasGatewayService gateway;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        AsaasProperties properties = new AsaasProperties();
        properties.setApiKey("test-api-key");
        properties.setBaseUrl(AsaasProperties.SANDBOX_URL);
        properties.setWebhookToken("webhook-secret");
        properties.setUserAgent("DN-Store Test/1.0");
        gateway = new AsaasGatewayService(restTemplate, properties);
    }

    @Test
    void createPayment_shouldCreatePixWithPersistedReferenceAndVerifyAmount() {
        UUID orderId = UUID.randomUUID();
        String reference = orderId + ":attempt-1";
        when(restTemplate.exchange(contains("/payments?externalReference="), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of())));
        when(restTemplate.exchange(contains("/customers?cpfCnpj=52998224725"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of(Map.of("id", "customer-1")))));
        when(restTemplate.exchange(eq("https://api-sandbox.asaas.com/v3/payments"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "id", "payment-1",
                        "status", "PENDING",
                        "billingType", "PIX",
                        "customer", "customer-1",
                        "externalReference", reference,
                        "value", new BigDecimal("89.90"))));
        when(restTemplate.exchange(contains("/pixQrCode"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("encodedImage", "encoded", "payload", "pix-payload")));

        PaymentGateway.PaymentResult result = gateway.createPayment(request(orderId, reference, "PIX"));

        assertThat(result.externalId()).isEqualTo("payment-1");
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.externalReference()).isEqualTo(reference);
        assertThat(result.amount()).isEqualByComparingTo("89.90");
        assertThat(result.customerId()).isEqualTo("customer-1");
        assertThat(result.pixQrCode()).isEqualTo("encoded");
        assertThat(result.pixCopyPaste()).isEqualTo("pix-payload");
    }

    @Test
    void createPayment_whenPreflightLookupFails_shouldNotPostCharge() {
        when(restTemplate.exchange(contains("/payments?externalReference="), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("network failure"));

        assertThatThrownBy(() -> gateway.createPayment(request(UUID.randomUUID(), "order:attempt", "PIX")))
                .isInstanceOf(PaymentGatewayException.class);

        verify(restTemplate, never()).exchange(contains("/payments"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void createPayment_whenPostTimesOut_shouldRequireReconciliation() {
        when(restTemplate.exchange(contains("/payments?externalReference="), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of())));
        when(restTemplate.exchange(contains("/customers?cpfCnpj="), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of(Map.of("id", "customer-1")))));
        when(restTemplate.exchange(contains("/payments"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> gateway.createPayment(request(UUID.randomUUID(), "order:attempt", "PIX")))
                .isInstanceOf(PaymentCreationUncertainException.class)
                .hasMessageContaining("reconciliação");
    }

    @Test
    void createPayment_shouldReuseExistingChargeForSameAttempt() {
        UUID orderId = UUID.randomUUID();
        String reference = orderId + ":attempt-1";
        when(restTemplate.exchange(contains("externalReference=" + orderId + "%3Aattempt-1"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of(Map.of(
                        "id", "existing-payment",
                        "status", "PENDING",
                        "billingType", "BOLETO",
                        "customer", "customer-1",
                        "externalReference", reference,
                        "value", BigDecimal.TEN,
                        "bankSlipUrl", "https://example.test/boleto",
                        "identificationField", "barcode")))));

        PaymentGateway.PaymentResult result = gateway.createPayment(
                request(orderId, reference, "BOLETO", BigDecimal.TEN));

        assertThat(result.externalId()).isEqualTo("existing-payment");
        assertThat(result.externalReference()).isEqualTo(reference);
        assertThat(result.boletoUrl()).isEqualTo("https://example.test/boleto");
        verify(restTemplate, never()).exchange(contains("/payments"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class));
        verify(restTemplate, never()).exchange(contains("/customers"), any(HttpMethod.class),
                any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void createPayment_whenExistingChargeAmountDiffers_shouldNotCreateAnotherCharge() {
        UUID orderId = UUID.randomUUID();
        String reference = orderId + ":attempt-1";
        when(restTemplate.exchange(contains("externalReference=" + orderId + "%3Aattempt-1"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of(Map.of(
                        "id", "existing-payment",
                        "status", "PENDING",
                        "billingType", "PIX",
                        "customer", "customer-1",
                        "externalReference", reference,
                        "value", BigDecimal.ONE)))));

        assertThatThrownBy(() -> gateway.createPayment(request(orderId, reference, "PIX")))
                .isInstanceOf(PaymentCreationUncertainException.class);
        verify(restTemplate, never()).exchange(contains("/payments"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void createPayment_withInvalidCpf_shouldNotCallAsaas() {
        PaymentGateway.PaymentRequest request = new PaymentGateway.PaymentRequest(
                UUID.randomUUID(), "order:attempt", BigDecimal.TEN, "Buyer", "buyer@example.com",
                "11111111111", "Order", "PIX", null, null);
        assertThatThrownBy(() -> gateway.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void createPayment_withCard_shouldBeRejected() {
        assertThatThrownBy(() -> gateway.createPayment(
                request(UUID.randomUUID(), "order:attempt", "CREDIT_CARD")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void processWebhook_shouldRequireEventAndPaymentIdentifiersAndReconcileFields() {
        PaymentGateway.WebhookResult result = gateway.processWebhook(
                """
                {"id":"evt-1","event":"PAYMENT_CONFIRMED","payment":{
                  "id":"payment-1","status":"CONFIRMED","externalReference":"order:attempt",
                  "value":10.00,"currency":"BRL","customer":"customer-1"}}
                """,
                "webhook-secret");

        assertThat(result.eventId()).isEqualTo("evt-1");
        assertThat(result.externalId()).isEqualTo("payment-1");
        assertThat(result.normalizedStatus()).isEqualTo("PAID");
        assertThat(result.externalReference()).isEqualTo("order:attempt");
        assertThat(result.amount()).isEqualByComparingTo("10.00");
        assertThat(result.currency()).isEqualTo("BRL");
        assertThat(result.customerId()).isEqualTo("customer-1");
    }

    @Test
    void processWebhook_withMissingEventId_shouldRejectPayload() {
        assertThatThrownBy(() -> gateway.processWebhook(
                "{\"payment\":{\"id\":\"payment-1\",\"status\":\"CONFIRMED\"}}",
                "webhook-secret"))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void processWebhook_withUnknownStatus_shouldRejectPayload() {
        assertThatThrownBy(() -> gateway.processWebhook(
                "{\"id\":\"evt-1\",\"event\":\"PAYMENT_UPDATED\",\"payment\":{\"id\":\"payment-1\",\"status\":\"UNKNOWN\"}}",
                "webhook-secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void processWebhook_withWrongToken_shouldRejectRequest() {
        assertThatThrownBy(() -> gateway.processWebhook(
                "{\"id\":\"evt-1\",\"payment\":{\"id\":\"payment-1\",\"status\":\"CONFIRMED\"}}",
                "wrong-token"))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void findPaymentByReference_whenProviderLookupFails_shouldThrowInsteadOfReturningNoPayment() {
        when(restTemplate.exchange(contains("/payments?externalReference="), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("network failure"));

        assertThatThrownBy(() -> gateway.findPaymentByReference("order:attempt"))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void createPayment_shouldEncodeReservedCharactersInExternalReference() {
        String reference = "order:a&attempt?1=2";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenAnswer(invocation -> {
                    String uri = invocation.getArgument(0);
                    assertThat(uri).contains("externalReference=order%3Aa%26attempt%3F1%3D2");
                    return ResponseEntity.ok(Map.of("data", List.of()));
                });
        when(restTemplate.exchange(contains("/customers?cpfCnpj="), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of(Map.of("id", "customer-1")))));
        when(restTemplate.exchange(eq("https://api-sandbox.asaas.com/v3/payments"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("stop before provider call"));

        assertThatThrownBy(() -> gateway.createPayment(request(UUID.randomUUID(), reference, "PIX")))
                .isInstanceOf(PaymentCreationUncertainException.class);
        verify(restTemplate).exchange(org.mockito.ArgumentMatchers.<String>argThat(
                        uri -> uri.contains("externalReference=order%3Aa%26attempt%3F1%3D2")),
                eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void allAsaasRequests_shouldIncludeConfiguredUserAgent() {
        when(restTemplate.exchange(contains("/payments?externalReference="), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of())));

        gateway.findPaymentByReference("order:attempt");

        var captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(contains("/payments?externalReference="), eq(HttpMethod.GET),
                captor.capture(), eq(Map.class));
        assertThat(captor.getValue().getHeaders().getFirst(HttpHeaders.USER_AGENT))
                .isEqualTo("DN-Store Test/1.0");
        assertThat(captor.getValue().getHeaders().getFirst("access_token")).isEqualTo("test-api-key");
    }

    private PaymentGateway.PaymentRequest request(UUID orderId, String reference, String method) {
        return request(orderId, reference, method, new BigDecimal("89.90"));
    }

    private PaymentGateway.PaymentRequest request(
            UUID orderId,
            String reference,
            String method,
            BigDecimal amount) {
        return new PaymentGateway.PaymentRequest(
                orderId, reference, amount, "Buyer", "buyer@example.com",
                "529.982.247-25", "Order", method, null, null);
    }
}
