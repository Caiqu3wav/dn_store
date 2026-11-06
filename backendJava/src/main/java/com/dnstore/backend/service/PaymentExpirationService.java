package com.dnstore.backend.service;

import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.enums.OrderStatus;
import com.dnstore.backend.model.enums.PaymentStatus;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.PaymentRepository;
import com.dnstore.backend.service.payment.PaymentGateway;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentExpirationService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderInventoryService orderInventoryService;
    private final PaymentGateway gateway;

    @Value("${payment.order-expiration-minutes:60}")
    private long orderExpirationMinutes;

    @Transactional
    public void expireIfDue(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !isExpirable(order.getStatus()) || order.getCreatedAt() == null
                || order.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(orderExpirationMinutes))) {
            return;
        }

        List<Payment> attempts = paymentRepository.findByOrderIdForUpdate(orderId);
        for (Payment payment : attempts) {
            if (!reconcileAndCancelIfNeeded(payment, order)) {
                return;
            }
        }

        if (!order.isStockReleased()) {
            orderInventoryService.releaseReservedStock(order);
        }
        order.setStatus(OrderStatus.EXPIRED.name());
        orderRepository.save(order);
    }

    private boolean reconcileAndCancelIfNeeded(Payment payment, Order order) {
        PaymentStatus localStatus = PaymentStatus.valueOf(payment.getStatus());
        PaymentGateway.ProviderPayment current;

        if (payment.getExternalId() == null) {
            if (localStatus != PaymentStatus.CREATING && localStatus != PaymentStatus.RECONCILIATION_REQUIRED) {
                return localStatus == PaymentStatus.FAILED;
            }
            current = gateway.findPaymentByReference(payment.getExternalReference());
            if (current == null) {
                if (payment.getCreatedAt() != null
                        && payment.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(2))) {
                    payment.setStatus(PaymentStatus.FAILED.name());
                    paymentRepository.save(payment);
                    return true;
                }
                return false;
            }
            payment.setExternalId(current.externalId());
        }

        current = gateway.getPayment(payment.getExternalId());
        validateProviderPayment(payment, current);
        PaymentStatus providerStatus = PaymentStatus.valueOf(current.status());

        if (providerStatus == PaymentStatus.PAID
                || providerStatus == PaymentStatus.REFUNDED
                || providerStatus == PaymentStatus.REFUND_REQUESTED
                || providerStatus == PaymentStatus.PARTIALLY_REFUNDED
                || providerStatus == PaymentStatus.CHARGEBACK_REQUESTED
                || providerStatus == PaymentStatus.CHARGEBACK_DISPUTE
                || providerStatus == PaymentStatus.AWAITING_CHARGEBACK_REVERSAL) {
            payment.setStatus(providerStatus.name());
            if (providerStatus == PaymentStatus.PAID && payment.getPaidAt() == null) {
                payment.setPaidAt(LocalDateTime.now());
            }
            if (payment.getProviderCustomerId() == null) {
                payment.setProviderCustomerId(current.customerId());
            }
            paymentRepository.save(payment);
            order.setStatus(OrderStatus.PAYMENT_REVIEW.name());
            orderRepository.save(order);
            return false;
        }

        if (providerStatus.mayHaveAnActiveCharge()) {
            current = gateway.cancelPayment(payment.getExternalId());
            validateProviderPayment(payment, current);
            providerStatus = PaymentStatus.valueOf(current.status());
            if (providerStatus.mayHaveAnActiveCharge()) {
                return false;
            }
        }

        if (providerStatus != PaymentStatus.FAILED) {
            return false;
        }
        payment.setStatus(PaymentStatus.FAILED.name());
        paymentRepository.save(payment);
        return true;
    }

    private void validateProviderPayment(Payment payment, PaymentGateway.ProviderPayment providerPayment) {
        if (!payment.getExternalId().equals(providerPayment.externalId())
                || !payment.getExternalReference().equals(providerPayment.externalReference())
                || providerPayment.amount() == null
                || payment.getAmount().compareTo(providerPayment.amount()) != 0
                || (providerPayment.currency() != null && !"BRL".equalsIgnoreCase(providerPayment.currency()))
                || (payment.getProviderCustomerId() != null
                        && !payment.getProviderCustomerId().equals(providerPayment.customerId()))) {
            throw new IllegalStateException("Pagamento reconciliado diverge da tentativa local.");
        }
    }

    private boolean isExpirable(String status) {
        return OrderStatus.PENDING_PAYMENT.name().equals(status)
                || OrderStatus.PAYMENT_FAILED.name().equals(status);
    }
}
