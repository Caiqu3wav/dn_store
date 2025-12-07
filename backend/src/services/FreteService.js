import { SedexStrategy } from '../strategies/SedexStrategy.js';
import { PacStrategy } from '../strategies/PacStrategy.js';

export class FreteService {
    constructor() {
        // Mapa de distâncias simuladas baseada no estado (UF)
        // SP é origem (0km), outros estados tem distâncias fixas
        this.distanciasPorEstado = {
            'SP': 100, // Dentro de SP
            'RJ': 400,
            'MG': 500,
            'ES': 800,
            'PR': 400,
            'SC': 700,
            'RS': 1100,
            'DF': 1000,
            // Outros...
            'default': 2000 // Norte/Nordeste longe
        };
    }

    /**
     * Valida CEP, Identifica distância e Calcula Frete via Estratégia
     */
    async calcularFrete(cep, peso, tipoStrategy = 'pac') {
        // 1. Validação simples de formato
        if (!cep || cep.replace(/\D/g, '').length !== 8) {
            throw new Error("CEP inválido. Formato esperado: 00000-000");
        }

        // 2. Consulta à API Externa (BrasilAPI ou ViaCEP)
        const dadosCep = await this.consultarCep(cep);

        // 3. Determinar Distância
        const distancia = this.distanciasPorEstado[dadosCep.uf] || this.distanciasPorEstado['default'];

        // 4. Selecionar Estratégia (Polimorfismo)
        let strategy;
        if (tipoStrategy.toLowerCase() === 'sedex') {
            strategy = new SedexStrategy();
        } else {
            strategy = new PacStrategy();
        }

        // 5. Calcular
        const resultado = strategy.calcular(distancia, peso);

        return {
            ...resultado,
            origem: 'São Paulo/SP',
            destino: `${dadosCep.localidade}/${dadosCep.uf}`,
            cep: cep
        };
    }

    async consultarCep(cep) {
        try {
            // Usando fetch nativo do Node.js
            const response = await fetch(`https://viacep.com.br/ws/${cep}/json/`);
            if (!response.ok) {
                throw new Error("Erro ao consultar serviço de CEP");
            }

            const data = await response.json();

            if (data.erro) {
                throw new Error("CEP não encontrado na base de dados.");
            }

            return data;
        } catch (error) {
            throw new Error(`Falha na verificação do CEP: ${error.message}`);
        }
    }
}
