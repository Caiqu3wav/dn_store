package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.dnstore.backend.model.PaymentWebhookEvent;
import com.dnstore.backend.repository.PaymentWebhookEventRepository;
import com.dnstore.backend.service.payment.PaymentGateway;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookIngestionServiceTest {

    @Mock PaymentGateway gateway;
    @Mock PaymentWebhookEventRepository repository;
    @InjectMocks PaymentWebhookIngestionService service;

    @Test
    void validWebhookIsPersistedWithoutCallingProviderHttpMethods() {
        when(gateway.processWebhook("payload", "token")).thenReturn(event("evt-1"));

        assertThat(service.ingest("payload", "token")).isTrue();
        verify(repository).saveAndFlush(any(PaymentWebhookEvent.class));
        verify(gateway).processWebhook("payload", "token");
        verify(gateway, never()).getPayment(anyString());
    }

    @Test
    void duplicateEventIsAcknowledgedWithoutSecondInsert() {
        when(gateway.processWebhook("payload", "token")).thenReturn(event("evt-1"));
        when(repository.saveAndFlush(any(PaymentWebhookEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(service.ingest("payload", "token")).isFalse();
    }

    @Test
    void oversizedPayloadIsRejectedBeforeParsing() {
        assertThatThrownBy(() -> service.ingest("x".repeat(PaymentWebhookIngestionService.MAX_PAYLOAD_BYTES + 1), "token"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway, repository);
    }

    private PaymentGateway.WebhookResult event(String id) {
        return new PaymentGateway.WebhookResult(id, "charge-1", "PAYMENT_CONFIRMED", "PAID",
                "order:key", BigDecimal.TEN, "BRL", "customer-1");
    }
}
