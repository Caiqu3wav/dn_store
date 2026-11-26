package com.dnstore.backend.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "stock_entry_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockEntryItem {

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false, updatable = false)
    private StockEntry entry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false, updatable = false)
    private ProductVariant variant;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_cost", precision = 12, scale = 2, updatable = false)
    private BigDecimal unitCost;

    public static StockEntryItem create(StockEntry entry, ProductVariant variant, int quantity,
                                        BigDecimal unitCost) {
        StockEntryItem i = new StockEntryItem();
        i.entry = entry;
        i.variant = variant;
        i.quantity = quantity;
        i.unitCost = unitCost;
        return i;
    }
}
