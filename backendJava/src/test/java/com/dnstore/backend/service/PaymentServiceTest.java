package com.dnstore.backend.service;

import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.User;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.GlobalExceptionHandler;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.PaymentRepository;
import com.dnstore.backend.service.payment.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentGateway gateway;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void initPayment_shouldRejectDuplicatePaymentForOrder() {
        User user = new User();
        user.setId(UUID.randomUUID());
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUser(user);
        order.setStatus("PENDING_PAYMENT");
        order.setTotal(new BigDecimal("120.00"));

        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_Id(order.getId())).thenReturn(Optional.of(new Payment()));

        assertThatThrownBy(() -> paymentService.initPayment(
                order.getId(),
                user.getId(),
                "12345678909",
                "PIX",
                null,
                null)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Já existe");
    }

    @Test
    void initPayment_shouldUsePersistedOrderTotal() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Buyer");
        user.setEmail("buyer@example.com");
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUser(user);
        order.setStatus("PENDING_PAYMENT");
        order.setTotal(new BigDecimal("129.90"));

        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_Id(order.getId())).thenReturn(Optional.empty());
        when(gateway.createPayment(any(PaymentGateway.PaymentRequest.class)))
                .thenReturn(new PaymentGateway.PaymentResult("charge-1", "PENDING", "PIX", null, null, null, null));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = paymentService.initPayment(order.getId(), user.getId(), "52998224725", "PIX", null, null);

        assertThat(payment.getAmount()).isEqualByComparingTo("129.90");
        org.mockito.ArgumentCaptor<PaymentGateway.PaymentRequest> requestCaptor = org.mockito.ArgumentCaptor
                .forClass(PaymentGateway.PaymentRequest.class);
        verify(gateway).createPayment(requestCaptor.capture());
        assertThat(requestCaptor.getValue().amount()).isEqualByComparingTo("129.90");
    }

    @Test
    void initPayment_shouldRejectMissingOrder() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.initPayment(orderId, UUID.randomUUID(),
                "52998224725", "PIX", null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Pedido não encontrado");
        verifyNoInteractions(gateway);
    }

    @Test
    void initPayment_shouldRejectAnotherUsersOrder() {
        User owner = new User();
        owner.setId(UUID.randomUUID());
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUser(owner);
        order.setStatus("PENDING_PAYMENT");
        order.setTotal(new BigDecimal("50.00"));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.initPayment(order.getId(), UUID.randomUUID(),
                "52998224725", "PIX", null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Pedido não encontrado");
        verifyNoInteractions(gateway);
    }

    @Test
    void handleWebhook_whenPaymentAlreadyPaid_shouldBeIdempotent() {
        User user = new User();
        user.setId(UUID.randomUUID());

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUser(user);
        order.setStatus("PAID");

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        payment.setExternalId("asaas-123");
        payment.setStatus("PAID");

        when(gateway.processWebhook(anyString(), anyString()))
                .thenReturn(new PaymentGateway.WebhookResult("asaas-123", "PAID"));
        when(paymentRepository.findByExternalIdForUpdate("asaas-123")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook("{}", "token");

        verify(orderRepository, never()).save(any(Order.class));
        assertThat(payment.getStatus()).isEqualTo("PAID");
    }

    @Test
    void handleWebhook_whenPaymentConfirmed_shouldUpdatePaymentAndOrderOnce() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus("PENDING_PAYMENT");
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setExternalId("asaas-confirmed");
        payment.setStatus("PENDING");

        when(gateway.processWebhook(anyString(), anyString()))
                .thenReturn(new PaymentGateway.WebhookResult("asaas-confirmed", "PAID"));
        when(paymentRepository.findByExternalIdForUpdate("asaas-confirmed")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(orderRepository.save(order)).thenReturn(order);

        paymentService.handleWebhook("{}", "token");
        paymentService.handleWebhook("{}", "token");

        assertThat(payment.getStatus()).isEqualTo("PAID");
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo("PAID");
        verify(paymentRepository, times(1)).save(payment);
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void webhookController_whenEventInvalid_shouldReturnBadRequest() throws Exception {
        PaymentService paymentServiceMock = mock(PaymentService.class);
        com.dnstore.backend.controller.PaymentController controller = new com.dnstore.backend.controller.PaymentController(
                paymentServiceMock);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        doThrow(new IllegalArgumentException("Webhook inválido"))
                .when(paymentServiceMock).handleWebhook(anyString(), anyString());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/payment/webhook")
                .content("{}")
                .header("asaas-access-token", "token"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void webhookController_whenTokenInvalid_shouldReturnUnauthorized() throws Exception {
        PaymentService paymentServiceMock = mock(PaymentService.class);
        com.dnstore.backend.controller.PaymentController controller = new com.dnstore.backend.controller.PaymentController(
                paymentServiceMock);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        doThrow(new com.dnstore.backend.service.payment.WebhookAuthenticationException())
                .when(paymentServiceMock).handleWebhook(anyString(), anyString());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/payment/webhook")
                .content("{}")
                .header("asaas-access-token", "wrong-token"))
                .andExpect(result -> {
                    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
                });
    }

    @Test
    void webhookController_whenProcessingFails_shouldReturnGenericServerError() throws Exception {
        PaymentService paymentServiceMock = mock(PaymentService.class);
        com.dnstore.backend.controller.PaymentController controller = new com.dnstore.backend.controller.PaymentController(
                paymentServiceMock);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        doThrow(new IllegalStateException("internal database detail"))
                .when(paymentServiceMock).handleWebhook(anyString(), anyString());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/payment/webhook")
                .content("{}")
                .header("asaas-access-token", "token"))
                .andExpect(result -> {
                    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
                    assertThat(result.getResponse().getContentAsString()).doesNotContain("internal database detail");
                });
    }
}
