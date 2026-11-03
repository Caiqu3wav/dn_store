-- DESTRUCTIVE, MANUAL-ONLY cleanup. Never run from Flyway or an application.
-- Deletes only rows in the listed commerce tables after the owner has confirmed
-- that every targeted row is simulated/test data. Preserves users, addresses,
-- categories, coupons, events, and flyway_schema_history.
--
-- Run 00_catalog_preflight_read_only.sql first using a read-only account.
-- The audit account must have complete visibility into FK and trigger metadata;
-- have a DBA confirm that before setting the dependency-review gate below.
-- Take and verify a restorable backup. The writes-stopped flag below is only
-- an operator assertion, not a database lock; stop API workers, schedulers,
-- webhook consumers, and all other writers through the post-cleanup checks.
-- Use a dedicated fresh authorized maintenance connection to defaultdb
-- with autocommit enabled. The pre-DDL check below queries INNODB_TRX; it
-- detects active InnoDB transactions, so the fresh dedicated session is also
-- required to rule out unrelated non-InnoDB work. CREATE PROCEDURE causes an
-- implicit commit: proceed only if the pre-DDL check returns 1. Fill every expected count
-- and the server UUID below from the immediately preceding audit. Run this
-- file with a MySQL client that honors DELIMITER (not through Flyway, JDBC, or
-- an application). Leave all gates closed until those reviews are complete.
-- Never disable foreign-key checks.
--
-- The helper checks for triggers on every deletion target and aborts if any
-- exist; inspect any such trigger and its effects before revising this guard.
-- The DELETE statements themselves run in one InnoDB transaction with
-- rollback on error. If the CALL aborts, the helper procedure remains; inspect
-- the error and use a reviewed fresh session before manually removing it.
-- A rollback is not a substitute for the backup.

SET @dn_cleanup_confirmed = 0;
SET @dn_all_target_rows_are_test_data = 0;
SET @dn_application_writes_stopped = 0;
SET @dn_coupons_reviewed_and_preserved = 0;
SET @dn_events_reviewed_and_preserved = 0;
SET @dn_dependencies_reviewed = 0;
SET @dn_backup_reference = NULL;
SET @dn_confirmed_server_uuid = NULL;

-- Fill with exact row counts from the read-only audit; NULL intentionally blocks.
SET @expected_products = NULL;
SET @expected_physical_products = NULL;
SET @expected_product_variants = NULL;
SET @expected_product_images = NULL;
SET @expected_favorites = NULL;
SET @expected_carts = NULL;
SET @expected_cart_items = NULL;
SET @expected_orders = NULL;
SET @expected_order_items = NULL;
SET @expected_payments = NULL;
SET @expected_shipments = NULL;
SET @expected_shipment_tracking = NULL;
SET @expected_users = NULL;
SET @expected_addresses = NULL;
SET @expected_categories = NULL;
SET @expected_coupons = NULL;
SET @expected_events = NULL;
SET @expected_flyway_rows = NULL;

-- INNODB_TRX is available on MySQL 8.4; unlike the absent session status
-- variable, it reports active InnoDB transactions by their MySQL connection ID.
-- This query must return 1 before continuing to the routine DDL below.
SET @dn_pre_ddl_no_active_innodb_transaction = NOT EXISTS (
    SELECT 1
    FROM information_schema.INNODB_TRX
    WHERE trx_mysql_thread_id = CONNECTION_ID()
);
SELECT IF(@@autocommit = 1, @dn_pre_ddl_no_active_innodb_transaction, 0)
    AS no_active_innodb_transaction_before_procedure_ddl;

