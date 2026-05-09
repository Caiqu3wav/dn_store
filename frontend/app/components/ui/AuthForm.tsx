import styled from "styled-components";
import { Title } from "./Text";
import { Button } from "../../Auth/page.style";
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

const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
}

export const AuthForm = ({ title, onSubmit }: AuthFormProps) => {
    return (
        <FormContainer>
            <Title>{title}</Title>

            <LabelInput name="email" type="email" placeholder="Email" label="Email" />
            <LabelInput name="password" type="password" placeholder="Senha" label="Senha" />
            <Button type="submit" onClick={handleSubmit}>Entrar</Button>

        </FormContainer>
    )
}