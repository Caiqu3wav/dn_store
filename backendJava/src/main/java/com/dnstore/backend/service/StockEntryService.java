package com.dnstore.backend.service;

import com.dnstore.backend.dto.stock.StockDtos.StockEntryItemRequest;
import com.dnstore.backend.dto.stock.StockDtos.StockEntryRequest;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.DuplicateStockEntryException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockEntry;
import com.dnstore.backend.model.StockEntryItem;
import com.dnstore.backend.model.Supplier;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.model.enums.StockMovementType;
import com.dnstore.backend.model.enums.StockReferenceType;
import com.dnstore.backend.repository.StockEntryRepository;
import com.dnstore.backend.repository.SupplierRepository;
import com.dnstore.backend.service.StockLedgerService.MovementSpec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registro de notas de entrada. A nota inteira (cabeçalho, itens, aumento de estoque e movimentos) é uma única
 * transação: todas as linhas ou nenhuma.
 *
 * <p>Este serviço NÃO é {@code @Transactional}: a transação é aberta aqui por programação para que a violação de
 * unicidade (duas requisições simultâneas com a mesma chave ou a mesma nota) seja tratada <b>depois</b> do
 * rollback, traduzindo-a em replay idempotente (200) ou 409.
 */
@Service
public class StockEntryService {

    public record EntryResult(UUID entryId, boolean replayed) {
    }

    private final StockEntryRepository entryRepository;
    private final SupplierRepository supplierRepository;
    private final StockLedgerService ledger;
    private final TransactionTemplate transaction;

    @PersistenceContext
    private EntityManager entityManager;

    public StockEntryService(StockEntryRepository entryRepository, SupplierRepository supplierRepository,
            StockLedgerService ledger, PlatformTransactionManager transactionManager) {
        this.entryRepository = entryRepository;
        this.supplierRepository = supplierRepository;
        this.ledger = ledger;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public EntryResult create(StockEntryRequest request, User actor) {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            // Defesa em profundidade: a rota já exige ADMIN.
            throw new AccessDeniedException("Apenas administradores podem registrar notas de entrada.");
        }
        String invoice = normalizeInvoice(request.invoiceNumber());
        validateItems(request.items());
        try {
            return transaction.execute(status -> createInTransaction(request, invoice, actor));
        } catch (DataIntegrityViolationException violation) {
            // Corrida: outra requisição gravou a mesma chave/nota entre a checagem e o INSERT.
            return transaction.execute(status -> resolveAfterConflict(request, invoice, violation));
        }
    }

    private EntryResult createInTransaction(StockEntryRequest request, String invoice, User actor) {
        StockEntry replay = entryRepository.findByIdempotencyKey(request.idempotencyKey()).orElse(null);
        if (replay != null) {
            return replayOf(replay, request, invoice);
        }
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado."));
        if (!supplier.isActive()) {
            throw new ConflictException("O fornecedor está inativo.");
        }
        StockEntry existing = entryRepository.findBySupplier_IdAndInvoiceNumber(supplier.getId(), invoice)
                .orElse(null);
        if (existing != null) {
            throw duplicate(existing, invoice);
        }

        List<StockEntryItemRequest> lines = request.items().stream()
                .sorted(Comparator.comparing(item -> item.variantId().toString()))
                .toList();
        Map<UUID, ProductVariant> variants = ledger.lockVariants(
                lines.stream().map(StockEntryItemRequest::variantId).toList());

        LocalDateTime now = LocalDateTime.now();
        StockEntry entry = new StockEntry();
        entry.setSupplier(supplier);
        entry.setInvoiceNumber(invoice);
        entry.setIssueDate(request.issueDate());
        entry.setReceivedAt(now);
        entry.setNotes(blankToNull(request.notes()));
        entry.setIdempotencyKey(request.idempotencyKey());
        entry.setCreatedBy(actor);
        entry.setCreatedAt(now);
        for (StockEntryItemRequest line : lines) {
            StockEntryItem item = new StockEntryItem();
            item.setProductVariant(variants.get(line.variantId()));
            item.setQuantity(line.quantity());
            item.setUnitCost(line.unitCost());
            entry.addItem(item);
        }
        // Grava primeiro: a UNIQUE (fornecedor, nota) falha aqui, antes de qualquer alteração de estoque.
        entryRepository.saveAndFlush(entry);

        MovementSpec spec = MovementSpec.of(StockMovementType.ENTRADA_NOTA, StockReferenceType.STOCK_ENTRY,
                entry.getId()).withActor(actor);
        for (StockEntryItem item : entry.getItems()) {
            ledger.applyDelta(item.getProductVariant(), item.getQuantity(), spec);
        }
        entityManager.flush();
        return new EntryResult(entry.getId(), false);
    }

    private EntryResult resolveAfterConflict(StockEntryRequest request, String invoice,
            DataIntegrityViolationException violation) {
        StockEntry replay = entryRepository.findByIdempotencyKey(request.idempotencyKey()).orElse(null);
        if (replay != null) {
            return replayOf(replay, request, invoice);
        }
        StockEntry existing = entryRepository.findBySupplier_IdAndInvoiceNumber(request.supplierId(), invoice)
                .orElse(null);
        if (existing != null) {
            throw duplicate(existing, invoice);
        }
        throw violation;
    }

    /** Mesma chave de idempotência: devolve a nota original se o conteúdo for o mesmo, senão 409. */
    private EntryResult replayOf(StockEntry stored, StockEntryRequest request, String invoice) {
        Map<UUID, Integer> storedLines = stored.getItems().stream().collect(Collectors.toMap(
                item -> item.getProductVariant().getId(), StockEntryItem::getQuantity, Integer::sum,
                LinkedHashMap::new));
        Map<UUID, Integer> requestedLines = request.items().stream().collect(Collectors.toMap(
                StockEntryItemRequest::variantId, StockEntryItemRequest::quantity, Integer::sum,
                LinkedHashMap::new));
        boolean same = stored.getSupplier().getId().equals(request.supplierId())
                && stored.getInvoiceNumber().equalsIgnoreCase(invoice)
                && storedLines.equals(requestedLines);
        if (!same) {
            throw new ConflictException("Esta chave de idempotência já foi usada por outra nota.");
        }
        return new EntryResult(stored.getId(), true);
    }

    private static DuplicateStockEntryException duplicate(StockEntry existing, String invoice) {
        return new DuplicateStockEntryException(
                "Já existe a nota " + invoice + " para este fornecedor.", existing.getId());
    }

    private static void validateItems(List<StockEntryItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("A nota precisa ter ao menos um item.");
        }
        Set<UUID> seen = new HashSet<>();
        for (StockEntryItemRequest item : items) {
            if (item == null || item.variantId() == null) {
                throw new IllegalArgumentException("Item da nota inválido.");
            }
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("A quantidade de cada item deve ser maior que zero.");
            }
            if (item.unitCost() != null && item.unitCost().signum() < 0) {
                throw new IllegalArgumentException("O custo unitário não pode ser negativo.");
            }
            if (!seen.add(item.variantId())) {
                throw new IllegalArgumentException(
                        "A mesma variação aparece mais de uma vez na nota; some as quantidades em uma linha.");
            }
        }
    }

    static String normalizeInvoice(String invoiceNumber) {
        String normalized = invoiceNumber == null ? "" : invoiceNumber.trim().replaceAll("\\s+", " ").toUpperCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Informe o número da nota.");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
