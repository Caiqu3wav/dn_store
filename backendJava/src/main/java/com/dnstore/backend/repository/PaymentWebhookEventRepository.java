package com.dnstore.backend.repository;

import com.dnstore.backend.model.PaymentWebhookEvent;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM PaymentWebhookEvent e WHERE e.eventId = :eventId")
    Optional<PaymentWebhookEvent> findByEventIdForUpdate(@Param("eventId") String eventId);
}
