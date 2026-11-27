package com.dnstore.backend.service;

import com.dnstore.backend.dto.StockDtos.AdoptResult;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.dnstore.backend.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Ação do admin sobre variantes que já tinham saldo antes do livro existir (a leitura fica em StockQueryService). */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockReconciliationService {

    static final int MAX_ADOPT = 100;

    private final ProductVariantRepository variantRepository;
    private final StockMovementRepository movementRepository;
    private final StockLedger ledger;

    /**
     * "Adotar saldo": grava um marcador OPENING (before = after = saldo atual, delta = 0) para variantes sem nenhum
     * movimento. Não inventa entrada; só dá ao livro um ponto de partida para a reconciliação. Variantes que já têm
     * livro são ignoradas.
     */
    @Transactional
    public AdoptResult adoptBalance(List<UUID> variantIds, String performedBy) {
        if (variantIds == null || variantIds.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma variação.");
        }
        if (variantIds.size() > MAX_ADOPT) {
            throw new IllegalArgumentException("Máximo de " + MAX_ADOPT + " variações por operação.");
        }
        List<UUID> ordered = variantIds.stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, ProductVariant> variants = variantRepository.findAllByIdInForUpdate(ordered).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        if (variants.size() != ordered.size()) {
            throw new ResourceNotFoundException("Uma ou mais variações não foram encontradas.");
        }
        int adopted = 0;
        List<UUID> skipped = new ArrayList<>();
        for (UUID id : ordered) {
            // Conferido sob o lock da variante: nenhuma venda concorrente pode criar o primeiro movimento aqui.
            if (movementRepository.existsByVariant_Id(id)) {
                skipped.add(id);
                continue;
            }
            ledger.recordOpening(variants.get(id), performedBy);
            adopted++;
        }
        log.info("stock.adopt_balance adopted={} skipped={} by={}", adopted, skipped.size(), performedBy);
        return new AdoptResult(adopted, skipped);
    }
}
