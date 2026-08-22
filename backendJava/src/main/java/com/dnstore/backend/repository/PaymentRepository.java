package com.dnstore.backend.repository;

import com.dnstore.backend.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByExternalId(String externalId);
    Optional<Payment> findByOrder_Id(UUID orderId);
}
