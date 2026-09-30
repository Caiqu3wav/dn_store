package com.dnstore.backend.model.enums;

import java.util.Locale;

public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED;

    public static PaymentStatus fromAsaas(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status Asaas ausente");
        }

        return switch (status.toUpperCase(Locale.ROOT)) {
            case "RECEIVED", "CONFIRMED" -> PAID;
            case "PENDING", "AWAITING_RISK_ANALYSIS" -> PENDING;
            case "OVERDUE", "REFUSED", "DELETED" -> FAILED;
            case "REFUNDED" -> REFUNDED;
            case "REFUND_REQUESTED" -> PENDING;
            default -> throw new IllegalArgumentException("Status Asaas não suportado");
        };
    }

    public OrderStatus orderStatus() {
        return switch (this) {
            case PENDING -> OrderStatus.PENDING_PAYMENT;
            case PAID, REFUNDED -> OrderStatus.PAID;
            case FAILED -> OrderStatus.PAYMENT_FAILED;
        };
    }
}