package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.PaymentRepository;
import com.dnstore.backend.service.payment.PaymentGateway;
import com.dnstore.backend.service.payment.PaymentGatewayException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentExpirationServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderInventoryService orderInventoryService;
    @Mock
    private PaymentGateway gateway;

    @InjectMocks
    private PaymentExpirationService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "orderExpirationMinutes", 60L);
    }

    @Test
    void expireIfDue_withoutPayment_shouldReleaseStockAndExpireOrder() {
        Order order = order("PENDING_PAYMENT", LocalDateTime.now().minusHours(2));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(order.getId())).thenReturn(List.of());

        service.expireIfDue(order.getId());

        assertThat(order.getStatus()).isEqualTo("EXPIRED");
        verify(orderInventoryService).releaseReservedStock(order);
        verify(orderRepository).save(order);
        verifyNoInteractions(gateway);
    }

    @Test
    void expireIfDue_withUncertainAttemptAndNoChargeAfterReconciliation_shouldExpire() {
        Order order = order("PENDING_PAYMENT", LocalDateTime.now().minusHours(2));
        Payment payment = payment(order, "RECONCILIATION_REQUIRED", null);
        payment.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(order.getId())).thenReturn(List.of(payment));
        when(gateway.findPaymentByReference(payment.getExternalReference())).thenReturn(null);

        service.expireIfDue(order.getId());

        assertThat(payment.getStatus()).isEqualTo("FAILED");
        assertThat(order.getStatus()).isEqualTo("EXPIRED");
        verify(orderInventoryService).releaseReservedStock(order);
    }

    @Test
    void expireIfDue_whenChargeStillActiveAndCancellationUnconfirmed_shouldRetainStock() {
        Order order = order("PENDING_PAYMENT", LocalDateTime.now().minusHours(2));
        Payment payment = payment(order, "PENDING", "charge-1");
        PaymentGateway.ProviderPayment stillPending = provider(payment, "PENDING");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(order.getId())).thenReturn(List.of(payment));
        when(gateway.getPayment("charge-1")).thenReturn(stillPending);
        when(gateway.cancelPayment("charge-1")).thenReturn(stillPending);

        service.expireIfDue(order.getId());

        assertThat(order.getStatus()).isEqualTo("PENDING_PAYMENT");
        verify(orderInventoryService, never()).releaseReservedStock(any());
        verify(orderRepository, never()).save(order);
    }

    @Test
    void expireIfDue_afterProviderConfirmsCancellation_shouldReleaseStock() {
        Order order = order("PENDING_PAYMENT", LocalDateTime.now().minusHours(2));
        Payment payment = payment(order, "PENDING", "charge-1");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(order.getId())).thenReturn(List.of(payment));
        when(gateway.getPayment("charge-1")).thenReturn(provider(payment, "PENDING"));
        when(gateway.cancelPayment("charge-1")).thenReturn(provider(payment, "FAILED"));

        service.expireIfDue(order.getId());

        assertThat(payment.getStatus()).isEqualTo("FAILED");
        assertThat(order.getStatus()).isEqualTo("EXPIRED");
        verify(orderInventoryService).releaseReservedStock(order);
    }

    @Test
    void expireIfDue_whenProviderShowsPaid_shouldRequireReviewAndKeepStock() {
        Order order = order("PENDING_PAYMENT", LocalDateTime.now().minusHours(2));
        Payment payment = payment(order, "PENDING", "charge-1");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(order.getId())).thenReturn(List.of(payment));
        when(gateway.getPayment("charge-1")).thenReturn(provider(payment, "PAID"));

        service.expireIfDue(order.getId());

        assertThat(payment.getStatus()).isEqualTo("PAID");
        assertThat(order.getStatus()).isEqualTo("PAYMENT_REVIEW");
        verify(orderInventoryService, never()).releaseReservedStock(any());
    }

    @Test
    void expireIfDue_providerUnavailable_shouldLeaveAttemptAndStockUnchanged() {
        Order order = order("PAYMENT_FAILED", LocalDateTime.now().minusHours(2));
        Payment payment = payment(order, "RECONCILIATION_REQUIRED", null);
        payment.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(order.getId())).thenReturn(List.of(payment));
        when(gateway.findPaymentByReference(payment.getExternalReference()))
                .thenThrow(new PaymentGatewayException("unavailable"));

        assertThatThrownBy(() -> service.expireIfDue(order.getId()))
                .isInstanceOf(PaymentGatewayException.class);
        assertThat(order.getStatus()).isEqualTo("PAYMENT_FAILED");
        verify(orderInventoryService, never()).releaseReservedStock(any());
    }

    @Test
    void expireIfDue_shouldNotExpireShippedOrders() {
        Order order = order("SHIPPED", LocalDateTime.now().minusHours(2));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        service.expireIfDue(order.getId());

        verifyNoInteractions(paymentRepository, orderInventoryService, gateway);
    }

    private Order order(String status, LocalDateTime createdAt) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUser(new User());
        order.setStatus(status);
        order.setCreatedAt(createdAt);
        order.setTotal(new BigDecimal("125.00"));
        return order;
    }

    private Payment payment(Order order, String status, String externalId) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        payment.setAttemptNumber(1);
        payment.setIdempotencyKey("key");
        payment.setExternalReference(order.getId() + ":key");
        payment.setExternalId(externalId);
        payment.setProviderCustomerId(externalId == null ? null : "customer-1");
        payment.setAmount(order.getTotal());
        payment.setPaymentMethod("PIX");
        payment.setStatus(status);
        payment.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        return payment;
    }

    private PaymentGateway.ProviderPayment provider(Payment payment, String status) {
        return new PaymentGateway.ProviderPayment(
                payment.getExternalId(),
                status,
                payment.getExternalReference(),
                payment.getAmount(),
                "BRL",
                "customer-1",
                "PIX",
                null,
                null,
                null,
                null);
    }
}
