import express from 'express';
import cors from 'cors';
import { ProdutoFisico } from './src/models/ProdutoFisico.js';
import { Carrinho } from './src/models/Carrinho.js';
import { PedidoService } from './src/services/PedidoService.js';

const app = express();
const PORT = 3002; // Porta alterada para evitar Conflito

// Middlewares
app.use(express.json());
app.use(cors());

// --- Inicialização ---
const pedidoService = new PedidoService();
const carrinho = new Carrinho();

// Catálogo (Agora usando ProdutoFisico com Peso)
const produtos = [
    new ProdutoFisico(1, "Notebook Gamer", 4500.00, "https://img.com/note.jpg", 2.5), // 2.5kg
    new ProdutoFisico(2, "Mouse Sem Fio", 120.00, "https://img.com/mouse.jpg", 0.2), // 200g
    new ProdutoFisico(3, "Teclado Mecânico", 350.00, "https://img.com/kb.jpg", 1.0), // 1kg
    new ProdutoFisico(4, "Monitor 144hz", 1200.00, "https://img.com/monitor.jpg", 5.0) // 5kg
];

// --- Rotas ---

app.get('/produtos', (req, res) => {
    res.json(produtos);
});

app.post('/carrinho/adicionar', (req, res) => {
    const { produtoId, quantidade } = req.body;

    const produto = produtos.find(p => p.id === parseInt(produtoId));

    if (!produto) {
        return res.status(404).json({ error: "Produto não encontrado" });
    }

    carrinho.adicionar(produto, quantidade || 1);

    res.json({
        message: "Produto adicionado!",
        carrinho: {
            itens: carrinho.itens,
            total: carrinho.total(),
            pesoTotal: carrinho.totalPeso()
        }
    });
});

app.post('/pedido/finalizar', async (req, res) => {
    // Agora aceita 'tipoFrete' (sedex ou pac)
    const { cep, tipoFrete } = req.body;

    try {
        // Chamada Assíncrona (Aguardar ViaCEP)
        const pedido = await pedidoService.criarPedido(carrinho, cep, tipoFrete);
        carrinho.limpar();
        res.status(201).json(pedido);
    } catch (error) {
        res.status(400).json({ error: error.message });
    }
});

app.get('/pedido/:id', (req, res) => {
    const pedido = pedidoService.buscarPorId(req.params.id);
    if (!pedido) return res.status(404).json({ error: "Pedido não encontrado" });
    res.json(pedido);
});

app.listen(PORT, () => {
    console.log(`🚀 Integrado com ViaCEP e Estratégias de Frete na porta ${PORT}`);
});
