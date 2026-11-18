-- Run once against the Aiven MySQL database.
-- After running, keep DDL_AUTO=validate.

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
    CONSTRAINT fk_sm_order   FOREIGN KEY (order_id)   REFERENCES orders (id)
);

CREATE INDEX IF NOT EXISTS idx_stock_movements_variant ON stock_movements (variant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_stock_movements_order   ON stock_movements (order_id);
