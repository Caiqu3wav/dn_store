ALTER TABLE payments
ADD CONSTRAINT uk_payments_order_id UNIQUE (order_id),
ADD CONSTRAINT uk_payments_external_id UNIQUE (external_id);