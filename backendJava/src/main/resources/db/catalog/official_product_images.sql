-- MANUAL-ONLY, idempotent association of the 20 owner-confirmed local images.
-- Run only after the approved catalog import. This is not a Flyway migration.
-- Camisa-DN-Ciclismo-Laranja-Azul.jpeg is intentionally not mapped.
-- No matching Meia DN Preta image is confirmed; keep it without an image.
-- If the CALL aborts, the helper procedure remains; inspect the error before retrying.
-- Use a fresh dedicated MySQL 8.4 session with autocommit=1. The pre-DDL
-- transaction check detects active InnoDB transactions; the dedicated session
-- also rules out unrelated non-InnoDB work. Continue only if it returns 1.

CREATE TEMPORARY TABLE tmp_dn_official_images (
    product_name VARCHAR(150) NOT NULL PRIMARY KEY,
    image_url VARCHAR(500) NOT NULL
);

INSERT INTO tmp_dn_official_images (product_name, image_url)
VALUES
    ('Camisa DN Ciclismo Vermelho/Amarelo', '/assets/Images/products/camisa-dn-ciclismo-vermelho-amarelo.jpeg'),
    ('Camiseta DN Poliamida Azul/Branco', '/assets/Images/products/camiseta-dn-poliamida-azul-branco.jpeg'),
    ('Camisa DN Ciclismo Azul/Branco', '/assets/Images/products/camisa-dn-ciclismo-azul-branco.jpeg'),
    ('Camisa DN Ciclismo Bordô/Amarelo', '/assets/Images/products/Camisa-DN-Ciclismo-Bordo-Amarelo.jpeg'),
    ('Camiseta DN Poliamida Preto/Cinza-claro', '/assets/Images/products/Camiseta-DN-Poliamida-Preto-Cinza-claro.jpeg'),
    ('Camiseta DN Poliamida Branco/Preto', '/assets/Images/products/Camiseta-DN-Poliamida-Branco-Preto.jpeg'),
    ('Camiseta DN Poliamida Preto/Verde-limão', '/assets/Images/products/Camiseta-DN-Poliamida-Preto-Verde-limão.jpeg'),
    ('Camiseta DN Poliamida Azul/Verde-limão', '/assets/Images/products/Camiseta-DN-Poliamida-Azul-Verde-limão.jpeg'),
    ('Camiseta DN Poliamida Vermelho/Cinza-prata', '/assets/Images/products/Camiseta-DN-Poliamida-Vermelho-Cinza-prata.jpeg'),
    ('Camiseta DN Poliamida Cinza/Verde-limão', '/assets/Images/products/camiseta-dn-poliamida-cinza-verde-limao.jpeg'),
    ('Camiseta DN Poliamida Preto/Laranja', '/assets/Images/products/Camiseta-DN-Poliamida-Preto-Laranja.jpeg'),
    ('Camiseta DN Poliamida Preto/Branco/Roxo', '/assets/Images/products/camiseta-dn-poliamida-preto-branco-roxo.jpeg'),
    ('Camiseta DN Poliamida Branco/Vermelho', '/assets/Images/products/camiseta-dn-poliamida-branco-vermelho.jpeg'),
    ('Camiseta DN Poliamida Cinza/Branco', '/assets/Images/products/Camiseta-DN-Poliamida-Cinza-Branco.jpeg'),
    ('Camisa DN Ciclismo Marrom/Rosa-claro', '/assets/Images/products/camisa-dn-ciclismo-marrom-rosa-claro.jpeg'),
    ('Camiseta DN Poliamida Vinho/Branco', '/assets/Images/products/camiseta-dn-poliamida-vinho-branco.jpeg'),
    ('Camisa DN Ciclismo Grafite/Branco', '/assets/Images/products/Camisa-DN-Ciclismo-Grafite-Branco.jpeg'),
    ('Camiseta DN Poliamida Laranja/Azul', '/assets/Images/products/Camiseta-DN-Poliamida-Laranja-Azul.jpeg'),
    ('Boné DN Verde/Preto', '/assets/Images/products/bone-dn-verde-preto.jpeg'),
    ('Boné DN Preto/Vermelho', '/assets/Images/products/Bone-DN-Preto-Vermelho.jpeg');

