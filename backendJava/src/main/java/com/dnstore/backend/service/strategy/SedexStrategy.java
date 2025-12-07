package com.dnstore.backend.service.strategy;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 🚀 SedexStrategy
 * 
 * Implementação concreta da estratégia de entrega.
 * Focada em RAPIDEZ, porém custo mais elevado.
 * 
 * Conceitos de POO:
 * - Implementação de Interface (implements DeliveryStrategy).
 * - Componente Spring (@Component) para Injeção de Dependência.
 */
@Component("SEDEX")
public class SedexStrategy implements DeliveryStrategy {

    @Override
    public DeliveryResult calculate(double weight, int distanceKm) {
        // Regra de Negócio: Preço Base R$ 20 + Taxa por Peso + Taxa por km
        BigDecimal baseCost = new BigDecimal("20.00");
        BigDecimal weightCost = new BigDecimal(weight).multiply(new BigDecimal("2.50"));
        BigDecimal distanceCost = new BigDecimal(distanceKm).multiply(new BigDecimal("0.08"));
        
        BigDecimal totalCost = baseCost.add(weightCost).add(distanceCost).setScale(2, RoundingMode.HALF_UP);
        
        // Regra de Prazo: Rápido (1 dia + 1 dia a cada 800km)
        int deadLine = 1 + (distanceKm / 800);
        
        return new DeliveryResult(totalCost, deadLine, "SEDEX");
    }
}
