-- Manual, idempotent association of the confirmed local product photographs.
-- Do not run through Flyway or against Aiven without explicit approval.
-- Product IDs are resolved by exact official product name at execution time;
-- the procedure aborts unless each name identifies exactly one existing row.
-- Existing image rows and URLs are preserved. Only the is_main flags are
-- aligned to make the confirmed local photograph the primary image.

CREATE TEMPORARY TABLE tmp_dn_store_official_product_image_mapping (
    product_name VARCHAR(150) NOT NULL PRIMARY KEY,
    image_url VARCHAR(500) NOT NULL
);

INSERT INTO tmp_dn_store_official_product_image_mapping (product_name, image_url)
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

DELIMITER //
CREATE PROCEDURE dnstore_associate_confirmed_local_product_images_20260626()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF (SELECT COUNT(*) FROM tmp_dn_store_official_product_image_mapping) <> 20
       OR EXISTS (
           SELECT 1
           FROM tmp_dn_store_official_product_image_mapping mapping
           LEFT JOIN products product
             ON BINARY product.name = BINARY mapping.product_name
           GROUP BY mapping.product_name
           HAVING COUNT(product.id) <> 1
       ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Image association aborted: each official product name must match exactly one existing product.';
    END IF;

    START TRANSACTION;

    INSERT INTO product_images (product_id, image_url, is_main)
    SELECT product.id,
           mapping.image_url,
           TRUE
    FROM tmp_dn_store_official_product_image_mapping mapping
    JOIN products product
      ON BINARY product.name = BINARY mapping.product_name
    LEFT JOIN product_images existing
      ON existing.product_id = product.id
     AND existing.image_url = mapping.image_url
    WHERE existing.id IS NULL;

    UPDATE product_images product_image
    JOIN products product ON product.id = product_image.product_id
    JOIN tmp_dn_store_official_product_image_mapping mapping
      ON BINARY mapping.product_name = BINARY product.name
    SET product_image.is_main = (product_image.image_url = mapping.image_url)
    WHERE product_image.is_main <> (product_image.image_url = mapping.image_url);

    COMMIT;
END//
DELIMITER ;

-- Manual invocation only; this file is not a Flyway migration.
CALL dnstore_associate_confirmed_local_product_images_20260626();
DROP PROCEDURE IF EXISTS dnstore_associate_confirmed_local_product_images_20260626;
DROP TEMPORARY TABLE IF EXISTS tmp_dn_store_official_product_image_mapping;
