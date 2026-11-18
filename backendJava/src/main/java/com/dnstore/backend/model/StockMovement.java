package com.dnstore.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_stock_movements_variant", columnList = "variant_id,created_at"),
        @Index(name = "idx_stock_movements_order", columnList = "order_id")
})
@Getter @Setter @NoArgsConstructor
public class StockMovement {

    public enum Type { SALE, CANCELLATION, ENTRY, ADJUSTMENT }

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    /** Positive = entrada, negative = saída. */
    @Column(nullable = false)
    private int delta;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    /** Filled for SALE and CANCELLATION. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    /** Free-text reason for ENTRY and ADJUSTMENT. */
    @Column(length = 255)
    private String reason;

    /** Admin user who triggered ENTRY or ADJUSTMENT. */
    @Column(name = "performed_by", length = 120)
    private String performedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
