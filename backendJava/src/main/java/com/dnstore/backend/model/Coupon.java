package com.dnstore.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "coupons")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "discount_value", precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_cart_value", precision = 10, scale = 2)
    private BigDecimal minCartValue;

    @Column(name = "max_usage")
    private Integer maxUsage;

    @Column(name = "current_usage")
    private Integer currentUsage = 0;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}