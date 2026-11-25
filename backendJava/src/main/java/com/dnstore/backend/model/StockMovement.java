package com.dnstore.backend.model;

import com.dnstore.backend.model.enums.StockMovementType;
import com.dnstore.backend.model.enums.StockReferenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;

/**
 * Linha do livro de estoque. Append-only: sem setters, {@link Immutable} (Hibernate ignora UPDATE) e sem
 * métodos de remoção no repositório. {@code seq} e {@code opening_marker} existem no banco; o último só é
 * gravado pela inserção nativa do saldo inicial.
 */
@Entity
@Immutable
@Table(name = "stock_movement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement {

    @Id @GeneratedValue(strategy = GenerationType.AUTO) @JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;

    /** Ordem total gerada pelo banco; só leitura. */
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_variant_id", nullable = false, updatable = false)
    private ProductVariant productVariant;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30, updatable = false)
    private StockMovementType type;

    @Column(name = "quantity_delta", nullable = false, updatable = false)
    private int quantityDelta;

    @Column(name = "balance_before", nullable = false, updatable = false)
    private int balanceBefore;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private int balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 20, updatable = false)
    private StockReferenceType referenceType;

    @Column(name = "reference_id", length = 36, updatable = false)
    private String referenceId;

    @Column(name = "reason_code", length = 30, updatable = false)
    private String reasonCode;

    @Column(name = "reason_text", length = 500, updatable = false)
    private String reasonText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", updatable = false)
    private User actor;

    @Column(name = "idempotency_key", length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private StockMovement(ProductVariant productVariant, StockMovementType type, int quantityDelta,
            int balanceBefore, StockReferenceType referenceType, String referenceId, String reasonCode,
            String reasonText, User actor, String idempotencyKey, LocalDateTime createdAt) {
        this.productVariant = productVariant;
        this.type = type;
        this.quantityDelta = quantityDelta;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceBefore + quantityDelta;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.reasonCode = reasonCode;
        this.reasonText = reasonText;
        this.actor = actor;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }
}
