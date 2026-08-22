package com.dnstore.backend.service;

import com.dnstore.backend.exception.DeliveryException;
import com.dnstore.backend.service.impl.ViaCepResponse;
import com.dnstore.backend.service.strategy.DeliveryStrategy;
import com.dnstore.backend.service.strategy.DeliveryStrategy.DeliveryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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
        log.info("Calculando frete simulado para CEP: {}, Estratégia: {}", zipCode, strategyName);
        return new DeliveryResult(new java.math.BigDecimal("10.00"), 3, strategyName != null ? strategyName : "PADRAO");
    }

    private int getDistanceFromState(String uf) {
        // Se UF desconhecida, assume longa distância (Frete Nacional)
        if (uf == null) return 2000; 
        return STATE_DISTANCES.getOrDefault(uf.toUpperCase(java.util.Locale.ROOT), 2000); 
    }
}
