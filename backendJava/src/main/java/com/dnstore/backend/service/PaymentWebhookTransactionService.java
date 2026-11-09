package com.dnstore.backend.service;

import com.dnstore.backend.model.PaymentWebhookEvent;
import com.dnstore.backend.repository.PaymentWebhookEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookTransactionService {

    private final PaymentWebhookEventRepository repository;
    @Value("${payment.webhook.max-attempts:8}") private int maxAttempts;
    @Value("${payment.webhook.lease-minutes:10}") private long leaseMinutes;

    @Transactional
    public PaymentWebhookEvent claimNext() {
        return repository.findNextEligibleForUpdate(LocalDateTime.now()).map(event -> {
            event.setProcessingStatus("PROCESSING");
            event.setAttemptCount(event.getAttemptCount() + 1);
            event.setLastAttemptAt(LocalDateTime.now());
            event.setProcessingStartedAt(LocalDateTime.now());
            event.setErrorMessage(null);
            return repository.save(event);
        }).orElse(null);
    }

    @Transactional
    public void complete(PaymentWebhookEvent event) {
        event.setProcessingStatus("PROCESSED");
        event.setProcessedAt(LocalDateTime.now());
        event.setProcessingStartedAt(null);
        event.setNextAttemptAt(null);
        repository.save(event);
    }

    @Transactional
    public void requireReview(PaymentWebhookEvent event, String message) {
        event.setProcessingStatus("REQUIRES_REVIEW");
        event.setProcessedAt(null);
        event.setProcessingStartedAt(null);
        event.setNextAttemptAt(null);
        event.setErrorMessage(message);
        repository.save(event);
    }

    @Transactional
    public void retryOrFail(PaymentWebhookEvent event, String message) {
        event.setErrorMessage(message);
        event.setProcessingStartedAt(null);
        if (event.getAttemptCount() >= maxAttempts) {
            event.setProcessingStatus("FAILED");
            event.setNextAttemptAt(null);
            log.error("payment.webhook.failed eventId={} attempts={}", event.getEventId(), event.getAttemptCount());
        } else {
            event.setProcessingStatus("RETRY");
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoffSeconds(event.getAttemptCount())));
            log.warn("payment.webhook.retry eventId={} attempt={}", event.getEventId(), event.getAttemptCount());
        }
        repository.save(event);
    }

    @Transactional
    public void recoverExpired() {
        List<PaymentWebhookEvent> expired = repository.findExpiredProcessing(
                LocalDateTime.now().minusMinutes(leaseMinutes));
        expired.forEach(event -> {
            event.setProcessingStatus("RETRY");
            event.setProcessingStartedAt(null);
            event.setNextAttemptAt(LocalDateTime.now());
            event.setErrorMessage("Lease de processamento expirado.");
            repository.save(event);
            log.warn("payment.webhook.recovered eventId={}", event.getEventId());
        });
    }

    private long backoffSeconds(int attempt) {
        return switch (Math.min(attempt, 4)) {
            case 1 -> 60;
            case 2 -> 300;
            case 3 -> 900;
            default -> 3600;
        };
    }
}
