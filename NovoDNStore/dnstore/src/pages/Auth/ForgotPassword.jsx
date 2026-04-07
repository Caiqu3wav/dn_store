import '../Auth/Login.css'

function ForgotPassword() {
  return (
    <div className="auth-container">
      <h2>Recuperar Senha</h2>

      <input type="email" placeholder="Digite seu email" />

      <button>Enviar</button>
    </div>
  )
}

export default ForgotPassword