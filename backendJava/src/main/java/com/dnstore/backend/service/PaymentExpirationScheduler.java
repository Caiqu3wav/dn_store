package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.service.payment.PaymentGatewayException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpirationScheduler {

    private final OrderRepository orderRepository;
    private final PaymentExpirationService expirationService;

    @Value("${payment.order-expiration-minutes:60}")
    private long orderExpirationMinutes;

    @Scheduled(fixedDelayString = "${payment.expiration-scan-ms:60000}")
    public void expirePaymentOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(orderExpirationMinutes);
        List<UUID> orderIds = orderRepository.findIdsByStatusInAndCreatedAtBefore(
                List.of("PENDING_PAYMENT", "PAYMENT_FAILED"), cutoff);
        for (UUID orderId : orderIds) {
            try {
                expirationService.expireIfDue(orderId);
            } catch (PaymentGatewayException | ConflictException | IllegalStateException exception) {
                log.warn("payment.expiration.deferred orderId={} failureType={}",
                        orderId, exception.getClass().getSimpleName());
            }
        }
    }
}
