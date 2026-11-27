-- Entrada de estoque por nota de fornecedor. Somente aditiva (CREATE TABLE IF NOT EXISTS) para poder
-- ser repetida com segurança se o Flyway marcar a migration como falha no meio (DDL do MySQL não é transacional).

CREATE TABLE IF NOT EXISTS supplier (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    document   VARCHAR(20)  NULL,
    contact    VARCHAR(255) NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT uk_supplier_name     UNIQUE (name),
    CONSTRAINT uk_supplier_document UNIQUE (document)
);

CREATE TABLE IF NOT EXISTS stock_entry (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    supplier_id     VARCHAR(36)  NOT NULL,
    invoice_number  VARCHAR(40)  NOT NULL,
    invoice_series  VARCHAR(10)  NOT NULL DEFAULT '',
    invoice_date    DATE         NOT NULL,
    idempotency_key VARCHAR(64)  NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    notes           VARCHAR(255) NULL,
    created_by      VARCHAR(150) NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT fk_se_supplier    FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT uk_se_invoice     UNIQUE (supplier_id, invoice_series, invoice_number),
    CONSTRAINT uk_se_idempotency UNIQUE (idempotency_key),
    INDEX idx_se_created (created_at),
    INDEX idx_se_invoice_date (invoice_date)
);

CREATE TABLE IF NOT EXISTS stock_entry_item (
    id         VARCHAR(36)   NOT NULL PRIMARY KEY,
    entry_id   VARCHAR(36)   NOT NULL,
    variant_id VARCHAR(36)   NOT NULL,
    quantity   INT           NOT NULL,
    unit_cost  DECIMAL(12,2) NULL,
    CONSTRAINT fk_sei_entry   FOREIGN KEY (entry_id)   REFERENCES stock_entry (id),
    CONSTRAINT fk_sei_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT uk_sei_entry_variant UNIQUE (entry_id, variant_id),
    CONSTRAINT ck_sei_quantity  CHECK (quantity > 0),
    CONSTRAINT ck_sei_unit_cost CHECK (unit_cost IS NULL OR unit_cost >= 0),
    INDEX idx_sei_variant (variant_id)
);
