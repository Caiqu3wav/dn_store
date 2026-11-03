-- MANUAL-ONLY, single-use catalog reconstruction for MySQL 8.4.
-- Never run through Flyway, the application, or before the approved cleanup.
-- This file has not been executed. It is expected to abort until every approval,
-- category row and inventory status has been confirmed.
--
-- Run the transaction preflight below on this same fresh, dedicated session
-- before any routine DDL. It detects active InnoDB transactions through
-- information_schema.INNODB_TRX; the dedicated session is required to rule out
-- unrelated non-InnoDB work. Proceed only when the preflight returns 1.
--
-- The 21 UUIDs are generated in this session's temporary staging table. A
-- successful import is deliberately not repeatable: the empty-catalog check
-- blocks another run. If a transaction rolls back, correct the staging rows and
-- CALL the procedure again in the same dedicated session (autocommit on, no
-- transaction already open).
--
-- All official prices below are owner-confirmed. Promotional prices are NULL.
-- Product names/categories and 20 image paths reflect reviewed local mappings;
-- the Meia DN Preta intentionally has no image until one is confirmed.
-- Three products have pending inventory and are imported without variants;
-- they remain visible but cannot be purchased. Shipping dimensions are unknown:
-- physical_products defaults them to zero, which prevents freight quotes until
-- actual measurements are supplied. Do not treat zero as a measurement.
-- If the CALL aborts, its helper procedure remains; correct the staged values
-- and retry only in the same session, or manually remove the helper after review.

