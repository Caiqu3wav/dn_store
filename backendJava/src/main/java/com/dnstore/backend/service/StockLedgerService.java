package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.StockMovementType;
import com.dnstore.backend.model.enums.StockReferenceType;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.dnstore.backend.repository.StockMovementRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Único ponto que escreve no livro de estoque ({@code stock_movement}).
 *
 * <p>Contrato: todos os métodos exigem transação ativa (MANDATORY) e a linha da variante <b>já travada</b>
 * (FOR UPDATE) pelo chamador, para que {@code balance_before/after} reflitam o saldo real mesmo com vendas
 * concorrentes. Os fluxos existentes (checkout, devolução) já travam; os demais usam {@link #lockVariants}.
 *
 * <p>Saldo inicial lazy: na primeira movimentação de uma variante que já tinha estoque, grava-se um
 * SALDO_INICIAL com o saldo anterior. A inserção usa {@code ON DUPLICATE KEY} sobre
 * {@code UNIQUE(variante, opening_marker)}: não depende de ler o livro (um SELECT em REPEATABLE READ poderia
 * enxergar um snapshot antigo) e nunca lança erro no meio do checkout se outra transação já gravou o saldo.
 */
@Service
public class StockLedgerService {

    private final StockMovementRepository movementRepository;
    private final ProductVariantRepository variantRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public StockLedgerService(StockMovementRepository movementRepository,
            ProductVariantRepository variantRepository) {
        this.movementRepository = movementRepository;
        this.variantRepository = variantRepository;
    }

    /** Dados descritivos de uma movimentação; o delta e os saldos são calculados pelo serviço. */
    public record MovementSpec(
            StockMovementType type,
            StockReferenceType referenceType,
            String referenceId,
            String reasonCode,
            String reasonText,
            User actor,
            String idempotencyKey) {

        public static MovementSpec of(StockMovementType type) {
            return new MovementSpec(type, null, null, null, null, null, null);
        }

        public static MovementSpec of(StockMovementType type, StockReferenceType referenceType, UUID referenceId) {
            return new MovementSpec(type, referenceType, referenceId == null ? null : referenceId.toString(),
                    null, null, null, null);
        }

        public MovementSpec withReason(String code, String text) {
            return new MovementSpec(type, referenceType, referenceId, code, text, actor, idempotencyKey);
        }

        public MovementSpec withActor(User newActor) {
            return new MovementSpec(type, referenceType, referenceId, reasonCode, reasonText, newActor,
                    idempotencyKey);
        }

        public MovementSpec withIdempotencyKey(String key) {
            return new MovementSpec(type, referenceType, referenceId, reasonCode, reasonText, actor, key);
        }
    }

    /**
     * Trava (FOR UPDATE, em ordem de id) e atualiza do banco as variantes informadas. O refresh com lock é
     * necessário porque entidades já carregadas nesta sessão não são re-hidratadas por uma query com lock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<UUID, ProductVariant> lockVariants(Collection<UUID> variantIds) {
        List<UUID> orderedIds = variantIds.stream()
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        Map<UUID, ProductVariant> locked = new LinkedHashMap<>();
        for (ProductVariant variant : variantRepository.findAllByIdInForUpdate(orderedIds)) {
            entityManager.refresh(variant, LockModeType.PESSIMISTIC_WRITE);
            locked.put(variant.getId(), variant);
        }
        if (locked.size() != orderedIds.size()) {
            throw new ResourceNotFoundException("Variação de produto não encontrada.");
        }
        return locked;
    }

    /**
     * Aplica {@code delta} ao estoque da variante (já travada) e registra a movimentação.
     *
     * @throws ConflictException se o saldo ficaria negativo
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement applyDelta(ProductVariant lockedVariant, int delta, MovementSpec spec) {
        int before = lockedVariant.getStock();
        int after;
        try {
            after = Math.addExact(before, delta);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Quantidade fora do limite permitido.");
        }
        if (after < 0) {
            throw new ConflictException("Estoque insuficiente para " + describe(lockedVariant)
                    + ": saldo " + before + ", movimento " + delta + ".");
        }
        lockedVariant.setStock(after);
        variantRepository.save(lockedVariant);
        return recordApplied(lockedVariant, before, spec);
    }

    /**
     * Registra uma mudança de estoque que o chamador já aplicou em {@code variant.stock}. Não altera o
     * estoque. Retorna {@code null} quando não houve mudança.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement recordApplied(ProductVariant variant, int balanceBefore, MovementSpec spec) {
        int delta = variant.getStock() - balanceBefore;
        if (delta == 0) {
            return null;
        }
        if (variant.getId() == null) {
            throw new IllegalStateException("A variante precisa estar persistida para registrar movimentação.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (balanceBefore > 0) {
            ensureOpeningBalance(variant.getId(), balanceBefore, now);
        }
        User actor = spec.actor() != null ? spec.actor() : currentUser();
        return movementRepository.save(StockMovement.builder()
                .productVariant(variant)
                .type(spec.type())
                .quantityDelta(delta)
                .balanceBefore(balanceBefore)
                .referenceType(spec.referenceType())
                .referenceId(spec.referenceId())
                .reasonCode(spec.reasonCode())
                .reasonText(spec.reasonText())
                .actor(actor)
                .idempotencyKey(spec.idempotencyKey())
                .createdAt(now)
                .build());
    }

    private void ensureOpeningBalance(UUID variantId, int balance, LocalDateTime now) {
        entityManager.createNativeQuery("""
                INSERT INTO stock_movement (id, product_variant_id, movement_type, quantity_delta, balance_before,
                                            balance_after, reason_text, opening_marker, created_at)
                VALUES (:id, :variantId, 'SALDO_INICIAL', :balance, 0, :balance,
                        'Saldo de abertura do livro de estoque', 1, :createdAt)
                ON DUPLICATE KEY UPDATE id = id
                """)
                .setParameter("id", UUID.randomUUID().toString())
                .setParameter("variantId", variantId.toString())
                .setParameter("balance", balance)
                .setParameter("createdAt", now)
                .executeUpdate();
    }

    private static User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof User user ? user : null;
    }

    private static String describe(ProductVariant variant) {
        String product = variant.getProduct() == null ? "produto" : variant.getProduct().getName();
        return product + (variant.getSize() == null ? "" : " (" + variant.getSize() + ")");
    }
}
