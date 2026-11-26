package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.OrderItem;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockEntry;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.StockMovement.Type;
import com.dnstore.backend.model.enums.StockAdjustmentCategory;
import com.dnstore.backend.repository.StockMovementRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Único ponto que muda o saldo de uma variante já existente e grava o livro na mesma transação.
 * Não há caminho "sem livro": se a gravação do movimento falhar, a transação inteira falha.
 *
 * <p>Toda operação exige a variante travada ({@code PESSIMISTIC_WRITE}) na sessão atual, para que
 * {@code stock_before} seja lido sob lock. As exceções declaradas estão em {@code StockWriteGuardTest}.
 */
@Component
@RequiredArgsConstructor
public class StockLedger {

    private final StockMovementRepository movements;
    private final EntityManager entityManager;

    /** Quem/por que originou o movimento. Só os campos que se aplicam ao tipo ficam preenchidos. */
    public record Context(Order order, StockEntry entry, String reason, StockAdjustmentCategory category,
                          String performedBy, String idempotencyKey) {

        public static Context forOrder(Order order) {
            return new Context(order, null, null, null, null, null);
        }

        public static Context forEntry(StockEntry entry, String reason, String performedBy) {
            return new Context(null, entry, reason, null, performedBy, null);
        }

        public static Context manual(String reason, StockAdjustmentCategory category, String performedBy,
                                     String idempotencyKey) {
            return new Context(null, null, reason, category, performedBy, idempotencyKey);
        }
    }

    /** Aplica {@code delta} ao saldo da variante (travada) e grava o movimento. Nunca deixa o saldo negativo. */
    public StockMovement post(ProductVariant variant, int delta, Type type, Context context) {
        requireLocked(variant);
        int before = variant.getStock();
        int after = Math.addExact(before, delta);
        if (after < 0) {
            throw new ConflictException("Estoque insuficiente: saldo " + before + ", movimento " + delta
                    + " (" + variant.getProduct().getName() + " " + variant.getSize() + ").");
        }
        variant.setStock(after);
        return save(variant, before, delta, type, context);
    }

    /**
     * Leva o saldo a um valor absoluto (edição de produto). Relê a variante sob lock, para que o delta
     * gravado seja contra o saldo real e não contra uma cópia antiga da sessão. Sem mudança, não grava nada.
     */
    public StockMovement postSetTo(ProductVariant variant, int target, Context context) {
        requireLocked(variant);
        entityManager.refresh(variant, LockModeType.PESSIMISTIC_WRITE);
        int delta = target - variant.getStock();
        if (delta == 0) {
            return null;
        }
        return post(variant, delta, Type.ADJUSTMENT, context);
    }

    /**
     * Registra a venda de um pedido cujo estoque o checkout já baixou (decisão: a baixa continua em
     * {@code OrderService}). {@code stock_before} = saldo atual + quantidade; {@code stock_after} = saldo atual.
     */
    public void recordSale(Order order) {
        Map<UUID, ProductVariant> variants = new LinkedHashMap<>();
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        if (order.getItems() == null) {
            return;
        }
        for (OrderItem item : order.getItems()) {
            ProductVariant variant = item.getProductVariant();
            if (variant == null) {
                continue;
            }
            variants.putIfAbsent(variant.getId(), variant);
            quantities.merge(variant.getId(), item.getQuantity(), Math::addExact);
        }
        quantities.forEach((variantId, quantity) -> {
            ProductVariant variant = variants.get(variantId);
            requireLocked(variant);
            int after = variant.getStock();
            save(variant, Math.addExact(after, quantity), -quantity, Type.SALE, Context.forOrder(order));
        });
    }

    /** Variante recém-criada com saldo inicial N: stock_before = 0, delta = N. Sem saldo, não há o que registrar. */
    public StockMovement recordInitial(ProductVariant variant, String performedBy) {
        if (variant.getId() == null) {
            throw new IllegalStateException("A variante precisa estar persistida antes de entrar no livro.");
        }
        int stock = variant.getStock();
        if (stock <= 0) {
            return null;
        }
        return save(variant, 0, stock, Type.INITIAL,
                new Context(null, null, "Saldo inicial da variação", null, performedBy, null));
    }

    /** Marcador de abertura de variante que já tinha saldo e não tinha livro: before = after = N, delta = 0. */
    public StockMovement recordOpening(ProductVariant variant, String performedBy) {
        requireLocked(variant);
        int stock = variant.getStock();
        return save(variant, stock, 0, Type.OPENING,
                new Context(null, null, "Saldo adotado no livro", null, performedBy, null));
    }

    private StockMovement save(ProductVariant variant, int before, int delta, Type type, Context c) {
        return movements.save(StockMovement.create(variant, before, delta, type, c.order(), c.entry(),
                c.reason(), c.category(), c.performedBy(), c.idempotencyKey()));
    }

    private void requireLocked(ProductVariant variant) {
        LockModeType mode;
        try {
            mode = entityManager.getLockMode(variant);
        } catch (IllegalArgumentException notManaged) {
            throw new IllegalStateException("A variante deve estar carregada com lock antes de mexer no estoque.",
                    notManaged);
        }
        if (mode != LockModeType.PESSIMISTIC_WRITE && mode != LockModeType.PESSIMISTIC_FORCE_INCREMENT) {
            throw new IllegalStateException("A variante " + variant.getId()
                    + " precisa de PESSIMISTIC_WRITE antes de mexer no estoque (atual: " + mode + ").");
        }
    }
}
