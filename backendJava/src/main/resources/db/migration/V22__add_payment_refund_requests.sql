CREATE TABLE payment_refunds (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    external_refund_id VARCHAR(100) NULL,
    requested_amount DECIMAL(10,2) NOT NULL,
    confirmed_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    requested_at TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP NULL,
    CONSTRAINT uk_payment_refund_idempotency UNIQUE (payment_id, idempotency_key),
    CONSTRAINT uk_payment_refund_external UNIQUE (external_refund_id),
    CONSTRAINT fk_payment_refund_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    INDEX idx_payment_refund_payment_status (payment_id, status)
);
