import { useState } from "react";
import styled from "styled-components";
import { Title } from "./Text";
import { Button } from "../../Auth/page.style";
import { LabelInput } from "./LabelInput";

interface AuthFormProps {
  title: string;
  buttonLabel?: string;
  onSubmit?: (email: string, password: string) => void;
  onChange?: (event: React.ChangeEvent<HTMLInputElement>) => void;
}

interface FormsDataTypes {
  email: string;
  password: string;
  confirmPassword?: string;
}

const FormContainer = styled.form`
  background-color: #f7f7f7;
  width: 100%;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
`;

export const AuthForm = ({
  title,
  buttonLabel,
  onSubmit,
  onChange,
}: AuthFormProps) => {
  const isRegister = title === "Cadastro";

  const [formsData, setFormsData] = useState<FormsDataTypes>({
    email: "",
    password: "",
    confirmPassword: "",
  });

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    onChange?.(event);

    const { name, value } = event.target;
    if (!isRegister && name === "confirmPassword") return;
    setFormsData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const [error, setError] = useState<string>("");

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    setError("");

    const { email, password, confirmPassword } = formsData;

    if (isRegister) {
      if (!confirmPassword) {
        setError("Confirme a senha.");
        return;
      }

      if (password !== confirmPassword) {
        setError("As senhas não conferem.");
        return;
      }
    }

    if (onSubmit) {
      onSubmit(email, password);
    } else {
      console.log({ email, password });
    }
  };


  // isRegister is now defined above

  return (
    <FormContainer onSubmit={handleSubmit}>
      <Title>{title}</Title>

      <LabelInput
        value={formsData.email}
        onChange={handleChange}
        name="email"
        type="email"
        placeholder="user@exemple.com"
        label="Email"
      />
      <LabelInput
        value={formsData.password}
        onChange={handleChange}
        name="password"
        type="password"
        placeholder="Senha"
        label="Senha"
      />
      {isRegister && (
        <LabelInput
          value={formsData.confirmPassword ?? ""}
          onChange={handleChange}
          name="confirmPassword"
          type="password"
          placeholder="Confirme a Senha"
          label="Confirmar a Senha"
        />
      )}


      {error && <p style={{ color: "#d00", marginTop: "1rem" }}>{error}</p>}

      <Button type="submit">
        {buttonLabel ?? (isRegister ? "Cadastrar" : "Acessar")}
      </Button>

    </FormContainer>
  );
};
