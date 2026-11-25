package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.OrderStatusAudit;
import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.repository.FinancialBlockRepository;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.OrderStatusAuditRepository;
import com.dnstore.backend.repository.PaymentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class OrderManualPaymentServiceTest {

    private static final String REASON = "Transferência bancária conferida no extrato";

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private FinancialBlockRepository financialBlockRepository;
    @Mock
    private OrderStatusAuditRepository auditRepository;
    @InjectMocks
    private OrderManualPaymentService service;

    private User admin;
    private Order order;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setName("Admin");
        admin.setRole(Role.ADMIN);

        order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus("PENDING_PAYMENT");
    }

    private void orderIsLocked() {
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
    }

    private Payment attempt(String status) {
        Payment payment = new Payment();
        payment.setStatus(status);
        return payment;
    }

    @Test
    void confirm_recordsAdminTimestampPreviousAndNewStatusAndMarksTheOrderAsManual() {
        orderIsLocked();
        when(paymentRepository.findByOrderIdForUpdate(order.getId())).thenReturn(List.of(attempt("FAILED")));
        when(orderRepository.save(order)).thenReturn(order);
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        Order result = service.confirmPayment(order.getId(), admin, "  " + REASON + "  ");

        assertThat(result.getStatus()).isEqualTo("PAID");
        assertThat(result.isManualPaymentConfirmed()).isTrue();
        ArgumentCaptor<OrderStatusAudit> captor = ArgumentCaptor.forClass(OrderStatusAudit.class);
        verify(auditRepository).save(captor.capture());
        OrderStatusAudit audit = captor.getValue();
        assertThat(audit.getActor()).isSameAs(admin);
        assertThat(audit.getOrder()).isSameAs(order);
        assertThat(audit.getPreviousStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(audit.getNewStatus()).isEqualTo("PAID");
        assertThat(audit.getChangeType()).isEqualTo(OrderStatusAudit.MANUAL_PAYMENT_CONFIRMATION);
        assertThat(audit.getReason()).isEqualTo(REASON);
        assertThat(audit.getCreatedAt()).isAfter(before);
    }

    @Test
    void confirm_worksForOrdersWithoutAnyAsaasAttempt() {
        order.setStatus("PAYMENT_FAILED");
        orderIsLocked();
        when(paymentRepository.findByOrderIdForUpdate(order.getId())).thenReturn(List.of());
        when(orderRepository.save(order)).thenReturn(order);

        assertThat(service.confirmPayment(order.getId(), admin, REASON).getStatus()).isEqualTo("PAID");
        verify(auditRepository).save(any(OrderStatusAudit.class));
    }

    @Test
    void confirm_requiresAnAdministrator() {
        User customer = new User();
        customer.setId(UUID.randomUUID());
        customer.setRole(Role.USER);

        assertThatThrownBy(() -> service.confirmPayment(order.getId(), customer, REASON))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.confirmPayment(order.getId(), null, REASON))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(orderRepository, auditRepository);
    }

    @Test
    void confirm_requiresAJustification() {
        for (String invalid : new String[] {null, "", "   ", "curto", "         x         "}) {
            assertThatThrownBy(() -> service.confirmPayment(order.getId(), admin, invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("justificativa");
        }
        assertThatThrownBy(() -> service.confirmPayment(order.getId(), admin, "x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no máximo");

        verifyNoInteractions(orderRepository, auditRepository);
        assertThat(order.getStatus()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void confirm_unknownOrder_is404() {
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmPayment(order.getId(), admin, REASON))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void confirm_isRejectedForEveryStatusThatIsNotAwaitingPayment_includingAnAlreadyPaidOrder() {
        for (String status : new String[] {"PAID", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED", "EXPIRED",
                "PAYMENT_REVIEW"}) {
            order.setStatus(status);
            orderIsLocked();

            assertThatThrownBy(() -> service.confirmPayment(order.getId(), admin, REASON))
                    .as(status)
                    .isInstanceOf(ConflictException.class);
            assertThat(order.getStatus()).isEqualTo(status);
        }
        verify(auditRepository, never()).save(any());
    }

    @Test
    void confirm_isRejectedWhenTheStockWasAlreadyReturned() {
        order.setStockReleased(true);
        orderIsLocked();

        assertThatThrownBy(() -> service.confirmPayment(order.getId(), admin, REASON))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("estoque");
        verify(auditRepository, never()).save(any());
    }

    @Test
    void confirm_isRejectedWhileAnAsaasAttemptIsAliveOrPaid_toAvoidChargingTwice() {
        for (String status : new String[] {"CREATING", "PENDING", "RECONCILIATION_REQUIRED", "OVERDUE", "PAID",
                "REFUNDED"}) {
            orderIsLocked();
            when(paymentRepository.findByOrderIdForUpdate(order.getId()))
                    .thenReturn(List.of(attempt("FAILED"), attempt(status)));

            assertThatThrownBy(() -> service.confirmPayment(order.getId(), admin, REASON))
                    .as(status)
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("duplicar a cobrança");
            assertThat(order.getStatus()).isEqualTo("PENDING_PAYMENT");
            assertThat(order.isManualPaymentConfirmed()).isFalse();
        }
        verify(auditRepository, never()).save(any());
    }

    @Test
    void confirm_isRejectedWhenThereIsAnActiveFinancialBlock() {
        orderIsLocked();
        when(financialBlockRepository.existsByOrder_IdAndActiveTrue(order.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.confirmPayment(order.getId(), admin, REASON))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("bloqueio financeiro");
        verify(auditRepository, never()).save(any());
    }

    @Test
    void listAudit_unknownOrder_is404() {
        when(orderRepository.existsById(order.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.listAudit(order.getId())).isInstanceOf(ResourceNotFoundException.class);
    }
}
