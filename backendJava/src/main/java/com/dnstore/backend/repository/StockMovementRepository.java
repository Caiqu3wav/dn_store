package com.dnstore.backend.repository;

import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.enums.StockReferenceType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Livro append-only: este repositório expõe só leitura e inserção. Não declara (e um teste proíbe) métodos de
 * UPDATE/DELETE.
 */
public interface StockMovementRepository extends Repository<StockMovement, UUID>,
        JpaSpecificationExecutor<StockMovement> {

    StockMovement save(StockMovement movement);

    java.util.Optional<StockMovement> findByIdempotencyKey(String idempotencyKey);

    List<StockMovement> findByReferenceTypeAndReferenceIdOrderBySeqAsc(
            StockReferenceType referenceType, String referenceId);

    /** Variantes cujo estoque atual diverge da soma dos deltas do livro. */
    @Query("""
            SELECT v.id AS variantId, v.stock AS stock, SUM(m.quantityDelta) AS ledgerBalance
            FROM StockMovement m JOIN m.productVariant v
            GROUP BY v.id, v.stock
            HAVING v.stock <> SUM(m.quantityDelta)
            """)
    List<ReconciliationRow> findDivergences();

    interface ReconciliationRow {
        UUID getVariantId();

        Integer getStock();

        Long getLedgerBalance();
    }

    /** Um registro por pedido que baixou estoque (SAIDA_VENDA). */
    @Query(value = """
            SELECT m.referenceId AS orderId, MIN(m.createdAt) AS movedAt,
                   COUNT(m) AS lines, SUM(-m.quantityDelta) AS units
            FROM StockMovement m
            WHERE m.type = com.dnstore.backend.model.enums.StockMovementType.SAIDA_VENDA
              AND (:fromTs IS NULL OR m.createdAt >= :fromTs)
              AND (:toTs IS NULL OR m.createdAt < :toTs)
            GROUP BY m.referenceId
            ORDER BY MIN(m.createdAt) DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT m.referenceId) FROM StockMovement m
            WHERE m.type = com.dnstore.backend.model.enums.StockMovementType.SAIDA_VENDA
              AND (:fromTs IS NULL OR m.createdAt >= :fromTs)
              AND (:toTs IS NULL OR m.createdAt < :toTs)
            """)
    Page<SaleOutflowRow> findSaleOutflows(@Param("fromTs") LocalDateTime from, @Param("toTs") LocalDateTime to,
            Pageable pageable);

    interface SaleOutflowRow {
        String getOrderId();

        LocalDateTime getMovedAt();

        Long getLines();

        Long getUnits();
    }

    /** Produto com movimentações não pode ser apagado fisicamente (FK); é arquivado. */
    @Query("SELECT COUNT(m) > 0 FROM StockMovement m WHERE m.productVariant.product.id = :productId")
    boolean isProductReferenced(@Param("productId") UUID productId);
}
