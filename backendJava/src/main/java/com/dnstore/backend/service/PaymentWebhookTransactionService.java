
package com.dnstore.backend.service;

import com.dnstore.backend.model.PaymentWebhookEvent;
import com.dnstore.backend.repository.PaymentWebhookEventRepository;
import jakarta.annotation.PostConstruct;
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

    @Value("${payment.webhook.max-attempts:8}")
    private int maxAttempts;

    @Value("${payment.webhook.lease-minutes:10}")
    private long leaseMinutes;

    @PostConstruct
    void validateConfiguration() {
        if (maxAttempts <= 0) {
            throw new IllegalStateException(
                    "payment.webhook.max-attempts deve ser maior que zero.");
        }

        if (leaseMinutes <= 0) {
            throw new IllegalStateException(
                    "payment.webhook.lease-minutes deve ser maior que zero.");
        }
    }

    @Transactional
    public PaymentWebhookEvent claimNext() {
        LocalDateTime now = LocalDateTime.now();

        return repository.findNextEligibleForUpdate(now)
                .map(event -> {
                    event.setProcessingStatus("PROCESSING");
                    event.setAttemptCount(event.getAttemptCount() + 1);
                    event.setLastAttemptAt(now);
                    event.setProcessingStartedAt(now);
                    event.setProcessedAt(null);
                    event.setErrorMessage(null);

                    return repository.saveAndFlush(event);
                })
                .orElse(null);
    }

    @Transactional
    public void complete(PaymentWebhookEvent event) {
        int updated = repository.completeClaimed(
                event.getEventId(),
                event.getAttemptCount(),
                LocalDateTime.now());

        if (updated == 0) {
            log.warn(
                    "payment.webhook.stale_completion eventId={} attempt={}",
                    event.getEventId(),
                    event.getAttemptCount());
        }
    }

    @Transactional
    public void requireReview(PaymentWebhookEvent event, String message) {
        int updated = repository.requireReviewClaimed(
                event.getEventId(),
                event.getAttemptCount(),
                message);

        if (updated == 0) {
            log.warn(
                    "payment.webhook.stale_review eventId={} attempt={}",
                    event.getEventId(),
                    event.getAttemptCount());
        }
    }

    @Transactional
    public void retryOrFail(PaymentWebhookEvent event, String message) {
        int attempt = event.getAttemptCount();

        if (attempt >= maxAttempts) {
            int updated = repository.failClaimed(
                    event.getEventId(),
                    attempt,
                    message);

            if (updated > 0) {
                log.error(
                        "payment.webhook.failed eventId={} attempts={}",
                        event.getEventId(),
                        attempt);
            } else {
                log.warn(
                        "payment.webhook.stale_failure eventId={} attempt={}",
                        event.getEventId(),
                        attempt);
            }

            return;
        }

        LocalDateTime nextAttemptAt = LocalDateTime.now()
                .plusSeconds(backoffSeconds(attempt));

        int updated = repository.retryClaimed(
                event.getEventId(),
                attempt,
                nextAttemptAt,
                message);

        if (updated > 0) {
            log.warn(
                    "payment.webhook.retry eventId={} attempt={} nextAttemptAt={}",
                    event.getEventId(),
                    attempt,
                    nextAttemptAt);
        } else {
            log.warn(
                    "payment.webhook.stale_retry eventId={} attempt={}",
                    event.getEventId(),
                    attempt);
        }
    }

    @Transactional
    public void recoverExpired() {
        LocalDateTime now = LocalDateTime.now();

        List<PaymentWebhookEvent> expired = repository.findExpiredProcessing(
                now.minusMinutes(leaseMinutes));

        for (PaymentWebhookEvent event : expired) {
            event.setProcessingStatus("RETRY");
            event.setProcessingStartedAt(null);
            event.setNextAttemptAt(now);
            event.setErrorMessage("Lease de processamento expirado.");

            log.warn(
                    "payment.webhook.recovered eventId={} attempt={}",
                    event.getEventId(),
                    event.getAttemptCount());
        }

        repository.saveAll(expired);
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
