"use client";
import { useState } from "react"
import { Container, Login, Logo } from "./page.style"
import { AuthForm } from "../components/ui/AuthForm"
import { OnClickText } from '../components/ui/Text';


function Auth() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [pagetype, setPagetype] = useState("login");

  interface AuthFormProps {
    onSubmit?: (email: string, password: string) => void;
  }

  interface pagetype {
    login: string;
    register: string;
  }


  return (
    (pagetype === "login") && (
      <Container>
        <Login>

          <Logo />

          <AuthForm title="Login" />

          <p>Não tem conta? <OnClickText onClick={()=> setPagetype("register")}>Cadastrar</OnClickText></p>
          <p><span>Esqueci minha senha</span></p>
        </Login>
      </Container>
    ) || (
      <Container>
        <Login>
          <Logo />
          <AuthForm title="Cadastrar" />
          <p>Já tem conta? <OnClickText onClick={()=> setPagetype("login")}>Entrar</OnClickText></p>
        </Login>
      </Container>
    )
  )
}

export default Auth