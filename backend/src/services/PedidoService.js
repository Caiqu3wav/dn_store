import { Pedido } from '../models/Pedido.js';
import { FreteService } from './FreteService.js';

export class PedidoService {
    constructor() {
        this.pedidos = [];
        this.contadorIds = 1;
        this.freteService = new FreteService(); // Injeção de dependência simples
    }

    async criarPedido(carrinho, cep, tipoFrete) {
        if (carrinho.itens.length === 0) {
            throw new Error("Carrinho vazio não pode gerar pedido.");
        }

        const totalProdutos = carrinho.total();
        const pesoTotal = carrinho.totalPeso();

        // Chamada assíncrona ao serviço de frete
        const freteCalc = await this.freteService.calcularFrete(cep, pesoTotal, tipoFrete);

        const totalGeral = totalProdutos + freteCalc.valor;

        // Snapshot dos itens
        const itensPedido = carrinho.itens.map(item => ({
            produto: item.produto, // Mantém referência mas idealmente seria cópia dados
            nome: item.produto.nome,
            preco: item.produto.preco,
            quantidade: item.quantidade,
            subtotal: item.produto.preco * item.quantidade
        }));

        const novoPedido = new Pedido(
            this.contadorIds++,
            itensPedido,
            totalProdutos,
            freteCalc.valor, // Valor do frete
            totalGeral
        );

        // Adiciona metadados do frete ao pedido para registro
        novoPedido.detalhesFrete = freteCalc;

        this.pedidos.push(novoPedido);

        return novoPedido;
    }

    buscarPorId(id) {
        return this.pedidos.find(p => p.id === parseInt(id));
    }
}
