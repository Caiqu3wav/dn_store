package com.dnstore.backend.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Cabeçalho de uma nota de entrada. Imutável: erro de nota se corrige com ajuste, não editando a nota. */
@Entity
@Immutable
@Table(name = "stock_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockEntry {

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false, updatable = false)
    private Supplier supplier;

    @Column(name = "invoice_number", nullable = false, length = 40, updatable = false)
    private String invoiceNumber;

    @Column(name = "invoice_series", nullable = false, length = 10, updatable = false)
    private String invoiceSeries;

    @Column(name = "invoice_date", nullable = false, updatable = false)
    private LocalDate invoiceDate;

    @Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(length = 255, updatable = false)
    private String notes;

    @Column(name = "created_by", nullable = false, length = 150, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static StockEntry create(Supplier supplier, String invoiceNumber, String invoiceSeries,
                                    LocalDate invoiceDate, String idempotencyKey, String requestHash,
                                    String notes, String createdBy) {
        StockEntry e = new StockEntry();
        e.supplier = supplier;
        e.invoiceNumber = invoiceNumber;
        e.invoiceSeries = invoiceSeries;
        e.invoiceDate = invoiceDate;
        e.idempotencyKey = idempotencyKey;
        e.requestHash = requestHash;
        e.notes = notes;
        e.createdBy = createdBy;
        e.createdAt = LocalDateTime.now();
        return e;
    }
}
