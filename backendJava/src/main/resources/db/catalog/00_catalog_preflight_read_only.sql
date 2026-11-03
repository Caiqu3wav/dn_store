-- Read-only preflight for a catalog rebuild. Run manually with a read-only
-- database account. This file contains SELECT and SHOW statements only.

SELECT DATABASE() AS database_name, VERSION() AS mysql_version,
       @@hostname AS server_hostname, @@port AS server_port,
       @@server_uuid AS server_uuid;
SHOW FULL TABLES;

-- Exact foreign-key graph, including delete rules, for every table in the schema.
-- Run this audit with an account that can see all schema constraint metadata.
SELECT
    kcu.TABLE_NAME AS child_table,
    kcu.COLUMN_NAME AS child_column,
    kcu.CONSTRAINT_NAME,
    kcu.REFERENCED_TABLE_NAME AS parent_table,
    kcu.REFERENCED_COLUMN_NAME AS parent_column,
    rc.DELETE_RULE,
    rc.UPDATE_RULE
FROM information_schema.KEY_COLUMN_USAGE kcu
LEFT JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
  ON rc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
 AND rc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
 AND rc.TABLE_NAME = kcu.TABLE_NAME
WHERE kcu.CONSTRAINT_SCHEMA = DATABASE()
  AND kcu.REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY parent_table, child_table, kcu.CONSTRAINT_NAME, kcu.ORDINAL_POSITION;

-- Triggers can cause side effects outside the tables directly deleted.
-- The cleanup procedure aborts if any trigger is attached to a deletion target.
SELECT
    TRIGGER_NAME,
    EVENT_OBJECT_TABLE AS table_name,
    ACTION_TIMING,
    EVENT_MANIPULATION
FROM information_schema.TRIGGERS
WHERE TRIGGER_SCHEMA = DATABASE()
  AND EVENT_OBJECT_TABLE IN (
      'products', 'physical_products', 'product_variants', 'product_images',
      'favorites', 'carts', 'cart_items', 'orders', 'order_items',
      'payments', 'shipments', 'shipment_tracking'
  )
ORDER BY EVENT_OBJECT_TABLE, ACTION_TIMING, EVENT_MANIPULATION, TRIGGER_NAME;

-- Table engines must be transactional for the cleanup DML to be atomic.
SELECT TABLE_NAME, ENGINE, TABLE_ROWS
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME;

-- Data counts: no user names, addresses, tokens, credentials, or payment payloads.
SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL SELECT 'addresses', COUNT(*) FROM addresses
UNION ALL SELECT 'categories', COUNT(*) FROM categories
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'physical_products', COUNT(*) FROM physical_products
UNION ALL SELECT 'product_variants', COUNT(*) FROM product_variants
UNION ALL SELECT 'product_images', COUNT(*) FROM product_images
UNION ALL SELECT 'favorites', COUNT(*) FROM favorites
UNION ALL SELECT 'carts', COUNT(*) FROM carts
UNION ALL SELECT 'cart_items', COUNT(*) FROM cart_items
UNION ALL SELECT 'orders', COUNT(*) FROM orders
UNION ALL SELECT 'order_items', COUNT(*) FROM order_items
UNION ALL SELECT 'payments', COUNT(*) FROM payments
UNION ALL SELECT 'shipments', COUNT(*) FROM shipments
UNION ALL SELECT 'shipment_tracking', COUNT(*) FROM shipment_tracking
UNION ALL SELECT 'coupons', COUNT(*) FROM coupons
UNION ALL SELECT 'events', COUNT(*) FROM events
ORDER BY table_name;

-- Copy this one-row manifest into the gated cleanup script only after reviewing
-- the complete audit output and confirming the server identity.
SELECT
    DATABASE() AS database_name,
    VERSION() AS mysql_version,
    @@server_uuid AS server_uuid,
    (SELECT COUNT(*) FROM products) AS expected_products,
    (SELECT COUNT(*) FROM physical_products) AS expected_physical_products,
    (SELECT COUNT(*) FROM product_variants) AS expected_product_variants,
    (SELECT COUNT(*) FROM product_images) AS expected_product_images,
    (SELECT COUNT(*) FROM favorites) AS expected_favorites,
    (SELECT COUNT(*) FROM carts) AS expected_carts,
    (SELECT COUNT(*) FROM cart_items) AS expected_cart_items,
    (SELECT COUNT(*) FROM orders) AS expected_orders,
    (SELECT COUNT(*) FROM order_items) AS expected_order_items,
    (SELECT COUNT(*) FROM payments) AS expected_payments,
    (SELECT COUNT(*) FROM shipments) AS expected_shipments,
    (SELECT COUNT(*) FROM shipment_tracking) AS expected_shipment_tracking,
    (SELECT COUNT(*) FROM users) AS expected_users,
    (SELECT COUNT(*) FROM addresses) AS expected_addresses,
    (SELECT COUNT(*) FROM categories) AS expected_categories,
    (SELECT COUNT(*) FROM coupons) AS expected_coupons,
    (SELECT COUNT(*) FROM events) AS expected_events,
    (SELECT COUNT(*) FROM flyway_schema_history) AS expected_flyway_rows;

