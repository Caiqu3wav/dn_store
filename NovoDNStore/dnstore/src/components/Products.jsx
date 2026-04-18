import "./Product.css";
import ProductCard from "./ProductCard";

import camisa1 from "../Images/DNCPB.jpeg";
import camisa2 from "../Images/DNCCL.jpeg";
import meia from "../Images/DNMB.jpeg";
import bone from "../Images/DNBB.jpeg";

const produtos = [
  { id: 1, nome: "Camiseta de poliamida", preco: "R$ 40,00", img: camisa1 },
  { id: 2, nome: "Camisa de ciclismo", preco: "R$ 180,00", img: camisa2 },
  { id: 3, nome: "Meia Desafio Natureza", preco: "R$ 50,00", img: meia },
  { id: 4, nome: "Boné Desafio Natureza", preco: "R$ 40,00", img: bone },
];

function Products() {
  return (
    <section className="products">
      <h2>Produtos em Destaque</h2>

      <div className="products-grid">
        {produtos.map((p) => (
          <ProductCard
            key={p.id}
            nome={p.nome}
            preco={p.preco}
            imagem={p.img}
          />
        ))}
      </div>
    </section>
  );
}

export default Products;