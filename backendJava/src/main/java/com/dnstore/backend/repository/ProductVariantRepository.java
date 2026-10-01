package com.dnstore.backend.repository;

import com.dnstore.backend.model.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    List<ProductVariant> findByProductId(UUID productId);

    Optional<ProductVariant> findByProductIdAndSize(UUID productId, String size);

    List<ProductVariant> findBySku(String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.id = :id")
    Optional<ProductVariant> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.id IN :ids ORDER BY pv.product.id, pv.id")
    List<ProductVariant> findAllByIdInForUpdate(@Param("ids") Collection<UUID> ids);
}