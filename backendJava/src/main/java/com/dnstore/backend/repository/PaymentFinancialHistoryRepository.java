package com.dnstore.backend.repository;

import com.dnstore.backend.model.PaymentFinancialHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentFinancialHistoryRepository extends JpaRepository<PaymentFinancialHistory, UUID> {}
