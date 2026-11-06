package com.dnstore.backend.repository;

import com.dnstore.backend.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByExternalId(String externalId);

    @EntityGraph(attributePaths = "order")
    List<Payment> findByOrder_IdOrderByAttemptNumberDesc(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId ORDER BY p.attemptNumber DESC")
    List<Payment> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") UUID paymentId);

    Optional<Payment> findByOrder_IdAndIdempotencyKey(UUID orderId, String idempotencyKey);

    List<Payment> findByOrder_IdAndStatusIn(UUID orderId, List<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.externalId = :externalId")
    Optional<Payment> findByExternalIdForUpdate(@Param("externalId") String externalId);
}
