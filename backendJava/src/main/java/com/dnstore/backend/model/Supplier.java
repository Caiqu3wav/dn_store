package com.dnstore.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

/** Fornecedor/fabricante de quem se recebe mercadoria por nota de entrada. */
@Entity
@Table(name = "supplier")
@Getter @Setter @NoArgsConstructor
public class Supplier {

    @Id @GeneratedValue(strategy = GenerationType.AUTO) @JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    /** CNPJ/CPF apenas com dígitos; opcional. */
    @Column(length = 20)
    private String document;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
