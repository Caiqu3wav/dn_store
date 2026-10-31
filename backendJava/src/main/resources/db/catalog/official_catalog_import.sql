-- Manual, idempotent DN Store catalog import.
-- This file is intentionally outside db/migration and is never executed by Flyway.
-- It is a staging template: do not run it until mappings, prices, shipping data,
-- images, and actual inventory counts have been confirmed.
--
-- The current database contains 16 active products, but names/categories do not
-- safely identify every official item. In particular, the orange/blue image is
-- a cycling jersey while the official list also has an orange/blue polyamide
-- shirt. The possible additional cycling product is deliberately not imported.
--
-- Before CALLing apply_dn_catalog_import:
--   1. Set classification_confirmed=TRUE only for reviewed, unambiguous rows.
--   2. Set source_product_id for each existing product to preserve its ID.
--   3. For new products, supply price, weight (kg), dimensions (cm), and image URL.
--   4. Populate tmp_dn_variants with the real stock for every desired size.
--      Zero is allowed only when the real stock count is confirmed to be zero.
--   5. Apply Flyway V15 first; it adds active and the unique product/size key.
--
-- The procedure wraps all writes in one transaction and signals an error before
-- any write if the staging data is incomplete or ambiguous.

DROP PROCEDURE IF EXISTS apply_dn_catalog_import;
DROP TEMPORARY TABLE IF EXISTS tmp_dn_variants;
DROP TEMPORARY TABLE IF EXISTS tmp_dn_catalog;

CREATE TEMPORARY TABLE tmp_dn_catalog (
    official_name VARCHAR(150) NOT NULL PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL,
    classification_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    source_product_id VARCHAR(36) NULL,
    target_product_id VARCHAR(36) NOT NULL,
    price DECIMAL(10,2) NULL,
    description TEXT NULL,
    color VARCHAR(50) NULL,
    weight DOUBLE NULL,
    width DOUBLE NULL,
    height DOUBLE NULL,
    depth DOUBLE NULL,
    image_url VARCHAR(500) NULL
);

INSERT INTO tmp_dn_catalog
    (official_name, category_name, classification_confirmed, source_product_id, target_product_id)
VALUES
    ('Camisa DN Ciclismo Vermelho/Amarelo', 'Camisas de Ciclismo', FALSE, NULL, UUID()),
    ('Camiseta DN Poliamida Azul/Branco', 'Camisa Poliamida', FALSE, NULL, UUID()),
    ('Camisa DN Ciclismo Azul/Branco', 'Camisas de Ciclismo', FALSE, NULL, UUID()),
    ('Camisa DN Ciclismo Bordô/Amarelo', 'Camisas de Ciclismo', FALSE, NULL, UUID()),
    ('Camiseta DN Poliamida Preto/Cinza-claro', 'Camisa Poliamida', FALSE, NULL, UUID()),
    ('Camiseta DN Poliamida Branco/Preto', 'Camisa Poliamida', FALSE, NULL, UUID()),
    ('Camiseta DN Poliamida Preto/Verde-limão', 'Camisa Poliamida', FALSE, NULL, UUID()),
    ('Camiseta DN Poliamida Azul/Verde-limão', 'Camisa Poliamida', FALSE, NULL, UUID()),
    ('Camiseta DN Poliamida Vermelho/Cinza-prata', 'Camisa Poliamida', FALSE, NULL, UUID()),
    ('Camiseta DN Poliamida Cinza/Verde-limão', 'Camisa Poliamida', FALSE, NULL, UUID()),
    ('Camiseta DN Poliamida Preto/Laranja', 'Camisa Poliamida', FALSE, NULL, UUID()),
    ('Camiseta DN Poliamida Preto/Branco/Roxo', 'Camisa Poliamida', FALSE, NULL, UUID()),
    ('Camiseta DN Poliamida Branco/Vermelho', 'Camisa Poliamida', FALSE, NULL, UUID()),
    ('Camiseta DN Poliamida Cinza/Branco', 'Camisa Poliamida', FALSE, NULL, UUID()),
    ('Camisa DN Ciclismo Marrom/Rosa-claro', 'Camisas de Ciclismo', FALSE, NULL, UUID()),
    ('Camiseta DN Poliamida Vinho/Branco', 'Camisa Poliamida', FALSE, NULL, UUID()),
    ('Camisa DN Ciclismo Grafite/Branco', 'Camisas de Ciclismo', FALSE, NULL, UUID()),
    ('Camiseta DN Poliamida Laranja/Azul', 'Camisa Poliamida', FALSE, NULL, UUID()),
    ('Boné DN Verde/Preto', 'Bonés & Meias', FALSE, NULL, UUID()),
    ('Boné DN Preto/Vermelho', 'Bonés & Meias', FALSE, NULL, UUID());

