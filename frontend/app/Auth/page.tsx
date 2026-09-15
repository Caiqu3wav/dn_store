"use client";

  return (
    <Container>
      <Login>
        <Logo />
        {errorMsg && (
          <div className="bg-red-500/10 border border-red-500/50 text-red-500 p-3 rounded-lg text-sm text-center mb-4 w-full max-w-sm">
            {errorMsg}
          </div>
        )}
        {pagetype === "login" ? (
          <>
            <AuthForm title="Entrar" onSubmit={handleLoginSubmit} />
            <p className="mt-4 text-gray-400">
              Não tem conta?{" "}
              <OnClickText onClick={() => setPagetype("register")}>
                Cadastrar
              </OnClickText>
            </p>
            <p className="mt-2">
              <a href="/Auth/recover" style={{ color: "#007bff", textDecoration: "none", fontSize: "0.9rem" }}>
                Esqueci minha senha
              </a>
            </p>
          </>
        ) : (
          <>
            <AuthForm title="Cadastro" onSubmit={handleRegisterSubmit} />
            <p className="mt-4 text-gray-400">
              Já tem conta?{" "}
              <OnClickText onClick={() => setPagetype("login")}>
                Entrar
              </OnClickText>
            </p>
          </>
        )}
      </Login>
    </Container>
  );
            </p>
          </>
        )}
      </Login>
    </Container>
  );
}

export default Auth;
