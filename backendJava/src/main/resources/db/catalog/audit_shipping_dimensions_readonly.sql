-- READ-ONLY. Somente SELECT: nenhuma escrita, nenhuma migration.
-- Confere os dados físicos usados na cotação do Melhor Envio (physical_products) e como eles se
-- ligam a products e product_variants. Rode com um usuário somente leitura, por exemplo:
--   mysql --default-character-set=utf8mb4 --init-command="SET SESSION TRANSACTION READ ONLY" \
--         --ssl-mode=REQUIRED -h <host> -P <porta> -u <usuario> -p <banco> < audit_shipping_dimensions_readonly.sql
-- Unidades: weight em kg; width/height/depth em cm. "depth" é o comprimento ("length") enviado ao
-- Melhor Envio (OrderService.shippingItems).

SET NAMES utf8mb4;

-- 1. Produtos por tipo e situação. O Hibernate só lê 'PhysicalProduct' (nome da entidade);
--    qualquer outro valor em product_type (ex.: o default 'physical' do V1) quebra a leitura do produto.
SELECT p.product_type, p.active, COUNT(*) AS products
FROM products p
GROUP BY p.product_type, p.active
ORDER BY p.product_type, p.active;

-- 2. Produtos ativos, medidas e variantes (esperado: 21 linhas).
SELECT p.name, p.product_type, p.price,
       pp.weight, pp.width, pp.height, pp.depth AS length,
       COUNT(v.id) AS variants,
       SUM(CASE WHEN v.active THEN 1 ELSE 0 END) AS active_variants,
       COALESCE(SUM(CASE WHEN v.active THEN v.stock ELSE 0 END), 0) AS active_variant_stock
FROM products p
LEFT JOIN physical_products pp ON pp.id = p.id
LEFT JOIN product_variants v ON v.product_id = p.id
WHERE p.active = TRUE
GROUP BY p.id, p.name, p.product_type, p.price, pp.weight, pp.width, pp.height, pp.depth
ORDER BY p.name;

-- 3. Problemas que fazem a cotação falhar (esperado: nenhuma linha).
SELECT p.name, 'sem linha em physical_products' AS problem
FROM products p
LEFT JOIN physical_products pp ON pp.id = p.id
WHERE p.active = TRUE AND pp.id IS NULL
UNION ALL
SELECT p.name, 'product_type diferente de PhysicalProduct'
FROM products p
WHERE p.active = TRUE AND p.product_type <> 'PhysicalProduct'
UNION ALL
SELECT p.name, 'peso ou dimensao nula/zero/negativa'
FROM products p JOIN physical_products pp ON pp.id = p.id
WHERE p.active = TRUE
  AND (pp.weight IS NULL OR pp.weight <= 0 OR pp.width IS NULL OR pp.width <= 0
       OR pp.height IS NULL OR pp.height <= 0 OR pp.depth IS NULL OR pp.depth <= 0)
UNION ALL
SELECT p.name, 'produto ativo sem variante ativa'
FROM products p
WHERE p.active = TRUE
  AND NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.active = TRUE)
UNION ALL
SELECT CONCAT('physical_products.id=', pp.id), 'linha órfã (sem produto)'
FROM physical_products pp
LEFT JOIN products p ON p.id = pp.id
WHERE p.id IS NULL;

-- 4. Medidas abaixo do mínimo que costuma valer para os Correios (11 x 2 x 16 cm: largura, altura,
--    comprimento). A documentação do Melhor Envio não fixa limites: confirme com uma cotação real.
SELECT p.name, pp.width, pp.height, pp.depth AS length,
       CONCAT_WS(', ',
           IF(pp.width < 11, 'largura < 11', NULL),
           IF(pp.height < 2, 'altura < 2', NULL),
           IF(pp.depth < 16, 'comprimento < 16', NULL)) AS below_minimum
FROM products p JOIN physical_products pp ON pp.id = p.id
WHERE p.active = TRUE AND (pp.width < 11 OR pp.height < 2 OR pp.depth < 16)
ORDER BY p.name;

-- 5. Medidas repetidas por grupo de produtos (confere se o lote atualizado ficou consistente).
SELECT pp.weight, pp.width, pp.height, pp.depth AS length, COUNT(*) AS products
FROM products p JOIN physical_products pp ON pp.id = p.id
WHERE p.active = TRUE
GROUP BY pp.weight, pp.width, pp.height, pp.depth
ORDER BY products DESC;
