package com.dnstore.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.GlobalExceptionHandler;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.User;
import com.dnstore.backend.service.OrderManualPaymentService;
import com.dnstore.backend.service.OrderManualPaymentService.AuditEntry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Contrato HTTP da confirmação manual. A autorização por perfil é coberta em AdminRoutesSecurityTest. */
class AdminOrderPaymentControllerTest {

    private final UUID orderId = UUID.randomUUID();
    private OrderManualPaymentService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(OrderManualPaymentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminOrderPaymentController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void confirmPayment_returnsTheUpdatedOrder() throws Exception {
        Order order = new Order();
        order.setId(orderId);
        order.setStatus("PAID");
        order.setManualPaymentConfirmed(true);
        when(service.confirmPayment(eq(orderId), any(), eq("Transferência conferida no extrato")))
                .thenReturn(order);

        mockMvc.perform(post("/api/orders/{id}/confirm-payment", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Transferência conferida no extrato\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.manualPaymentConfirmed").value(true));
    }

    @Test
    void confirmPayment_withoutBodyIsReportedAsMissingJustification() throws Exception {
        when(service.confirmPayment(eq(orderId), any(), eq(null)))
                .thenThrow(new IllegalArgumentException("Informe uma justificativa com pelo menos 10 caracteres."));

        mockMvc.perform(post("/api/orders/{id}/confirm-payment", orderId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmPayment_conflictWithAnAsaasAttemptIs409() throws Exception {
        when(service.confirmPayment(eq(orderId), any(), any()))
                .thenThrow(new ConflictException("Há uma tentativa de pagamento do Asaas em andamento."));

        mockMvc.perform(post("/api/orders/{id}/confirm-payment", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Transferência conferida no extrato\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void statusAudit_listsWhoWhenAndWhatChanged() throws Exception {
        UUID adminId = UUID.randomUUID();
        when(service.listAudit(orderId)).thenReturn(List.of(new AuditEntry(
                UUID.randomUUID(), "MANUAL_PAYMENT_CONFIRMATION", "PENDING_PAYMENT", "PAID",
                "Transferência conferida no extrato", LocalDateTime.of(2026, 11, 17, 21, 0), adminId, "Admin")));

        mockMvc.perform(get("/api/orders/{id}/status-audit", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].changeType").value("MANUAL_PAYMENT_CONFIRMATION"))
                .andExpect(jsonPath("$[0].previousStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$[0].newStatus").value("PAID"))
                .andExpect(jsonPath("$[0].actorId").value(adminId.toString()))
                .andExpect(jsonPath("$[0].actorName").value("Admin"));
    }
}
