package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.model.Payment;
import com.dnstore.backend.model.PaymentRefund;
import com.dnstore.backend.model.enums.PaymentStatus;
import com.dnstore.backend.model.enums.PaymentTransitionPolicy;
import com.dnstore.backend.repository.PaymentRefundRepository;
import com.dnstore.backend.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentRefundService {
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository refundRepository;

    /** Registers intent only; provider confirmation must arrive through canonical reconciliation. */
    @Transactional
    public PaymentRefund requestRefund(UUID paymentId, BigDecimal amount, String idempotencyKey) {
        PaymentRefund existing = refundRepository.findByPayment_IdAndIdempotencyKey(paymentId, idempotencyKey).orElse(null);
        if (existing != null) return existing;
        if (amount == null || amount.signum() <= 0 || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Valor ou chave de reembolso inválidos.");
        }
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ConflictException("Pagamento não encontrado."));
        PaymentStatus current = PaymentStatus.valueOf(payment.getStatus());
        BigDecimal received = payment.getReceivedAmount() == null ? BigDecimal.ZERO : payment.getReceivedAmount();
        BigDecimal refunded = payment.getRefundedAmount() == null ? BigDecimal.ZERO : payment.getRefundedAmount();
        if (amount.compareTo(received.subtract(refunded)) > 0
                || !PaymentTransitionPolicy.isAllowed(current, PaymentStatus.REFUND_REQUESTED)) {
            throw new ConflictException("Reembolso excede o saldo recebido ou o estado financeiro não permite a solicitação.");
        }
        PaymentRefund refund = new PaymentRefund();
        refund.setPayment(payment); refund.setIdempotencyKey(idempotencyKey);
        refund.setRequestedAmount(amount); refund.setStatus("REQUESTED");
        payment.setStatus(PaymentStatus.REFUND_REQUESTED.name());
        paymentRepository.save(payment);
        return refundRepository.save(refund);
    }
}
