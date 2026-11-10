package com.dnstore.backend.repository;

import com.dnstore.backend.model.ShippingQuoteOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ShippingQuoteOptionRepository extends JpaRepository<ShippingQuoteOptionEntity, UUID> {

    Optional<ShippingQuoteOptionEntity> findByIdAndUser_IdAndExpiresAtAfter(
            UUID id, UUID userId, LocalDateTime now);

    @Modifying
    @Transactional
    @Query("""
            DELETE FROM ShippingQuoteOptionEntity q
            WHERE q.expiresAt <= :now
              AND NOT EXISTS (
                  SELECT o.id FROM Order o
                  WHERE o.shippingQuoteOptionId = q.id
              )
            """)
    int deleteExpiredUnconsumed(@Param("now") LocalDateTime now);
}
