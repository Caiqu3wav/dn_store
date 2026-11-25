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
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.id IN :ids ORDER BY pv.id")
    List<ProductVariant> findAllByIdInForUpdate(@Param("ids") Collection<UUID> ids);

    @Query(value = """
            SELECT pv FROM ProductVariant pv JOIN FETCH pv.product p
            WHERE (:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(pv.sku) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY p.name, pv.size
            """,
            countQuery = """
            SELECT COUNT(pv) FROM ProductVariant pv JOIN pv.product p
            WHERE (:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(pv.sku) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    org.springframework.data.domain.Page<ProductVariant> searchForStock(
            @Param("q") String q, org.springframework.data.domain.Pageable pageable);
}
