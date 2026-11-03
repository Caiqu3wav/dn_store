-- Manual-only. Not a Flyway migration. Do not execute with --force.
-- Run only after reviewing the SELECT pre-checks and stopping application writers.
-- Use a dedicated MySQL 8.4 session with autocommit=1.

SET @dn_meia_expected_server_uuid =
    '2e9aeb7e-c815-11f1-b115-c617b308a14e';
SET @dn_meia_application_writes_stopped =
    COALESCE(@dn_meia_application_writes_stopped, 0);

-- Set to 1 only after stopping application writers and verifying the server UUID.
SET @dn_meia_no_active_innodb_transaction = NOT EXISTS (
    SELECT 1
    FROM information_schema.INNODB_TRX
    WHERE trx_mysql_thread_id = CONNECTION_ID()
);

SET @dn_meia_schema_compatible = (
    SELECT COUNT(*) = 14
       AND SUM(CASE WHEN
            (TABLE_NAME = 'products' AND COLUMN_NAME = 'id'
                AND DATA_TYPE IN ('varchar', 'char')
                AND CHARACTER_MAXIMUM_LENGTH >= 36)
         OR (TABLE_NAME = 'products' AND COLUMN_NAME = 'name'
                AND DATA_TYPE IN ('varchar', 'char')
                AND CHARACTER_MAXIMUM_LENGTH >= CHAR_LENGTH('Meia DN Preta'))
         OR (TABLE_NAME = 'products' AND COLUMN_NAME = 'active'
                AND DATA_TYPE IN ('tinyint', 'bit'))
         OR (TABLE_NAME = 'physical_products' AND COLUMN_NAME = 'id'
                AND DATA_TYPE IN ('varchar', 'char')
                AND CHARACTER_MAXIMUM_LENGTH >= 36)
         OR (TABLE_NAME = 'physical_products' AND COLUMN_NAME = 'stock'
                AND DATA_TYPE IN ('int', 'integer'))
         OR (TABLE_NAME = 'product_variants' AND COLUMN_NAME = 'id'
                AND DATA_TYPE IN ('varchar', 'char')
                AND CHARACTER_MAXIMUM_LENGTH >= 36)
         OR (TABLE_NAME = 'product_variants' AND COLUMN_NAME = 'product_id'
                AND DATA_TYPE IN ('varchar', 'char')
                AND CHARACTER_MAXIMUM_LENGTH >= 36)
         OR (TABLE_NAME = 'product_variants' AND COLUMN_NAME = 'size'
                AND DATA_TYPE IN ('varchar', 'char')
                AND CHARACTER_MAXIMUM_LENGTH >= CHAR_LENGTH('Único'))
         OR (TABLE_NAME = 'product_variants' AND COLUMN_NAME = 'stock'
                AND DATA_TYPE IN ('int', 'integer'))
         OR (TABLE_NAME = 'product_variants' AND COLUMN_NAME = 'active'
                AND DATA_TYPE IN ('tinyint', 'bit'))
         OR (TABLE_NAME = 'product_images' AND COLUMN_NAME = 'id'
                AND DATA_TYPE IN ('varchar', 'char')
                AND CHARACTER_MAXIMUM_LENGTH >= 36)
         OR (TABLE_NAME = 'product_images' AND COLUMN_NAME = 'product_id'
                AND DATA_TYPE IN ('varchar', 'char')
                AND CHARACTER_MAXIMUM_LENGTH >= 36)
         OR (TABLE_NAME = 'product_images' AND COLUMN_NAME = 'image_url'
                AND DATA_TYPE IN ('varchar', 'char')
                AND CHARACTER_MAXIMUM_LENGTH >=
                    CHAR_LENGTH('/assets/Images/products/meia-dn-preta.jpeg'))
         OR (TABLE_NAME = 'product_images' AND COLUMN_NAME = 'is_main'
                AND DATA_TYPE IN ('tinyint', 'bit'))
       THEN 1 ELSE 0 END) = 14
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND (
           (TABLE_NAME = 'products' AND COLUMN_NAME IN ('id', 'name', 'active'))
        OR (TABLE_NAME = 'physical_products' AND COLUMN_NAME IN ('id', 'stock'))
        OR (TABLE_NAME = 'product_variants'
            AND COLUMN_NAME IN ('id', 'product_id', 'size', 'stock', 'active'))
        OR (TABLE_NAME = 'product_images'
            AND COLUMN_NAME IN ('id', 'product_id', 'image_url', 'is_main'))
      )
);

SET @dn_meia_target_tables_innodb = (
    SELECT COUNT(*) = 4
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN (
          'products', 'physical_products', 'product_variants', 'product_images'
      )
      AND ENGINE = 'InnoDB'
);

SET @dn_meia_target_trigger_count = (
    SELECT COUNT(*)
    FROM information_schema.TRIGGERS
    WHERE TRIGGER_SCHEMA = DATABASE()
      AND EVENT_OBJECT_TABLE IN (
          'products', 'physical_products', 'product_variants', 'product_images'
      )
);

