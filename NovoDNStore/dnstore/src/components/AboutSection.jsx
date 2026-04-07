import '../components/AboutSection.css'

function AboutSection() {
  return (
    <section className="about">
      <div className="about-container">

        <div className="about-text">
          <h2>Sobre o Desafio Natureza</h2>
          <p>
            Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut
             labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco
             laboris nisi ut aliquip ex ea commodo consequat.
          </p>
        </div>

        <div className="about-image">
          <img src="/images/umaLogo.jpg" alt="Sobre nós" />
        </div>

      </div>
    </section>
  )
}

export default AboutSection