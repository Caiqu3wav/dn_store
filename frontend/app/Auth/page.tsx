"use client";
import { useState } from "react"
import { Container, Login, Logo } from "./page.style"
import { AuthForm } from "../components/ui/AuthForm"
import { OnClickText } from '../components/ui/Text';


function Auth() {

  const [pagetype, setPagetype] = useState("login");

  interface pagetype {
    login: string;
    register: string;
  }

  


  return (
    (pagetype === "login") && (
      <Container>
        <Login>

          <Logo />

          <AuthForm title="Entrar" />

          <p>
            Não tem conta? <OnClickText onClick={() => setPagetype("register")}>Cadastrar</OnClickText>
          </p>
          <p>
            <a href="/Auth/recover" style={{ color: "#007bff", textDecoration: "none" }}>
              Esqueci minha senha
            </a>
          </p>
        </Login>
      </Container>
    ) || (
      pagetype === "register" && (
        <Container>
          <Login>
            <Logo />
            <AuthForm title="Cadastro" />
            <p>
              Já tem conta? <OnClickText onClick={() => setPagetype("login")}>Entrar</OnClickText>
            </p>
          </Login>
        </Container>
      )
    )
  )
}

export default Auth

