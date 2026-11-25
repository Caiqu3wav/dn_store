package com.dnstore.backend.controller;

import com.dnstore.backend.dto.AdminDashboardDTO;
import com.dnstore.backend.model.PaymentFinancialHistory;
import com.dnstore.backend.repository.PaymentFinancialHistoryRepository;
import com.dnstore.backend.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminDashboardService dashboardService;
    private final PaymentFinancialHistoryRepository historyRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDTO> dashboard() {
        return ResponseEntity.ok(dashboardService.dashboard());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/orders/{orderId}/history")
    public ResponseEntity<List<PaymentFinancialHistory>> orderHistory(@PathVariable UUID orderId) {
        return ResponseEntity.ok(historyRepository.findByOrderIdOrderByProcessedAtDesc(orderId));
    }
}
