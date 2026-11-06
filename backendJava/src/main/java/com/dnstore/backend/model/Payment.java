package com.dnstore.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_order_attempt_key", columnNames = { "order_id", "idempotency_key" }),
        @UniqueConstraint(name = "uk_payments_external_id", columnNames = "external_id"),
        @UniqueConstraint(name = "uk_payments_order_attempt", columnNames = { "order_id", "attempt_number" })
})
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "external_reference", nullable = false, length = 150)
    private String externalReference;

    @Column(name = "provider_customer_id", length = 100)
    private String providerCustomerId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod; // PIX | BOLETO

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "pix_qr_code", columnDefinition = "TEXT")
    private String pixQrCode;

    @Column(name = "pix_copy_paste", columnDefinition = "TEXT")
    private String pixCopyPaste;

    @Column(name = "boleto_url", columnDefinition = "TEXT")
    private String boletoUrl;

    @Column(name = "boleto_barcode", columnDefinition = "TEXT")
    private String boletoBarcode;

    @Column(name = "installments")
    private Integer installments;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null)
            status = "PENDING";
    }
}
