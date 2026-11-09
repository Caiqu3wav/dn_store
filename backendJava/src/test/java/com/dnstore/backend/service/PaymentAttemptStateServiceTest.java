package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.PaymentWebhookEvent;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.PaymentRepository;
import com.dnstore.backend.repository.PaymentWebhookEventRepository;
import com.dnstore.backend.service.payment.PaymentGateway;
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
class PaymentAttemptStateServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentWebhookEventRepository webhookEventRepository;
    @Mock
    private OrderInventoryService orderInventoryService;

    @InjectMocks
    private PaymentAttemptStateService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "orderExpirationMinutes", 60L);
    }

    @Test
    void prepareAttempt_shouldAllowRetryWithinWindowOnSameOrder() {
        User user = user();
        Order order = order(user, "PAYMENT_FAILED", LocalDateTime.now().minusMinutes(30));
        Payment failed = payment(order, "FAILED", "old-key", "old-charge");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_IdAndIdempotencyKey(order.getId(), "new-key"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByOrder_IdOrderByAttemptNumberDesc(order.getId()))
                .thenReturn(List.of(failed));
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentAttemptStateService.PreparedAttempt prepared =
                service.prepareAttempt(order.getId(), user.getId(), "new-key", "PIX", null);

        assertThat(prepared.newlyCreated()).isTrue();
        assertThat(prepared.payment().getOrder()).isSameAs(order);
        assertThat(prepared.payment().getAttemptNumber()).isEqualTo(2);
        assertThat(prepared.payment().getExternalReference()).isEqualTo(order.getId() + ":new-key");
        assertThat(prepared.payment().getStatus()).isEqualTo("CREATING");
        verify(orderInventoryService, never()).reserveReleasedStock(any());
    }

    @Test
    void prepareAttempt_withSameIdempotencyKey_shouldReturnExistingAttempt() {
        User user = user();
        Order order = order(user, "PENDING_PAYMENT", LocalDateTime.now().minusMinutes(5));
        Payment existing = payment(order, "PENDING", "same-key", "charge-1");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_IdAndIdempotencyKey(order.getId(), "same-key"))
                .thenReturn(Optional.of(existing));

        PaymentAttemptStateService.PreparedAttempt prepared =
                service.prepareAttempt(order.getId(), user.getId(), "same-key", "PIX", null);

        assertThat(prepared.payment()).isSameAs(existing);
        assertThat(prepared.newlyCreated()).isFalse();
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void prepareAttempt_shouldAcceptUrlSafeIdempotencyKey() {
        User user = user();
        Order order = order(user, "PAYMENT_FAILED", LocalDateTime.now().minusMinutes(5));
        Payment failed = payment(order, "FAILED", "old-key", "old-charge");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_IdAndIdempotencyKey(order.getId(), "retry_01-~.key"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByOrder_IdOrderByAttemptNumberDesc(order.getId()))
                .thenReturn(List.of(failed));
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentAttemptStateService.PreparedAttempt prepared = service.prepareAttempt(
                order.getId(), user.getId(), "retry_01-~.key", "PIX", null);

        assertThat(prepared.newlyCreated()).isTrue();
    }

    @Test
    void prepareAttempt_shouldRejectReservedCharactersInNewIdempotencyKey() {
        User user = user();
        Order order = order(user, "PAYMENT_FAILED", LocalDateTime.now().minusMinutes(5));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_IdAndIdempotencyKey(order.getId(), "retry&again"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepareAttempt(
                order.getId(), user.getId(), "retry&again", "PIX", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Chave de idempotência inválida.");
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void prepareAttempt_withActiveCharge_shouldNotCreateSecondAttempt() {
        User user = user();
        Order order = order(user, "PENDING_PAYMENT", LocalDateTime.now().minusMinutes(5));
        Payment pending = payment(order, "PENDING", "old-key", "charge-1");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_IdAndIdempotencyKey(order.getId(), "new-key"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByOrder_IdOrderByAttemptNumberDesc(order.getId()))
                .thenReturn(List.of(pending));

        PaymentAttemptStateService.PreparedAttempt prepared =
                service.prepareAttempt(order.getId(), user.getId(), "new-key", "BOLETO", null);

        assertThat(prepared.payment()).isSameAs(pending);
        assertThat(prepared.newlyCreated()).isFalse();
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void prepareAttempt_withOlderActiveCharge_shouldNotCreateAnotherAttempt() {
        User user = user();
        Order order = order(user, "PAYMENT_FAILED", LocalDateTime.now().minusMinutes(5));
        Payment latestFailed = payment(order, "FAILED", "latest-key", "charge-latest");
        latestFailed.setAttemptNumber(2);
        Payment olderPending = payment(order, "PENDING", "older-key", "charge-older");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_IdAndIdempotencyKey(order.getId(), "new-key"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByOrder_IdOrderByAttemptNumberDesc(order.getId()))
                .thenReturn(List.of(latestFailed, olderPending));

        PaymentAttemptStateService.PreparedAttempt prepared =
                service.prepareAttempt(order.getId(), user.getId(), "new-key", "PIX", null);

        assertThat(prepared.payment()).isSameAs(olderPending);
        assertThat(prepared.newlyCreated()).isFalse();
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void prepareAttempt_afterExpiration_shouldRejectRetry() {
        User user = user();
        Order order = order(user, "PAYMENT_FAILED", LocalDateTime.now().minusMinutes(61));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.prepareAttempt(
                order.getId(), user.getId(), "new-key", "PIX", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("expirou");
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void prepareAttempt_forAnotherUsersOrder_shouldNotExposeOrChargeOrder() {
        User owner = user();
        Order order = order(owner, "PENDING_PAYMENT", LocalDateTime.now().minusMinutes(5));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.prepareAttempt(
                order.getId(), UUID.randomUUID(), "key", "PIX", null))
                .isInstanceOf(com.dnstore.backend.exception.ResourceNotFoundException.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void applyWebhook_duplicateEvent_shouldHaveNoSecondEffect() {
        Order order = order(user(), "PENDING_PAYMENT", LocalDateTime.now().minusMinutes(5));
        Payment payment = payment(order, "PENDING", "key", "charge-1");
        mockWebhookLookup(payment, order);
        when(webhookEventRepository.existsById("event-1")).thenReturn(false, true);
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(orderRepository.save(order)).thenReturn(order);

        PaymentGateway.WebhookResult event = webhook("event-1", "charge-1", "PAID");
        PaymentGateway.ProviderPayment provider = provider("charge-1", "PAID", payment.getExternalReference());

        assertThat(service.applyWebhook(event, provider)).isTrue();
        assertThat(service.applyWebhook(event, provider)).isFalse();

        verify(orderRepository, times(1)).save(order);
        verify(webhookEventRepository, times(1)).saveAndFlush(any(PaymentWebhookEvent.class));
        assertThat(payment.getStatus()).isEqualTo("PAID");
    }

    @Test
    void applyWebhook_outOfOrderPendingAfterPaid_shouldNotDowngradePaymentOrOrder() {
        Order order = order(user(), "PAID", LocalDateTime.now().minusMinutes(5));
        Payment payment = payment(order, "PAID", "key", "charge-1");
        mockWebhookLookup(payment, order);

        service.applyWebhook(
                webhook("event-old", "charge-1", "PENDING"),
                provider("charge-1", "PENDING", payment.getExternalReference()));

        assertThat(payment.getStatus()).isEqualTo("PAID");
        assertThat(order.getStatus()).isEqualTo("PAID");
        verify(orderRepository, never()).save(order);
    }

    @Test
    void applyWebhook_outOfOrderPendingAfterFailure_shouldNotReopenPayment() {
        Order order = order(user(), "PAYMENT_FAILED", LocalDateTime.now().minusMinutes(5));
        Payment payment = payment(order, "FAILED", "key", "charge-1");
        mockWebhookLookup(payment, order);

        service.applyWebhook(
                webhook("event-old", "charge-1", "PENDING"),
                provider("charge-1", "PENDING", payment.getExternalReference()));

        assertThat(payment.getStatus()).isEqualTo("FAILED");
        assertThat(order.getStatus()).isEqualTo("PAYMENT_FAILED");
        verify(orderRepository, never()).save(order);
    }

    @Test
    void applyWebhook_paidShouldPreserveProcessingAndShippingStates() {
        for (String logisticalStatus : List.of("PROCESSING", "SHIPPED", "DELIVERED")) {
            Order order = order(user(), logisticalStatus, LocalDateTime.now().minusMinutes(5));
            Payment payment = payment(order, "PENDING", "key", "charge-1");
            mockWebhookLookup(payment, order);

            service.applyWebhook(
                    webhook("event-" + logisticalStatus, "charge-1", "PAID"),
                    provider("charge-1", "PAID", payment.getExternalReference()));

            assertThat(order.getStatus()).isEqualTo(logisticalStatus);
        }
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void applyWebhook_latePaymentShouldEnterReviewInsteadOfReactivatingOrder() {
        Order order = order(user(), "PENDING_PAYMENT", LocalDateTime.now().minusMinutes(61));
        Payment payment = payment(order, "PENDING", "key", "charge-1");
        mockWebhookLookup(payment, order);
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(orderRepository.save(order)).thenReturn(order);

        service.applyWebhook(
                webhook("event-late", "charge-1", "PAID"),
                provider("charge-1", "PAID", payment.getExternalReference()));

        assertThat(payment.getStatus()).isEqualTo("PAID");
        assertThat(order.getStatus()).isEqualTo("PAYMENT_REVIEW");
        verify(orderInventoryService, never()).reserveReleasedStock(any());
    }

    @Test
    void applyWebhook_afterCancellationOrExpirationShouldNotReactivateOrder() {
        for (String logisticalStatus : List.of("CANCELLED", "EXPIRED")) {
            Order order = order(user(), logisticalStatus, LocalDateTime.now().minusMinutes(61));
            Payment payment = payment(order, "PENDING", "key", "charge-1");
            mockWebhookLookup(payment, order);

            service.applyWebhook(
                    webhook("event-" + logisticalStatus, "charge-1", "PAID"),
                    provider("charge-1", "PAID", payment.getExternalReference()));

            assertThat(order.getStatus()).isEqualTo(logisticalStatus);
        }
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void applyWebhook_refundStatusesShouldNotChangeOrderLogistics() {
        for (String refundStatus : List.of("REFUND_REQUESTED", "PARTIALLY_REFUNDED", "REFUNDED")) {
            Order order = order(user(), "PROCESSING", LocalDateTime.now().minusMinutes(5));
            Payment payment = payment(order, "PAID", "key", "charge-1");
            mockWebhookLookup(payment, order);

            service.applyWebhook(
                    webhook("event-" + refundStatus, "charge-1", refundStatus),
                    provider("charge-1", refundStatus, payment.getExternalReference()));

            assertThat(payment.getStatus()).isEqualTo(refundStatus);
            assertThat(order.getStatus()).isEqualTo("PROCESSING");
        }
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void applyWebhook_divergentReferenceOrAmountShouldRejectEvent() {
        Order order = order(user(), "PENDING_PAYMENT", LocalDateTime.now().minusMinutes(5));
        Payment payment = payment(order, "PENDING", "key", "charge-1");
        mockWebhookLookup(payment, order);

        assertThatThrownBy(() -> service.applyWebhook(
                webhook("event-bad", "charge-1", "PAID"),
                new PaymentGateway.ProviderPayment(
                        "charge-1", "PAID", "other-order", BigDecimal.TEN,
                        "BRL", "customer-1", "PIX", null, null, null, null)))
                .isInstanceOf(ConflictException.class);
        verify(webhookEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void applyWebhook_paidWithoutReservedStockShouldRequireReviewWithoutReReserving() {
        Order order = order(user(), "PAYMENT_FAILED", LocalDateTime.now().minusMinutes(5));
        order.setStockReleased(true);
        Payment payment = payment(order, "PENDING", "key", "charge-1");
        mockWebhookLookup(payment, order);
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(orderRepository.save(order)).thenReturn(order);

        service.applyWebhook(
                webhook("event-stock", "charge-1", "PAID"),
                provider("charge-1", "PAID", payment.getExternalReference()));

        assertThat(order.getStatus()).isEqualTo("PAYMENT_REVIEW");
        verify(orderInventoryService, never()).reserveReleasedStock(any());
    }

    private void mockWebhookLookup(Payment payment, Order order) {
        when(paymentRepository.findByExternalId(payment.getExternalId())).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByExternalIdForUpdate(payment.getExternalId())).thenReturn(Optional.of(payment));
    }

    private PaymentGateway.WebhookResult webhook(String eventId, String externalId, String status) {
        return new PaymentGateway.WebhookResult(
                eventId, externalId, status, null, null, null, null);
    }

    private PaymentGateway.ProviderPayment provider(String externalId, String status, String reference) {
        return new PaymentGateway.ProviderPayment(
                externalId, status, reference, new BigDecimal("125.00"),
                "BRL", "customer-1", "PIX", null, null, null, null);
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    private Order order(User user, String status, LocalDateTime createdAt) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUser(user);
        order.setStatus(status);
        order.setCreatedAt(createdAt);
        order.setTotal(new BigDecimal("125.00"));
        return order;
    }

    private Payment payment(Order order, String status, String key, String externalId) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        payment.setAttemptNumber(1);
        payment.setIdempotencyKey(key);
        payment.setExternalReference(order.getId() + ":" + key);
        payment.setExternalId(externalId);
        payment.setProviderCustomerId("customer-1");
        payment.setAmount(new BigDecimal("125.00"));
        payment.setPaymentMethod("PIX");
        payment.setStatus(status);
        payment.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        return payment;
    }
}
