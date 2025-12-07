export class FreteStrategy {
    constructor() {
        if (this.constructor === FreteStrategy) {
            throw new Error("Classe abstrata 'FreteStrategy' não pode ser instanciada diretamente.");
        }
    }

    /**
     * Calcula o preço e prazo do frete.
     * @param {number} distancia (simulado via CEP)
     * @param {number} peso 
     * @returns {Object} { valor: number, prazo: number }
     */
    calcular(distancia, peso) {
        throw new Error("O método 'calcular' deve ser implementado.");
    }
}
