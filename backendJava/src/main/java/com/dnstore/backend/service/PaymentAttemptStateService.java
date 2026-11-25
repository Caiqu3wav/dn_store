
package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.PaymentWebhookEvent;
import com.dnstore.backend.model.PaymentFinancialHistory;
import com.dnstore.backend.model.FinancialBlock;
import com.dnstore.backend.model.enums.OrderStatus;
import com.dnstore.backend.model.enums.PaymentStatus;
import com.dnstore.backend.model.enums.PaymentTransitionPolicy;
import com.dnstore.backend.model.enums.PaymentTransitionResult;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.PaymentRepository;
import com.dnstore.backend.repository.PaymentWebhookEventRepository;
import com.dnstore.backend.repository.PaymentFinancialHistoryRepository;
import com.dnstore.backend.repository.FinancialBlockRepository;
import com.dnstore.backend.service.payment.PaymentGateway;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAttemptStateService {

        private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._~-]{1,64}");

        private final PaymentRepository paymentRepository;
        private final OrderRepository orderRepository;
        private final PaymentWebhookEventRepository webhookEventRepository;

        @Autowired(required = false)
        private PaymentFinancialHistoryRepository historyRepository;

        @Autowired(required = false)
        private FinancialBlockRepository blockRepository;

        @Value("${payment.order-expiration-minutes:60}")
        private long orderExpirationMinutes;

        public record PreparedAttempt(Payment payment, boolean newlyCreated) {
        }

        @Transactional
        public PreparedAttempt prepareAttempt(
                        UUID orderId,
                        UUID userId,
                        String idempotencyKey,
                        String paymentMethod,
                        Integer installments) {

                // 1. Valida a chave antes de qualquer consulta.
                if (idempotencyKey == null
                                || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
                        throw new IllegalArgumentException("Chave de idempotência inválida.");
                }

                // 2. Bloqueia o pedido para evitar tentativas concorrentes.
                Order order = orderRepository.findByIdForUpdate(orderId)
                                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));

                // 3. Valida se o pedido pertence ao usuário.
                if (order.getUser() == null
                                || !order.getUser().getId().equals(userId)) {
                        throw new ResourceNotFoundException("Pedido não encontrado.");
                }

                // 4. Verifica se o pedido ainda está dentro do prazo.
                if (order.getCreatedAt() == null
                                || !order.getCreatedAt().isAfter(
                                                LocalDateTime.now().minusMinutes(orderExpirationMinutes))) {
                        throw new ConflictException(
                                        "O prazo para pagamento deste pedido expirou.");
                }

                // 5. Verifica se o pedido pode receber pagamento.
                if (!isPaymentOpen(order.getStatus())) {
                        throw new ConflictException(
                                        "Pedido não está disponível para pagamento.");
                }

                if (order.getTotal() == null || order.getTotal().signum() <= 0) {
                        throw new ConflictException(
                                        "Pedido não possui valor válido para pagamento.");
                }

                // 6. Verifica idempotência.
                // Uma chave já utilizada não deve gerar uma nova tentativa.
                var sameKey = paymentRepository.findByOrder_IdAndIdempotencyKey(
                                orderId,
                                idempotencyKey);

                if (sameKey.isPresent()) {
                        return new PreparedAttempt(sameKey.get(), false);
                }

                // 7. Busca tentativas anteriores.
                List<Payment> attempts = paymentRepository.findByOrder_IdOrderByAttemptNumberDesc(orderId);

                if (!attempts.isEmpty()) {
                        Payment latest = attempts.get(0);
                        PaymentStatus latestStatus = PaymentStatus.valueOf(latest.getStatus());

                        // Não cria outra cobrança enquanto existir uma tentativa
                        // que ainda possa produzir efeitos financeiros.
                        Payment activeAttempt = attempts.stream()
                                        .filter(attempt -> PaymentStatus.valueOf(attempt.getStatus())
                                                        .mayHaveAnActiveCharge())
                                        .findFirst()
                                        .orElse(null);

                        if (activeAttempt != null) {
                                return new PreparedAttempt(activeAttempt, false);
                        }

                        // Impede nova cobrança quando existe uma situação financeira
                        // que exige bloqueio ou análise.
                        boolean hasFinancialRestriction = attempts.stream()
                                        .map(attempt -> PaymentStatus.valueOf(attempt.getStatus()))
                                        .anyMatch(status -> status == PaymentStatus.PAID
                                                        || status == PaymentStatus.REFUND_REQUESTED
                                                        || status == PaymentStatus.REFUND_IN_PROGRESS
                                                        || status == PaymentStatus.PARTIALLY_REFUNDED
                                                        || status == PaymentStatus.REFUNDED
                                                        || status == PaymentStatus.REFUND_DENIED
                                                        || status == PaymentStatus.CHARGEBACK_REQUESTED
                                                        || status == PaymentStatus.CHARGEBACK_DISPUTE
                                                        || status == PaymentStatus.AWAITING_CHARGEBACK_REVERSAL
                                                        || status == PaymentStatus.CHARGEBACK_REVERSED);

                        if (hasFinancialRestriction) {
                                throw new ConflictException(
                                                "O pedido possui uma situação financeira que impede nova cobrança.");
                        }

                        // Só permite nova tentativa se a última falhou definitivamente.
                        if (!latestStatus.mayStartAnotherAttempt()) {
                                return new PreparedAttempt(latest, false);
                        }
                }

                // A partir daqui permanece seu código de criação da nova tentativa.

                // 6. Cria uma nova tentativa.
                Payment payment = new Payment();
                payment.setOrder(order);
                payment.setAttemptNumber(
                                attempts.isEmpty() ? 1 : attempts.get(0).getAttemptNumber() + 1);
                payment.setIdempotencyKey(idempotencyKey);
                payment.setExternalReference(orderId + ":" + idempotencyKey);
                payment.setPaymentMethod(paymentMethod);
                payment.setAmount(order.getTotal());
                payment.setInstallments(installments);
                payment.setStatus(PaymentStatus.CREATING.name());

                return new PreparedAttempt(
                                paymentRepository.saveAndFlush(payment),
                                true);
        }

        @Transactional
        public Payment completeAttempt(
                        UUID paymentId,
                        PaymentGateway.PaymentResult result) {

                if (result == null || result.status() == null
                                || result.status().isBlank()) {
                        throw new IllegalArgumentException(
                                        "Resposta inválida do gateway de pagamento.");
                }

                Payment reference = paymentRepository.findById(paymentId)
                                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado."));

                Order order = orderRepository.findByIdForUpdate(
                                reference.getOrder().getId())
                                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));

                Payment payment = lockPayment(paymentId);
                payment.setOrder(order);

                PaymentStatus nextStatus = PaymentStatus.valueOf(result.status());

                verifyProviderPayment(
                                payment,
                                result.externalId(),
                                result.externalReference(),
                                result.amount(),
                                result.currency(),
                                result.customerId());

                PaymentStatus currentStatus = PaymentStatus.valueOf(payment.getStatus());

                boolean sameStatus = currentStatus == nextStatus;
                boolean transitionAllowed = PaymentTransitionPolicy.isAllowed(currentStatus, nextStatus);

                boolean canApplyResult = sameStatus || transitionAllowed;

                // Mantém os identificadores para permitir conciliação posterior.
                payment.setExternalId(result.externalId());
                payment.setProviderCustomerId(result.customerId());

                if (canApplyResult) {
                        if (result.paymentMethod() != null) {
                                payment.setPaymentMethod(result.paymentMethod());
                        }

                        payment.setPixQrCode(result.pixQrCode());
                        payment.setPixCopyPaste(result.pixCopyPaste());
                        payment.setBoletoUrl(result.boletoUrl());
                        payment.setBoletoBarcode(result.boletoBarcode());

                        if (transitionAllowed) {
                                payment.setStatus(nextStatus.name());
                        }

                        if (nextStatus == PaymentStatus.PAID
                                        && payment.getPaidAt() == null) {
                                payment.setPaidAt(LocalDateTime.now());
                        }
                } else {
                        log.warn(
                                        "payment.attempt.transition_rejected paymentId={} current={} next={}",
                                        paymentId,
                                        currentStatus,
                                        nextStatus);
                }

                Payment saved = paymentRepository.save(payment);

                // Histórico e logística somente quando há mudança efetiva.
                if (transitionAllowed && !sameStatus) {
                        applyLogisticsTransition(order, nextStatus);

                        recordHistory(
                                        payment,
                                        order,
                                        currentStatus,
                                        nextStatus,
                                        null,
                                        null,
                                        PaymentTransitionResult.APPLIED,
                                        "Confirmação da cobrança.");
                }

                return saved;
        }

        @Transactional
        public Payment markUncertain(UUID paymentId) {
                Payment payment = lockPayment(paymentId);
                PaymentStatus status = PaymentStatus.valueOf(payment.getStatus());

                if (status == PaymentStatus.CREATING
                                || status == PaymentStatus.RECONCILIATION_REQUIRED) {
                        payment.setStatus(
                                        PaymentStatus.RECONCILIATION_REQUIRED.name());
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
                                || payment.getCreatedAt().isAfter(
                                                LocalDateTime.now().minusMinutes(1))) {
                        throw new ConflictException(
                                        "A tentativa ainda não pode ser considerada sem cobrança.");
                }

                if (PaymentStatus.CREATING.name().equals(payment.getStatus())
                                || PaymentStatus.RECONCILIATION_REQUIRED.name()
                                                .equals(payment.getStatus())) {
                        payment.setStatus(PaymentStatus.FAILED.name());
                }

                return paymentRepository.save(payment);
        }

        @Transactional
        public boolean applyWebhook(
                        PaymentGateway.WebhookResult event,
                        PaymentGateway.ProviderPayment providerPayment) {

                return applyWebhookInternal(event, providerPayment, false);
        }

        @Transactional
        public boolean applyPersistedWebhook(
                        PaymentGateway.WebhookResult event,
                        PaymentGateway.ProviderPayment providerPayment) {

                return applyWebhookInternal(event, providerPayment, true);
        }

        private boolean applyWebhookInternal(
                        PaymentGateway.WebhookResult event,
                        PaymentGateway.ProviderPayment providerPayment,
                        boolean alreadyPersisted) {

                if (event == null || providerPayment == null
                                || event.eventId() == null || event.eventId().isBlank()
                                || event.externalId() == null || event.externalId().isBlank()
                                || providerPayment.status() == null
                                || providerPayment.status().isBlank()) {
                        throw new IllegalArgumentException(
                                        "Dados insuficientes para processar o webhook.");
                }

                if (!alreadyPersisted
                                && webhookEventRepository.existsById(event.eventId())) {
                        return false;
                }

                Payment paymentReference = paymentRepository
                                .findByExternalId(event.externalId())
                                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado."));

                Order order = orderRepository.findByIdForUpdate(
                                paymentReference.getOrder().getId())
                                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));

                Payment payment = paymentRepository
                                .findByExternalIdForUpdate(event.externalId())
                                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado."));

                if (!alreadyPersisted
                                && webhookEventRepository
                                                .findByEventIdForUpdate(event.eventId()).isPresent()) {
                        return false;
                }

                verifyProviderPayment(
                                payment,
                                providerPayment.externalId(),
                                providerPayment.externalReference(),
                                providerPayment.amount(),
                                providerPayment.currency(),
                                providerPayment.customerId());

                verifyEventMatchesProvider(event, providerPayment);

                if (!alreadyPersisted) {
                        persistEvent(event, payment.getExternalId());
                }

                PaymentStatus current = PaymentStatus.valueOf(payment.getStatus());

                PaymentStatus next = PaymentStatus.valueOf(providerPayment.status());

                if (!PaymentTransitionPolicy.isAllowed(current, next)) {
                        log.warn(
                                        "payment.webhook.transition_rejected paymentId={} current={} next={}",
                                        payment.getId(),
                                        current,
                                        next);

                        recordHistory(
                                        payment,
                                        order,
                                        current,
                                        next,
                                        event.eventType(),
                                        event.eventId(),
                                        PaymentTransitionResult.IGNORED_OUT_OF_ORDER,
                                        "Transição fora da ordem canônica.");

                        return true;
                }

                if (current == next) {
                        return true;
                }

                // Atualiza o status somente depois das validações.
                payment.setStatus(next.name());

                if (next == PaymentStatus.PAID
                                && providerPayment.amount() != null) {
                        payment.setReceivedAmount(providerPayment.amount());
                }

                // Não utilizar providerPayment.amount() como valor reembolsado.
                // Esse campo representa o valor da cobrança. O valor efetivamente
                // reembolsado precisa vir de uma fonte específica de reembolsos.

                if (payment.getProviderCustomerId() == null) {
                        payment.setProviderCustomerId(providerPayment.customerId());
                }

                if (next == PaymentStatus.PAID
                                && payment.getPaidAt() == null) {
                        payment.setPaidAt(LocalDateTime.now());
                }

                paymentRepository.save(payment);

                applyLogisticsTransition(order, next);

                recordHistory(
                                payment,
                                order,
                                current,
                                next,
                                event.eventType(),
                                event.eventId(),
                                PaymentTransitionResult.APPLIED,
                                "Webhook reconciliado com o estado canônico.");

                updateFinancialBlock(payment, order, next);

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
                                || (payment.getExternalId() != null
                                                && !externalId.equals(payment.getExternalId()))
                                || payment.getExternalReference() == null
                                || !payment.getExternalReference().equals(externalReference)
                                || amount == null
                                || payment.getAmount() == null
                                || amount.compareTo(payment.getAmount()) != 0
                                || (currency != null && !"BRL".equalsIgnoreCase(currency))
                                || (payment.getProviderCustomerId() != null
                                                && !payment.getProviderCustomerId().equals(customerId))) {

                        throw new ConflictException(
                                        "Dados financeiros do provedor não correspondem ao pedido.");
                }
        }

        private void verifyEventMatchesProvider(
                        PaymentGateway.WebhookResult event,
                        PaymentGateway.ProviderPayment providerPayment) {

                if (event.externalId() == null
                                || !event.externalId().equals(providerPayment.externalId())
                                || (event.externalReference() != null
                                                && !event.externalReference()
                                                                .equals(providerPayment.externalReference()))
                                || (event.amount() != null
                                                && providerPayment.amount() != null
                                                && event.amount().compareTo(providerPayment.amount()) != 0)
                                || (event.currency() != null
                                                && providerPayment.currency() != null
                                                && !event.currency()
                                                                .equalsIgnoreCase(providerPayment.currency()))
                                || (event.customerId() != null
                                                && providerPayment.customerId() != null
                                                && !event.customerId()
                                                                .equals(providerPayment.customerId()))) {

                        throw new ConflictException(
                                        "Webhook não corresponde ao estado reconciliado do provedor.");
                }
        }

        private void persistEvent(
                        PaymentGateway.WebhookResult event,
                        String externalPaymentId) {

                webhookEventRepository.saveAndFlush(
                                new PaymentWebhookEvent(event.eventId(), externalPaymentId));
        }

        private void recordHistory(
                        Payment payment,
                        Order order,
                        PaymentStatus previous,
                        PaymentStatus next,
                        String eventType,
                        String eventId,
                        PaymentTransitionResult result,
                        String reason) {

                if (historyRepository == null) {
                        return;
                }

                PaymentFinancialHistory history = new PaymentFinancialHistory();
                history.setPayment(payment);
                history.setOrder(order);
                history.setPreviousStatus(
                                previous == null ? null : previous.name());
                history.setNewStatus(
                                next == null ? null : next.name());
                history.setEventType(eventType);
                history.setEventId(eventId);
                history.setExternalPaymentId(payment.getExternalId());
                history.setAmount(payment.getAmount());
                history.setRefundedAmount(payment.getRefundedAmount());
                history.setSource("WEBHOOK");
                history.setResult(result.name());
                history.setReason(reason);

                historyRepository.save(history);
        }

        private void updateFinancialBlock(
                        Payment payment,
                        Order order,
                        PaymentStatus status) {

                if (blockRepository == null) {
                        return;
                }

                boolean needsBlock = switch (status) {
                        case REFUND_REQUESTED,
                                        REFUND_IN_PROGRESS,
                                        PARTIALLY_REFUNDED,
                                        CHARGEBACK_REQUESTED,
                                        CHARGEBACK_DISPUTE,
                                        AWAITING_CHARGEBACK_REVERSAL,
                                        RECONCILIATION_REQUIRED,
                                        OVERDUE ->
                                true;
                        default -> false;
                };

                // Um pagamento do Asaas confirmado depois de o admin já ter confirmado o pedido manualmente
                // significa que o cliente pagou duas vezes: exige reconciliação (e bloqueia a logística).
                boolean duplicateOfManualPayment = status == PaymentStatus.PAID
                                && order.isManualPaymentConfirmed();

                if ((needsBlock || duplicateOfManualPayment)
                                && !blockRepository.existsByOrder_IdAndActiveTrue(order.getId())) {

                        FinancialBlock block = new FinancialBlock();
                        block.setPayment(payment);
                        block.setOrder(order);
                        block.setBlockType(duplicateOfManualPayment
                                        ? "MANUAL_CONFIRMATION_CONFLICT"
                                        : status.name());
                        block.setReason(duplicateOfManualPayment
                                        ? "Pagamento do Asaas recebido em pedido já confirmado manualmente. Requer reconciliação."
                                        : "Ocorrência financeira requer reconciliação.");

                        blockRepository.save(block);
                }
        }

        private void applyLogisticsTransition(
                        Order order,
                        PaymentStatus paymentStatus) {

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

                        if (expired || order.isStockReleased()) {
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
