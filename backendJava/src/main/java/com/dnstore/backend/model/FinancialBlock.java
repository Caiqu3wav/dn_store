package com.dnstore.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

@Entity @Table(name = "payment_financial_blocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_financial_block_payment_type", columnNames = {"payment_id", "block_type"}),
        indexes = @Index(name = "idx_financial_block_open", columnList = "order_id,active"))
@Getter @Setter @NoArgsConstructor
public class FinancialBlock {
    @Id @GeneratedValue(strategy = GenerationType.AUTO) @JdbcTypeCode(java.sql.Types.VARCHAR) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_id", nullable = false) private Payment payment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false) private Order order;
    @Column(name = "block_type", nullable = false, length = 40) private String blockType;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private LocalDateTime openedAt;
    private LocalDateTime resolvedAt;
    @Column(length = 500) private String reason;
    @PrePersist void onCreate() { if (openedAt == null) openedAt = LocalDateTime.now(); }
}
