
package com.dnstore.backend.model.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Central, deterministic policy for provider-driven financial transitions.
 */
public final class PaymentTransitionPolicy {

        private PaymentTransitionPolicy() {
        }

        public static boolean isAllowed(
                        PaymentStatus current,
                        PaymentStatus next) {

                if (current == null || next == null) {
                        return false;
                }

                if (current == next) {
                        return true;
                }

                return allowedFrom(current).contains(next);
        }

        private static Set<PaymentStatus> allowedFrom(PaymentStatus current) {
                return switch (current) {

                        case CREATING, RECONCILIATION_REQUIRED ->
                                EnumSet.of(
                                                PaymentStatus.PENDING,
                                                PaymentStatus.PAID,
                                                PaymentStatus.FAILED,
                                                PaymentStatus.OVERDUE);

                        case PENDING ->
                                EnumSet.of(
                                                PaymentStatus.PAID,
                                                PaymentStatus.FAILED,
                                                PaymentStatus.OVERDUE);

                        case OVERDUE ->
                                EnumSet.of(
                                                PaymentStatus.PAID,
                                                PaymentStatus.FAILED);

                        case FAILED ->
                                EnumSet.of(PaymentStatus.PAID);

                        case PAID ->
                                EnumSet.of(
                                                PaymentStatus.REFUND_REQUESTED,
                                                PaymentStatus.REFUND_IN_PROGRESS,
                                                PaymentStatus.PARTIALLY_REFUNDED,
                                                PaymentStatus.REFUNDED,
                                                PaymentStatus.REFUND_DENIED,
                                                PaymentStatus.CHARGEBACK_REQUESTED,
                                                PaymentStatus.CHARGEBACK_DISPUTE);

                        case REFUND_REQUESTED ->
                                EnumSet.of(
                                                PaymentStatus.REFUND_IN_PROGRESS,
                                                PaymentStatus.PARTIALLY_REFUNDED,
                                                PaymentStatus.REFUNDED,
                                                PaymentStatus.REFUND_DENIED);

                        case REFUND_IN_PROGRESS ->
                                EnumSet.of(
                                                PaymentStatus.PARTIALLY_REFUNDED,
                                                PaymentStatus.REFUNDED,
                                                PaymentStatus.REFUND_DENIED);

                        case PARTIALLY_REFUNDED ->
                                EnumSet.of(
                                                PaymentStatus.REFUND_IN_PROGRESS,
                                                PaymentStatus.REFUNDED,
                                                PaymentStatus.REFUND_DENIED,
                                                PaymentStatus.CHARGEBACK_REQUESTED,
                                                PaymentStatus.CHARGEBACK_DISPUTE);

                        case REFUND_DENIED ->
                                EnumSet.of(
                                                PaymentStatus.REFUND_REQUESTED,
                                                PaymentStatus.REFUND_IN_PROGRESS,
                                                PaymentStatus.PARTIALLY_REFUNDED,
                                                PaymentStatus.REFUNDED,
                                                PaymentStatus.CHARGEBACK_REQUESTED,
                                                PaymentStatus.CHARGEBACK_DISPUTE);

                        case CHARGEBACK_REQUESTED ->
                                EnumSet.of(
                                                PaymentStatus.CHARGEBACK_DISPUTE,
                                                PaymentStatus.AWAITING_CHARGEBACK_REVERSAL,
                                                PaymentStatus.CHARGEBACK_REVERSED);

                        case CHARGEBACK_DISPUTE ->
                                EnumSet.of(
                                                PaymentStatus.AWAITING_CHARGEBACK_REVERSAL,
                                                PaymentStatus.CHARGEBACK_REVERSED);

                        case AWAITING_CHARGEBACK_REVERSAL ->
                                EnumSet.of(PaymentStatus.CHARGEBACK_REVERSED);

                        // Estados mantidos apenas para compatibilidade histórica.
                        case DUNNING_REQUESTED ->
                                EnumSet.of(
                                                PaymentStatus.DUNNING_RECEIVED,
                                                PaymentStatus.PAID,
                                                PaymentStatus.FAILED);

                        case DUNNING_RECEIVED ->
                                EnumSet.of(PaymentStatus.PAID);

                        // Estados financeiros terminais.
                        case REFUNDED, CHARGEBACK_REVERSED ->
                                EnumSet.noneOf(PaymentStatus.class);
                };
        }
}
