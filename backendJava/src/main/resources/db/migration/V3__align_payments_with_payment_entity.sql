-- Align the original payments table with the current Payment entity.
ALTER TABLE payments
    CHANGE COLUMN method payment_method VARCHAR(20) NOT NULL,
    CHANGE COLUMN transaction_id external_id VARCHAR(100),
    ADD COLUMN amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER external_id,
    ADD COLUMN pix_qr_code TEXT,
    ADD COLUMN pix_copy_paste TEXT,
    ADD COLUMN boleto_url TEXT,
    ADD COLUMN boleto_barcode TEXT,
    ADD COLUMN installments INT,
    ADD COLUMN paid_at TIMESTAMP NULL DEFAULT NULL;

ALTER TABLE payments
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING';