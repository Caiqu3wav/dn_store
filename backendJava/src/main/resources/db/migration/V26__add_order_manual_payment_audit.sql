-- Confirmação manual de pagamento por administrador: marca no pedido + trilha de auditoria.
-- Migration apenas aditiva (coluna com DEFAULT e tabela nova); não altera nem remove dados existentes.

ALTER TABLE orders
    ADD COLUMN manual_payment_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE order_status_audit (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    actor_user_id VARCHAR(36) NOT NULL,
    change_type VARCHAR(40) NOT NULL,
    previous_status VARCHAR(40) NOT NULL,
    new_status VARCHAR(40) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_order_status_audit_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_status_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
    INDEX idx_order_status_audit_order_time (order_id, created_at)
);