SET @dn_meia_procedure_exists = (
    SELECT COUNT(*)
    FROM information_schema.ROUTINES
    WHERE ROUTINE_SCHEMA = DATABASE()
      AND ROUTINE_TYPE = 'PROCEDURE'
      AND ROUTINE_NAME = 'dnstore_update_meia_dn_preta_v1'
);

SELECT DATABASE() AS database_name,
       VERSION() AS mysql_version,
       @@server_uuid AS server_uuid,
       @@autocommit AS autocommit,
       @dn_meia_no_active_innodb_transaction AS no_active_transaction,
       @dn_meia_schema_compatible AS schema_compatible,
       @dn_meia_target_tables_innodb AS target_tables_are_innodb,
       @dn_meia_target_trigger_count AS target_trigger_count,
       @dn_meia_procedure_exists AS procedure_already_exists;

-- A failed guard deliberately raises a SQL error before CREATE PROCEDURE.
-- Run in batch mode without --force so the client stops on that error.
SET @dn_meia_preflight_sql = IF(
       DATABASE() = 'defaultdb'
   AND VERSION() LIKE '8.4.%'
   AND @@autocommit = 1
   AND @@server_uuid = @dn_meia_expected_server_uuid
   AND @dn_meia_application_writes_stopped = 1
   AND @dn_meia_no_active_innodb_transaction = 1
   AND @dn_meia_schema_compatible = 1
   AND @dn_meia_target_tables_innodb = 1
   AND @dn_meia_target_trigger_count = 0
   AND @dn_meia_procedure_exists = 0,
   'SELECT 1',
   'SELECT * FROM `dnstore_preflight_must_abort_before_ddl`'
);

PREPARE dn_meia_preflight_guard FROM @dn_meia_preflight_sql;
EXECUTE dn_meia_preflight_guard;
DEALLOCATE PREPARE dn_meia_preflight_guard;

DELIMITER //

