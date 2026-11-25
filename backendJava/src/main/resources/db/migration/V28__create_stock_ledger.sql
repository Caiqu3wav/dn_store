-- Módulo de estoque: fornecedores, notas de entrada e livro de movimentações (append-only).
-- Migration apenas ADITIVA: só CREATE TABLE de tabelas novas. Não altera, não lê e não escreve em tabelas
-- existentes (product_variants, orders, users...), então não há lock em tabelas de produção nem perda de dados.
-- O saldo inicial de cada variante é gravado de forma lazy pela aplicação, na primeira movimentação.

CREATE TABLE supplier (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    document VARCHAR(20) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_supplier_name UNIQUE (name),
    CONSTRAINT uk_supplier_document UNIQUE (document)
);

CREATE TABLE stock_entry (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    supplier_id VARCHAR(36) NOT NULL,
    invoice_number VARCHAR(40) NOT NULL,
    issue_date DATE NULL,
    received_at TIMESTAMP NOT NULL,
    notes VARCHAR(500) NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_stock_entry_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id),
    CONSTRAINT fk_stock_entry_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT uk_stock_entry_supplier_invoice UNIQUE (supplier_id, invoice_number),
    CONSTRAINT uk_stock_entry_idempotency UNIQUE (idempotency_key),
    INDEX idx_stock_entry_received_at (received_at)
);

CREATE TABLE stock_entry_item (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    entry_id VARCHAR(36) NOT NULL,
    product_variant_id VARCHAR(36) NOT NULL,
    quantity INT NOT NULL,
    unit_cost DECIMAL(10,2) NULL,
    CONSTRAINT fk_stock_entry_item_entry FOREIGN KEY (entry_id) REFERENCES stock_entry(id),
    CONSTRAINT fk_stock_entry_item_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id),
    CONSTRAINT uk_stock_entry_item_entry_variant UNIQUE (entry_id, product_variant_id),
    CONSTRAINT ck_stock_entry_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_entry_item_cost CHECK (unit_cost IS NULL OR unit_cost >= 0)
);

-- Livro de movimentações. Nunca recebe UPDATE nem DELETE (garantido na aplicação e por teste).
-- seq dá ordem total estável para movimentos gravados no mesmo milissegundo.
-- opening_marker = 1 só no SALDO_INICIAL: a UNIQUE (variante, marker) impede dois saldos iniciais
-- da mesma variante mesmo sob concorrência (NULLs não colidem).
CREATE TABLE stock_movement (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    seq BIGINT NOT NULL AUTO_INCREMENT,
    product_variant_id VARCHAR(36) NOT NULL,
    movement_type VARCHAR(30) NOT NULL,
    quantity_delta INT NOT NULL,
    balance_before INT NOT NULL,
    balance_after INT NOT NULL,
    reference_type VARCHAR(20) NULL,
    reference_id VARCHAR(36) NULL,
    reason_code VARCHAR(30) NULL,
    reason_text VARCHAR(500) NULL,
    actor_user_id VARCHAR(36) NULL,
    idempotency_key VARCHAR(64) NULL,
    opening_marker TINYINT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_stock_movement_seq UNIQUE (seq),
    CONSTRAINT uk_stock_movement_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uk_stock_movement_opening UNIQUE (product_variant_id, opening_marker),
    CONSTRAINT fk_stock_movement_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id),
    CONSTRAINT fk_stock_movement_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
    CONSTRAINT ck_stock_movement_delta CHECK (quantity_delta <> 0),
    CONSTRAINT ck_stock_movement_balance CHECK (balance_before >= 0 AND balance_after = balance_before + quantity_delta
        AND balance_after >= 0),
    CONSTRAINT ck_stock_movement_opening CHECK (opening_marker IS NULL OR opening_marker = 1),
    INDEX idx_stock_movement_variant_time (product_variant_id, created_at),
    INDEX idx_stock_movement_type_time (movement_type, created_at),
    INDEX idx_stock_movement_reference (reference_type, reference_id),
    INDEX idx_stock_movement_actor_time (actor_user_id, created_at),
    INDEX idx_stock_movement_created_at (created_at)
);
