"use client";
import { useState } from "react"
import { Container, Login, Logo } from "./page.style"
import { AuthForm } from "../components/ui/AuthForm"
import { OnClickText } from '../components/ui/Text';
import { useSearchParams } from "next/navigation";
import { Row } from "react-bootstrap";
import Link from "next/link";

function Auth() {

  const searchParams = useSearchParams();

  const [pagetype, setPagetype] = useState(
    searchParams.get("mode") === "register" ? "register" : "login"
  );

  interface pagetype {
    login: string;
    register: string;
  }

  


  return (
    (pagetype === "login" && (
      <Container>
        <Login>
          <Logo />

          <AuthForm title="Entrar" />

          <p>
            Não tem conta?{" "}
            <OnClickText onClick={() => setPagetype("register")}>
              Cadastrar
            </OnClickText>
          </p>
          <Row>
            <Link
              href="/Auth/recover"
              style={{ color: "#007bff" }}
              onMouseEnter={(e) => {
                e.currentTarget.style.textDecoration = "underline";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.textDecoration = "none";
              }}
            >
              Esqueci minha senha
            </Link>
            <span style={{ margin: 10 }}>|</span>
          </Row>
        </Login>
      </Container>
    )) ||
    (pagetype === "register" && (
      <Container>
        <Login>
          <Logo />
          <AuthForm title="Cadastro" />
          <Row
            style={{
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
              marginTop: 10,
            }}
          >
            <span>
              Já tem conta?{" "}
              <OnClickText onClick={() => setPagetype("login")}>
                Entrar
              </OnClickText>
            </span>
            <span style={{ margin: 10 }}>|</span>
            
          </Row>
        </Login>
      </Container>
    ))
  );
}

export default Auth

