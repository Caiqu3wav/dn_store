CREATE TABLE IF NOT EXISTS stock_movements (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    variant_id   VARCHAR(36)  NOT NULL,
    delta        INT          NOT NULL,
    stock_after  INT          NOT NULL,
    type         VARCHAR(20)  NOT NULL,
    order_id     VARCHAR(36)  NULL,
    reason       VARCHAR(255) NULL,
    performed_by VARCHAR(120) NULL,
    created_at   DATETIME(6)  NOT NULL,
    CONSTRAINT fk_sm_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT fk_sm_order   FOREIGN KEY (order_id)   REFERENCES orders (id),
    INDEX idx_stock_movements_variant (variant_id, created_at),
    INDEX idx_stock_movements_order (order_id)
);
