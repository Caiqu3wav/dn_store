CREATE TABLE product_default_rules (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    name VARCHAR(150) NOT NULL,
    criteria_json TEXT NOT NULL,
    values_json TEXT NOT NULL,
    excluded_product_ids_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_products INT NOT NULL DEFAULT 0
);
