package com.dnstore.backend.model;

import com.dnstore.backend.model.enums.StockAdjustmentCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Linha do livro de estoque. Append-only: sem setters, sem delete no repositório e {@link Immutable}
 * para o Hibernate ignorar qualquer alteração. Só é criada por {@code StockLedger}.
 */
@Entity
@Immutable
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_stock_movements_variant", columnList = "variant_id,created_at"),
        @Index(name = "idx_stock_movements_order", columnList = "order_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement {

    public enum Type {
        SALE, CANCELLATION, ENTRY, ADJUSTMENT,
        /** Variante criada já com saldo (stock_before = 0). */
        INITIAL,
        /** Pedido liberado que voltou a reservar estoque. */
        REACTIVATION,
        /** Marcador de abertura de uma variante que já tinha saldo e não tinha livro (delta = 0). */
        OPENING
    }

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;

    /** Sequência global do banco; ordena o log. Preenchida pelo MySQL, nunca pela aplicação. */
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false, updatable = false)
    private ProductVariant variant;

    /** Positive = entrada, negative = saída. */
    @Column(nullable = false, updatable = false)
    private int delta;

    /** Null nas linhas anteriores à V30. */
    @Column(name = "stock_before", updatable = false)
    private Integer stockBefore;

    @Column(name = "stock_after", nullable = false, updatable = false)
    private int stockAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Type type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_entry_id", updatable = false)
    private StockEntry stockEntry;

    @Column(length = 255, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, updatable = false)
    private StockAdjustmentCategory category;

    /** E-mail do admin que originou a movimentação (null em vendas e cancelamentos). */
    @Column(name = "performed_by", length = 120, updatable = false)
    private String performedBy;

    @Column(name = "idempotency_key", length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static StockMovement create(ProductVariant variant, int stockBefore, int delta, Type type,
                                       Order order, StockEntry stockEntry, String reason,
                                       StockAdjustmentCategory category, String performedBy,
                                       String idempotencyKey) {
        StockMovement m = new StockMovement();
        m.variant = variant;
        m.stockBefore = stockBefore;
        m.delta = delta;
        m.stockAfter = Math.addExact(stockBefore, delta);
        m.type = type;
        m.order = order;
        m.stockEntry = stockEntry;
        m.reason = reason;
        m.category = category;
        m.performedBy = performedBy;
        m.idempotencyKey = idempotencyKey;
        m.createdAt = LocalDateTime.now();
        return m;
    }
}
