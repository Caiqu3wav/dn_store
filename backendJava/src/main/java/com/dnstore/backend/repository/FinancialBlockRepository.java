package com.dnstore.backend.repository;

import com.dnstore.backend.model.FinancialBlock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialBlockRepository extends JpaRepository<FinancialBlock, UUID> {
    List<FinancialBlock> findByOrder_IdAndActiveTrue(UUID orderId);
    boolean existsByOrder_IdAndActiveTrue(UUID orderId);
}
