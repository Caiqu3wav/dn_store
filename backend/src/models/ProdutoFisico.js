import { Produto } from './Produto.js';

export class ProdutoFisico extends Produto {
    constructor(id, nome, preco, imagem, peso = 1.0) {
        super(id, nome, preco, imagem); // Chamada ao construtor da classe pai
        this.peso = peso; // Propriedade específica de ProdutoFisico
    }
}
