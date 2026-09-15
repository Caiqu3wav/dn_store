"use client";

import { LabelInput } from "@/app/components/ui/LabelInput";
import { Col, Container, Row } from "react-bootstrap";
import styled from "styled-components";

export const Profile = styled.div`
  background-color: #f7f7f7;
  padding: 2rem;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.6);
  width: 35%;
  animation: fadeIn 0.5s ease-in-out;
  @keyframes fadeIn {
    from {
      opacity: 0;
    }
    to {
      opacity: 1;
    }
  }
`;

export const FieldRow = styled(Row)`
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
`;

export const EditButton = styled.button`
  margin-top: 1rem;
  padding: 0.5rem 1rem;
  background-color: #007bff;
  color: #fff;
  border: none;
  border-radius: 5px;
`;

export default function ProfilePage() {
  const temporaryUser = {
    name: "Usuário de Teste",
    email: "usuario.teste@example.com",
  };

  return (
    <Container
      fluid
      style={{
        minHeight: "100vh",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        background: "#fff",
      }}
    >
      <Profile className="d-flex flex-column align-items-center justify-content-center">
        <h1 style={{ fontSize: 28, fontWeight: 700, marginBottom: 12 }}>
          Perfil do Usuário
        </h1>
        <p style={{ color: "#444", marginBottom: 20 }}>
          Aqui você pode visualizar e editar suas informações de perfil.
        </p>
        <hr style={{ width: "100%", marginBottom: 20 }} />
        <FieldRow className="d-flex justify-content-center align-items-center mb-3">
          <Col className="d-flex justify-content-center align-items-center mb-3">
            <LabelInput
              label="Nome"
              name="name"
              type="text"
              value={temporaryUser.name}
              noOutline={true}
              disabled
            />
          </Col>
          <Col className="d-flex justify-content-center align-items-center mb-3">
            <EditButton>Editar</EditButton>
          </Col>
        </FieldRow>

        <hr style={{ width: "100%", marginBottom: 20 }} />
        <FieldRow className="d-flex justify-content-center align-items-center mb-3">
          <LabelInput
            label="Email"
            name="email"
            type="email"
            value={temporaryUser.email}
            noOutline={true}
            disabled
          />
          <EditButton>Editar</EditButton>
        </FieldRow>
        <hr style={{ width: "100%", marginBottom: 20 }} />
      </Profile>
    </Container>
  );
}