CREATE TEMPORARY TABLE tmp_dn_variants (
    official_name VARCHAR(150) NOT NULL,
    size VARCHAR(10) NOT NULL,
    stock INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (official_name, size)
);

DELIMITER //
CREATE PROCEDURE apply_dn_catalog_import()
BEGIN
    DECLARE invalid_rows INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF (SELECT COUNT(*) FROM tmp_dn_catalog) <> 20
       OR EXISTS (
           SELECT 1 FROM tmp_dn_catalog
           WHERE classification_confirmed = FALSE
       ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Import aborted: review and confirm all 20 product mappings first.';
    END IF;

    SELECT COUNT(*) INTO invalid_rows
    FROM (
        SELECT c.official_name
        FROM tmp_dn_catalog c
        LEFT JOIN categories cat ON cat.name = c.category_name
        GROUP BY c.official_name
        HAVING COUNT(cat.id) <> 1
    ) missing_or_ambiguous_categories;
    IF invalid_rows > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Import aborted: a catalog category is missing or ambiguous.';
    END IF;

    IF EXISTS (
        SELECT source_product_id
        FROM tmp_dn_catalog
        WHERE source_product_id IS NOT NULL
        GROUP BY source_product_id
        HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Import aborted: one existing product was mapped more than once.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM tmp_dn_catalog c
        LEFT JOIN products p ON p.id = c.source_product_id
        WHERE c.source_product_id IS NOT NULL
          AND (p.id IS NULL OR p.product_type <> 'PhysicalProduct')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Import aborted: a source ID is missing or is not a physical product.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM tmp_dn_catalog c
        JOIN products p ON p.name = c.official_name
        WHERE c.source_product_id IS NOT NULL
          AND p.id <> c.source_product_id
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Import aborted: an official name is already assigned to another product.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM tmp_dn_catalog c
        WHERE c.source_product_id IS NULL
          AND (c.price IS NULL OR c.price < 0
               OR c.weight IS NULL OR c.weight <= 0
               OR c.width IS NULL OR c.width <= 0
               OR c.height IS NULL OR c.height <= 0
               OR c.depth IS NULL OR c.depth <= 0
               OR c.image_url IS NULL OR TRIM(c.image_url) = '')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Import aborted: new products require confirmed price, package data, and image URL.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM tmp_dn_catalog c
        LEFT JOIN tmp_dn_variants v ON v.official_name = c.official_name
        GROUP BY c.official_name
        HAVING COUNT(v.size) = 0
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Import aborted: provide the real stock count for at least one size per product.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM tmp_dn_variants v
        LEFT JOIN tmp_dn_catalog c ON c.official_name = v.official_name
        WHERE c.official_name IS NULL
           OR v.stock < 0
           OR BINARY v.size NOT IN ('PP', 'P', 'M', 'G', 'GG', 'BL P', 'BL M', 'BL G', 'Único')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Import aborted: invalid product, size, or stock in variation staging.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM tmp_dn_catalog c
        LEFT JOIN tmp_dn_variants v ON v.official_name = c.official_name
        GROUP BY c.official_name, c.category_name
        HAVING (c.category_name IN ('Camisa Poliamida', 'Camisas de Ciclismo')
                AND (COUNT(DISTINCT v.size) <> 8 OR SUM(v.size = 'Único') > 0))
            OR (c.category_name = 'Bonés & Meias'
                AND (COUNT(DISTINCT v.size) <> 1 OR SUM(v.size = 'Único') <> 1))
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Import aborted: apparel requires all eight sizes; caps require Único.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM tmp_dn_catalog c
        LEFT JOIN products p ON p.name = c.official_name
        WHERE c.source_product_id IS NULL
        GROUP BY c.official_name
        HAVING COUNT(p.id) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Import aborted: an official product name already has multiple records.';
    END IF;

    START TRANSACTION;

    UPDATE tmp_dn_catalog c
    LEFT JOIN products existing
      ON existing.id = c.source_product_id
      OR (c.source_product_id IS NULL AND existing.name = c.official_name)
    SET c.target_product_id = COALESCE(existing.id, c.target_product_id);

    UPDATE products p
    JOIN tmp_dn_catalog c ON c.target_product_id = p.id
    JOIN categories cat ON cat.name = c.category_name
    SET p.name = c.official_name,
        p.category_id = cat.id,
        p.price = COALESCE(c.price, p.price),
        p.description = COALESCE(c.description, p.description),
        p.color = COALESCE(c.color, p.color);

    INSERT INTO products
        (id, name, description, price, category_id, product_type, active, color)
    SELECT c.target_product_id, c.official_name, c.description, c.price, cat.id,
           'PhysicalProduct', TRUE, c.color
    FROM tmp_dn_catalog c
    JOIN categories cat ON cat.name = c.category_name
    LEFT JOIN products existing ON existing.id = c.target_product_id
    WHERE existing.id IS NULL;

    UPDATE physical_products pp
    JOIN tmp_dn_catalog c ON c.target_product_id = pp.id
    SET pp.weight = COALESCE(c.weight, pp.weight),
        pp.width = COALESCE(c.width, pp.width),
        pp.height = COALESCE(c.height, pp.height),
        pp.depth = COALESCE(c.depth, pp.depth);

    INSERT INTO physical_products (id, weight, width, height, depth, stock)
    SELECT c.target_product_id, c.weight, c.width, c.height, c.depth, 0
    FROM tmp_dn_catalog c
    LEFT JOIN physical_products pp ON pp.id = c.target_product_id
    WHERE pp.id IS NULL;

    INSERT INTO product_images (product_id, image_url, is_main)
    SELECT c.target_product_id, c.image_url,
           NOT EXISTS (
               SELECT 1 FROM product_images current_main
               WHERE current_main.product_id = c.target_product_id AND current_main.is_main = TRUE
           )
    FROM tmp_dn_catalog c
    LEFT JOIN product_images existing
      ON existing.product_id = c.target_product_id AND existing.image_url = c.image_url
    WHERE c.image_url IS NOT NULL AND existing.id IS NULL;

    -- Preserve old single-size rows and their order history, but do not expose
    -- "Único" as a clothing size once explicit apparel sizes are staged.
    UPDATE product_variants pv
    JOIN tmp_dn_catalog c ON c.target_product_id = pv.product_id
    SET pv.active = FALSE
    WHERE pv.size = 'Único'
      AND c.category_name IN ('Camisa Poliamida', 'Camisas de Ciclismo');

    INSERT INTO product_variants (product_id, size, stock, active, sku, color)
    SELECT c.target_product_id, v.size, v.stock, v.active,
           CONCAT('DN-', c.target_product_id, '-', REPLACE(v.size, ' ', '-')),
           c.color
    FROM tmp_dn_variants v
    JOIN tmp_dn_catalog c ON c.official_name = v.official_name
    ON DUPLICATE KEY UPDATE
        stock = VALUES(stock),
        active = VALUES(active),
        sku = VALUES(sku),
        color = VALUES(color);

    UPDATE physical_products pp
    JOIN tmp_dn_catalog c ON c.target_product_id = pp.id
    SET pp.stock = (
        SELECT COALESCE(SUM(pv.stock), 0)
        FROM product_variants pv
        WHERE pv.product_id = pp.id AND pv.active = TRUE
    );

    COMMIT;
END//
DELIMITER ;

-- This currently aborts at preflight because no remote mappings or stock counts
-- have been supplied. It is intentionally not called automatically by Flyway.
CALL apply_dn_catalog_import();
DROP PROCEDURE IF EXISTS apply_dn_catalog_import;
