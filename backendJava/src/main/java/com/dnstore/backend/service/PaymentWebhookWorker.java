
package com.dnstore.backend.service;

import com.dnstore.backend.model.PaymentWebhookEvent;
import com.dnstore.backend.service.payment.PaymentGateway;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

@Slf4j
@Service
@Profile("!readonly")
@RequiredArgsConstructor
public class PaymentWebhookWorker {

    private static final Set<String> KNOWN_EVENTS = Set.of(
            "PAYMENT_CREATED",
            "PAYMENT_UPDATED",
            "PAYMENT_CONFIRMED",
            "PAYMENT_RECEIVED",
            "PAYMENT_OVERDUE",
            "PAYMENT_DELETED",
            "PAYMENT_RESTORED",
            "PAYMENT_REFUNDED",
            "PAYMENT_REFUND_REQUESTED",
            "PAYMENT_PARTIALLY_REFUNDED",
            "PAYMENT_CHARGEBACK_REQUESTED",
            "PAYMENT_CHARGEBACK_DISPUTE",
            "PAYMENT_AWAITING_CHARGEBACK_REVERSAL",
            "PAYMENT_DUNNING_REQUESTED",
            "PAYMENT_DUNNING_RECEIVED",
            "PAYMENT_AUTHORIZED",
            "PAYMENT_APPROVED_BY_RISK_ANALYSIS",
            "PAYMENT_REPROVED_BY_RISK_ANALYSIS");

    private final PaymentWebhookTransactionService transactionService;
    private final PaymentGateway gateway;
    private final PaymentAttemptStateService paymentState;

    @Scheduled(fixedDelayString = "${payment.webhook.scan-ms:1000}")
    public void scheduledProcess() {
        transactionService.recoverExpired();

        PaymentWebhookEvent event = transactionService.claimNext();

        if (event != null) {
            process(event);
        }
    }

    void process(PaymentWebhookEvent event) {
        try {
            if (!isKnownEvent(event.getEventType())) {
                transactionService.requireReview(
                        event,
                        "Evento Asaas desconhecido; revisão manual necessária.");

                log.info(
                        "payment.webhook.unknown eventId={} eventType={}",
                        event.getEventId(),
                        event.getEventType());

                return;
            }

            PaymentGateway.ProviderPayment canonical = gateway.getPayment(event.getExternalPaymentId());

            PaymentGateway.WebhookResult webhookResult = new PaymentGateway.WebhookResult(
                    event.getEventId(),
                    event.getExternalPaymentId(),
                    event.getEventType(),
                    canonical.status(),
                    canonical.externalReference(),
                    canonical.amount(),
                    canonical.currency(),
                    canonical.customerId());

            paymentState.applyPersistedWebhook(
                    webhookResult,
                    canonical);

            transactionService.complete(event);

            log.info(
                    "payment.webhook.processed eventId={}",
                    event.getEventId());

        } catch (Exception exception) {
            transactionService.retryOrFail(
                    event,
                    sanitize(exception));
        }
    }

    private boolean isKnownEvent(String eventType) {
        return eventType != null && KNOWN_EVENTS.contains(eventType);
    }

    private String sanitize(Exception exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        String sanitized = message.replaceAll(
                "(?i)(access_token|authorization|token|api[-_]?key)\\s*[:=]\\s*[^,; ]+",
                "$1=[REDACTED]");

        return sanitized.substring(
                0,
                Math.min(500, sanitized.length()));
    }
}
