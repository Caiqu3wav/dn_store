function Auth() {
  return (
    <div className="auth-container">
      <h2>Login</h2>

      <input type="email" placeholder="Email" />
      <input type="password" placeholder="Senha" />

      <button>Entrar</button>

      <p>Não tem conta? <span>Cadastrar</span></p>
      <p><span>Esqueci minha senha</span></p>
    </div>
  )
}

export default Auth