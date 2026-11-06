package com.dnstore.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "product_default_rules")
@NoArgsConstructor
public class ProductDefaultRule {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "criteria_json", nullable = false, columnDefinition = "TEXT")
    private String criteriaJson;

    @Column(name = "values_json", nullable = false, columnDefinition = "TEXT")
    private String valuesJson;

    @Column(name = "excluded_product_ids_json", nullable = false, columnDefinition = "TEXT")
    private String excludedProductIdsJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_products", nullable = false)
    private int updatedProducts;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
