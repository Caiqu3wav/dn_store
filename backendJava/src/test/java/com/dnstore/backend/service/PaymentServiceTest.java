package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.dnstore.backend.exception.GlobalExceptionHandler;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.PaymentRepository;
import com.dnstore.backend.service.payment.PaymentCreationUncertainException;
import com.dnstore.backend.service.payment.PaymentGateway;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentGateway gateway;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentAttemptStateService paymentState;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void initPayment_shouldUsePersistedOrderTotalAndUniqueAttemptReference() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        User user = user();
        Order order = order(orderId, user);
        Payment payment = payment(paymentId, order, "CREATING");
        when(paymentState.prepareAttempt(orderId, user.getId(), "key-1", "PIX", null))
                .thenReturn(new PaymentAttemptStateService.PreparedAttempt(payment, true));
        when(orderRepository.findByIdWithUser(orderId)).thenReturn(Optional.of(order));
        PaymentGateway.PaymentResult result = new PaymentGateway.PaymentResult(
                "charge-1", "PENDING", "PIX", "qr", "copy", null, null,
                order.getTotal(), payment.getExternalReference(), "customer-1", "BRL");
        when(gateway.createPayment(any(PaymentGateway.PaymentRequest.class))).thenReturn(result);
        when(paymentState.completeAttempt(paymentId, result)).thenReturn(payment);

        Payment actual = paymentService.initPayment(
                orderId, user.getId(), "key-1", "52998224725", "PIX", null);

        assertThat(actual).isSameAs(payment);
        ArgumentCaptor<PaymentGateway.PaymentRequest> captor =
                ArgumentCaptor.forClass(PaymentGateway.PaymentRequest.class);
        verify(gateway).createPayment(captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("129.90");
        assertThat(captor.getValue().externalReference()).isEqualTo(payment.getExternalReference());
    }

    @Test
    void initPayment_duplicateRequest_shouldReuseExistingAttemptWithoutCallingProvider() {
        UUID orderId = UUID.randomUUID();
        User user = user();
        Payment payment = payment(UUID.randomUUID(), order(orderId, user), "PENDING");
        when(paymentState.prepareAttempt(orderId, user.getId(), "key-1", "PIX", null))
                .thenReturn(new PaymentAttemptStateService.PreparedAttempt(payment, false));

        assertThat(paymentService.initPayment(
                orderId, user.getId(), "key-1", "52998224725", "PIX", null)).isSameAs(payment);
        verifyNoInteractions(gateway);
    }

    @Test
    void initPayment_whenPostOutcomeIsUncertain_shouldPersistReconciliationState() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        User user = user();
        Payment payment = payment(paymentId, order(orderId, user), "CREATING");
        when(paymentState.prepareAttempt(orderId, user.getId(), "key-1", "PIX", null))
                .thenReturn(new PaymentAttemptStateService.PreparedAttempt(payment, true));
        when(orderRepository.findByIdWithUser(orderId)).thenReturn(Optional.of(payment.getOrder()));
        when(gateway.createPayment(any(PaymentGateway.PaymentRequest.class)))
                .thenThrow(new PaymentCreationUncertainException("uncertain", null));
        Payment uncertain = payment(paymentId, payment.getOrder(), "RECONCILIATION_REQUIRED");
        when(paymentState.markUncertain(paymentId)).thenReturn(uncertain);

        Payment result = paymentService.initPayment(
                orderId, user.getId(), "key-1", "52998224725", "PIX", null);

        assertThat(result.getStatus()).isEqualTo("RECONCILIATION_REQUIRED");
        verify(paymentState, never()).markDefinitiveFailure(paymentId);
    }

    @Test
    void initPayment_whenSameAttemptIsUncertain_shouldReconcileBeforeAnyNewPost() {
        UUID orderId = UUID.randomUUID();
        User user = user();
        Payment payment = payment(UUID.randomUUID(), order(orderId, user), "RECONCILIATION_REQUIRED");
        payment.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        when(paymentState.prepareAttempt(orderId, user.getId(), "key-1", "PIX", null))
                .thenReturn(new PaymentAttemptStateService.PreparedAttempt(payment, false));
        when(gateway.findPaymentByReference(payment.getExternalReference())).thenReturn(null);
        when(paymentState.markReconciledFailure(payment.getId())).thenReturn(payment);

        paymentService.initPayment(orderId, user.getId(), "key-1", "52998224725", "PIX", null);

        verify(gateway).findPaymentByReference(payment.getExternalReference());
        verify(gateway, never()).createPayment(any());
        verify(paymentState).markReconciledFailure(payment.getId());
    }

    @Test
    void initPayment_whenCardRequested_shouldRejectWithoutCreatingAttempt() {
        assertThatThrownBy(() -> paymentService.initPayment(
                UUID.randomUUID(), UUID.randomUUID(), "key-1", "52998224725", "CREDIT_CARD", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(paymentState, gateway);
    }

    @Test
    void handleWebhook_shouldReconcileCanonicalProviderStateBeforeApplying() {
        PaymentGateway.WebhookResult event = new PaymentGateway.WebhookResult(
                "evt-1", "charge-1", "PAID", "order:attempt", BigDecimal.TEN, "BRL", "customer-1");
        PaymentGateway.ProviderPayment canonical = new PaymentGateway.ProviderPayment(
                "charge-1", "PAID", "order:attempt", BigDecimal.TEN,
                "BRL", "customer-1", "PIX", null, null, null, null);
        when(gateway.processWebhook("payload", "signature")).thenReturn(event);
        when(gateway.getPayment("charge-1")).thenReturn(canonical);

        paymentService.handleWebhook("payload", "signature");

        verify(paymentState).applyWebhook(event, canonical);
    }

    @Test
    void findByOrderId_shouldReturnLatestAttempt() {
        UUID orderId = UUID.randomUUID();
        Payment latest = new Payment();
        when(paymentRepository.findByOrder_IdOrderByAttemptNumberDesc(orderId))
                .thenReturn(java.util.List.of(latest));

        assertThat(paymentService.findByOrderId(orderId)).isSameAs(latest);
    }

    @Test
    void webhookController_whenTokenInvalid_shouldReturnUnauthorized() throws Exception {
        PaymentService service = mock(PaymentService.class);
        var controller = new com.dnstore.backend.controller.PaymentController(service);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        doThrow(new com.dnstore.backend.service.payment.WebhookAuthenticationException())
                .when(service).handleWebhook(anyString(), anyString());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/payment/webhook")
                        .content("{}")
                        .header("asaas-access-token", "wrong-token"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Buyer");
        user.setEmail("buyer@example.com");
        return user;
    }

    private Order order(UUID id, User user) {
        Order order = new Order();
        order.setId(id);
        order.setUser(user);
        order.setStatus("PENDING_PAYMENT");
        order.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        order.setTotal(new BigDecimal("129.90"));
        return order;
    }

    private Payment payment(UUID id, Order order, String status) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setOrder(order);
        payment.setAttemptNumber(1);
        payment.setIdempotencyKey("key-1");
        payment.setExternalReference(order.getId() + ":key-1");
        if (!"CREATING".equals(status) && !"RECONCILIATION_REQUIRED".equals(status)) {
            payment.setExternalId("charge-1");
        }
        payment.setAmount(order.getTotal());
        payment.setPaymentMethod("PIX");
        payment.setStatus(status);
        payment.setCreatedAt(LocalDateTime.now());
        return payment;
    }
}
