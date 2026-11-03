-- Read-only validation after the approved import and image association.
-- Run manually with a read-only account; this file contains SELECT/SHOW only.

SELECT DATABASE() AS database_name, VERSION() AS mysql_version;

SELECT
    COUNT(*) AS product_count,
    SUM(active = TRUE) AS active_product_count,
    SUM(active = FALSE) AS inactive_product_count
FROM products;

WITH expected_catalog AS (
    SELECT 'Camisa DN Ciclismo Vermelho/Amarelo' AS name, 209.99 AS price, 'Camisas de Ciclismo' AS category
    UNION ALL SELECT 'Camiseta DN Poliamida Azul/Branco', 59.99, 'Camiseta Poliamida'
    UNION ALL SELECT 'Camisa DN Ciclismo Azul/Branco', 209.99, 'Camisas de Ciclismo'
    UNION ALL SELECT 'Camisa DN Ciclismo Bordô/Amarelo', 209.99, 'Camisas de Ciclismo'
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Cinza-claro', 59.99, 'Camiseta Poliamida'
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Preto', 59.99, 'Camiseta Poliamida'
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Verde-limão', 59.99, 'Camiseta Poliamida'
    UNION ALL SELECT 'Camiseta DN Poliamida Azul/Verde-limão', 59.99, 'Camiseta Poliamida'
    UNION ALL SELECT 'Camiseta DN Poliamida Vermelho/Cinza-prata', 59.99, 'Camiseta Poliamida'
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Verde-limão', 59.99, 'Camiseta Poliamida'
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Laranja', 59.99, 'Camiseta Poliamida'
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Branco/Roxo', 59.99, 'Camiseta Poliamida'
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Vermelho', 59.99, 'Camiseta Poliamida'
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Branco', 59.99, 'Camiseta Poliamida'
    UNION ALL SELECT 'Camisa DN Ciclismo Marrom/Rosa-claro', 209.99, 'Camisas de Ciclismo'
    UNION ALL SELECT 'Camiseta DN Poliamida Vinho/Branco', 59.99, 'Camiseta Poliamida'
    UNION ALL SELECT 'Camisa DN Ciclismo Grafite/Branco', 209.99, 'Camisas de Ciclismo'
    UNION ALL SELECT 'Camiseta DN Poliamida Laranja/Azul', 59.99, 'Camiseta Poliamida'
    UNION ALL SELECT 'Boné DN Verde/Preto', 29.99, 'Bonés & Meias'
    UNION ALL SELECT 'Boné DN Preto/Vermelho', 69.99, 'Bonés & Meias'
    UNION ALL SELECT 'Meia DN Preta', 39.99, 'Bonés & Meias'
)
SELECT e.name AS expected_name, e.price AS expected_price, e.category AS expected_category,
       p.id AS product_id, p.name AS actual_name, p.price AS actual_price,
       p.active, c.name AS actual_category
FROM expected_catalog e
LEFT JOIN products p ON BINARY p.name = BINARY e.name
LEFT JOIN categories c ON c.id = p.category_id
WHERE p.id IS NULL
   OR p.price <> e.price
   OR p.active <> TRUE
   OR BINARY c.name <> BINARY e.category
ORDER BY e.name;

SELECT p.name, pv.size, COUNT(pv.id) AS variant_rows,
       MIN(pv.stock) AS min_stock, SUM(pv.stock) AS variant_stock_total,
       pp.stock AS aggregate_stock
FROM products p
LEFT JOIN product_variants pv ON pv.product_id = p.id
LEFT JOIN physical_products pp ON pp.id = p.id
GROUP BY p.id, p.name, pp.stock
ORDER BY p.name, pv.size;

SELECT BINARY name AS exact_name, COUNT(*) AS duplicate_count
FROM products
GROUP BY BINARY name
HAVING COUNT(*) > 1;

SELECT product_id, size, COUNT(*) AS duplicate_count
FROM product_variants
GROUP BY product_id, size
HAVING COUNT(*) > 1;

SELECT p.name, COUNT(pi.id) AS image_count,
       SUM(pi.is_main = TRUE) AS main_image_count
FROM products p
LEFT JOIN product_images pi ON pi.product_id = p.id
GROUP BY p.id, p.name
ORDER BY p.name;

SELECT COUNT(*) AS meia_image_count,
       SUM(pi.is_main = TRUE) AS meia_main_image_count
FROM products p
LEFT JOIN product_images pi ON pi.product_id = p.id
WHERE BINARY p.name = BINARY 'Meia DN Preta';

SELECT
    (SELECT COUNT(*) FROM users) AS users_preserved,
    (SELECT COUNT(*) FROM addresses) AS addresses_preserved,
    (SELECT COUNT(*) FROM categories) AS categories_present,
    (SELECT COUNT(*) FROM coupons) AS coupons_preserved,
    (SELECT COUNT(*) FROM events) AS events_preserved,
    (SELECT COUNT(*) FROM flyway_schema_history) AS flyway_history_rows;

SELECT version, description, script, success
FROM flyway_schema_history
ORDER BY installed_rank;
