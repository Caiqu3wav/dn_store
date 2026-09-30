package com.dnstore.backend.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AsaasGatewayServiceTest {

    private RestTemplate restTemplate;
    private AsaasGatewayService gateway;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        gateway = new AsaasGatewayService(restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(gateway, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(gateway, "webhookToken", "webhook-secret");
    }

    @Test
    void createPayment_shouldReuseCustomerAndCreatePixWithValidCpf() {
        when(restTemplate.exchange(contains("/payments?externalReference="), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of())));
        when(restTemplate.exchange(contains("/customers?cpfCnpj=52998224725"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of(Map.of("id", "customer-1")))));
        when(restTemplate.exchange(contains("/payments"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "id", "payment-1",
                        "status", "PENDING",
                        "billingType", "PIX")));
        when(restTemplate.exchange(contains("/pixQrCode"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("encodedImage", "encoded", "payload", "pix-payload")));

        PaymentGateway.PaymentResult result = gateway.createPayment(new PaymentGateway.PaymentRequest(
                UUID.randomUUID(), new BigDecimal("89.90"), "Buyer", "buyer@example.com",
                "529.982.247-25", "Order", "PIX", null, null));

        assertThat(result.externalId()).isEqualTo("payment-1");
        assertThat(result.paymentMethod()).isEqualTo("PIX");
        assertThat(result.pixQrCode()).isEqualTo("encoded");
        assertThat(result.pixCopyPaste()).isEqualTo("pix-payload");
        verify(restTemplate, never()).exchange(contains("/customers"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void createPayment_withInvalidCpf_shouldNotCallAsaas() {
        assertThatThrownBy(() -> gateway.createPayment(new PaymentGateway.PaymentRequest(
                UUID.randomUUID(), BigDecimal.TEN, "Buyer", "buyer@example.com",
                "11111111111", "Order", "PIX", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CPF/CNPJ");

        verifyNoInteractions(restTemplate);
    }

    @Test
    void createPayment_shouldRecoverExistingChargeByOrderReference() {
        UUID orderId = UUID.randomUUID();
        when(restTemplate.exchange(contains("externalReference=" + orderId), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of(Map.of(
                        "id", "existing-payment",
                        "status", "PENDING",
                        "billingType", "BOLETO",
                        "bankSlipUrl", "https://example.test/boleto",
                        "identificationField", "barcode")))));

        PaymentGateway.PaymentResult result = gateway.createPayment(new PaymentGateway.PaymentRequest(
                orderId, BigDecimal.TEN, "Buyer", "buyer@example.com", "52998224725",
                "Order", "BOLETO", null, null));

        assertThat(result.externalId()).isEqualTo("existing-payment");
        assertThat(result.paymentMethod()).isEqualTo("BOLETO");
        assertThat(result.boletoUrl()).isEqualTo("https://example.test/boleto");
        verify(restTemplate, never()).exchange(contains("/payments"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class));
        verify(restTemplate, never()).exchange(contains("/customers"), any(HttpMethod.class),
                any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void createPayment_whenCustomerSearchFails_shouldNotCreateDuplicateCustomer() {
        when(restTemplate.exchange(contains("/payments?externalReference="), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of())));
        when(restTemplate.exchange(contains("/customers?cpfCnpj="), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("network failure"));

        assertThatThrownBy(() -> gateway.createPayment(new PaymentGateway.PaymentRequest(
                UUID.randomUUID(), BigDecimal.TEN, "Buyer", "buyer@example.com",
                "52998224725", "Order", "PIX", null, null)))
                .isInstanceOf(PaymentGatewayException.class);

        verify(restTemplate, never()).exchange(contains("/customers"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class));
        verify(restTemplate, never()).exchange(contains("/payments"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void processWebhook_withUnknownAsaasStatus_shouldRejectEvent() {
        assertThatThrownBy(() -> gateway.processWebhook(
                "{\"payment\":{\"id\":\"payment-1\",\"status\":\"UNKNOWN_STATUS\"}}",
                "webhook-secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void processWebhook_withWrongToken_shouldRejectRequest() {
        assertThatThrownBy(() -> gateway.processWebhook(
                "{\"payment\":{\"id\":\"payment-1\",\"status\":\"CONFIRMED\"}}",
                "wrong-token"))
                .isInstanceOf(WebhookAuthenticationException.class);
    }
}