CREATE PROCEDURE dnstore_update_meia_dn_preta_v1()
SQL SECURITY INVOKER
BEGIN
    DECLARE v_product_id VARCHAR(36);
    DECLARE v_variant_id VARCHAR(36);
    DECLARE v_product_count INT DEFAULT 0;
    DECLARE v_physical_count INT DEFAULT 0;
    DECLARE v_variant_count INT DEFAULT 0;
    DECLARE v_unique_variant_count INT DEFAULT 0;
    DECLARE v_product_active BOOLEAN DEFAULT FALSE;
    DECLARE v_variant_active BOOLEAN DEFAULT FALSE;
    DECLARE v_physical_stock INT DEFAULT NULL;
    DECLARE v_variant_stock INT DEFAULT NULL;
    DECLARE v_target_image_count INT DEFAULT 0;
    DECLARE v_other_image_count INT DEFAULT 0;
    DECLARE v_image_other_owner_count INT DEFAULT 0;
    DECLARE v_trigger_count INT DEFAULT 0;
    DECLARE v_aggregate_stock INT DEFAULT NULL;
    DECLARE v_transaction_started BOOLEAN DEFAULT FALSE;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        IF v_transaction_started THEN
            ROLLBACK;
        END IF;
        RESIGNAL;
    END;

    IF DATABASE() <> 'defaultdb'
       OR VERSION() NOT LIKE '8.4.%'
       OR @@autocommit <> 1
       OR @@server_uuid <> @dn_meia_expected_server_uuid
       OR @dn_meia_application_writes_stopped <> 1
       OR EXISTS (
            SELECT 1
            FROM information_schema.INNODB_TRX
            WHERE trx_mysql_thread_id = CONNECTION_ID()
       ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT =
                'Aborted: verify database, version, UUID, autocommit, stopped writers, and no active session transaction.';
    END IF;

    SELECT COUNT(*) INTO v_trigger_count
    FROM information_schema.TRIGGERS
    WHERE TRIGGER_SCHEMA = DATABASE()
      AND EVENT_OBJECT_TABLE IN (
          'products', 'physical_products', 'product_variants', 'product_images'
      );

    IF v_trigger_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Aborted: target tables have triggers; review their effects.';
    END IF;

    START TRANSACTION;
    SET v_transaction_started = TRUE;

    SELECT COUNT(*) INTO v_product_count
    FROM products
    WHERE BINARY name = BINARY 'Meia DN Preta';

    IF v_product_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Aborted: expected exactly one Meia DN Preta product.';
    END IF;

    SELECT id, active INTO v_product_id, v_product_active
    FROM products
    WHERE BINARY name = BINARY 'Meia DN Preta'
    FOR UPDATE;

    IF v_product_active <> TRUE THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Aborted: product is inactive; it will not be activated automatically.';
    END IF;

    SELECT COUNT(*) INTO v_physical_count
    FROM physical_products
    WHERE id = v_product_id;

    IF v_physical_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Aborted: expected exactly one physical-product record.';
    END IF;

    SELECT stock INTO v_physical_stock
    FROM physical_products
    WHERE id = v_product_id
    FOR UPDATE;

    SELECT COUNT(*) INTO v_variant_count
    FROM product_variants
    WHERE product_id = v_product_id;

    SELECT COUNT(*) INTO v_unique_variant_count
    FROM product_variants
    WHERE product_id = v_product_id
      AND BINARY size = BINARY 'Único';

    IF v_variant_count <> 1 OR v_unique_variant_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Aborted: expected exactly one variant, size Único.';
    END IF;

    SELECT id, stock, active
      INTO v_variant_id, v_variant_stock, v_variant_active
    FROM product_variants
    WHERE product_id = v_product_id
      AND BINARY size = BINARY 'Único'
    FOR UPDATE;

    IF v_physical_stock <> 10
       OR v_variant_stock <> 10
       OR v_variant_active <> TRUE THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Aborted: expected aggregate stock 10 and active Único variant stock 10.';
    END IF;

    SELECT COUNT(*) INTO v_target_image_count
    FROM product_images
    WHERE product_id = v_product_id
      AND BINARY image_url =
          BINARY '/assets/Images/products/meia-dn-preta.jpeg';

    SELECT COUNT(*) INTO v_other_image_count
    FROM product_images
    WHERE product_id = v_product_id
      AND BINARY image_url <>
          BINARY '/assets/Images/products/meia-dn-preta.jpeg';

    SELECT COUNT(*) INTO v_image_other_owner_count
    FROM product_images pi
    JOIN products p ON p.id = pi.product_id
    WHERE BINARY pi.image_url =
          BINARY '/assets/Images/products/meia-dn-preta.jpeg'
      AND p.id <> v_product_id;

    IF v_target_image_count > 1
       OR v_other_image_count <> 0
       OR v_image_other_owner_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Aborted: existing image association requires manual review.';
    END IF;

    UPDATE product_variants
    SET stock = 0
    WHERE id = v_variant_id
      AND stock = 10
      AND active = TRUE;

    IF ROW_COUNT() <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Aborted: variant was not updated exactly once.';
    END IF;

    UPDATE physical_products
    SET stock = 0
    WHERE id = v_product_id
      AND stock = 10;

    IF ROW_COUNT() <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Aborted: aggregate stock was not updated exactly once.';
    END IF;

    IF v_target_image_count = 0 THEN
        INSERT INTO product_images (id, product_id, image_url, is_main)
        VALUES (
            UUID(),
            v_product_id,
            '/assets/Images/products/meia-dn-preta.jpeg',
            TRUE
        );
    ELSE
        UPDATE product_images
        SET is_main = TRUE
        WHERE product_id = v_product_id
          AND BINARY image_url =
              BINARY '/assets/Images/products/meia-dn-preta.jpeg';
    END IF;

    SELECT pp.stock,
           COALESCE(SUM(CASE WHEN pv.active = TRUE THEN pv.stock ELSE 0 END), 0)
      INTO v_physical_stock, v_aggregate_stock
    FROM physical_products pp
    LEFT JOIN product_variants pv ON pv.product_id = pp.id
    WHERE pp.id = v_product_id
    GROUP BY pp.stock;

    IF v_physical_stock <> 0
       OR v_aggregate_stock <> 0
       OR NOT EXISTS (
            SELECT 1 FROM products
            WHERE id = v_product_id AND active = TRUE
       )
       OR NOT EXISTS (
            SELECT 1 FROM product_variants
            WHERE id = v_variant_id AND stock = 0 AND active = TRUE
       )
       OR NOT EXISTS (
            SELECT 1 FROM product_images
            WHERE product_id = v_product_id
              AND BINARY image_url =
                  BINARY '/assets/Images/products/meia-dn-preta.jpeg'
              AND is_main = TRUE
       ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Aborted: final validation failed; changes rolled back.';
    END IF;

    COMMIT;
    SET v_transaction_started = FALSE;

    SELECT p.id AS product_id, p.name, p.active,
           pp.stock AS aggregate_stock,
           pv.id AS variant_id, pv.size, pv.stock AS variant_stock,
           pv.active AS variant_active, pi.image_url, pi.is_main
    FROM products p
    JOIN physical_products pp ON pp.id = p.id
    JOIN product_variants pv ON pv.product_id = p.id
    JOIN product_images pi ON pi.product_id = p.id
    WHERE p.id = v_product_id
      AND pv.id = v_variant_id
      AND BINARY pi.image_url =
          BINARY '/assets/Images/products/meia-dn-preta.jpeg';
END//

DELIMITER ;

CALL dnstore_update_meia_dn_preta_v1();
DROP PROCEDURE dnstore_update_meia_dn_preta_v1;
