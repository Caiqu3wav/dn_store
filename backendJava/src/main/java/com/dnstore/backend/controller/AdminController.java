package com.dnstore.backend.controller;

import com.dnstore.backend.dto.AdminDashboardDTO;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.PaymentFinancialHistory;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.PaymentFinancialHistoryRepository;
import com.dnstore.backend.repository.ProductRepository;
import com.dnstore.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PaymentFinancialHistoryRepository historyRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDTO> dashboard() {
        long totalOrders = orderRepository.count();
        BigDecimal totalRevenue = orderRepository.sumTotalRevenue();

        BigDecimal averageTicket = totalOrders == 0
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);

        return ResponseEntity.ok(new AdminDashboardDTO(
                totalOrders,
                userRepository.count(),
                productRepository.count(),
                totalRevenue,
                averageTicket));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/orders/{orderId}/history")
    public ResponseEntity<List<PaymentFinancialHistory>> orderHistory(@PathVariable UUID orderId) {
        return ResponseEntity.ok(historyRepository.findByOrderIdOrderByProcessedAtDesc(orderId));
    }
}