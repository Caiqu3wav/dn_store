-- Add the shipping cost required by the current Order entity.
ALTER TABLE orders
    ADD COLUMN shipping_cost DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER discount_amount;