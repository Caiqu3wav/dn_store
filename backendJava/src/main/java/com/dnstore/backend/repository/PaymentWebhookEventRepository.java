package com.dnstore.backend.repository;

import com.dnstore.backend.model.PaymentWebhookEvent;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM PaymentWebhookEvent e WHERE e.eventId = :eventId")
    Optional<PaymentWebhookEvent> findByEventIdForUpdate(@Param("eventId") String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM PaymentWebhookEvent e WHERE e.processingStatus IN ('PENDING', 'RETRY') "
            + "AND (e.nextAttemptAt IS NULL OR e.nextAttemptAt <= :now) ORDER BY e.receivedAt")
    Optional<PaymentWebhookEvent> findNextEligibleForUpdate(@Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM PaymentWebhookEvent e WHERE e.processingStatus = 'PROCESSING' "
            + "AND e.processingStartedAt < :leaseCutoff")
    java.util.List<PaymentWebhookEvent> findExpiredProcessing(@Param("leaseCutoff") LocalDateTime leaseCutoff);
}
