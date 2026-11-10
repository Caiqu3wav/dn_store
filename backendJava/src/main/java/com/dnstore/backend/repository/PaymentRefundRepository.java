package com.dnstore.backend.repository;

import com.dnstore.backend.model.PaymentRefund;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {
    Optional<PaymentRefund> findByPayment_IdAndIdempotencyKey(UUID paymentId, String idempotencyKey);
}
