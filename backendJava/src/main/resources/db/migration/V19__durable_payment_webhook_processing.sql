ALTER TABLE payment_webhook_events
    ADD COLUMN event_type VARCHAR(100) NULL AFTER external_payment_id,
    ADD COLUMN payload LONGTEXT NULL AFTER event_type,
    ADD COLUMN received_at TIMESTAMP NULL AFTER payload,
    ADD COLUMN processing_status VARCHAR(20) NULL AFTER received_at,
    ADD COLUMN attempt_count INT NULL AFTER processing_status,
    ADD COLUMN last_attempt_at TIMESTAMP NULL AFTER attempt_count,
    ADD COLUMN next_attempt_at TIMESTAMP NULL AFTER last_attempt_at,
    ADD COLUMN processing_started_at TIMESTAMP NULL AFTER next_attempt_at,
    ADD COLUMN error_message VARCHAR(500) NULL AFTER processed_at;

UPDATE payment_webhook_events
SET event_type = 'LEGACY',
    received_at = COALESCE(processed_at, CURRENT_TIMESTAMP),
    processing_status = 'PROCESSED',
    attempt_count = 0
WHERE event_type IS NULL;

ALTER TABLE payment_webhook_events
    MODIFY COLUMN event_type VARCHAR(100) NOT NULL DEFAULT 'LEGACY',
    MODIFY COLUMN received_at TIMESTAMP NOT NULL,
    MODIFY COLUMN processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    MODIFY COLUMN attempt_count INT NOT NULL DEFAULT 0;

CREATE INDEX idx_payment_webhook_processing
    ON payment_webhook_events (processing_status, next_attempt_at, received_at);
CREATE INDEX idx_payment_webhook_processing_lease
    ON payment_webhook_events (processing_status, processing_started_at);