CREATE TEMPORARY TABLE tmp_dn_catalog (
    official_name VARCHAR(150) NOT NULL PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    image_url VARCHAR(500) NULL,
    category_confirmed BOOLEAN NOT NULL DEFAULT TRUE,
    inventory_pending BOOLEAN NOT NULL DEFAULT FALSE,
    image_decision_confirmed BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO tmp_dn_catalog
    (official_name, category_name, price, product_id, image_url, inventory_pending)
VALUES
    ('Camisa DN Ciclismo Vermelho/Amarelo', 'Camisas de Ciclismo', 209.99, UUID(), '/assets/Images/products/camisa-dn-ciclismo-vermelho-amarelo.jpeg', FALSE),
    ('Camiseta DN Poliamida Azul/Branco', 'Camiseta Poliamida', 59.99, UUID(), '/assets/Images/products/camiseta-dn-poliamida-azul-branco.jpeg', FALSE),
    ('Camisa DN Ciclismo Azul/Branco', 'Camisas de Ciclismo', 209.99, UUID(), '/assets/Images/products/camisa-dn-ciclismo-azul-branco.jpeg', FALSE),
    ('Camisa DN Ciclismo Bordô/Amarelo', 'Camisas de Ciclismo', 209.99, UUID(), '/assets/Images/products/Camisa-DN-Ciclismo-Bordo-Amarelo.jpeg', FALSE),
    ('Camiseta DN Poliamida Preto/Cinza-claro', 'Camiseta Poliamida', 59.99, UUID(), '/assets/Images/products/Camiseta-DN-Poliamida-Preto-Cinza-claro.jpeg', FALSE),
    ('Camiseta DN Poliamida Branco/Preto', 'Camiseta Poliamida', 59.99, UUID(), '/assets/Images/products/Camiseta-DN-Poliamida-Branco-Preto.jpeg', FALSE),
    ('Camiseta DN Poliamida Preto/Verde-limão', 'Camiseta Poliamida', 59.99, UUID(), '/assets/Images/products/Camiseta-DN-Poliamida-Preto-Verde-limão.jpeg', TRUE),
    ('Camiseta DN Poliamida Azul/Verde-limão', 'Camiseta Poliamida', 59.99, UUID(), '/assets/Images/products/Camiseta-DN-Poliamida-Azul-Verde-limão.jpeg', TRUE),
    ('Camiseta DN Poliamida Vermelho/Cinza-prata', 'Camiseta Poliamida', 59.99, UUID(), '/assets/Images/products/Camiseta-DN-Poliamida-Vermelho-Cinza-prata.jpeg', FALSE),
    ('Camiseta DN Poliamida Cinza/Verde-limão', 'Camiseta Poliamida', 59.99, UUID(), '/assets/Images/products/camiseta-dn-poliamida-cinza-verde-limao.jpeg', FALSE),
    ('Camiseta DN Poliamida Preto/Laranja', 'Camiseta Poliamida', 59.99, UUID(), '/assets/Images/products/Camiseta-DN-Poliamida-Preto-Laranja.jpeg', FALSE),
    ('Camiseta DN Poliamida Preto/Branco/Roxo', 'Camiseta Poliamida', 59.99, UUID(), '/assets/Images/products/camiseta-dn-poliamida-preto-branco-roxo.jpeg', FALSE),
    ('Camiseta DN Poliamida Branco/Vermelho', 'Camiseta Poliamida', 59.99, UUID(), '/assets/Images/products/camiseta-dn-poliamida-branco-vermelho.jpeg', FALSE),
    ('Camiseta DN Poliamida Cinza/Branco', 'Camiseta Poliamida', 59.99, UUID(), '/assets/Images/products/Camiseta-DN-Poliamida-Cinza-Branco.jpeg', FALSE),
    ('Camisa DN Ciclismo Marrom/Rosa-claro', 'Camisas de Ciclismo', 209.99, UUID(), '/assets/Images/products/camisa-dn-ciclismo-marrom-rosa-claro.jpeg', FALSE),
    ('Camiseta DN Poliamida Vinho/Branco', 'Camiseta Poliamida', 59.99, UUID(), '/assets/Images/products/camiseta-dn-poliamida-vinho-branco.jpeg', FALSE),
    ('Camisa DN Ciclismo Grafite/Branco', 'Camisas de Ciclismo', 209.99, UUID(), '/assets/Images/products/Camisa-DN-Ciclismo-Grafite-Branco.jpeg', TRUE),
    ('Camiseta DN Poliamida Laranja/Azul', 'Camiseta Poliamida', 59.99, UUID(), '/assets/Images/products/Camiseta-DN-Poliamida-Laranja-Azul.jpeg', FALSE),
    ('Boné DN Verde/Preto', 'Bonés & Meias', 29.99, UUID(), '/assets/Images/products/bone-dn-verde-preto.jpeg', FALSE),
    ('Boné DN Preto/Vermelho', 'Bonés & Meias', 69.99, UUID(), '/assets/Images/products/Bone-DN-Preto-Vermelho.jpeg', FALSE),
    ('Meia DN Preta', 'Bonés & Meias', 39.99, UUID(), NULL, FALSE);

-- Only owner-confirmed size/stock pairs are included. Explicit zeroes are
-- preserved. Do not copy legacy aggregate stock or old Único variants.
CREATE TEMPORARY TABLE tmp_dn_variants (
    official_name VARCHAR(150) NOT NULL,
    size VARCHAR(10) NOT NULL,
    stock INT NOT NULL,
    stock_confirmed BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (official_name, size)
);

INSERT INTO tmp_dn_variants (official_name, size, stock)
VALUES
    ('Camisa DN Ciclismo Marrom/Rosa-claro', 'PP', 4),
    ('Camisa DN Ciclismo Marrom/Rosa-claro', 'P', 4),
    ('Camisa DN Ciclismo Marrom/Rosa-claro', 'M', 7),
    ('Camisa DN Ciclismo Marrom/Rosa-claro', 'G', 6),
    ('Camisa DN Ciclismo Marrom/Rosa-claro', 'GG', 6),
    ('Camisa DN Ciclismo Vermelho/Amarelo', 'PP', 0),
    ('Camisa DN Ciclismo Vermelho/Amarelo', 'P', 0),
    ('Camisa DN Ciclismo Vermelho/Amarelo', 'M', 2),
    ('Camisa DN Ciclismo Vermelho/Amarelo', 'G', 0),
    ('Camisa DN Ciclismo Vermelho/Amarelo', 'GG', 3),
    ('Camisa DN Ciclismo Azul/Branco', 'PP', 2),
    ('Camisa DN Ciclismo Azul/Branco', 'P', 1),
    ('Camisa DN Ciclismo Azul/Branco', 'M', 2),
    ('Camisa DN Ciclismo Azul/Branco', 'G', 0),
    ('Camisa DN Ciclismo Azul/Branco', 'GG', 3),
    ('Camisa DN Ciclismo Bordô/Amarelo', 'PP', 3),
    ('Camisa DN Ciclismo Bordô/Amarelo', 'P', 2),
    ('Camisa DN Ciclismo Bordô/Amarelo', 'M', 6),
    ('Camisa DN Ciclismo Bordô/Amarelo', 'G', 1),
    ('Camisa DN Ciclismo Bordô/Amarelo', 'GG', 3),
    ('Camiseta DN Poliamida Cinza/Branco', 'BL P', 8),
    ('Camiseta DN Poliamida Cinza/Branco', 'BL M', 8),
    ('Camiseta DN Poliamida Cinza/Branco', 'BL G', 7),
    ('Camiseta DN Poliamida Cinza/Branco', 'P', 12),
    ('Camiseta DN Poliamida Cinza/Branco', 'M', 6),
    ('Camiseta DN Poliamida Cinza/Branco', 'G', 7),
    ('Camiseta DN Poliamida Cinza/Branco', 'GG', 10),
    ('Camiseta DN Poliamida Vinho/Branco', 'BL P', 4),
    ('Camiseta DN Poliamida Vinho/Branco', 'BL M', 6),
    ('Camiseta DN Poliamida Vinho/Branco', 'BL G', 5),
    ('Camiseta DN Poliamida Vinho/Branco', 'P', 12),
    ('Camiseta DN Poliamida Vinho/Branco', 'M', 6),
    ('Camiseta DN Poliamida Vinho/Branco', 'G', 4),
    ('Camiseta DN Poliamida Vinho/Branco', 'GG', 3),
    ('Camiseta DN Poliamida Laranja/Azul', 'BL P', 8),
    ('Camiseta DN Poliamida Laranja/Azul', 'BL M', 10),
    ('Camiseta DN Poliamida Laranja/Azul', 'BL G', 7),
    ('Camiseta DN Poliamida Laranja/Azul', 'P', 22),
    ('Camiseta DN Poliamida Laranja/Azul', 'M', 32),
    ('Camiseta DN Poliamida Laranja/Azul', 'G', 25),
    ('Camiseta DN Poliamida Laranja/Azul', 'GG', 16),
    ('Camiseta DN Poliamida Azul/Branco', 'BL P', 7),
    ('Camiseta DN Poliamida Azul/Branco', 'BL M', 6),
    ('Camiseta DN Poliamida Azul/Branco', 'BL G', 4),
    ('Camiseta DN Poliamida Azul/Branco', 'P', 10),
    ('Camiseta DN Poliamida Azul/Branco', 'M', 4),
    ('Camiseta DN Poliamida Azul/Branco', 'G', 0),
    ('Camiseta DN Poliamida Azul/Branco', 'GG', 0),
    ('Camiseta DN Poliamida Preto/Cinza-claro', 'BL P', 4),
    ('Camiseta DN Poliamida Preto/Cinza-claro', 'BL M', 5),
    ('Camiseta DN Poliamida Preto/Cinza-claro', 'BL G', 6),
    ('Camiseta DN Poliamida Preto/Cinza-claro', 'P', 0),
    ('Camiseta DN Poliamida Preto/Cinza-claro', 'M', 4),
    ('Camiseta DN Poliamida Preto/Cinza-claro', 'G', 0),
    ('Camiseta DN Poliamida Preto/Cinza-claro', 'GG', 0),
    ('Camiseta DN Poliamida Branco/Preto', 'BL P', 7),
    ('Camiseta DN Poliamida Branco/Preto', 'BL M', 8),
    ('Camiseta DN Poliamida Branco/Preto', 'BL G', 6),
    ('Camiseta DN Poliamida Branco/Preto', 'P', 11),
    ('Camiseta DN Poliamida Branco/Preto', 'M', 16),
    ('Camiseta DN Poliamida Branco/Preto', 'G', 10),
    ('Camiseta DN Poliamida Branco/Preto', 'GG', 12),
    ('Camiseta DN Poliamida Vermelho/Cinza-prata', 'BL P', 8),
    ('Camiseta DN Poliamida Vermelho/Cinza-prata', 'BL M', 7),
    ('Camiseta DN Poliamida Vermelho/Cinza-prata', 'BL G', 6),
    ('Camiseta DN Poliamida Vermelho/Cinza-prata', 'P', 12),
    ('Camiseta DN Poliamida Vermelho/Cinza-prata', 'M', 22),
    ('Camiseta DN Poliamida Vermelho/Cinza-prata', 'G', 2),
    ('Camiseta DN Poliamida Vermelho/Cinza-prata', 'GG', 0),
    ('Camiseta DN Poliamida Cinza/Verde-limão', 'BL P', 3),
    ('Camiseta DN Poliamida Cinza/Verde-limão', 'BL M', 4),
    ('Camiseta DN Poliamida Cinza/Verde-limão', 'BL G', 2),
    ('Camiseta DN Poliamida Cinza/Verde-limão', 'P', 8),
    ('Camiseta DN Poliamida Cinza/Verde-limão', 'M', 12),
    ('Camiseta DN Poliamida Cinza/Verde-limão', 'G', 0),
    ('Camiseta DN Poliamida Cinza/Verde-limão', 'GG', 0),
    ('Camiseta DN Poliamida Preto/Laranja', 'BL P', 3),
    ('Camiseta DN Poliamida Preto/Laranja', 'BL M', 4),
    ('Camiseta DN Poliamida Preto/Laranja', 'BL G', 2),
    ('Camiseta DN Poliamida Preto/Laranja', 'P', 8),
    ('Camiseta DN Poliamida Preto/Laranja', 'M', 12),
    ('Camiseta DN Poliamida Preto/Laranja', 'G', 0),
    ('Camiseta DN Poliamida Preto/Laranja', 'GG', 0),
    ('Camiseta DN Poliamida Preto/Branco/Roxo', 'BL P', 2),
    ('Camiseta DN Poliamida Preto/Branco/Roxo', 'BL M', 4),
    ('Camiseta DN Poliamida Preto/Branco/Roxo', 'BL G', 6),
    ('Camiseta DN Poliamida Preto/Branco/Roxo', 'P', 7),
    ('Camiseta DN Poliamida Preto/Branco/Roxo', 'M', 22),
    ('Camiseta DN Poliamida Preto/Branco/Roxo', 'G', 1),
    ('Camiseta DN Poliamida Preto/Branco/Roxo', 'GG', 5),
    ('Camiseta DN Poliamida Branco/Vermelho', 'BL P', 2),
    ('Camiseta DN Poliamida Branco/Vermelho', 'BL M', 4),
    ('Camiseta DN Poliamida Branco/Vermelho', 'BL G', 6),
    ('Camiseta DN Poliamida Branco/Vermelho', 'P', 7),
    ('Camiseta DN Poliamida Branco/Vermelho', 'M', 22),
    ('Camiseta DN Poliamida Branco/Vermelho', 'G', 1),
    ('Camiseta DN Poliamida Branco/Vermelho', 'GG', 5),
    ('Boné DN Verde/Preto', 'Único', 16),
    ('Boné DN Preto/Vermelho', 'Único', 8),
    ('Meia DN Preta', 'Único', 10);

-- CREATE PROCEDURE implicitly commits; this check must return 1 before
-- continuing. A permission or metadata error must be resolved, not bypassed.
SET @dn_catalog_pre_ddl_no_active_innodb_transaction = NOT EXISTS (
    SELECT 1
    FROM information_schema.INNODB_TRX
    WHERE trx_mysql_thread_id = CONNECTION_ID()
);
SELECT IF(@@autocommit = 1, @dn_catalog_pre_ddl_no_active_innodb_transaction, 0)
    AS no_active_innodb_transaction_before_procedure_ddl;

DELIMITER //
CREATE PROCEDURE apply_dn_official_catalog_v2()
SQL SECURITY INVOKER
BEGIN
    DECLARE invalid_rows INT DEFAULT 0;
    DECLARE v_catalog_rows INT DEFAULT 0;
    DECLARE v_variant_rows INT DEFAULT 0;
    DECLARE v_pending_product_rows INT DEFAULT 0;
    DECLARE v_invalid_pending_products INT DEFAULT 0;
    DECLARE v_duplicate_staged_names INT DEFAULT 0;
    DECLARE v_product_rows INT DEFAULT 0;
    DECLARE v_physical_product_rows INT DEFAULT 0;
    DECLARE v_imported_variant_rows INT DEFAULT 0;
    DECLARE v_mismatched_imported_variants INT DEFAULT 0;
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
       OR COALESCE(@dn_catalog_pre_ddl_no_active_innodb_transaction, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Catalog import aborted: verify MySQL 8.4 defaultdb, autocommit, and the fresh-session InnoDB transaction preflight.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM flyway_schema_history
        WHERE version = '14' AND success = 1
    ) OR NOT EXISTS (
        SELECT 1 FROM flyway_schema_history
        WHERE version = '15' AND success = 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Catalog import aborted: apply and verify Flyway V15 after cleanup first.';
    END IF;

    SELECT COUNT(*) INTO v_catalog_rows
    FROM tmp_dn_catalog;

    SELECT COUNT(*) INTO v_duplicate_staged_names
    FROM (
        SELECT official_name
        FROM tmp_dn_catalog
        GROUP BY official_name
        HAVING COUNT(*) <> 1
    ) duplicate_names;

    IF v_catalog_rows <> 21
       OR (SELECT COUNT(*) FROM products) <> 0
       OR (SELECT COUNT(*) FROM physical_products) <> 0
       OR (SELECT COUNT(*) FROM product_variants) <> 0
       OR (SELECT COUNT(*) FROM product_images) <> 0
       OR (SELECT COUNT(*) FROM orders) <> 0
       OR (SELECT COUNT(*) FROM order_items) <> 0
       OR (SELECT COUNT(*) FROM payments) <> 0
       OR (SELECT COUNT(*) FROM shipments) <> 0
       OR (SELECT COUNT(*) FROM shipment_tracking) <> 0
       OR (SELECT COUNT(*) FROM carts) <> 0
       OR (SELECT COUNT(*) FROM cart_items) <> 0
       OR (SELECT COUNT(*) FROM favorites) <> 0
       OR v_duplicate_staged_names <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Catalog import aborted: expected 21 staged products and a fully cleaned catalog.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM tmp_dn_catalog c
        LEFT JOIN categories cat
          ON BINARY cat.name = BINARY c.category_name
        GROUP BY c.official_name
        HAVING COUNT(cat.id) <> 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Catalog import aborted: each category must match exactly one preserved category row.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM tmp_dn_catalog
        WHERE category_confirmed = FALSE
           OR image_decision_confirmed = FALSE
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Catalog import aborted: confirm category assignments and image decisions.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM tmp_dn_catalog
        WHERE (category_name = 'Camisas de Ciclismo' AND price <> 209.99)
           OR (category_name = 'Camiseta Poliamida' AND price <> 59.99)
           OR (official_name = 'Boné DN Verde/Preto' AND price <> 29.99)
           OR (official_name = 'Boné DN Preto/Vermelho' AND price <> 69.99)
           OR (official_name = 'Meia DN Preta' AND price <> 39.99)
           OR (category_name NOT IN ('Camisas de Ciclismo', 'Camiseta Poliamida', 'Bonés & Meias'))
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Catalog import aborted: staged price or category differs from the official catalog.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM tmp_dn_catalog c
        LEFT JOIN tmp_dn_variants v ON v.official_name = c.official_name
        GROUP BY c.official_name, c.inventory_pending
        HAVING (c.inventory_pending = FALSE AND COUNT(v.size) = 0)
            OR (c.inventory_pending = TRUE AND COUNT(v.size) <> 0)
            OR SUM(v.stock_confirmed = FALSE OR v.stock < 0) > 0
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Catalog import aborted: inventory-pending products must have no variants; all other products need confirmed non-negative size/stock rows.';
    END IF;

    SELECT COUNT(*) INTO v_variant_rows
    FROM tmp_dn_variants;

    SELECT COUNT(*) INTO v_pending_product_rows
    FROM tmp_dn_catalog
    WHERE inventory_pending = TRUE;

    SELECT COUNT(*) INTO v_invalid_pending_products
    FROM tmp_dn_catalog
    WHERE inventory_pending = TRUE
      AND BINARY official_name NOT IN (
          BINARY 'Camisa DN Ciclismo Grafite/Branco',
          BINARY 'Camiseta DN Poliamida Preto/Verde-limão',
          BINARY 'Camiseta DN Poliamida Azul/Verde-limão'
      );

    IF v_variant_rows <> 100
       OR v_pending_product_rows <> 3
       OR v_invalid_pending_products <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Catalog import aborted: expected exactly 100 confirmed variants and the three explicitly pending products.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM tmp_dn_variants v
        LEFT JOIN tmp_dn_catalog c ON c.official_name = v.official_name
        WHERE c.official_name IS NULL
           OR BINARY v.size NOT IN ('PP', 'P', 'M', 'G', 'GG', 'BL P', 'BL M', 'BL G', 'Único')
           OR (c.category_name IN ('Camisas de Ciclismo', 'Camiseta Poliamida') AND v.size = 'Único')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Catalog import aborted: variant refers to an unknown product or an unsupported size.';
    END IF;

    SELECT COUNT(*) INTO invalid_rows
    FROM (
        SELECT official_name
        FROM tmp_dn_catalog
        WHERE BINARY official_name IN (
            SELECT BINARY name FROM products
        )
    ) existing_official_names;
    IF invalid_rows <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Catalog import aborted: products already exist; do not infer identity or duplicate the catalog.';
    END IF;

    START TRANSACTION;
    SET v_transaction_started = TRUE;

    INSERT INTO products
        (id, name, description, price, promotional_price, category_id, product_type, active, color)
    SELECT c.product_id, c.official_name, NULL, c.price, NULL, cat.id,
           'PhysicalProduct', TRUE, NULL
    FROM tmp_dn_catalog c
    JOIN categories cat ON BINARY cat.name = BINARY c.category_name;

    -- V1 defaults unknown freight dimensions to zero; DeliveryService rejects
    -- those values for quotes until actual measurements are supplied.
    INSERT INTO physical_products (id, stock)
    SELECT c.product_id, COALESCE(SUM(v.stock), 0)
    FROM tmp_dn_catalog c
    LEFT JOIN tmp_dn_variants v ON v.official_name = c.official_name
    GROUP BY c.product_id;

    INSERT INTO product_variants (product_id, size, stock, active, sku, color)
    SELECT c.product_id, v.size, v.stock, TRUE,
           CONCAT('DN-', c.product_id, '-', REPLACE(v.size, ' ', '-')),
           NULL
    FROM tmp_dn_catalog c
    JOIN tmp_dn_variants v ON v.official_name = c.official_name;

    SELECT COUNT(*) INTO v_product_rows
    FROM products;

    SELECT COUNT(*) INTO v_physical_product_rows
    FROM physical_products;

    SELECT COUNT(*) INTO v_imported_variant_rows
    FROM product_variants;

    SELECT COUNT(*) INTO v_mismatched_imported_variants
    FROM tmp_dn_variants expected
    JOIN tmp_dn_catalog c ON c.official_name = expected.official_name
    LEFT JOIN product_variants actual
      ON actual.product_id = c.product_id
     AND BINARY actual.size = BINARY expected.size
    WHERE actual.id IS NULL
       OR actual.stock <> expected.stock;

    IF v_product_rows <> 21
       OR v_physical_product_rows <> 21
       OR v_imported_variant_rows <> 100
       OR v_mismatched_imported_variants <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Catalog import aborted: inserted product, variant, or stock rows differ from the approved staging.';
    END IF;

    COMMIT;
    SET v_transaction_started = FALSE;

    SELECT COUNT(*) AS created_products FROM products;
    SELECT p.name, p.price, pv.size, pv.stock, pv.active
    FROM products p
    JOIN product_variants pv ON pv.product_id = p.id
    ORDER BY p.name, pv.size;
END//
DELIMITER ;

-- Intentionally aborts until all staging confirmations are supplied.
CALL apply_dn_official_catalog_v2();

-- Reached only after a successful import.
DROP PROCEDURE apply_dn_official_catalog_v2;
