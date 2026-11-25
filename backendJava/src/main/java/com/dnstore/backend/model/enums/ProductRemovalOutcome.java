package com.dnstore.backend.model.enums;

/**
 * Resultado da remoção de um produto: apagado de vez ou apenas arquivado (desativado)
 * quando já existem pedidos ou carrinhos que o referenciam.
 */
public enum ProductRemovalOutcome {
    DELETED,
    ARCHIVED
}