DELIMITER //
CREATE PROCEDURE dnstore_cleanup_simulated_catalog_v1()
SQL SECURITY INVOKER
BEGIN
    DECLARE v_users BIGINT DEFAULT 0;
    DECLARE v_addresses BIGINT DEFAULT 0;
    DECLARE v_categories BIGINT DEFAULT 0;
    DECLARE v_coupons BIGINT DEFAULT 0;
    DECLARE v_events BIGINT DEFAULT 0;
    DECLARE v_flyway_rows BIGINT DEFAULT 0;
    DECLARE v_transaction_started BOOLEAN DEFAULT FALSE;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        IF v_transaction_started THEN
            ROLLBACK;
        END IF;
        RESIGNAL;
    END;

    IF DATABASE() IS NULL OR DATABASE() <> 'defaultdb'
       OR VERSION() NOT LIKE '8.4.%'
       OR @@autocommit <> 1
       OR COALESCE(@dn_pre_ddl_no_active_innodb_transaction, 0) <> 1
       OR @dn_confirmed_server_uuid IS NULL
       OR @dn_confirmed_server_uuid <> @@server_uuid THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cleanup aborted: verify MySQL 8.4 defaultdb, audited server UUID, autocommit, and the fresh-session InnoDB transaction preflight.';
    END IF;

    IF COALESCE(@dn_cleanup_confirmed, 0) <> 1
       OR COALESCE(@dn_all_target_rows_are_test_data, 0) <> 1
       OR COALESCE(@dn_application_writes_stopped, 0) <> 1
       OR COALESCE(@dn_coupons_reviewed_and_preserved, 0) <> 1
       OR COALESCE(@dn_events_reviewed_and_preserved, 0) <> 1
       OR COALESCE(@dn_dependencies_reviewed, 0) <> 1
       OR @dn_backup_reference IS NULL
       OR TRIM(@dn_backup_reference) = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cleanup aborted: owner approval, test-data and dependency reviews, maintenance, coupon/event review, and backup reference are required.';
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_TYPE = 'BASE TABLE'
          AND TABLE_NAME IN (
              'products', 'physical_products', 'product_variants', 'product_images',
              'favorites', 'carts', 'cart_items', 'orders', 'order_items',
              'payments', 'shipments', 'shipment_tracking', 'users', 'addresses',
              'categories', 'coupons', 'events', 'flyway_schema_history'
          )) <> 18 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cleanup aborted: an expected table is missing or is not a base table.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME IN (
              'products', 'physical_products', 'product_variants', 'product_images',
              'favorites', 'carts', 'cart_items', 'orders', 'order_items',
              'payments', 'shipments', 'shipment_tracking'
          )
          AND ENGINE <> 'InnoDB'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cleanup aborted: all target tables must use InnoDB.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM flyway_schema_history
        WHERE version = '14' AND success = 1
    ) OR EXISTS (
        SELECT 1 FROM flyway_schema_history
        WHERE version = '15' AND success = 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cleanup aborted: expected Flyway V14 applied and V15 not applied; re-audit before proceeding.';
    END IF;

    START TRANSACTION;
    SET v_transaction_started = TRUE;

    IF @expected_products IS NULL
       OR @expected_physical_products IS NULL
       OR @expected_product_variants IS NULL
       OR @expected_product_images IS NULL
       OR @expected_favorites IS NULL
       OR @expected_carts IS NULL
       OR @expected_cart_items IS NULL
       OR @expected_orders IS NULL
       OR @expected_order_items IS NULL
       OR @expected_payments IS NULL
       OR @expected_shipments IS NULL
       OR @expected_shipment_tracking IS NULL
       OR @expected_users IS NULL
       OR @expected_addresses IS NULL
       OR @expected_categories IS NULL
       OR @expected_coupons IS NULL
       OR @expected_events IS NULL
       OR @expected_flyway_rows IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cleanup aborted: populate all target and preserved-table counts from a fresh read-only audit.';
    END IF;

    SELECT COUNT(*) INTO v_users FROM users;
    SELECT COUNT(*) INTO v_addresses FROM addresses;
    SELECT COUNT(*) INTO v_categories FROM categories;
    SELECT COUNT(*) INTO v_coupons FROM coupons;
    SELECT COUNT(*) INTO v_events FROM events;
    SELECT COUNT(*) INTO v_flyway_rows FROM flyway_schema_history;

    IF v_users <> @expected_users
       OR v_addresses <> @expected_addresses
       OR v_categories <> @expected_categories
       OR v_coupons <> @expected_coupons
       OR v_events <> @expected_events
       OR v_flyway_rows <> @expected_flyway_rows THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cleanup aborted: preserved-table counts differ from the audited manifest.';
    END IF;

    IF (SELECT COUNT(*) FROM products) <> @expected_products
       OR (SELECT COUNT(*) FROM physical_products) <> @expected_physical_products
       OR (SELECT COUNT(*) FROM product_variants) <> @expected_product_variants
       OR (SELECT COUNT(*) FROM product_images) <> @expected_product_images
       OR (SELECT COUNT(*) FROM favorites) <> @expected_favorites
       OR (SELECT COUNT(*) FROM carts) <> @expected_carts
       OR (SELECT COUNT(*) FROM cart_items) <> @expected_cart_items
       OR (SELECT COUNT(*) FROM orders) <> @expected_orders
       OR (SELECT COUNT(*) FROM order_items) <> @expected_order_items
       OR (SELECT COUNT(*) FROM payments) <> @expected_payments
       OR (SELECT COUNT(*) FROM shipments) <> @expected_shipments
       OR (SELECT COUNT(*) FROM shipment_tracking) <> @expected_shipment_tracking THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cleanup aborted: live row counts differ from the audited manifest.';
    END IF;

    -- Abort if any unreviewed FK points into a table this script deletes.
    IF EXISTS (
        SELECT 1
        FROM information_schema.KEY_COLUMN_USAGE k
        WHERE k.CONSTRAINT_SCHEMA = DATABASE()
          AND k.REFERENCED_TABLE_NAME IN (
              'products', 'physical_products', 'product_variants', 'product_images',
              'favorites', 'carts', 'cart_items', 'orders', 'order_items',
              'payments', 'shipments', 'shipment_tracking'
          )
          AND NOT (
              (k.TABLE_NAME = 'physical_products' AND k.REFERENCED_TABLE_NAME = 'products')
              OR (k.TABLE_NAME = 'product_images' AND k.REFERENCED_TABLE_NAME = 'products')
              OR (k.TABLE_NAME = 'product_variants' AND k.REFERENCED_TABLE_NAME = 'products')
              OR (k.TABLE_NAME = 'favorites' AND k.REFERENCED_TABLE_NAME = 'products')
              OR (k.TABLE_NAME = 'cart_items' AND k.REFERENCED_TABLE_NAME IN ('carts', 'product_variants'))
              OR (k.TABLE_NAME = 'order_items' AND k.REFERENCED_TABLE_NAME IN ('orders', 'product_variants'))
              OR (k.TABLE_NAME = 'payments' AND k.REFERENCED_TABLE_NAME = 'orders')
              OR (k.TABLE_NAME = 'shipments' AND k.REFERENCED_TABLE_NAME = 'orders')
              OR (k.TABLE_NAME = 'shipment_tracking' AND k.REFERENCED_TABLE_NAME = 'shipments')
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cleanup aborted: an unreviewed foreign key references a deletion target.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.TRIGGERS t
        WHERE t.TRIGGER_SCHEMA = DATABASE()
          AND t.EVENT_OBJECT_TABLE IN (
              'products', 'physical_products', 'product_variants', 'product_images',
              'favorites', 'carts', 'cart_items', 'orders', 'order_items',
              'payments', 'shipments', 'shipment_tracking'
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cleanup aborted: a trigger exists on a deletion target; inspect its effects before proceeding.';
    END IF;

    SELECT 'before' AS phase, 'products' AS table_name, COUNT(*) AS row_count FROM products
    UNION ALL SELECT 'before', 'physical_products', COUNT(*) FROM physical_products
    UNION ALL SELECT 'before', 'product_variants', COUNT(*) FROM product_variants
    UNION ALL SELECT 'before', 'product_images', COUNT(*) FROM product_images
    UNION ALL SELECT 'before', 'favorites', COUNT(*) FROM favorites
    UNION ALL SELECT 'before', 'carts', COUNT(*) FROM carts
    UNION ALL SELECT 'before', 'cart_items', COUNT(*) FROM cart_items
    UNION ALL SELECT 'before', 'orders', COUNT(*) FROM orders
    UNION ALL SELECT 'before', 'order_items', COUNT(*) FROM order_items
    UNION ALL SELECT 'before', 'payments', COUNT(*) FROM payments
    UNION ALL SELECT 'before', 'shipments', COUNT(*) FROM shipments
    UNION ALL SELECT 'before', 'shipment_tracking', COUNT(*) FROM shipment_tracking
    UNION ALL SELECT 'before', 'users (preserved)', COUNT(*) FROM users
    UNION ALL SELECT 'before', 'addresses (preserved)', COUNT(*) FROM addresses
    UNION ALL SELECT 'before', 'categories (preserved)', COUNT(*) FROM categories
    UNION ALL SELECT 'before', 'coupons (preserved)', COUNT(*) FROM coupons
    UNION ALL SELECT 'before', 'events (preserved)', COUNT(*) FROM events
    UNION ALL SELECT 'before', 'flyway_schema_history (preserved)', COUNT(*) FROM flyway_schema_history
    ORDER BY table_name;

    DELETE FROM shipment_tracking;
    DELETE FROM shipments;
    DELETE FROM payments;
    DELETE FROM order_items;
    DELETE FROM orders;
    DELETE FROM cart_items;
    DELETE FROM carts;
    DELETE FROM favorites;
    DELETE FROM product_images;
    DELETE FROM product_variants;
    DELETE FROM physical_products;
    DELETE FROM products;

    IF (SELECT COUNT(*) FROM products) <> 0
       OR (SELECT COUNT(*) FROM physical_products) <> 0
       OR (SELECT COUNT(*) FROM product_variants) <> 0
       OR (SELECT COUNT(*) FROM product_images) <> 0
       OR (SELECT COUNT(*) FROM favorites) <> 0
       OR (SELECT COUNT(*) FROM carts) <> 0
       OR (SELECT COUNT(*) FROM cart_items) <> 0
       OR (SELECT COUNT(*) FROM orders) <> 0
       OR (SELECT COUNT(*) FROM order_items) <> 0
       OR (SELECT COUNT(*) FROM payments) <> 0
       OR (SELECT COUNT(*) FROM shipments) <> 0
       OR (SELECT COUNT(*) FROM shipment_tracking) <> 0
       OR (SELECT COUNT(*) FROM users) <> v_users
       OR (SELECT COUNT(*) FROM addresses) <> v_addresses
       OR (SELECT COUNT(*) FROM categories) <> v_categories
       OR (SELECT COUNT(*) FROM coupons) <> v_coupons
       OR (SELECT COUNT(*) FROM events) <> v_events
       OR (SELECT COUNT(*) FROM flyway_schema_history) <> v_flyway_rows THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cleanup aborted: target rows remain after deletion.';
    END IF;

    COMMIT;
    SET v_transaction_started = FALSE;

    SELECT 'after' AS phase, 'products' AS table_name, COUNT(*) AS row_count FROM products
    UNION ALL SELECT 'after', 'physical_products', COUNT(*) FROM physical_products
    UNION ALL SELECT 'after', 'product_variants', COUNT(*) FROM product_variants
    UNION ALL SELECT 'after', 'product_images', COUNT(*) FROM product_images
    UNION ALL SELECT 'after', 'favorites', COUNT(*) FROM favorites
    UNION ALL SELECT 'after', 'carts', COUNT(*) FROM carts
    UNION ALL SELECT 'after', 'cart_items', COUNT(*) FROM cart_items
    UNION ALL SELECT 'after', 'orders', COUNT(*) FROM orders
    UNION ALL SELECT 'after', 'order_items', COUNT(*) FROM order_items
    UNION ALL SELECT 'after', 'payments', COUNT(*) FROM payments
    UNION ALL SELECT 'after', 'shipments', COUNT(*) FROM shipments
    UNION ALL SELECT 'after', 'shipment_tracking', COUNT(*) FROM shipment_tracking
    UNION ALL SELECT 'after', 'users (preserved)', COUNT(*) FROM users
    UNION ALL SELECT 'after', 'addresses (preserved)', COUNT(*) FROM addresses
    UNION ALL SELECT 'after', 'categories (preserved)', COUNT(*) FROM categories
    UNION ALL SELECT 'after', 'coupons (preserved)', COUNT(*) FROM coupons
    UNION ALL SELECT 'after', 'events (preserved)', COUNT(*) FROM events
    UNION ALL SELECT 'after', 'flyway_schema_history (preserved)', COUNT(*) FROM flyway_schema_history
    ORDER BY table_name;

    SELECT
        (SELECT COUNT(*) FROM users) AS preserved_users,
        (SELECT COUNT(*) FROM addresses) AS preserved_addresses,
        (SELECT COUNT(*) FROM categories) AS preserved_categories,
        (SELECT COUNT(*) FROM coupons) AS preserved_coupons,
        (SELECT COUNT(*) FROM events) AS preserved_events,
        (SELECT COUNT(*) FROM flyway_schema_history) AS preserved_flyway_rows;
END//
DELIMITER ;

-- This CALL is intentionally gated by unset approval/count variables above.
CALL dnstore_cleanup_simulated_catalog_v1();

-- Reached only if the procedure completed successfully.
DROP PROCEDURE dnstore_cleanup_simulated_catalog_v1;
