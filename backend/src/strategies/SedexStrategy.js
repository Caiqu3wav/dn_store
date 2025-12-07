import { FreteStrategy } from './FreteStrategy.js';

export class SedexStrategy extends FreteStrategy {
    calcular(distancia, peso) {
        // Lógica de Negócio: Sedex é rápido e caro
        // Preço base R$ 15,00 + R$ 1,50 por kg + taxa de distância
        const preco = 15.00 + (peso * 1.50) + (distancia * 0.05);

        // Prazo rápido: base 1 dia + 1 dia a cada 500km
        const prazo = 1 + Math.floor(distancia / 500);

        return {
            valor: parseFloat(preco.toFixed(2)),
            prazo: prazo,
            tipo: 'SEDEX'
        };
    }
}
