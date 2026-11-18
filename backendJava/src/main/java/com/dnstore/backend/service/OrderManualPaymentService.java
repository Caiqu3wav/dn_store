package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.OrderStatusAudit;
import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.OrderStatus;
import com.dnstore.backend.model.enums.PaymentStatus;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.repository.FinancialBlockRepository;
import com.dnstore.backend.repository.OrderRepository;
import com.dnstore.backend.repository.OrderStatusAuditRepository;
import com.dnstore.backend.repository.PaymentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmação manual de pagamento (ex.: transferência fora do Asaas). É uma operação excepcional de
 * administrador, sempre com justificativa e trilha de auditoria, e nunca se confunde com a confirmação
 * automática: o pedido recebe {@code manualPaymentConfirmed = true} e não há pagamento do Asaas associado.
 *
 * Para não duplicar cobrança, só é aceita quando todas as tentativas do Asaas estão encerradas como FAILED
 * (ou não existem). Se mesmo assim um pagamento do Asaas chegar depois, o fluxo de webhook abre um bloqueio
 * financeiro para reconciliação (ver PaymentAttemptStateService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderManualPaymentService {

    static final int MIN_REASON_LENGTH = 10;
    static final int MAX_REASON_LENGTH = 500;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final FinancialBlockRepository financialBlockRepository;
    private final OrderStatusAuditRepository auditRepository;

    public record AuditEntry(
            UUID id,
            String changeType,
            String previousStatus,
            String newStatus,
            String reason,
            LocalDateTime createdAt,
            UUID actorId,
            String actorName) {
    }

    @Transactional
    public Order confirmPayment(UUID orderId, User actor, String reason) {
        // Defesa em profundidade: a rota já exige ADMIN, mas o serviço não confia só nisso.
        if (actor == null || actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Apenas administradores podem confirmar pagamentos manualmente.");
        }
        String justification = normalizeReason(reason);

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));

        String previousStatus = order.getStatus();
        if (!OrderStatus.PENDING_PAYMENT.name().equals(previousStatus)
                && !OrderStatus.PAYMENT_FAILED.name().equals(previousStatus)) {
            throw new ConflictException("Só é possível confirmar manualmente pedidos aguardando pagamento "
                    + "ou com pagamento falho. Status atual: " + previousStatus + ".");
        }
        if (order.isStockReleased()) {
            throw new ConflictException("O estoque deste pedido já foi devolvido; não é possível confirmá-lo.");
        }
        if (financialBlockRepository.existsByOrder_IdAndActiveTrue(orderId)) {
            throw new ConflictException("O pedido possui um bloqueio financeiro ativo e requer reconciliação.");
        }

        List<Payment> attempts = paymentRepository.findByOrderIdForUpdate(orderId);
        boolean hasLiveAttempt = attempts.stream()
                .anyMatch(payment -> PaymentStatus.valueOf(payment.getStatus()) != PaymentStatus.FAILED);
        if (hasLiveAttempt) {
            throw new ConflictException("Há uma tentativa de pagamento do Asaas em andamento ou já concluída. "
                    + "Confirmar manualmente poderia duplicar a cobrança; aguarde o desfecho ou reconcilie no Asaas.");
        }

        order.setStatus(OrderStatus.PAID.name());
        order.setManualPaymentConfirmed(true);
        Order saved = orderRepository.save(order);

        OrderStatusAudit audit = new OrderStatusAudit();
        audit.setOrder(saved);
        audit.setActor(actor);
        audit.setChangeType(OrderStatusAudit.MANUAL_PAYMENT_CONFIRMATION);
        audit.setPreviousStatus(previousStatus);
        audit.setNewStatus(OrderStatus.PAID.name());
        audit.setReason(justification);
        audit.setCreatedAt(LocalDateTime.now());
        auditRepository.save(audit);

        log.warn("order.manual_payment_confirmed orderId={} adminId={} from={} to={}",
                orderId, actor.getId(), previousStatus, OrderStatus.PAID.name());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> listAudit(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Pedido não encontrado.");
        }
        return auditRepository.findByOrder_IdOrderByCreatedAtDesc(orderId).stream()
                .map(audit -> new AuditEntry(
                        audit.getId(),
                        audit.getChangeType(),
                        audit.getPreviousStatus(),
                        audit.getNewStatus(),
                        audit.getReason(),
                        audit.getCreatedAt(),
                        audit.getActor().getId(),
                        audit.getActor().getName()))
                .toList();
    }

    private String normalizeReason(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < MIN_REASON_LENGTH) {
            throw new IllegalArgumentException("Informe uma justificativa com pelo menos "
                    + MIN_REASON_LENGTH + " caracteres.");
        }
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("A justificativa pode ter no máximo "
                    + MAX_REASON_LENGTH + " caracteres.");
        }
        return trimmed;
    }
}
