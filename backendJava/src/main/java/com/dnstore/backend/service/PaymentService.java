package com.dnstore.backend.service;

import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.enums.OrderStatus;
import com.dnstore.backend.model.enums.PaymentStatus;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.PaymentRepository;
import com.dnstore.backend.service.payment.PaymentGateway;
import com.dnstore.backend.service.payment.PaymentGatewayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class PaymentService {

    private final PaymentGateway gateway;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public PaymentService(
            @Qualifier("asaas") PaymentGateway gateway,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository) {
        this.gateway = gateway;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Payment initPayment(
            UUID orderId,
            UUID requestingUserId,
            String cpfCnpj,
            String paymentMethod,
            Integer installments,
            PaymentGateway.CardData cardData) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));

        if (!order.getUser().getId().equals(requestingUserId)) {
            throw new ResourceNotFoundException("Pedido não encontrado.");
        }

        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            throw new ConflictException("Pedido não está aguardando pagamento.");
        }

        if (order.getTotal() == null || order.getTotal().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new ConflictException("Pedido não possui valor válido para pagamento.");
        }

        if (paymentRepository.findByOrder_Id(orderId).isPresent()) {
            throw new ConflictException("Já existe um pagamento iniciado para este pedido.");
        }

        PaymentGateway.PaymentRequest gatewayRequest = new PaymentGateway.PaymentRequest(
                order.getId(),
                order.getTotal(),
                order.getUser().getName(),
                order.getUser().getEmail(),
                cpfCnpj,
                "Pedido DN Store #" + order.getId(),
                paymentMethod,
                installments,
                cardData);

        PaymentGateway.PaymentResult result = gateway.createPayment(gatewayRequest);
        if (result == null || result.externalId() == null || result.externalId().isBlank()) {
            throw new PaymentGatewayException("Gateway retornou uma cobrança inválida");
        }

        PaymentStatus paymentStatus = PaymentStatus.valueOf(result.status());

        Payment payment = new Payment();

        payment.setOrder(order);
        payment.setExternalId(result.externalId());
        payment.setPaymentMethod(result.paymentMethod() == null ? paymentMethod : result.paymentMethod());

        // O valor vem exclusivamente do banco
        payment.setAmount(order.getTotal());

        payment.setStatus(paymentStatus.name());
        payment.setPixQrCode(result.pixQrCode());
        payment.setPixCopyPaste(result.pixCopyPaste());
        payment.setBoletoUrl(result.boletoUrl());
        payment.setBoletoBarcode(result.boletoBarcode());
        payment.setInstallments(installments);

        return paymentRepository.save(payment);
    }

    @Transactional
    public void handleWebhook(String rawPayload, String signatureHeader) {
        PaymentGateway.WebhookResult result = gateway.processWebhook(rawPayload, signatureHeader);

        Payment payment = paymentRepository.findByExternalIdForUpdate(result.externalId())
                .orElseThrow(() -> {
                    log.warn("Webhook recebido para pagamento não localizado");
                    return new ResourceNotFoundException("Pagamento não encontrado.");
                });

        PaymentStatus currentStatus = PaymentStatus.valueOf(payment.getStatus());
        PaymentStatus nextStatus = PaymentStatus.valueOf(result.normalizedStatus());
        if (currentStatus == nextStatus || currentStatus == PaymentStatus.REFUNDED
                || (currentStatus == PaymentStatus.PAID && nextStatus != PaymentStatus.REFUNDED)) {
            return;
        }

        payment.setStatus(nextStatus.name());
        if (nextStatus == PaymentStatus.PAID && payment.getPaidAt() == null) {
            payment.setPaidAt(LocalDateTime.now());
        }

        String nextOrderStatus = nextStatus.orderStatus().name();
        if (nextStatus != PaymentStatus.PENDING
                && !nextOrderStatus.equals(payment.getOrder().getStatus())) {
            payment.getOrder().setStatus(nextOrderStatus);
            orderRepository.save(payment.getOrder());
        }
        paymentRepository.save(payment);
    }

    public Payment findByOrderId(UUID orderId) {
        return paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado para o pedido."));
    }

    /**
     * Busca pagamento garantindo que o pedido pertence ao usuário — previne IDOR.
     */
    public Payment findByOrderIdAndUser(UUID orderId, UUID userId) {
        Payment payment = findByOrderId(orderId);
        if (!payment.getOrder().getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Pagamento não encontrado para o pedido.");
        }
        return payment;
    }
}
