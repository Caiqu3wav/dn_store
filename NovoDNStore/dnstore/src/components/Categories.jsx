import '../components/Categories.css'
function Categories() {

  const categorias = [
    {
      id: 1,
      nome: "Camisetas",
      imagem: "/images/CDNBlue.JPG"
    },
    {
      id: 2,
      nome: "",
      imagem: "/images/BoneTeste.webp"
    },
    {
      id: 3,
      nome: "Meias",
      imagem: "/images/CaDN.jpg"
    }
  ]

  return (
    <section className="categories">
      <h2>Categorias</h2>

      <div className="categories-container">
        {categorias.map((categoria) => (
          <div className="category-card" key={categoria.id}>
            <img src={categoria.imagem} alt={categoria.nome} />
            <h3>{categoria.nome}</h3>
          </div>
        ))}
      </div>
    </section>
  )
}

export default Categories;