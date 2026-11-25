package com.dnstore.backend.service;

import com.dnstore.backend.dto.stock.StockDtos.StockAdjustmentRequest;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.model.enums.StockAdjustmentReason;
import com.dnstore.backend.model.enums.StockMovementType;
import com.dnstore.backend.repository.StockMovementRepository;
import com.dnstore.backend.service.StockLedgerService.MovementSpec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ajuste manual de estoque (inventário, perda, avaria, devolução de cliente). Exige motivo, trava a variante e
 * confere {@code expectedBalance} (o saldo que o admin viu) para não ajustar em cima de dado velho.
 */
@Service
public class StockAdjustmentService {

    public record AdjustmentResult(UUID movementId, boolean replayed) {
    }

    private final StockMovementRepository movementRepository;
    private final StockLedgerService ledger;
    private final TransactionTemplate transaction;

    @PersistenceContext
    private EntityManager entityManager;

    public StockAdjustmentService(StockMovementRepository movementRepository, StockLedgerService ledger,
            PlatformTransactionManager transactionManager) {
        this.movementRepository = movementRepository;
        this.ledger = ledger;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public AdjustmentResult adjust(StockAdjustmentRequest request, User actor) {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Apenas administradores podem ajustar o estoque.");
        }
        validate(request);
        try {
            return transaction.execute(status -> adjustInTransaction(request, actor));
        } catch (DataIntegrityViolationException violation) {
            return transaction.execute(status -> {
                StockMovement replay = movementRepository.findByIdempotencyKey(request.idempotencyKey())
                        .orElseThrow(() -> violation);
                return replayOf(replay, request);
            });
        }
    }

    private AdjustmentResult adjustInTransaction(StockAdjustmentRequest request, User actor) {
        StockMovement replay = movementRepository.findByIdempotencyKey(request.idempotencyKey()).orElse(null);
        if (replay != null) {
            return replayOf(replay, request);
        }
        ProductVariant variant = ledger.lockVariants(List.of(request.variantId())).get(request.variantId());
        if (variant.getStock() != request.expectedBalance()) {
            throw new ConflictException("O estoque mudou desde que você abriu a tela (atual: "
                    + variant.getStock() + ", esperado: " + request.expectedBalance() + "). Recarregue e confira.");
        }
        StockMovement movement = ledger.applyDelta(variant, request.delta(),
                MovementSpec.of(StockMovementType.AJUSTE)
                        .withReason(request.reasonCode().name(), request.reason().trim())
                        .withActor(actor)
                        .withIdempotencyKey(request.idempotencyKey()));
        entityManager.flush();
        return new AdjustmentResult(movement.getId(), false);
    }

    private AdjustmentResult replayOf(StockMovement stored, StockAdjustmentRequest request) {
        boolean same = stored.getType() == StockMovementType.AJUSTE
                && stored.getProductVariant().getId().equals(request.variantId())
                && stored.getQuantityDelta() == request.delta();
        if (!same) {
            throw new ConflictException("Esta chave de idempotência já foi usada por outro ajuste.");
        }
        return new AdjustmentResult(stored.getId(), true);
    }

    private static void validate(StockAdjustmentRequest request) {
        int delta = request.delta();
        if (delta == 0) {
            throw new IllegalArgumentException("O ajuste não pode ser zero.");
        }
        StockAdjustmentReason reason = request.reasonCode();
        if ((reason == StockAdjustmentReason.PERDA || reason == StockAdjustmentReason.AVARIA) && delta > 0) {
            throw new IllegalArgumentException("Perda e avaria devem reduzir o estoque.");
        }
        if (reason == StockAdjustmentReason.DEVOLUCAO_CLIENTE && delta < 0) {
            throw new IllegalArgumentException("Devolução de cliente deve aumentar o estoque.");
        }
        if (request.reason() == null || request.reason().trim().length() < 10) {
            throw new IllegalArgumentException("Informe o motivo do ajuste (mínimo de 10 caracteres).");
        }
    }
}
