package com.dnstore.backend.service;

import com.dnstore.backend.dto.AdminDashboardDTO;
import com.dnstore.backend.model.enums.OrderStatus;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.PaymentRepository;
import com.dnstore.backend.repository.ProductRepository;
import com.dnstore.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Indicadores do painel. Receita e ticket médio consideram apenas pedidos com pagamento confirmado
 * ({@link OrderStatus#REVENUE_STATUSES}), líquidos de reembolsos. Pendentes, falhos, expirados,
 * cancelados e em revisão de pagamento ficam de fora.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public AdminDashboardDTO dashboard() {
        long paidOrders = orderRepository.countByStatusIn(OrderStatus.REVENUE_STATUSES);
        BigDecimal gross = orderRepository.sumTotalByStatusIn(OrderStatus.REVENUE_STATUSES);
        BigDecimal refunded = paymentRepository.sumRefundedByOrderStatusIn(OrderStatus.REVENUE_STATUSES);

        BigDecimal revenue = gross.subtract(refunded).max(BigDecimal.ZERO);
        BigDecimal averageTicket = paidOrders == 0
                ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(paidOrders), 2, RoundingMode.HALF_UP);

        return new AdminDashboardDTO(
                orderRepository.count(),
                userRepository.count(),
                productRepository.count(),
                revenue,
                averageTicket,
                paidOrders);
    }
}
