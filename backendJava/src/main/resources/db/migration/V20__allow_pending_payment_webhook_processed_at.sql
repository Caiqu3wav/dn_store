ALTER TABLE payment_webhook_events
    MODIFY COLUMN processed_at TIMESTAMP NULL DEFAULT NULL;
