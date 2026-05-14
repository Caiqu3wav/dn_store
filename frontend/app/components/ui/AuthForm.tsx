import styled from "styled-components";
import { Title } from "./Text";
import { Button } from "../../auth/page.style";
import { LabelInput } from "./LabelInput";

interface AuthFormProps {
    title: string;
    onSubmit?: (email: string, password: string) => void;
}

const FormContainer = styled.form`
    background-color: #F9F9F9;
    width: 100%;
    border-radius: 10%;
    display: flex;
    flex-direction: column;
        align-items: center;
        justify-content: center;
`;

export const AuthForm = ({ title }: AuthFormProps) => {

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();

        const formData = new FormData(e.currentTarget as HTMLFormElement);
        const email = formData.get("email") as string;
        const password = formData.get("password") as string;
        console.log({ email, password });
    }

    return (
        <FormContainer onSubmit={handleSubmit}>
            <Title>{title}</Title>

            <LabelInput name="email" type="email" placeholder="Email" label="Email" />
            <LabelInput name="password" type="password" placeholder="Senha" label="Senha" />
            {title === "Cadastro" && (
                <LabelInput name="confirmPassword" type="password" placeholder="Confirmar Senha" label="Confirmar Senha" />
            )}
            <Button type="submit">{title === "Entrar" ? "Acessar" : "Cadastro"}</Button>

        </FormContainer>
    )
}