export class Pedido {
    constructor(id, itens, totalProdutos, frete, totalGeral) {
        this.id = id;
        this.data = new Date();
        this.itens = itens; // Copia do array de itens
        this.totalProdutos = totalProdutos;
        this.frete = frete;
        this.totalGeral = totalGeral;
        this.status = 'CONFIRMADO';
    }
}
