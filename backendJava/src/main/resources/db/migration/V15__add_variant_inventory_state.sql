ALTER TABLE product_variants
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT uk_product_variants_product_size UNIQUE (product_id, size);

ALTER TABLE orders
    ADD COLUMN stock_released BOOLEAN NOT NULL DEFAULT FALSE;
