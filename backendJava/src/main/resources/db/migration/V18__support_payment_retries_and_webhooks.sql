ALTER TABLE payments
    ADD INDEX idx_payments_order_id (order_id);

ALTER TABLE payments
    DROP INDEX uk_payments_order_id,
    ADD COLUMN external_reference VARCHAR(150) NULL AFTER external_id,
    ADD COLUMN provider_customer_id VARCHAR(100) NULL AFTER external_reference,
    ADD COLUMN idempotency_key VARCHAR(64) NULL AFTER provider_customer_id,
    ADD COLUMN attempt_number INT NULL AFTER idempotency_key;

UPDATE payments
SET external_reference = order_id,
    idempotency_key = CONCAT('legacy-', id),
    attempt_number = 1
WHERE external_reference IS NULL
   OR idempotency_key IS NULL
   OR attempt_number IS NULL;

ALTER TABLE payments
    MODIFY COLUMN external_reference VARCHAR(150) NOT NULL,
    MODIFY COLUMN idempotency_key VARCHAR(64) NOT NULL,
    MODIFY COLUMN attempt_number INT NOT NULL,
    MODIFY COLUMN status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    ADD CONSTRAINT uk_payments_order_attempt_key UNIQUE (order_id, idempotency_key),
    ADD CONSTRAINT uk_payments_order_attempt UNIQUE (order_id, attempt_number);

CREATE TABLE payment_webhook_events (
    event_id VARCHAR(100) NOT NULL PRIMARY KEY,
    external_payment_id VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_payment_expiration ON orders (status, created_at);
