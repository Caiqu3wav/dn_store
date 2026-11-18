package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dnstore.backend.dto.AdminDashboardDTO;
import com.dnstore.backend.model.enums.OrderStatus;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.PaymentRepository;
import com.dnstore.backend.repository.ProductRepository;
import com.dnstore.backend.repository.UserRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @InjectMocks
    private AdminDashboardService service;

    @Test
    void revenueStatuses_areExactlyThePaidOnes() {
        assertThat(OrderStatus.REVENUE_STATUSES)
                .containsExactlyInAnyOrder("PAID", "PROCESSING", "SHIPPED", "DELIVERED");
        assertThat(OrderStatus.REVENUE_STATUSES)
                .doesNotContain("PENDING_PAYMENT", "PAYMENT_FAILED", "EXPIRED", "CANCELLED", "PAYMENT_REVIEW");
    }

    @Test
    void dashboard_revenueAndTicketUseOnlyPaidOrdersNetOfRefunds() {
        when(orderRepository.count()).thenReturn(10L);
        when(orderRepository.countByStatusIn(OrderStatus.REVENUE_STATUSES)).thenReturn(3L);
        when(orderRepository.sumTotalByStatusIn(OrderStatus.REVENUE_STATUSES)).thenReturn(new BigDecimal("700.00"));
        when(paymentRepository.sumRefundedByOrderStatusIn(OrderStatus.REVENUE_STATUSES))
                .thenReturn(new BigDecimal("100.00"));
        when(userRepository.count()).thenReturn(4L);
        when(productRepository.count()).thenReturn(22L);

        AdminDashboardDTO dto = service.dashboard();

        assertThat(dto.getTotalOrders()).isEqualTo(10);
        assertThat(dto.getPaidOrders()).isEqualTo(3);
        assertThat(dto.getTotalRevenue()).isEqualByComparingTo("600.00");
        assertThat(dto.getAverageTicket()).isEqualByComparingTo("200.00");
        verify(orderRepository).sumTotalByStatusIn(OrderStatus.REVENUE_STATUSES);
    }

    @Test
    void dashboard_withoutPaidOrdersEverythingFinancialIsZero() {
        when(orderRepository.count()).thenReturn(5L);
        when(orderRepository.countByStatusIn(OrderStatus.REVENUE_STATUSES)).thenReturn(0L);
        when(orderRepository.sumTotalByStatusIn(OrderStatus.REVENUE_STATUSES)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.sumRefundedByOrderStatusIn(OrderStatus.REVENUE_STATUSES)).thenReturn(BigDecimal.ZERO);

        AdminDashboardDTO dto = service.dashboard();

        assertThat(dto.getTotalRevenue()).isEqualByComparingTo("0");
        assertThat(dto.getAverageTicket()).isEqualByComparingTo("0");
    }

    @Test
    void dashboard_neverShowsNegativeRevenue() {
        when(orderRepository.count()).thenReturn(1L);
        when(orderRepository.countByStatusIn(OrderStatus.REVENUE_STATUSES)).thenReturn(1L);
        when(orderRepository.sumTotalByStatusIn(OrderStatus.REVENUE_STATUSES)).thenReturn(new BigDecimal("50.00"));
        when(paymentRepository.sumRefundedByOrderStatusIn(OrderStatus.REVENUE_STATUSES))
                .thenReturn(new BigDecimal("80.00"));

        assertThat(service.dashboard().getTotalRevenue()).isEqualByComparingTo("0");
    }
}
