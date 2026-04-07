import '../components/Events.css'

function Events() {
  return (
    <section className="events">
      <h2>Próximos Eventos</h2>

      <div className="events-container">

        <div className="event-card">
          <h3>Pedal na Serra da Mantiqueira</h3>
          <p>📅 12 de Abril</p>
          <p>📍 Campos do Jordão</p>
        </div>

        <div className="event-card">
          <h3>Trilha MTB Parque da Cidade</h3>
          <p>📅 20 de Abril</p>
          <p>📍 São José dos Campos</p>
        </div>

        <div className="event-card">
          <h3>Desafio MTB Pico do Itapeva</h3>
          <p>📅 04 de Maio</p>
          <p>📍 Pindamonhangaba</p>
        </div>

        <div className="event-card">
          <h3>Pedal Estrada Velha</h3>
          <p>📅 18 de Maio</p>
          <p>📍 Taubaté</p>
        </div>

      </div>
    </section>
  )
}

export default Events