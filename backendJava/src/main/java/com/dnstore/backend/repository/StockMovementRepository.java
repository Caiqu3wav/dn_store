package com.dnstore.backend.repository;

import com.dnstore.backend.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    @Query("SELECT m FROM StockMovement m JOIN FETCH m.variant v JOIN FETCH v.product " +
           "WHERE v.product.id = :productId ORDER BY m.createdAt DESC")
    List<StockMovement> findByProductIdOrderByCreatedAtDesc(@Param("productId") UUID productId);

    @Query("SELECT m FROM StockMovement m JOIN FETCH m.variant v JOIN FETCH v.product " +
           "WHERE m.variant.id = :variantId ORDER BY m.createdAt DESC")
    List<StockMovement> findByVariantIdOrderByCreatedAtDesc(@Param("variantId") UUID variantId);
}
