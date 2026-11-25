-- MANUAL-ONLY. Not a Flyway migration. Do not run through the application.
-- Do not run against Aiven/production without explicit approval and a backup.
--
-- Registers the packed shipping dimensions and weight used by the Melhor Envio
-- quote (physical_products.height / width / depth / weight, in cm and kg).
-- "depth" is the package length: OrderService sends it as "length".
-- These are package measurements, not apparel sizes (PP/P/M/G/GG): variants
-- carry only size, stock and SKU and are never touched here.
--
-- Measurements (owner-provided):
--   Bone                      height 12  width 20  length 25  weight 0.20
--   Camisa de ciclismo        height  4  width 22  length 30  weight 0.25
--   Camisa de poliamida       height  3  width 22  length 28  weight 0.20
--   Meia                      height  3  width 10  length 20  weight 0.08
--
-- Products are matched by the official catalog name prefix (see
-- official_catalog_import.sql). The four prefixes are disjoint. "Camisa de
-- poliamida" is the catalog's "Camiseta DN Poliamida ..." line.
-- Prices, images, SKUs, stock and every other column are left unchanged.
-- Re-running is safe: it sets the same values again.
--
-- Usage: review the preview below, then run the whole file in one session:
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p <database> < set_shipping_dimensions.sql

SET NAMES utf8mb4;

-- 1. Preview (read-only): current values of the products this script will change.
SELECT p.name,
       pp.height, pp.width, pp.depth AS length, pp.weight
FROM products p
JOIN physical_products pp ON pp.id = p.id
WHERE p.name LIKE 'Boné DN %'
   OR p.name LIKE 'Camisa DN Ciclismo %'
   OR p.name LIKE 'Camiseta DN Poliamida %'
   OR p.name LIKE 'Meia DN %'
ORDER BY p.name;

-- 2. Update, all or nothing.
START TRANSACTION;

UPDATE physical_products pp
JOIN products p ON p.id = pp.id
SET pp.height = 12, pp.width = 20, pp.depth = 25, pp.weight = 0.20
WHERE p.name LIKE 'Boné DN %';

UPDATE physical_products pp
JOIN products p ON p.id = pp.id
SET pp.height = 4, pp.width = 22, pp.depth = 30, pp.weight = 0.25
WHERE p.name LIKE 'Camisa DN Ciclismo %';

UPDATE physical_products pp
JOIN products p ON p.id = pp.id
SET pp.height = 3, pp.width = 22, pp.depth = 28, pp.weight = 0.20
WHERE p.name LIKE 'Camiseta DN Poliamida %';

UPDATE physical_products pp
JOIN products p ON p.id = pp.id
SET pp.height = 3, pp.width = 10, pp.depth = 20, pp.weight = 0.08
WHERE p.name LIKE 'Meia DN %';

COMMIT;

-- 3. Verification (read-only): one row per product line, with how many products
-- carry exactly the expected measurements. Every "matching" must equal "total";
-- a line with total 0 means the catalog has no product with that name prefix.
SELECT line,
       COUNT(*) AS total,
       SUM(height = h AND width = w AND depth = l AND weight = kg) AS matching
FROM (
    SELECT 'Bone' AS line, 12 AS h, 20 AS w, 25 AS l, 0.20 AS kg, pp.*
    FROM products p JOIN physical_products pp ON pp.id = p.id
    WHERE p.name LIKE 'Boné DN %'
    UNION ALL
    SELECT 'Camisa de ciclismo', 4, 22, 30, 0.25, pp.*
    FROM products p JOIN physical_products pp ON pp.id = p.id
    WHERE p.name LIKE 'Camisa DN Ciclismo %'
    UNION ALL
    SELECT 'Camisa de poliamida', 3, 22, 28, 0.20, pp.*
    FROM products p JOIN physical_products pp ON pp.id = p.id
    WHERE p.name LIKE 'Camiseta DN Poliamida %'
    UNION ALL
    SELECT 'Meia', 3, 10, 20, 0.08, pp.*
    FROM products p JOIN physical_products pp ON pp.id = p.id
    WHERE p.name LIKE 'Meia DN %'
) t
GROUP BY line;
