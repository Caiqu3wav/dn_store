import "./ProductCard.css";

function ProductCard({ nome, preco, imagem }) {
  return (
    <div className="card">
      <img src={imagem} alt={nome} />
      <h3>{nome}</h3>
      <p>{preco}</p>
      <button>Comprar</button>
    </div>
  );
}

export default ProductCard;