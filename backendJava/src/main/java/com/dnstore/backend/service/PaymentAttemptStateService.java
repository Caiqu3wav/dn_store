package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.PaymentWebhookEvent;
import com.dnstore.backend.model.enums.OrderStatus;
import com.dnstore.backend.model.enums.PaymentStatus;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.PaymentRepository;
import com.dnstore.backend.repository.PaymentWebhookEventRepository;
import com.dnstore.backend.service.payment.PaymentGateway;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAttemptStateService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    @Value("${payment.order-expiration-minutes:60}")
    private long orderExpirationMinutes;

    public record PreparedAttempt(Payment payment, boolean newlyCreated) {}

    @Transactional
    public PreparedAttempt prepareAttempt(
            UUID orderId,
            UUID userId,
            String idempotencyKey,
            String paymentMethod,
            Integer installments) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new IllegalArgumentException("Chave de idempotência inválida.");
        }

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        if (order.getUser() == null || !order.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Pedido não encontrado.");
        }
        if (order.getCreatedAt() == null
                || !order.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(orderExpirationMinutes))) {
            throw new ConflictException("O prazo para pagamento deste pedido expirou.");
        }
        if (!isPaymentOpen(order.getStatus())) {
            throw new ConflictException("Pedido não está disponível para pagamento.");
        }
        if (order.getTotal() == null || order.getTotal().signum() <= 0) {
            throw new ConflictException("Pedido não possui valor válido para pagamento.");
        }

        var sameKey = paymentRepository.findByOrder_IdAndIdempotencyKey(orderId, idempotencyKey);
        if (sameKey.isPresent()) {
            return new PreparedAttempt(sameKey.get(), false);
        }

        List<Payment> attempts = paymentRepository.findByOrder_IdOrderByAttemptNumberDesc(orderId);
        if (!attempts.isEmpty()) {
            Payment latest = attempts.get(0);
            PaymentStatus latestStatus = PaymentStatus.valueOf(latest.getStatus());
            if (attempts.stream().anyMatch(attempt ->
                    PaymentStatus.valueOf(attempt.getStatus()).mayHaveAnActiveCharge())) {
                Payment activeAttempt = attempts.stream()
                        .filter(attempt -> PaymentStatus.valueOf(attempt.getStatus()).mayHaveAnActiveCharge())
                        .findFirst()
                        .orElseThrow();
                return new PreparedAttempt(activeAttempt, false);
            }
            if (attempts.stream().anyMatch(attempt -> {
                PaymentStatus status = PaymentStatus.valueOf(attempt.getStatus());
                return status == PaymentStatus.PAID
                        || status == PaymentStatus.REFUND_REQUESTED
                        || status == PaymentStatus.PARTIALLY_REFUNDED
                        || status == PaymentStatus.REFUNDED
                        || status == PaymentStatus.CHARGEBACK_REQUESTED
                        || status == PaymentStatus.CHARGEBACK_DISPUTE
                        || status == PaymentStatus.AWAITING_CHARGEBACK_REVERSAL;
            })) {
                throw new ConflictException("O pedido possui uma situação financeira que impede nova cobrança.");
            }
            if (!latestStatus.mayStartAnotherAttempt()) {
                return new PreparedAttempt(latest, false);
            }
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAttemptNumber(attempts.isEmpty() ? 1 : attempts.get(0).getAttemptNumber() + 1);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setExternalReference(orderId + ":" + idempotencyKey);
        payment.setPaymentMethod(paymentMethod);
        payment.setAmount(order.getTotal());
        payment.setInstallments(installments);
        payment.setStatus(PaymentStatus.CREATING.name());
        return new PreparedAttempt(paymentRepository.saveAndFlush(payment), true);
    }

    @Transactional
    public Payment completeAttempt(UUID paymentId, PaymentGateway.PaymentResult result) {
        Payment reference = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado."));
        Order order = orderRepository.findByIdForUpdate(reference.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        Payment payment = lockPayment(paymentId);
        payment.setOrder(order);
        PaymentStatus nextStatus = PaymentStatus.valueOf(result.status());
        verifyProviderPayment(payment, result.externalId(), result.externalReference(),
                result.amount(), result.currency(), result.customerId());
        PaymentStatus currentStatus = PaymentStatus.valueOf(payment.getStatus());
        payment.setExternalId(result.externalId());
        payment.setProviderCustomerId(result.customerId());
        payment.setPaymentMethod(result.paymentMethod() == null ? payment.getPaymentMethod() : result.paymentMethod());
        if (isAllowedTransition(currentStatus, nextStatus)) {
            payment.setStatus(nextStatus.name());
        }
        payment.setPixQrCode(result.pixQrCode());
        payment.setPixCopyPaste(result.pixCopyPaste());
        payment.setBoletoUrl(result.boletoUrl());
        payment.setBoletoBarcode(result.boletoBarcode());
        if (nextStatus == PaymentStatus.PAID && payment.getPaidAt() == null) {
            payment.setPaidAt(LocalDateTime.now());
        }
        Payment saved = paymentRepository.save(payment);
        if (isAllowedTransition(currentStatus, nextStatus)) {
            applyLogisticsTransition(order, nextStatus);
        }
        return saved;
    }

    @Transactional
    public Payment markUncertain(UUID paymentId) {
        Payment payment = lockPayment(paymentId);
        PaymentStatus status = PaymentStatus.valueOf(payment.getStatus());
        if (status == PaymentStatus.CREATING || status == PaymentStatus.RECONCILIATION_REQUIRED) {
            payment.setStatus(PaymentStatus.RECONCILIATION_REQUIRED.name());
        }
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment markDefinitiveFailure(UUID paymentId) {
        Payment payment = lockPayment(paymentId);
        PaymentStatus status = PaymentStatus.valueOf(payment.getStatus());
        if (status == PaymentStatus.CREATING) {
            payment.setStatus(PaymentStatus.FAILED.name());
        }
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment markReconciledFailure(UUID paymentId) {
        Payment payment = lockPayment(paymentId);
        if (payment.getExternalId() != null
                || payment.getCreatedAt() == null
                || payment.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(1))) {
            throw new ConflictException("A tentativa ainda não pode ser considerada sem cobrança.");
        }
        if (PaymentStatus.CREATING.name().equals(payment.getStatus())
                || PaymentStatus.RECONCILIATION_REQUIRED.name().equals(payment.getStatus())) {
            payment.setStatus(PaymentStatus.FAILED.name());
        }
        return paymentRepository.save(payment);
    }

    @Transactional
    public boolean applyWebhook(
            PaymentGateway.WebhookResult event,
            PaymentGateway.ProviderPayment providerPayment) {
        if (webhookEventRepository.existsById(event.eventId())) {
            return false;
        }
        Payment paymentReference = paymentRepository.findByExternalId(event.externalId())
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado."));
        Order order = orderRepository.findByIdForUpdate(paymentReference.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        Payment payment = paymentRepository.findByExternalIdForUpdate(event.externalId())
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado."));
        if (webhookEventRepository.findByEventIdForUpdate(event.eventId()).isPresent()) {
            return false;
        }

        verifyProviderPayment(payment, providerPayment.externalId(), providerPayment.externalReference(),
                providerPayment.amount(), providerPayment.currency(), providerPayment.customerId());
        verifyEventMatchesProvider(event, providerPayment);
        persistEvent(event, payment.getExternalId());

        PaymentStatus current = PaymentStatus.valueOf(payment.getStatus());
        PaymentStatus next = PaymentStatus.valueOf(providerPayment.status());
        if (!isAllowedTransition(current, next)) {
            log.warn("payment.webhook.transition_rejected paymentId={} current={} next={}",
                    payment.getId(), current, next);
            return true;
        }
        if (current == next) {
            return true;
        }

        payment.setStatus(next.name());
        if (payment.getProviderCustomerId() == null) {
            payment.setProviderCustomerId(providerPayment.customerId());
        }
        if (next == PaymentStatus.PAID && payment.getPaidAt() == null) {
            payment.setPaidAt(LocalDateTime.now());
        }
        paymentRepository.save(payment);
        applyLogisticsTransition(order, next);
        return true;
    }

    @Transactional
    public List<Payment> lockAttemptsForOrder(UUID orderId) {
        return paymentRepository.findByOrderIdForUpdate(orderId);
    }

    private Payment lockPayment(UUID paymentId) {
        return paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado."));
    }

    private boolean isPaymentOpen(String status) {
        return OrderStatus.PENDING_PAYMENT.name().equals(status)
                || OrderStatus.PAYMENT_FAILED.name().equals(status);
    }

    private void verifyProviderPayment(
            Payment payment,
            String externalId,
            String externalReference,
            BigDecimal amount,
            String currency,
            String customerId) {
        if (externalId == null || externalId.isBlank()
                || !externalId.equals(payment.getExternalId()) && payment.getExternalId() != null
                || !payment.getExternalReference().equals(externalReference)
                || amount == null || amount.compareTo(payment.getAmount()) != 0
                || (currency != null && !"BRL".equalsIgnoreCase(currency))
                || (payment.getProviderCustomerId() != null
                        && !payment.getProviderCustomerId().equals(customerId))) {
            throw new ConflictException("Dados financeiros do provedor não correspondem ao pedido.");
        }
    }

    private void verifyEventMatchesProvider(
            PaymentGateway.WebhookResult event,
            PaymentGateway.ProviderPayment providerPayment) {
        if (!event.externalId().equals(providerPayment.externalId())
                || (event.externalReference() != null
                        && !event.externalReference().equals(providerPayment.externalReference()))
                || (event.amount() != null && providerPayment.amount() != null
                        && event.amount().compareTo(providerPayment.amount()) != 0)
                || (event.currency() != null && providerPayment.currency() != null
                        && !event.currency().equalsIgnoreCase(providerPayment.currency()))
                || (event.customerId() != null && providerPayment.customerId() != null
                        && !event.customerId().equals(providerPayment.customerId()))) {
            throw new ConflictException("Webhook não corresponde ao estado reconciliado do provedor.");
        }
    }

    private void persistEvent(PaymentGateway.WebhookResult event, String externalPaymentId) {
        webhookEventRepository.saveAndFlush(new PaymentWebhookEvent(event.eventId(), externalPaymentId));
    }

    private boolean isAllowedTransition(PaymentStatus current, PaymentStatus next) {
        if (current == next) {
            return true;
        }
        if (current == PaymentStatus.REFUNDED) {
            return false;
        }
        if (current == PaymentStatus.FAILED) {
            return next == PaymentStatus.PAID;
        }
        if (current == PaymentStatus.OVERDUE) {
            return next == PaymentStatus.PAID || next == PaymentStatus.FAILED;
        }
        if (current == PaymentStatus.PAID) {
            return next == PaymentStatus.REFUND_REQUESTED
                    || next == PaymentStatus.PARTIALLY_REFUNDED
                    || next == PaymentStatus.REFUNDED
                    || next == PaymentStatus.CHARGEBACK_REQUESTED
                    || next == PaymentStatus.CHARGEBACK_DISPUTE
                    || next == PaymentStatus.AWAITING_CHARGEBACK_REVERSAL;
        }
        if (current == PaymentStatus.REFUND_REQUESTED || current == PaymentStatus.PARTIALLY_REFUNDED) {
            return next == PaymentStatus.PARTIALLY_REFUNDED
                    || next == PaymentStatus.REFUNDED
                    || next == PaymentStatus.CHARGEBACK_REQUESTED
                    || next == PaymentStatus.CHARGEBACK_DISPUTE
                    || next == PaymentStatus.AWAITING_CHARGEBACK_REVERSAL;
        }
        return true;
    }

    private void applyLogisticsTransition(Order order, PaymentStatus paymentStatus) {
        String orderStatus = order.getStatus();
        if (OrderStatus.PROCESSING.name().equals(orderStatus)
                || OrderStatus.SHIPPED.name().equals(orderStatus)
                || OrderStatus.DELIVERED.name().equals(orderStatus)
                || OrderStatus.CANCELLED.name().equals(orderStatus)
                || OrderStatus.EXPIRED.name().equals(orderStatus)
                || OrderStatus.PAYMENT_REVIEW.name().equals(orderStatus)) {
            return;
        }

        if (paymentStatus == PaymentStatus.PAID
                && (OrderStatus.PENDING_PAYMENT.name().equals(orderStatus)
                        || OrderStatus.PAYMENT_FAILED.name().equals(orderStatus))) {
            boolean expired = order.getCreatedAt() == null
                    || !order.getCreatedAt().isAfter(
                            LocalDateTime.now().minusMinutes(orderExpirationMinutes));
            if (expired) {
                order.setStatus(OrderStatus.PAYMENT_REVIEW.name());
            } else if (order.isStockReleased()) {
                order.setStatus(OrderStatus.PAYMENT_REVIEW.name());
            } else {
                order.setStatus(OrderStatus.PAID.name());
            }
            orderRepository.save(order);
        } else if (paymentStatus == PaymentStatus.FAILED
                && OrderStatus.PENDING_PAYMENT.name().equals(orderStatus)) {
            order.setStatus(OrderStatus.PAYMENT_FAILED.name());
            orderRepository.save(order);
        }
    }
}
