-- Preflight duplicate user_id values before applying; this migration intentionally does not delete cart data.
ALTER TABLE carts
ADD CONSTRAINT uk_carts_user_id UNIQUE (user_id);