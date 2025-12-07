package com.dnstore.backend.service.strategy;

import java.math.BigDecimal;

/**
 * 🚚 Interface DeliveryStrategy
 * 
 * Define o contrato para cálculo de frete. 
 * Qualquer nova forma de entrega (Sedex, Jadlog, UberFlash) só precisa implementar esta interface.
 * 
 * Conceitos de POO:
 * - Interface: Define O QUE deve ser feito, não COMO.
 * - Pattern Strategy: Permite trocar o algoritmo de cálculo em tempo de execução.
 */
public interface DeliveryStrategy {
    
    /**
     * Calcula o custo e prazo de entrega.
     * @param weight Peso total em kg
     * @param distanceKm Distância simulada em km (ou baseada no CEP)
     * @return DeliveryResult contendo valor e dias
     */
    DeliveryResult calculate(double weight, int distanceKm);
    
    // Objeto de valor (Record) para retornar os dados imutáveis
    record DeliveryResult(BigDecimal cost, int deadLineDays, String typeName) {}
}
