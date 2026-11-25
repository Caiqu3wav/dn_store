package com.dnstore.backend.model.enums;

/** Tipos de movimentação do livro de estoque. */
public enum StockMovementType {
    /** Entrada por nota de fornecedor. */
    ENTRADA_NOTA,
    /** Baixa por venda (reserva criada no checkout). */
    SAIDA_VENDA,
    /** Devolução ao estoque por cancelamento ou expiração do pedido. */
    DEVOLUCAO_PEDIDO,
    /** Ajuste manual feito por administrador (inventário, perda, avaria, devolução de cliente). */
    AJUSTE,
    /** Alteração de estoque feita pelo cadastro do produto (inclui o estoque inicial do produto novo). */
    EDICAO_CADASTRO,
    /** Saldo de abertura gravado na primeira movimentação de uma variante que já tinha estoque. */
    SALDO_INICIAL
}
