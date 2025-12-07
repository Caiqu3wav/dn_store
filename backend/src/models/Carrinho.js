export class Carrinho {
    constructor() {
        this.itens = [];
    }

    adicionar(produto, quantidade) {
        const itemExistente = this.itens.find(item => item.produto.id === produto.id);
        if (itemExistente) {
            itemExistente.quantidade += quantidade;
        } else {
            this.itens.push({
                produto: produto,
                quantidade: quantidade
            });
        }
    }

    total() {
        return this.itens.reduce((acc, item) => acc + (item.produto.preco * item.quantidade), 0);
    }

    // [NOVO] Cálculo de peso para o frete
    totalPeso() {
        return this.itens.reduce((acc, item) => {
            // Se for produto físico, usa o peso, senão padrão 0
            const pesoUnitario = item.produto.peso || 0;
            return acc + (pesoUnitario * item.quantidade);
        }, 0);
    }

    limpar() {
        this.itens = [];
    }
}
