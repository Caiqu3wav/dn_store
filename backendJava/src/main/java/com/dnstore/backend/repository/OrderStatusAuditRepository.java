package com.dnstore.backend.repository;

import com.dnstore.backend.model.OrderStatusAudit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusAuditRepository extends JpaRepository<OrderStatusAudit, UUID> {

    @EntityGraph(attributePaths = "actor")
    List<OrderStatusAudit> findByOrder_IdOrderByCreatedAtDesc(UUID orderId);
}
