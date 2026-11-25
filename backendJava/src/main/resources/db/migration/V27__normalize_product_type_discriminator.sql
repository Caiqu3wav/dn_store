-- Product usa @DiscriminatorColumn(name = "product_type") e a unica subclasse concreta e PhysicalProduct,
-- cujo valor de discriminador e 'PhysicalProduct'. O DEFAULT do V1 era 'physical': uma linha criada sem
-- informar o tipo (SQL manual, importacao) nao e lida pelo Hibernate.
--
-- Idempotente e segura para reexecucao. A ordem importa: o DEFAULT muda primeiro (so metadados, sem
-- reconstruir a tabela), para que nenhuma linha nova nasca com 'physical' enquanto as antigas sao
-- normalizadas. O MySQL compara product_type sem diferenciar maiusculas de minusculas, entao 'Physical'
-- e 'PHYSICAL' tambem sao normalizados. Valores NULL ou desconhecidos nao sao alterados.
ALTER TABLE products ALTER COLUMN product_type SET DEFAULT 'PhysicalProduct';

UPDATE products
SET product_type = 'PhysicalProduct'
WHERE product_type = 'physical';
