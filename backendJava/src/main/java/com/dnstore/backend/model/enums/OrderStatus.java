package com.dnstore.backend.model.enums;

import java.util.List;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PAYMENT_FAILED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    EXPIRED,
    PAYMENT_REVIEW;

    /**
     * Pedidos com pagamento efetivamente confirmado (pelo Asaas ou manualmente por um admin).
     * Fora daqui ficam pendentes, falhos, expirados, cancelados e em revisão de pagamento.
     */
    public static final List<String> REVENUE_STATUSES = List.of(
            PAID.name(), PROCESSING.name(), SHIPPED.name(), DELIVERED.name());
}