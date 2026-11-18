package com.dnstore.backend.controller;

import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.User;
import com.dnstore.backend.service.OrderManualPaymentService;
import com.dnstore.backend.service.OrderManualPaymentService.AuditEntry;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Confirmação manual de pagamento e sua trilha de auditoria. Somente administradores. */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class AdminOrderPaymentController {

    private final OrderManualPaymentService manualPaymentService;

    public record ConfirmPaymentRequest(String reason) {
    }

    @PostMapping("/{id}/confirm-payment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Order> confirmPayment(
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmPaymentRequest request,
            @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(manualPaymentService.confirmPayment(
                id, admin, request == null ? null : request.reason()));
    }

    @GetMapping("/{id}/status-audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuditEntry>> statusAudit(@PathVariable UUID id) {
        return ResponseEntity.ok(manualPaymentService.listAudit(id));
    }
}