-- CREATE PROCEDURE implicitly commits; this check must return 1 before
-- continuing. A permission or metadata error must be resolved, not bypassed.
SET @dn_images_pre_ddl_no_active_innodb_transaction = NOT EXISTS (
    SELECT 1
    FROM information_schema.INNODB_TRX
    WHERE trx_mysql_thread_id = CONNECTION_ID()
);
SELECT IF(@@autocommit = 1, @dn_images_pre_ddl_no_active_innodb_transaction, 0)
    AS no_active_innodb_transaction_before_procedure_ddl;

DELIMITER //
CREATE PROCEDURE dnstore_associate_official_images_v2()
SQL SECURITY INVOKER
BEGIN
    DECLARE v_mapping_rows INT DEFAULT 0;
    DECLARE v_invalid_mapping_targets INT DEFAULT 0;
    DECLARE v_active_products INT DEFAULT 0;
    DECLARE v_total_products INT DEFAULT 0;
    DECLARE v_meia_products INT DEFAULT 0;
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
       OR COALESCE(@dn_images_pre_ddl_no_active_innodb_transaction, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Image association aborted: verify MySQL 8.4 defaultdb, autocommit, and the fresh-session InnoDB transaction preflight.';
    END IF;

    SELECT COUNT(*) INTO v_mapping_rows
    FROM tmp_dn_official_images;

    SELECT COUNT(*) INTO v_invalid_mapping_targets
    FROM (
        SELECT mapping.product_name
        FROM tmp_dn_official_images mapping
        LEFT JOIN products p ON BINARY p.name = BINARY mapping.product_name
        GROUP BY mapping.product_name
        HAVING COUNT(p.id) <> 1
    ) invalid_targets;

    SELECT COUNT(*) INTO v_active_products
    FROM products
    WHERE active = TRUE;

    SELECT COUNT(*) INTO v_total_products
    FROM products;

    SELECT COUNT(*) INTO v_meia_products
    FROM products
    WHERE BINARY name = BINARY 'Meia DN Preta';

    IF v_mapping_rows <> 20
       OR v_active_products <> 21
       OR v_total_products <> 21
       OR v_invalid_mapping_targets <> 0
       OR v_meia_products <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Image association aborted: expected exactly the 21-product catalog and unique image targets.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM tmp_dn_official_images mapping
        JOIN products p ON BINARY p.name = BINARY mapping.product_name
        JOIN product_images pi
          ON pi.product_id = p.id AND pi.image_url = mapping.image_url
        GROUP BY p.id, mapping.image_url
        HAVING COUNT(pi.id) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Image association aborted: duplicate image rows need manual review.';
    END IF;

    START TRANSACTION;
    SET v_transaction_started = TRUE;

    INSERT INTO product_images (product_id, image_url, is_main)
    SELECT p.id, mapping.image_url, TRUE
    FROM tmp_dn_official_images mapping
    JOIN products p ON BINARY p.name = BINARY mapping.product_name
    LEFT JOIN product_images existing
      ON existing.product_id = p.id AND existing.image_url = mapping.image_url
    WHERE existing.id IS NULL;

    UPDATE product_images pi
    JOIN products p ON p.id = pi.product_id
    JOIN tmp_dn_official_images mapping
      ON BINARY mapping.product_name = BINARY p.name
    SET pi.is_main = (pi.image_url = mapping.image_url)
    WHERE pi.is_main <> (pi.image_url = mapping.image_url);

    COMMIT;
    SET v_transaction_started = FALSE;

    SELECT p.name, pi.image_url, pi.is_main
    FROM products p
    LEFT JOIN product_images pi ON pi.product_id = p.id
    WHERE p.active = TRUE
    ORDER BY p.name, pi.is_main DESC;

    SELECT 'PENDING_IMAGE_CONFIRMATION' AS status,
           'Meia DN Preta has no confirmed local photograph; no image row was created.' AS detail
    WHERE NOT EXISTS (
        SELECT 1 FROM product_images pi
        JOIN products p ON p.id = pi.product_id
        WHERE BINARY p.name = BINARY 'Meia DN Preta'
    );
END//
DELIMITER ;

CALL dnstore_associate_official_images_v2();
DROP PROCEDURE dnstore_associate_official_images_v2;
