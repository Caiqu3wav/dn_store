ALTER TABLE payments
    ADD COLUMN refunded_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN received_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN last_provider_event_at TIMESTAMP NULL;

CREATE TABLE payment_financial_history (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    previous_status VARCHAR(40) NULL,
    new_status VARCHAR(40) NULL,
    event_type VARCHAR(100) NULL,
    event_id VARCHAR(100) NULL,
    external_payment_id VARCHAR(100) NULL,
    amount DECIMAL(10,2) NULL,
    affected_amount DECIMAL(10,2) NULL,
    refunded_amount DECIMAL(10,2) NULL,
    provider_event_at TIMESTAMP NULL,
    received_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    source VARCHAR(20) NOT NULL,
    result VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NULL,
    CONSTRAINT fk_payment_history_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_payment_history_order FOREIGN KEY (order_id) REFERENCES orders(id),
    INDEX idx_payment_history_payment_time (payment_id, processed_at),
    INDEX idx_payment_history_order_time (order_id, processed_at),
    INDEX idx_payment_history_event (event_id)
);

CREATE TABLE payment_financial_blocks (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    block_type VARCHAR(40) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    opened_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP NULL,
    reason VARCHAR(500) NULL,
    CONSTRAINT uk_financial_block_payment_type UNIQUE (payment_id, block_type),
    CONSTRAINT fk_financial_block_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_financial_block_order FOREIGN KEY (order_id) REFERENCES orders(id),
    INDEX idx_financial_block_open (order_id, active)
);
