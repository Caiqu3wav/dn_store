-- Estende o livro de estoque (V28) de forma aditiva. Uma única instrução ALTER: no MySQL 8 o DDL de uma
-- instrução é atômico (tudo ou nada), então uma falha não deixa a tabela pela metade.
--  * Linhas antigas ficam com stock_before/category/stock_entry_id/idempotency_key NULL.
--  * seq: sequência global e monotônica, usada para ordenar o log e detectar quebra de cadeia. As linhas
--    que já existem recebem seq na ordem de leitura da tabela (não necessariamente cronológica entre si);
--    por isso a detecção de quebra de cadeia só compara linhas novas (stock_before preenchido).
--  * ck_sm_balance só avalia linhas com stock_before preenchido, então não pode reprovar dado legado.
ALTER TABLE stock_movements
    ADD COLUMN seq             BIGINT      NOT NULL AUTO_INCREMENT,
    ADD COLUMN stock_before    INT         NULL,
    ADD COLUMN category        VARCHAR(30) NULL,
    ADD COLUMN stock_entry_id  VARCHAR(36) NULL,
    ADD COLUMN idempotency_key VARCHAR(64) NULL,
    ADD UNIQUE KEY uk_sm_seq (seq),
    ADD CONSTRAINT fk_sm_entry       FOREIGN KEY (stock_entry_id) REFERENCES stock_entry (id),
    ADD CONSTRAINT uk_sm_idempotency UNIQUE (idempotency_key),
    ADD CONSTRAINT ck_sm_balance     CHECK (stock_before IS NULL OR stock_after = stock_before + delta),
    ADD INDEX idx_sm_variant_seq   (variant_id, seq),
    ADD INDEX idx_sm_type_created  (type, created_at),
    ADD INDEX idx_sm_created       (created_at),
    ADD INDEX idx_sm_entry         (stock_entry_id),
    ADD INDEX idx_sm_actor_created (performed_by, created_at);
