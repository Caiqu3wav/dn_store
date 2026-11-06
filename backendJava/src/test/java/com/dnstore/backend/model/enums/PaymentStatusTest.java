package com.dnstore.backend.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class PaymentStatusTest {

    @ParameterizedTest
    @MethodSource("asaasStatuses")
    void fromAsaas_shouldMapSupportedStatuses(String asaasStatus, PaymentStatus expected) {
        assertThat(PaymentStatus.fromAsaas(asaasStatus)).isEqualTo(expected);
    }

    @Test
    void fromAsaas_shouldRejectUnknownOrMissingStatus() {
        assertThatThrownBy(() -> PaymentStatus.fromAsaas("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentStatus.fromAsaas(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentStatus.fromAsaas(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("nonPaidStatuses")
    void orderStatus_shouldNotTreatNonPaidFinancialStatesAsPaid(PaymentStatus status) {
        assertThat(status.orderStatus()).isEmpty();
    }

    private static Stream<Arguments> asaasStatuses() {
        return Stream.of(
                Arguments.of("PENDING", PaymentStatus.PENDING),
                Arguments.of("CONFIRMED", PaymentStatus.PAID),
                Arguments.of("RECEIVED", PaymentStatus.PAID),
                Arguments.of("OVERDUE", PaymentStatus.OVERDUE),
                Arguments.of("REFUSED", PaymentStatus.FAILED),
                Arguments.of("REFUND_REQUESTED", PaymentStatus.REFUND_REQUESTED),
                Arguments.of("PARTIALLY_REFUNDED", PaymentStatus.PARTIALLY_REFUNDED),
                Arguments.of("REFUNDED", PaymentStatus.REFUNDED),
                Arguments.of("AWAITING_RISK_ANALYSIS", PaymentStatus.PENDING),
                Arguments.of("RECEIVED_IN_CASH", PaymentStatus.PAID),
                Arguments.of("DUNNING_REQUESTED", PaymentStatus.DUNNING_REQUESTED),
                Arguments.of("DUNNING_RECEIVED", PaymentStatus.DUNNING_RECEIVED),
                Arguments.of("CHARGEBACK_REQUESTED", PaymentStatus.CHARGEBACK_REQUESTED),
                Arguments.of("CHARGEBACK_DISPUTE", PaymentStatus.CHARGEBACK_DISPUTE),
                Arguments.of("AWAITING_CHARGEBACK_REVERSAL", PaymentStatus.AWAITING_CHARGEBACK_REVERSAL),
                Arguments.of("DELETED", PaymentStatus.FAILED));
    }

    private static Stream<Arguments> nonPaidStatuses() {
        return Stream.of(
                Arguments.of(PaymentStatus.PENDING),
                Arguments.of(PaymentStatus.OVERDUE),
                Arguments.of(PaymentStatus.REFUND_REQUESTED),
                Arguments.of(PaymentStatus.PARTIALLY_REFUNDED),
                Arguments.of(PaymentStatus.REFUNDED),
                Arguments.of(PaymentStatus.DUNNING_REQUESTED),
                Arguments.of(PaymentStatus.CHARGEBACK_REQUESTED),
                Arguments.of(PaymentStatus.CHARGEBACK_DISPUTE),
                Arguments.of(PaymentStatus.AWAITING_CHARGEBACK_REVERSAL));
    }
}
