
package com.dnstore.backend.repository;

import com.dnstore.backend.model.PaymentWebhookEvent;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentWebhookEventRepository
                extends JpaRepository<PaymentWebhookEvent, String> {

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        SELECT e
                        FROM PaymentWebhookEvent e
                        WHERE e.eventId = :eventId
                        """)
        Optional<PaymentWebhookEvent> findByEventIdForUpdate(
                        @Param("eventId") String eventId);

        /*
         * MySQL 8+: seleciona somente um evento elegível.
         * SKIP LOCKED permite que outro worker pule registros
         * que já estejam sendo reivindicados por outro processo.
         *
         * A consulta é nativa porque precisamos de LIMIT 1
         * e SKIP LOCKED.
         */
        @Query(value = """
                        SELECT *
                        FROM payment_webhook_events
                        WHERE processing_status IN ('PENDING', 'RETRY')
                          AND (
                              next_attempt_at IS NULL
                              OR next_attempt_at <= :now
                          )
                        ORDER BY received_at ASC
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                        """, nativeQuery = true)
        Optional<PaymentWebhookEvent> findNextEligibleForUpdate(
                        @Param("now") LocalDateTime now);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        SELECT e
                        FROM PaymentWebhookEvent e
                        WHERE e.processingStatus = 'PROCESSING'
                          AND e.processingStartedAt < :leaseCutoff
                        """)
        List<PaymentWebhookEvent> findExpiredProcessing(
                        @Param("leaseCutoff") LocalDateTime leaseCutoff);

        /*
         * As atualizações abaixo são condicionais:
         * só podem modificar um evento que continue PROCESSING
         * e cuja tentativa ainda seja a mesma reivindicada
         * pelo worker.
         */

        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("""
                        UPDATE PaymentWebhookEvent e
                        SET e.processingStatus = 'PROCESSED',
                            e.processedAt = :now,
                            e.processingStartedAt = NULL,
                            e.nextAttemptAt = NULL,
                            e.errorMessage = NULL
                        WHERE e.eventId = :eventId
                          AND e.processingStatus = 'PROCESSING'
                          AND e.attemptCount = :attemptCount
                        """)
        int completeClaimed(
                        @Param("eventId") String eventId,
                        @Param("attemptCount") int attemptCount,
                        @Param("now") LocalDateTime now);

        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("""
                        UPDATE PaymentWebhookEvent e
                        SET e.processingStatus = 'REQUIRES_REVIEW',
                            e.processedAt = NULL,
                            e.processingStartedAt = NULL,
                            e.nextAttemptAt = NULL,
                            e.errorMessage = :message
                        WHERE e.eventId = :eventId
                          AND e.processingStatus = 'PROCESSING'
                          AND e.attemptCount = :attemptCount
                        """)
        int requireReviewClaimed(
                        @Param("eventId") String eventId,
                        @Param("attemptCount") int attemptCount,
                        @Param("message") String message);

        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("""
                        UPDATE PaymentWebhookEvent e
                        SET e.processingStatus = 'RETRY',
                            e.processedAt = NULL,
                            e.processingStartedAt = NULL,
                            e.nextAttemptAt = :nextAttemptAt,
                            e.errorMessage = :message
                        WHERE e.eventId = :eventId
                          AND e.processingStatus = 'PROCESSING'
                          AND e.attemptCount = :attemptCount
                        """)
        int retryClaimed(
                        @Param("eventId") String eventId,
                        @Param("attemptCount") int attemptCount,
                        @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                        @Param("message") String message);

        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("""
                        UPDATE PaymentWebhookEvent e
                        SET e.processingStatus = 'FAILED',
                            e.processedAt = NULL,
                            e.processingStartedAt = NULL,
                            e.nextAttemptAt = NULL,
                            e.errorMessage = :message
                        WHERE e.eventId = :eventId
                          AND e.processingStatus = 'PROCESSING'
                          AND e.attemptCount = :attemptCount
                        """)
        int failClaimed(
                        @Param("eventId") String eventId,
                        @Param("attemptCount") int attemptCount,
                        @Param("message") String message);
}
