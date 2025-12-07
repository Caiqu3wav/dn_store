package com.dnstore.backend.service.strategy;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 🐢 PacStrategy
 * 
 * Implementação concreta focada em ECONOMIA.
 * Custo menor, prazo maior.
 * Demonstrando que diferentes classes podem resolver o mesmo problema (calcular frete) de formas distintas (Polimorfismo).
 */
@Component("PAC")
public class PacStrategy implements DeliveryStrategy {

    @Override
    public DeliveryResult calculate(double weight, int distanceKm) {
        // Regra de Negócio: Preço Base R$ 10 + Menor taxa por peso
        BigDecimal baseCost = new BigDecimal("10.00");
        BigDecimal weightCost = new BigDecimal(weight).multiply(new BigDecimal("1.00"));
        BigDecimal distanceCost = new BigDecimal(distanceKm).multiply(new BigDecimal("0.03"));
        
        BigDecimal totalCost = baseCost.add(weightCost).add(distanceCost).setScale(2, RoundingMode.HALF_UP);
        
        // Regra de Prazo: Mais lento (4 dias + 1 dia a cada 500km)
        int deadLine = 4 + (distanceKm / 500);
        
        return new DeliveryResult(totalCost, deadLine, "PAC");
    }
}
