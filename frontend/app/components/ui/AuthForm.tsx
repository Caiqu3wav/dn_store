import { useState } from "react";
import styled from "styled-components";
import { Title } from "./Text";
import { Button } from "../../Auth/page.style";
import { LabelInput } from "./LabelInput";
import { AddressForm } from "./AddressForm";
import { addressFormsDataTypes } from "./AddressForm";

interface FormData {
  email: string;
  password: string;
  confirmPassword?: string;
  cpf: string;
  addressFormsData: addressFormsDataTypes;
}


interface AuthFormProps {
  title: string;
  buttonLabel?: string;
  onSubmit?: (email: string, password: string, zipCode: string, addressFormsData: addressFormsDataTypes) => void;
  onChange?: (event: React.ChangeEvent<HTMLInputElement>) => void;
}

const FormContainer = styled.form`
  background-color: #f7f7f7;
  width: 100%;
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

  const [formsData, setFormsData] = useState<FormData>(
    {
      email: "",
      password: "",
      confirmPassword: "",
      cpf: "",
      addressFormsData: {
        city: "",
        state: "",
        neighborhood: "",
        street: "", 
        number: "",
        complement: "",
        zipCode: "",
      },
    }
  );


  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    onChange?.(event);

    const { name, value } = event.target;
    if (!isRegister && name === "confirmPassword") return;
    setFormsData((prev) => {
      const addressField = name as keyof addressFormsDataTypes;

      if (addressField in prev.addressFormsData) {
        return {
          ...prev,
          addressFormsData: {
            ...prev.addressFormsData,
            [addressField]: value,
          },
        };
      }

      return {
        ...prev,
        [name]: value,
      };
    });
  };

  const [error, setError] = useState<string>("");

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    setError("");

    const { email, password, confirmPassword, cpf, addressFormsData } = formsData;

    if (isRegister) {
      if (!confirmPassword) {
        setError("Confirme a senha.");
        return;
      }

      if (password !== confirmPassword) {
        setError("As senhas não conferem.");
        return;
      }

      if (!cpf.trim()) {
        setError("Informe o CPF.");
        return;
      }

      const requiredAddressFields: (keyof addressFormsDataTypes)[] = [
        "zipCode",
        "state",
        "city",
        "neighborhood",
        "street",
        "number",
      ];

      if (requiredAddressFields.some((field) => !addressFormsData[field]?.trim())) {
        setError("Preencha todos os campos obrigatórios do endereço.");
        return;
      }
    }

    if (onSubmit) {
      onSubmit(email, password, cpf, addressFormsData);
    } else {
      console.log({ email, password, cpf, addressFormsData });
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
        required
      />
      <LabelInput
        value={formsData.password}
        onChange={handleChange}
        name="password"
        type="password"
        placeholder="Senha"
        label="Senha"
        required
      />
      {isRegister && (
        <LabelInput
          value={formsData.confirmPassword ?? ""}
          onChange={handleChange}
          name="confirmPassword"
          type="password"
          placeholder="Confirme a Senha"
          label="Confirmar a Senha"
          required
        />
      )}

      {isRegister && (
        <LabelInput
          value={formsData.cpf}
          onChange={handleChange}
          name="cpf"
          type="text"
          placeholder="CPF"
          label="CPF"
          required
        />
      )&&(
        <AddressForm
          addressFormsData={formsData.addressFormsData}
          onChange={handleChange}

        />
      )}

      {error && <p style={{ color: "#d00", marginTop: "1rem" }}>{error}</p>}

      <Button type="submit">
        {buttonLabel ?? (isRegister ? "Cadastrar" : "Acessar")}
      </Button>
    </FormContainer>
  );
};
