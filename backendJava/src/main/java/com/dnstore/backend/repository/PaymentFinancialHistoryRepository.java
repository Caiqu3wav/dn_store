package com.dnstore.backend.repository;

import com.dnstore.backend.model.PaymentFinancialHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PaymentFinancialHistoryRepository extends JpaRepository<PaymentFinancialHistory, UUID> {

    @Query("SELECT h FROM PaymentFinancialHistory h WHERE h.order.id = :orderId ORDER BY h.processedAt DESC")
    List<PaymentFinancialHistory> findByOrderIdOrderByProcessedAtDesc(@Param("orderId") UUID orderId);
}
