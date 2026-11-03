-- Read-only final gate. The caller must require the single result to be OK.
WITH expected_products AS (
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
),
expected_variants AS (
    SELECT 'Camisa DN Ciclismo Marrom/Rosa-claro' AS name, 'PP' AS size, 4 AS stock
    UNION ALL SELECT 'Camisa DN Ciclismo Marrom/Rosa-claro', 'P', 4
    UNION ALL SELECT 'Camisa DN Ciclismo Marrom/Rosa-claro', 'M', 7
    UNION ALL SELECT 'Camisa DN Ciclismo Marrom/Rosa-claro', 'G', 6
    UNION ALL SELECT 'Camisa DN Ciclismo Marrom/Rosa-claro', 'GG', 6
    UNION ALL SELECT 'Camisa DN Ciclismo Vermelho/Amarelo', 'PP', 0
    UNION ALL SELECT 'Camisa DN Ciclismo Vermelho/Amarelo', 'P', 0
    UNION ALL SELECT 'Camisa DN Ciclismo Vermelho/Amarelo', 'M', 2
    UNION ALL SELECT 'Camisa DN Ciclismo Vermelho/Amarelo', 'G', 0
    UNION ALL SELECT 'Camisa DN Ciclismo Vermelho/Amarelo', 'GG', 3
    UNION ALL SELECT 'Camisa DN Ciclismo Azul/Branco', 'PP', 2
    UNION ALL SELECT 'Camisa DN Ciclismo Azul/Branco', 'P', 1
    UNION ALL SELECT 'Camisa DN Ciclismo Azul/Branco', 'M', 2
    UNION ALL SELECT 'Camisa DN Ciclismo Azul/Branco', 'G', 0
    UNION ALL SELECT 'Camisa DN Ciclismo Azul/Branco', 'GG', 3
    UNION ALL SELECT 'Camisa DN Ciclismo Bordô/Amarelo', 'PP', 3
    UNION ALL SELECT 'Camisa DN Ciclismo Bordô/Amarelo', 'P', 2
    UNION ALL SELECT 'Camisa DN Ciclismo Bordô/Amarelo', 'M', 6
    UNION ALL SELECT 'Camisa DN Ciclismo Bordô/Amarelo', 'G', 1
    UNION ALL SELECT 'Camisa DN Ciclismo Bordô/Amarelo', 'GG', 3
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Branco', 'BL P', 8
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Branco', 'BL M', 8
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Branco', 'BL G', 7
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Branco', 'P', 12
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Branco', 'M', 6
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Branco', 'G', 7
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Branco', 'GG', 10
    UNION ALL SELECT 'Camiseta DN Poliamida Vinho/Branco', 'BL P', 4
    UNION ALL SELECT 'Camiseta DN Poliamida Vinho/Branco', 'BL M', 6
    UNION ALL SELECT 'Camiseta DN Poliamida Vinho/Branco', 'BL G', 5
    UNION ALL SELECT 'Camiseta DN Poliamida Vinho/Branco', 'P', 12
    UNION ALL SELECT 'Camiseta DN Poliamida Vinho/Branco', 'M', 6
    UNION ALL SELECT 'Camiseta DN Poliamida Vinho/Branco', 'G', 4
    UNION ALL SELECT 'Camiseta DN Poliamida Vinho/Branco', 'GG', 3
    UNION ALL SELECT 'Camiseta DN Poliamida Laranja/Azul', 'BL P', 8
    UNION ALL SELECT 'Camiseta DN Poliamida Laranja/Azul', 'BL M', 10
    UNION ALL SELECT 'Camiseta DN Poliamida Laranja/Azul', 'BL G', 7
    UNION ALL SELECT 'Camiseta DN Poliamida Laranja/Azul', 'P', 22
    UNION ALL SELECT 'Camiseta DN Poliamida Laranja/Azul', 'M', 32
    UNION ALL SELECT 'Camiseta DN Poliamida Laranja/Azul', 'G', 25
    UNION ALL SELECT 'Camiseta DN Poliamida Laranja/Azul', 'GG', 16
    UNION ALL SELECT 'Camiseta DN Poliamida Azul/Branco', 'BL P', 7
    UNION ALL SELECT 'Camiseta DN Poliamida Azul/Branco', 'BL M', 6
    UNION ALL SELECT 'Camiseta DN Poliamida Azul/Branco', 'BL G', 4
    UNION ALL SELECT 'Camiseta DN Poliamida Azul/Branco', 'P', 10
    UNION ALL SELECT 'Camiseta DN Poliamida Azul/Branco', 'M', 4
    UNION ALL SELECT 'Camiseta DN Poliamida Azul/Branco', 'G', 0
    UNION ALL SELECT 'Camiseta DN Poliamida Azul/Branco', 'GG', 0
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Cinza-claro', 'BL P', 4
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Cinza-claro', 'BL M', 5
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Cinza-claro', 'BL G', 6
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Cinza-claro', 'P', 0
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Cinza-claro', 'M', 4
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Cinza-claro', 'G', 0
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Cinza-claro', 'GG', 0
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Preto', 'BL P', 7
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Preto', 'BL M', 8
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Preto', 'BL G', 6
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Preto', 'P', 11
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Preto', 'M', 16
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Preto', 'G', 10
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Preto', 'GG', 12
    UNION ALL SELECT 'Camiseta DN Poliamida Vermelho/Cinza-prata', 'BL P', 8
    UNION ALL SELECT 'Camiseta DN Poliamida Vermelho/Cinza-prata', 'BL M', 7
    UNION ALL SELECT 'Camiseta DN Poliamida Vermelho/Cinza-prata', 'BL G', 6
    UNION ALL SELECT 'Camiseta DN Poliamida Vermelho/Cinza-prata', 'P', 12
    UNION ALL SELECT 'Camiseta DN Poliamida Vermelho/Cinza-prata', 'M', 22
    UNION ALL SELECT 'Camiseta DN Poliamida Vermelho/Cinza-prata', 'G', 2
    UNION ALL SELECT 'Camiseta DN Poliamida Vermelho/Cinza-prata', 'GG', 0
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Verde-limão', 'BL P', 3
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Verde-limão', 'BL M', 4
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Verde-limão', 'BL G', 2
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Verde-limão', 'P', 8
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Verde-limão', 'M', 12
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Verde-limão', 'G', 0
    UNION ALL SELECT 'Camiseta DN Poliamida Cinza/Verde-limão', 'GG', 0
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Laranja', 'BL P', 3
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Laranja', 'BL M', 4
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Laranja', 'BL G', 2
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Laranja', 'P', 8
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Laranja', 'M', 12
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Laranja', 'G', 0
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Laranja', 'GG', 0
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Branco/Roxo', 'BL P', 2
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Branco/Roxo', 'BL M', 4
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Branco/Roxo', 'BL G', 6
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Branco/Roxo', 'P', 7
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Branco/Roxo', 'M', 22
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Branco/Roxo', 'G', 1
    UNION ALL SELECT 'Camiseta DN Poliamida Preto/Branco/Roxo', 'GG', 5
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Vermelho', 'BL P', 2
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Vermelho', 'BL M', 4
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Vermelho', 'BL G', 6
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Vermelho', 'P', 7
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Vermelho', 'M', 22
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Vermelho', 'G', 1
    UNION ALL SELECT 'Camiseta DN Poliamida Branco/Vermelho', 'GG', 5
    UNION ALL SELECT 'Boné DN Verde/Preto', 'Único', 16
    UNION ALL SELECT 'Boné DN Preto/Vermelho', 'Único', 8
    UNION ALL SELECT 'Meia DN Preta', 'Único', 10
),
checks AS (
    SELECT
        (SELECT COUNT(*) FROM products) AS product_count,
        (SELECT COUNT(*)
         FROM expected_products e
         WHERE (
             SELECT COUNT(*)
             FROM products p
             JOIN categories c ON c.id = p.category_id
             WHERE BINARY p.name = BINARY e.name
               AND p.price = e.price
               AND p.active = TRUE
               AND BINARY c.name = BINARY e.category
         ) = 1) AS matching_products,
        (SELECT COUNT(*) FROM product_variants) AS variant_count,
        (SELECT COUNT(*)
         FROM expected_variants e
         JOIN products p ON BINARY p.name = BINARY e.name
         JOIN product_variants v
           ON v.product_id = p.id
          AND BINARY v.size = BINARY e.size
          AND v.stock = e.stock
          AND v.active = TRUE) AS matching_variants,
        (SELECT COUNT(*)
         FROM physical_products pp
         JOIN products p ON p.id = pp.id
         WHERE pp.stock <> COALESCE((
             SELECT SUM(v.stock)
             FROM product_variants v
             WHERE v.product_id = p.id
         ), 0)) AS aggregate_stock_mismatches,
        (SELECT COUNT(*)
         FROM products p
         WHERE NOT EXISTS (
             SELECT 1 FROM physical_products pp WHERE pp.id = p.id
         )) AS missing_physical_rows,
        (SELECT COUNT(*) FROM product_images) AS image_count,
        (SELECT COUNT(*) FROM product_images WHERE is_main = TRUE) AS main_image_count,
        (SELECT COUNT(*)
         FROM product_images pi
         JOIN products p ON p.id = pi.product_id
         WHERE BINARY p.name = BINARY 'Meia DN Preta') AS meia_image_count,
        (SELECT COUNT(*)
         FROM flyway_schema_history
         WHERE version = '15' AND success = 1) AS successful_v15_rows
)
SELECT IF(
    product_count = 21
    AND matching_products = 21
    AND variant_count = 100
    AND matching_variants = 100
    AND aggregate_stock_mismatches = 0
    AND missing_physical_rows = 0
    AND image_count = 20
    AND main_image_count = 20
    AND meia_image_count = 0
    AND successful_v15_rows = 1,
    'OK',
    'FAIL'
) AS catalog_assertion
FROM checks;
