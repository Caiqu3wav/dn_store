import '../Auth/Login.css'

function Register() {
  return (
    <div className="auth-container">
      <h2>Cadastrar</h2>

      <input type="text" placeholder="Nome" />
      <input type="email" placeholder="Email" />
      <input type="password" placeholder="Senha" />

      <button>Criar Conta</button>
    </div>
  )
}

export default Register