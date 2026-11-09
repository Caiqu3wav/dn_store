package com.dnstore.backend.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentTransitionPolicyTest {
    @Test
    void terminalRefundAndChargebackStatesCannotReturnToPaid() {
        assertThat(PaymentTransitionPolicy.isAllowed(PaymentStatus.REFUNDED, PaymentStatus.PAID)).isFalse();
        assertThat(PaymentTransitionPolicy.isAllowed(PaymentStatus.CHARGEBACK_REVERSED, PaymentStatus.PAID)).isFalse();
    }

    @Test
    void oldPendingEventCannotRegressPaidPayment() {
        assertThat(PaymentTransitionPolicy.isAllowed(PaymentStatus.PAID, PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentTransitionPolicy.isAllowed(PaymentStatus.CHARGEBACK_DISPUTE, PaymentStatus.PAID)).isFalse();
    }

    @Test
    void refundAndChargebackFlowsAreExplicit() {
        assertThat(PaymentTransitionPolicy.isAllowed(PaymentStatus.PAID, PaymentStatus.REFUND_REQUESTED)).isTrue();
        assertThat(PaymentTransitionPolicy.isAllowed(PaymentStatus.REFUND_REQUESTED, PaymentStatus.REFUND_IN_PROGRESS)).isTrue();
        assertThat(PaymentTransitionPolicy.isAllowed(PaymentStatus.CHARGEBACK_REQUESTED, PaymentStatus.CHARGEBACK_DISPUTE)).isTrue();
        assertThat(PaymentTransitionPolicy.isAllowed(PaymentStatus.CHARGEBACK_DISPUTE, PaymentStatus.CHARGEBACK_REVERSED)).isTrue();
    }
}