SELECT p.id, p.name, p.price, p.promotional_price, p.active, p.product_type,
       c.id AS category_id, c.name AS category_name,
       pp.stock AS legacy_aggregate_stock
FROM products p
LEFT JOIN categories c ON c.id = p.category_id
LEFT JOIN physical_products pp ON pp.id = p.id
ORDER BY p.name, p.id;

SELECT p.id AS product_id, p.name, pv.id AS variant_id, pv.size, pv.stock,
       pv.sku
FROM products p
LEFT JOIN product_variants pv ON pv.product_id = p.id
ORDER BY p.name, pv.size, pv.id;

SELECT p.id AS product_id, p.name, pi.id AS image_id, pi.image_url, pi.is_main
FROM products p
LEFT JOIN product_images pi ON pi.product_id = p.id
ORDER BY p.name, pi.is_main DESC, pi.id;

SELECT c.id, c.name, c.slug, COUNT(p.id) AS product_count
FROM categories c
LEFT JOIN products p ON p.category_id = c.id
GROUP BY c.id, c.name, c.slug
ORDER BY c.name;

-- Historical usage is reported by counts only. Do not select personal data.
SELECT p.id AS product_id, p.name,
       COUNT(DISTINCT oi.order_id) AS order_count,
       SUM(oi.quantity) AS units_in_order_items
FROM products p
JOIN product_variants pv ON pv.product_id = p.id
JOIN order_items oi ON oi.product_variant_id = pv.id
GROUP BY p.id, p.name
ORDER BY p.name;

SELECT o.status, COUNT(*) AS order_count,
       COUNT(DISTINCT pay.id) AS payment_count,
       COUNT(DISTINCT s.id) AS shipment_count
FROM orders o
LEFT JOIN payments pay ON pay.order_id = o.id
LEFT JOIN shipments s ON s.order_id = o.id
GROUP BY o.status
ORDER BY o.status;

-- Coupons and events are intentionally preserved by the cleanup script.
-- Inspect these summaries and decide manually whether their contents are test data.
SELECT active, COUNT(*) AS coupon_count,
       SUM(COALESCE(current_usage, 0)) AS recorded_usage
FROM coupons
GROUP BY active
ORDER BY active;

SELECT id, code, active, current_usage, max_usage, expires_at
FROM coupons
ORDER BY code;

SELECT COUNT(*) AS event_count,
       SUM(date IS NOT NULL AND date < CURRENT_TIMESTAMP) AS past_event_count
FROM events;

SELECT id, name, date, location
FROM events
ORDER BY date, name;

SELECT product_id, size, COUNT(*) AS duplicate_count
FROM product_variants
WHERE size IS NOT NULL
GROUP BY product_id, size
HAVING COUNT(*) > 1;

SELECT product_id, COUNT(*) AS null_size_count
FROM product_variants
WHERE size IS NULL
GROUP BY product_id
HAVING COUNT(*) > 0;

SHOW INDEX FROM product_variants;
SHOW CREATE TABLE products;
SHOW CREATE TABLE physical_products;
SHOW CREATE TABLE product_variants;
SHOW CREATE TABLE product_images;
SHOW CREATE TABLE categories;
SHOW CREATE TABLE users;
SHOW CREATE TABLE addresses;
SHOW CREATE TABLE favorites;
SHOW CREATE TABLE carts;
SHOW CREATE TABLE cart_items;
SHOW CREATE TABLE orders;
SHOW CREATE TABLE order_items;
SHOW CREATE TABLE payments;
SHOW CREATE TABLE shipments;
SHOW CREATE TABLE shipment_tracking;
SHOW CREATE TABLE coupons;
SHOW CREATE TABLE events;
SHOW CREATE TABLE flyway_schema_history;

SELECT installed_rank, version, description, type, script, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
