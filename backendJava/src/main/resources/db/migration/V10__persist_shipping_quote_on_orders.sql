ALTER TABLE orders
    ADD COLUMN shipping_type VARCHAR(20) NULL,
    ADD COLUMN shipping_deadline_days INT NULL;