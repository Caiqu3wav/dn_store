package com.dnstore.backend.service;

import com.dnstore.backend.service.strategy.DeliveryStrategy;
import com.dnstore.backend.service.strategy.DeliveryStrategy.DeliveryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 🚚 DeliveryService (Serviço de Entregas)
 * 
 * Responsável por:
 * 1. Validar o CEP via API Externa (ViaCEP).
 * 2. Selecionar a Estratégia de Frete correta (SEDEX ou PAC).
 * 3. Calcular custos.
 * 
 * Conceito: Service Layer e Integração.
 */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final RestTemplate restTemplate;
    // O Spring injeta TODAS as implementações de DeliveryStrategy em um Map!
    // A chave é o nome do componente (ex: "SEDEX", "PAC").
    private final Map<String, DeliveryStrategy> strategies;

    public DeliveryResult calculateShipping(String zipCode, double weight, String strategyName) {
        // 1. Validar CEP (Simulação de chamada real)
        validateZipCode(zipCode);
        
        // 2. Simular distância baseada no estado (simplificado para demo)
        // Em um app real, usaríamos o UF retornado pelo ViaCEP para consultar uma tabela de distâncias.
        int simulatedDistance = getSimulatedDistance(zipCode);

        // 3. Selecionar Estratégia
        DeliveryStrategy strategy = strategies.get(strategyName.toUpperCase());
        if (strategy == null) {
            throw new IllegalArgumentException("Tipo de frete inválido: " + strategyName);
        }

        // 4. Executar cálculo (Polimorfismo em ação!)
        return strategy.calculate(weight, simulatedDistance);
    }
    
    private void validateZipCode(String zipCode) {
        String url = "https://viacep.com.br/ws/" + zipCode + "/json/";
        try {
            // Se o request falhar, lança exceção
            String result = restTemplate.getForObject(url, String.class);
            if (result != null && result.contains("\"erro\": true")) {
                throw new IllegalArgumentException("CEP não encontrado.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Erro ao validar CEP: " + e.getMessage());
        }
    }
    
    private int getSimulatedDistance(String zipCode) {
        // Simplificado: CEPs de SP (01xxx) são pertos, outros longe
        if (zipCode.startsWith("0")) return 50; 
        return 800;
    }
}
