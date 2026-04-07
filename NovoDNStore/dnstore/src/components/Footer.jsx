import '../components/Footer.css'

function Footer() {
  return (
    <footer className="footer">

      <div className="footer-container">

        <div className="footer-section">
          <h3>Desafio Natureza</h3>
          <p>Explorando limites, vivendo aventuras.</p>
        </div>

        <div className="footer-section">
          <h4>Links</h4>
          <ul>
            <li>Início</li>
            <li>Produtos</li>
            <li>Eventos</li>
            <li>Sobre</li>
          </ul>
        </div>

        <div className="footer-section">
          <h4>Contato</h4>
          <p>Email: contato@desafionatureza.com</p>
          <p>Telefone: (12) 99999-9999</p>
        </div>

      </div>

      <div className="footer-bottom">
        <p>© 2026 Desafio Natureza - Todos os direitos reservados</p>
      </div>

    </footer>
  )
}

export default Footer