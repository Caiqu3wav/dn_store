package com.dnstore.backend.service;

import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.Payment;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.PaymentRepository;
import com.dnstore.backend.service.payment.PaymentGateway;
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
            OrderRepository orderRepository
    ) {
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
            PaymentGateway.CardData cardData
    ) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Pedido não encontrado")
                );

        if (!order.getUser().getId().equals(requestingUserId)) {
            throw new IllegalArgumentException("Pedido não encontrado");
        }

        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new IllegalStateException(
                    "Pedido não está aguardando pagamento"
            );
        }

        if (paymentRepository.findByOrder_Id(orderId).isPresent()) {
            throw new IllegalStateException(
                    "Já existe um pagamento iniciado para este pedido"
            );
        }

        PaymentGateway.PaymentRequest gatewayRequest =
                new PaymentGateway.PaymentRequest(
                        order.getId(),
                        order.getTotal(),
                        order.getUser().getName(),
                        order.getUser().getEmail(),
                        cpfCnpj,
                        "Pedido DN Store #" + order.getId(),
                        paymentMethod,
                        installments,
                        cardData
                );

        PaymentGateway.PaymentResult result =
                gateway.createPayment(gatewayRequest);

        Payment payment = new Payment();

        payment.setOrder(order);
        payment.setExternalId(result.externalId());
        payment.setPaymentMethod(paymentMethod);

        // O valor vem exclusivamente do banco
        payment.setAmount(order.getTotal());

        payment.setStatus(result.status());
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

        Payment payment = paymentRepository.findByExternalId(result.externalId())
                .orElseThrow(() -> {
                    log.warn("Webhook recebido para pagamento desconhecido: {}", result.externalId());
                    return new IllegalArgumentException("Pagamento não encontrado");
                });

        String status = result.normalizedStatus();

        payment.setStatus(status);

        if ("PAID".equals(status)) {
            payment.setPaidAt(LocalDateTime.now());
            payment.getOrder().setStatus("PAID");
            orderRepository.save(payment.getOrder());
            log.info("Pedido {} marcado como PAID", payment.getOrder().getId());
        } else if ("FAILED".equals(status)) {
            payment.getOrder().setStatus("PAYMENT_FAILED");
            orderRepository.save(payment.getOrder());
        }

        paymentRepository.save(payment);
    }

    public Payment findByOrderId(UUID orderId) {
        return paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pagamento não encontrado para o pedido"));
    }

    /** Busca pagamento garantindo que o pedido pertence ao usuário — previne IDOR. */
    public Payment findByOrderIdAndUser(UUID orderId, UUID userId) {
        Payment payment = findByOrderId(orderId);
        if (!payment.getOrder().getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Pagamento não encontrado para o pedido");
        }
        return payment;
    }
}
