package com.dnstore.backend.model.enums;

import java.util.Locale;
import java.util.Optional;

public enum PaymentStatus {
    CREATING,
    RECONCILIATION_REQUIRED,
    PENDING,
    PAID,
    FAILED,
    OVERDUE,
    REFUND_REQUESTED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    DUNNING_REQUESTED,
    DUNNING_RECEIVED,
    CHARGEBACK_REQUESTED,
    CHARGEBACK_DISPUTE,
    AWAITING_CHARGEBACK_REVERSAL;

    public static PaymentStatus fromAsaas(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status Asaas ausente");
        }

        return switch (status.toUpperCase(Locale.ROOT)) {
            case "RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH" -> PAID;
            case "PENDING", "AWAITING_RISK_ANALYSIS" -> PENDING;
            case "OVERDUE" -> OVERDUE;
            case "REFUSED", "DELETED" -> FAILED;
            case "REFUND_REQUESTED" -> REFUND_REQUESTED;
            case "PARTIALLY_REFUNDED" -> PARTIALLY_REFUNDED;
            case "REFUNDED" -> REFUNDED;
            case "DUNNING_REQUESTED" -> DUNNING_REQUESTED;
            case "DUNNING_RECEIVED" -> DUNNING_RECEIVED;
            case "CHARGEBACK_REQUESTED" -> CHARGEBACK_REQUESTED;
            case "CHARGEBACK_DISPUTE" -> CHARGEBACK_DISPUTE;
            case "AWAITING_CHARGEBACK_REVERSAL" -> AWAITING_CHARGEBACK_REVERSAL;
            default -> throw new IllegalArgumentException("Status Asaas não suportado");
        };
    }

    public Optional<OrderStatus> orderStatus() {
        return switch (this) {
            case PAID -> Optional.of(OrderStatus.PAID);
            case FAILED -> Optional.of(OrderStatus.PAYMENT_FAILED);
            default -> Optional.empty();
        };
    }

    public boolean mayHaveAnActiveCharge() {
        return switch (this) {
            case CREATING, RECONCILIATION_REQUIRED, PENDING, OVERDUE,
                    REFUND_REQUESTED, PARTIALLY_REFUNDED, DUNNING_REQUESTED,
                    DUNNING_RECEIVED, CHARGEBACK_REQUESTED, CHARGEBACK_DISPUTE,
                    AWAITING_CHARGEBACK_REVERSAL -> true;
            case PAID, FAILED, REFUNDED -> false;
        };
    }

    public boolean mayStartAnotherAttempt() {
        return this == FAILED;
    }
}