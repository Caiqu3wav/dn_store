package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.dnstore.backend.model.PaymentWebhookEvent;
import com.dnstore.backend.repository.PaymentWebhookEventRepository;
import com.dnstore.backend.service.payment.PaymentGateway;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookWorkerTest {

    @Mock PaymentWebhookTransactionService transactionService;
    @Mock PaymentGateway gateway;
    @Mock PaymentAttemptStateService paymentState;
    @InjectMocks PaymentWebhookWorker worker;

    @BeforeEach
    void setUp() {
    }

    @Test
    void processingFailureSchedulesRetryAndDoesNotLoseEvent() {
        PaymentWebhookEvent event = event("PAYMENT_CONFIRMED");
        event.setAttemptCount(1);
        when(gateway.getPayment("charge-1")).thenThrow(new RuntimeException("provider unavailable"));

        worker.process(event);

        verify(transactionService).retryOrFail(eq(event), anyString());
    }

    @Test
    void secondFailureMovesEventToFailed() {
        PaymentWebhookEvent event = event("PAYMENT_CONFIRMED");
        event.setAttemptCount(2);
        when(gateway.getPayment("charge-1")).thenThrow(new RuntimeException("provider unavailable"));

        worker.process(event);

        verify(transactionService).retryOrFail(eq(event), anyString());
    }

    @Test
    void unknownEventRequiresReviewWithoutCallingProvider() {
        PaymentWebhookEvent event = event("PAYMENT_NEW_UNDOCUMENTED_EVENT");

        worker.process(event);

        verify(transactionService).requireReview(eq(event), contains("desconhecido"));
        verifyNoInteractions(gateway, paymentState);
    }

    @Test
    void expiredLeaseIsReturnedToRetry() {
        PaymentWebhookEvent event = event("PAYMENT_CONFIRMED");
        event.setProcessingStatus("PROCESSING");
        event.setProcessingStartedAt(LocalDateTime.now().minusMinutes(20));
        worker.scheduledProcess();

        verify(transactionService).recoverExpired();
    }

    private PaymentWebhookEvent event(String type) {
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setEventId("evt-1");
        event.setExternalPaymentId("charge-1");
        event.setEventType(type);
        event.setProcessingStatus("PROCESSING");
        return event;
    }
}
