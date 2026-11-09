package com.dnstore.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "payment_refunds", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_refund_idempotency", columnNames = {"payment_id", "idempotency_key"}),
        @UniqueConstraint(name = "uk_payment_refund_external", columnNames = "external_refund_id")
})
@Getter @Setter @NoArgsConstructor
public class PaymentRefund {
    @Id @GeneratedValue(strategy = GenerationType.AUTO) @JdbcTypeCode(java.sql.Types.VARCHAR) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_id", nullable = false) private Payment payment;
    @Column(name = "idempotency_key", nullable = false, length = 64) private String idempotencyKey;
    @Column(name = "external_refund_id", length = 100) private String externalRefundId;
    @Column(name = "requested_amount", nullable = false, precision = 10, scale = 2) private BigDecimal requestedAmount;
    @Column(name = "confirmed_amount", nullable = false, precision = 10, scale = 2) private BigDecimal confirmedAmount = BigDecimal.ZERO;
    @Column(nullable = false, length = 30) private String status = "REQUESTED";
    @Column(name = "requested_at", nullable = false) private LocalDateTime requestedAt;
    private LocalDateTime confirmedAt;
    @PrePersist void onCreate() { if (requestedAt == null) requestedAt = LocalDateTime.now(); }
}
