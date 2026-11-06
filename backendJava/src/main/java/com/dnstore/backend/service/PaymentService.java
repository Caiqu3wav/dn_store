package com.dnstore.backend.service;

import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.enums.PaymentStatus;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.PaymentRepository;
import com.dnstore.backend.service.payment.PaymentCreationUncertainException;
import com.dnstore.backend.service.payment.PaymentGateway;
import com.dnstore.backend.service.payment.PaymentGatewayException;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PaymentService {

    private final PaymentGateway gateway;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentAttemptStateService paymentState;

    public PaymentService(
            @Qualifier("asaas") PaymentGateway gateway,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            PaymentAttemptStateService paymentState) {
        this.gateway = gateway;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentState = paymentState;
    }

    public Payment initPayment(
            UUID orderId,
            UUID requestingUserId,
            String idempotencyKey,
            String cpfCnpj,
            String paymentMethod,
            Integer installments) {
        paymentMethod = paymentMethod == null ? "" : paymentMethod.trim().toUpperCase(Locale.ROOT);
        if (!"PIX".equals(paymentMethod) && !"BOLETO".equals(paymentMethod)) {
            throw new IllegalArgumentException("Cartão temporariamente indisponível. Use Pix ou boleto.");
        }
        installments = null;

        PaymentAttemptStateService.PreparedAttempt prepared = paymentState.prepareAttempt(
                orderId, requestingUserId, idempotencyKey, paymentMethod, installments);
        Payment payment = prepared.payment();

        if (!prepared.newlyCreated()) {
            return reconcileExistingAttempt(payment);
        }

        Order order = orderRepository.findByIdWithUser(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        PaymentGateway.PaymentRequest request = new PaymentGateway.PaymentRequest(
                order.getId(),
                payment.getExternalReference(),
                payment.getAmount(),
                order.getUser().getName(),
                order.getUser().getEmail(),
                cpfCnpj,
                "Pedido DN Store #" + order.getId(),
                paymentMethod,
                installments,
                null);

        try {
            PaymentGateway.PaymentResult result = gateway.createPayment(request);
            return paymentState.completeAttempt(payment.getId(), result);
        } catch (PaymentCreationUncertainException exception) {
            log.warn("payment.creation.uncertain orderId={} attemptId={}", orderId, payment.getId());
            return paymentState.markUncertain(payment.getId());
        } catch (PaymentGatewayException | IllegalArgumentException exception) {
            paymentState.markDefinitiveFailure(payment.getId());
            throw exception;
        }
    }

    private Payment reconcileExistingAttempt(Payment payment) {
        PaymentStatus status = PaymentStatus.valueOf(payment.getStatus());
        if (status != PaymentStatus.CREATING && status != PaymentStatus.RECONCILIATION_REQUIRED) {
            return payment;
        }

        try {
            PaymentGateway.ProviderPayment found = gateway.findPaymentByReference(payment.getExternalReference());
            if (found != null) {
                PaymentGateway.ProviderPayment canonical = gateway.getPayment(found.externalId());
                return paymentState.completeAttempt(payment.getId(), canonical.toPaymentResult());
            }
            if (payment.getCreatedAt() != null
                    && payment.getCreatedAt().isBefore(java.time.LocalDateTime.now().minusMinutes(1))) {
                return paymentState.markReconciledFailure(payment.getId());
            }
        } catch (PaymentGatewayException exception) {
            log.warn("payment.reconciliation.unavailable attemptId={}", payment.getId());
        }
        return paymentState.markUncertain(payment.getId());
    }

    public void handleWebhook(String rawPayload, String signatureHeader) {
        PaymentGateway.WebhookResult event = gateway.processWebhook(rawPayload, signatureHeader);
        PaymentGateway.ProviderPayment providerPayment = gateway.getPayment(event.externalId());
        paymentState.applyWebhook(event, providerPayment);
    }

    @Transactional(readOnly = true)
    public Payment findByOrderId(UUID orderId) {
        return paymentRepository.findByOrder_IdOrderByAttemptNumberDesc(orderId).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado para o pedido."));
    }

    @Transactional(readOnly = true)
    public Payment findByOrderIdAndUser(UUID orderId, UUID userId) {
        Payment payment = findByOrderId(orderId);
        if (payment.getOrder().getUser() == null || !payment.getOrder().getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Pagamento não encontrado para o pedido.");
        }
        return payment;
    }
}
