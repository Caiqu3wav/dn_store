CREATE TABLE melhor_envio_tokens (
    environment VARCHAR(16) NOT NULL PRIMARY KEY,
    access_token_ciphertext VARBINARY(2048) NOT NULL,
    refresh_token_ciphertext VARBINARY(2048) NOT NULL,
    expires_at DATETIME NOT NULL,
    refresh_expires_at DATETIME NOT NULL,
    token_status VARCHAR(24) NOT NULL,
    CONSTRAINT chk_melhor_envio_token_environment
        CHECK (environment IN ('sandbox', 'production')),
    CONSTRAINT chk_melhor_envio_token_status
        CHECK (token_status IN ('AUTHORIZED', 'REAUTH_REQUIRED'))
);

CREATE TABLE melhor_envio_oauth_states (
    state_hash VARCHAR(64) NOT NULL PRIMARY KEY,
    environment VARCHAR(16) NOT NULL,
    expires_at DATETIME NOT NULL,
    INDEX idx_me_oauth_state_expiration (environment, expires_at),
    CONSTRAINT chk_me_oauth_state_environment
        CHECK (environment IN ('sandbox', 'production'))
);

CREATE TABLE shipping_quote_options (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    service_id VARCHAR(64) NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    delivery_days INT NOT NULL,
    source VARCHAR(32) NOT NULL,
    provider_environment VARCHAR(16) NOT NULL,
    origin_zip VARCHAR(8) NOT NULL,
    destination_zip VARCHAR(8) NOT NULL,
    cart_fingerprint VARCHAR(64) NOT NULL,
    quoted_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    INDEX idx_shipping_quote_user_expiration (user_id, expires_at),
    CONSTRAINT fk_shipping_quote_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_shipping_quote_price
        CHECK (price >= 0),
    CONSTRAINT chk_shipping_quote_delivery_days
        CHECK (delivery_days > 0)
);

ALTER TABLE orders
    MODIFY COLUMN shipping_type VARCHAR(100) NULL,
    ADD COLUMN shipping_service_id VARCHAR(64) NULL,
    ADD COLUMN shipping_source VARCHAR(32) NULL,
    ADD COLUMN shipping_quote_option_id VARCHAR(36) NULL,
    ADD CONSTRAINT uq_orders_shipping_quote_option UNIQUE (shipping_quote_option_id),
    ADD CONSTRAINT fk_orders_shipping_quote_option
        FOREIGN KEY (shipping_quote_option_id) REFERENCES shipping_quote_options(id);
