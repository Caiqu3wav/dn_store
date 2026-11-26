package com.dnstore.backend.repository;

import com.dnstore.backend.model.StockMovement;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Livro append-only: de propósito só expõe leitura e {@code save}. Não estende JpaRepository para
 * que não exista delete/deleteAll disponível a quem injetar o repositório.
 */
public interface StockMovementRepository extends Repository<StockMovement, UUID> {

    StockMovement save(StockMovement movement);

    Optional<StockMovement> findById(UUID id);

    Optional<StockMovement> findByIdempotencyKey(String idempotencyKey);

    boolean existsByVariant_Id(UUID variantId);

    Optional<StockMovement> findFirstByVariant_IdOrderBySeqDesc(UUID variantId);

    @Query("SELECT m FROM StockMovement m JOIN FETCH m.variant v JOIN FETCH v.product " +
           "WHERE m.stockEntry.id = :entryId ORDER BY m.seq ASC")
    List<StockMovement> findByEntryId(@Param("entryId") UUID entryId);

    @Query("SELECT m FROM StockMovement m JOIN FETCH m.variant v JOIN FETCH v.product " +
           "WHERE v.product.id = :productId ORDER BY m.seq DESC")
    List<StockMovement> findByProductIdOrderBySeqDesc(@Param("productId") UUID productId);

    @Query("SELECT m FROM StockMovement m JOIN FETCH m.variant v JOIN FETCH v.product " +
           "WHERE m.variant.id = :variantId ORDER BY m.seq DESC")
    List<StockMovement> findByVariantIdOrderBySeqDesc(@Param("variantId") UUID variantId);
}
