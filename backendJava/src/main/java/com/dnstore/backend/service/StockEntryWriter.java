package com.dnstore.backend.service;

import com.dnstore.backend.dto.StockDtos.EntryDetail;
import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.exception.StockConflictException;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.StockEntry;
import com.dnstore.backend.model.StockEntryItem;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.Supplier;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.dnstore.backend.repository.StockEntryItemRepository;
import com.dnstore.backend.repository.StockEntryRepository;
import com.dnstore.backend.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Transação única da nota: tudo ou nada. Variantes travadas em ordem de id, como o checkout. */
@Service
@RequiredArgsConstructor
public class StockEntryWriter {

    private final SupplierRepository supplierRepository;
    private final ProductVariantRepository variantRepository;
    private final StockEntryRepository entryRepository;
    private final StockEntryItemRepository itemRepository;
    private final StockLedger ledger;
    private final StockQueryService queryService;

    @Transactional
    public EntryDetail write(StockEntryService.CreateCommand cmd, String hash) {
        Supplier supplier = supplierRepository.findById(cmd.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado."));
        if (!supplier.isActive()) {
            throw new ConflictException("O fornecedor está inativo.");
        }
        Map<UUID, ProductVariant> variants = lockVariants(cmd.lines());

        // Conferido depois do lock das variantes: reenvios simultâneos da mesma nota se serializam aqui.
        var byKey = entryRepository.findByIdempotencyKey(cmd.idempotencyKey());
        if (byKey.isPresent()) {
            return replay(byKey.get(), hash);
        }
        var byInvoice = entryRepository.findByInvoice(supplier.getId(), cmd.invoiceSeries(), cmd.invoiceNumber());
        if (byInvoice.isPresent()) {
            throw StockEntryService.duplicateInvoice(byInvoice.get());
        }

        // saveAndFlush: a unicidade da nota estoura aqui, antes de mexer em saldo.
        StockEntry entry = entryRepository.saveAndFlush(StockEntry.create(supplier, cmd.invoiceNumber(),
                cmd.invoiceSeries(), cmd.invoiceDate(), cmd.idempotencyKey(), hash, cmd.notes(), cmd.performedBy()));
        String reason = "NF " + (cmd.invoiceSeries().isEmpty() ? "" : cmd.invoiceSeries() + "-") + cmd.invoiceNumber();
        for (StockEntryService.Line line : cmd.lines()) {
            ProductVariant variant = variants.get(line.variantId());
            itemRepository.save(StockEntryItem.create(entry, variant, line.quantity(), line.unitCost()));
            ledger.post(variant, line.quantity(), StockMovement.Type.ENTRY,
                    StockLedger.Context.forEntry(entry, reason, cmd.performedBy()));
            variantRepository.save(variant);
        }
        return queryService.entryDetail(entry.getId(), false);
    }

    /** Chamado fora da transação que falhou por unicidade: decide entre reenvio idêntico e nota duplicada. */
    @Transactional(readOnly = true)
    public EntryDetail resolveAfterConflict(StockEntryService.CreateCommand cmd, String hash,
                                            DataIntegrityViolationException race) {
        var byKey = entryRepository.findByIdempotencyKey(cmd.idempotencyKey());
        if (byKey.isPresent()) {
            return replay(byKey.get(), hash);
        }
        var byInvoice = entryRepository.findByInvoice(cmd.supplierId(), cmd.invoiceSeries(), cmd.invoiceNumber());
        if (byInvoice.isPresent()) {
            throw StockEntryService.duplicateInvoice(byInvoice.get());
        }
        throw race;
    }

    private EntryDetail replay(StockEntry existing, String hash) {
        if (!existing.getRequestHash().equals(hash)) {
            throw new StockConflictException("A chave de idempotência já foi usada por uma nota diferente.",
                    Map.of("existingEntryId", existing.getId().toString()));
        }
        return queryService.entryDetail(existing.getId(), true);
    }

    private Map<UUID, ProductVariant> lockVariants(List<StockEntryService.Line> lines) {
        List<UUID> ordered = lines.stream().map(StockEntryService.Line::variantId)
                .sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, ProductVariant> variants = variantRepository.findAllByIdInForUpdate(ordered).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        if (variants.size() != ordered.size()) {
            throw new ResourceNotFoundException("Uma ou mais variações da nota não foram encontradas.");
        }
        return variants;
    }
}
