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
@Table(name = "payment_financial_history", indexes = {
        @Index(name = "idx_payment_history_payment_time", columnList = "payment_id,processed_at"),
        @Index(name = "idx_payment_history_order_time", columnList = "order_id,processed_at")
})
@Getter @Setter @NoArgsConstructor
public class PaymentFinancialHistory {
    @Id @GeneratedValue(strategy = GenerationType.AUTO) @JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(name = "previous_status", length = 40) private String previousStatus;
    @Column(name = "new_status", length = 40) private String newStatus;
    @Column(name = "event_type", length = 100) private String eventType;
    @Column(name = "event_id", length = 100) private String eventId;
    @Column(name = "external_payment_id", length = 100) private String externalPaymentId;
    @Column(precision = 10, scale = 2) private BigDecimal amount;
    @Column(name = "affected_amount", precision = 10, scale = 2) private BigDecimal affectedAmount;
    @Column(name = "refunded_amount", precision = 10, scale = 2) private BigDecimal refundedAmount;
    @Column(name = "provider_event_at") private LocalDateTime providerEventAt;
    @Column(name = "received_at", nullable = false) private LocalDateTime receivedAt;
    @Column(name = "processed_at", nullable = false) private LocalDateTime processedAt;
    @Column(nullable = false, length = 20) private String source;
    @Column(nullable = false, length = 30) private String result;
    @Column(length = 500) private String reason;
    @PrePersist void onCreate() {
        if (receivedAt == null) receivedAt = LocalDateTime.now();
        if (processedAt == null) processedAt = LocalDateTime.now();
    }
}
