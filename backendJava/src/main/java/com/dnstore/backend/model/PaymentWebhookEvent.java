package com.dnstore.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment_webhook_events")
@Getter
@Setter
@NoArgsConstructor
public class PaymentWebhookEvent {

    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "external_payment_id", nullable = false, length = 100)
    private String externalPaymentId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", columnDefinition = "LONGTEXT")
    private String payload;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processing_status", nullable = false, length = 20)
    private String processingStatus;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public PaymentWebhookEvent(String eventId, String externalPaymentId) {
        this.eventId = eventId;
        this.externalPaymentId = externalPaymentId;
        this.eventType = "LEGACY";
        this.processingStatus = "PROCESSED";
    }

    @PrePersist
    protected void onCreate() {
        if (processedAt == null) {
            processedAt = "PROCESSED".equals(processingStatus) ? LocalDateTime.now() : null;
        }
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
        if (processingStatus == null) {
            processingStatus = "PENDING";
        }
    }
}
