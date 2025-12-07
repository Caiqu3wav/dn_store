import { FreteStrategy } from './FreteStrategy.js';

export class PacStrategy extends FreteStrategy {
    calcular(distancia, peso) {
        // Lógica de Negócio: PAC é lento e barato
        // Preço base R$ 10,00 + R$ 0,50 por kg + taxa de distância menor
        const preco = 10.00 + (peso * 0.50) + (distancia * 0.02);

        // Prazo lento: base 5 dias + 1 dia a cada 300km
        const prazo = 5 + Math.floor(distancia / 300);

        return {
            valor: parseFloat(preco.toFixed(2)),
            prazo: prazo,
            tipo: 'PAC'
        };
    }
}
