package com.dnstore.backend.service;

import com.dnstore.backend.exception.DeliveryException;
import com.dnstore.backend.service.impl.ViaCepResponse;
import com.dnstore.backend.service.strategy.DeliveryStrategy;
import com.dnstore.backend.service.strategy.DeliveryStrategy.DeliveryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 🚚 DeliveryService (Serviço de Entregas)
 * 
 * Responsável por orquestrar o cálculo de frete:
 * 1. Valida e enriquece o CEP via Serviço dedicado (ZipCodeService).
 * 2. Determina a distância baseada na região (UF).
 * 3. Delega o cálculo final para a estratégia selecionada.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final ZipCodeService zipCodeService;
    private final Map<String, DeliveryStrategy> strategies;

    @Value("${shipping.origin-zip:12600-000}")
    private String originZip;
    
    private static final Map<String, Integer> STATE_DISTANCES = new HashMap<>();

    static {
        // Tabela de Zonas (Em produção, isso viria de um Banco de Dados)
        // Distância do Centro de Distribuição (ex: SP) em km
        STATE_DISTANCES.put("SP", 50);
        STATE_DISTANCES.put("RJ", 400);
        STATE_DISTANCES.put("MG", 600);
        STATE_DISTANCES.put("ES", 800);
        STATE_DISTANCES.put("PR", 700);
        STATE_DISTANCES.put("SC", 850);
        STATE_DISTANCES.put("RS", 1000);
        STATE_DISTANCES.put("DF", 1000);
    }

    public DeliveryResult calculateShipping(String zipCode, double weight, String strategyName) {
        return calculateOptions(zipCode, weight).stream()
                .filter(option -> option.typeName().equalsIgnoreCase(strategyName))
                .findFirst()
                .orElseThrow(() -> new DeliveryException("Serviço de entrega inválido."));
    }

    public List<DeliveryResult> calculateOptions(String zipCode, double weight) {
        String originState = zipCodeService.getAddress(originZip).getUf();
        String destinationState = zipCodeService.getAddress(zipCode).getUf();
        if (!"SP".equalsIgnoreCase(originState)) {
            throw new DeliveryException("O frete estimado temporariamente requer origem no estado de SP.");
        }
        int distance = getDistanceFromState(destinationState);
        log.info("Estimando frete de {} para {}, UF {}, peso {} kg", originZip, zipCode, destinationState, weight);

        return strategies.values().stream()
                .map(strategy -> strategy.calculate(weight, distance))
                .sorted(Comparator.comparing(DeliveryResult::cost))
                .toList();
    }

    private int getDistanceFromState(String uf) {
        // Se UF desconhecida, assume longa distância (Frete Nacional)
        if (uf == null) return 2000; 
        return STATE_DISTANCES.getOrDefault(uf.toUpperCase(java.util.Locale.ROOT), 2000); 
    }
}
