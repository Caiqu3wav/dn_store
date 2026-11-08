package com.dnstore.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "shipping_quote_options")
public class ShippingQuoteOptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "service_id", length = 64, nullable = false)
    private String serviceId;

    @Column(name = "service_name", length = 100, nullable = false)
    private String serviceName;

    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "delivery_days", nullable = false)
    private int deliveryDays;

    @Column(name = "source", length = 32, nullable = false)
    private String source;

    @Column(name = "provider_environment", length = 16, nullable = false)
    private String providerEnvironment;

    @Column(name = "origin_zip", length = 8, nullable = false)
    private String originZip;

    @Column(name = "destination_zip", length = 8, nullable = false)
    private String destinationZip;

    @Column(name = "cart_fingerprint", length = 64, nullable = false)
    private String cartFingerprint;

    @Column(name = "quoted_at", nullable = false)
    private LocalDateTime quotedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
