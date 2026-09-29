import { useCallback, useMemo, useState } from "react";
import styled from "styled-components";
import { ArrowLeft, LoaderCircle } from "lucide-react";
import { Title } from "./Text";
import { Button } from "../../auth/page.style";
import { LabelInput } from "./LabelInput";
import { AddressForm, addressFormsDataTypes } from "./AddressForm";
import { useCepLookup } from "../../../hooks/useCepLookup";
import {
  formatCep,
  formatCpf,
  formatPhone,
  onlyDigits,
} from "../../../utils/authFormatters";
import {
  getPasswordStrength,
  isValidCpf,
  isValidEmail,
} from "../../../utils/authValidation";
import { normalizeAuthPayload } from "../../../utils/authPayload";

export interface AuthFormSubmission {
  name: string;
  phone: string;
  email: string;
  password: string;
  cpf: string;
  address: addressFormsDataTypes;
}

interface FormData extends AuthFormSubmission {
  confirmPassword: string;
}

interface AuthFormProps {
  title: string;
  buttonLabel?: string;
  isSubmitting?: boolean;
  onSubmit?: (data: AuthFormSubmission) => void | Promise<void>;
}

const FormContainer = styled.form`
  background-color: #f7f7f7;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
`;

const initialFormData: FormData = {
  name: "",
  phone: "",
  email: "",
  password: "",
  confirmPassword: "",
  cpf: "",
  address: {
    city: "",
    state: "",
    neighborhood: "",
    street: "",
    number: "",
    complement: "",
    zipCode: "",
  },
};

const stepLabels = ["Dados pessoais", "Senha", "Endereço"];

export const AuthForm = ({
  title,
  buttonLabel,
  isSubmitting = false,
  onSubmit,
}: AuthFormProps) => {
  const isRegister = title === "Cadastro";
  const [step, setStep] = useState(0);
  const [formsData, setFormsData] = useState<FormData>(initialFormData);
  const [error, setError] = useState("");

  const updateAddress = useCallback(
    (data: Partial<addressFormsDataTypes>) => {
      setFormsData((prev) => ({
        ...prev,
        address: {
          ...prev.address,
          ...data,
        },
      }));
    },
    []
  );

  const {
    lookupCep,
    isLoading: isCepLoading,
    error: cepError,
  } = useCepLookup(updateAddress);

  const passwordStrength = useMemo(
    () => getPasswordStrength(formsData.password),
    [formsData.password]
  );

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const { name } = event.target;
    let value = event.target.value;

    if (name === "cpf") value = formatCpf(value);
    if (name === "phone") value = formatPhone(value);
    if (name === "zipCode") value = formatCep(value);

    setError("");

    const addressField = name as keyof addressFormsDataTypes;

    if (addressField in formsData.address) {
      setFormsData((prev) => ({
        ...prev,
        address: {
          ...prev.address,
          [addressField]: value,
        },
      }));

      if (name === "zipCode" && onlyDigits(value).length === 8) {
        void lookupCep(value);
      }

      return;
    }

    setFormsData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const validatePersonalStep = (): boolean => {
    const { name, email, phone, cpf } = formsData;

    if (name.trim().length < 3) {
      setError("Informe seu nome completo.");
      return false;
    }

    if (!isValidEmail(email)) {
      setError("Informe um email válido.");
      return false;
    }

    const phoneDigits = onlyDigits(phone);

    if (phoneDigits && (phoneDigits.length < 10 || phoneDigits.length > 11)) {
      setError("Informe um telefone válido.");
      return false;
    }

    if (!isValidCpf(cpf)) {
      setError("Informe um CPF válido.");
      return false;
    }

    return true;
  };

  const validatePasswordStep = (): boolean => {
    const { password, confirmPassword } = formsData;

    if (password.length < 8) {
      setError("A senha deve ter pelo menos 8 caracteres.");
      return false;
    }

    if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) {
      setError("Use pelo menos uma letra e um número.");
      return false;
    }

    if (password !== confirmPassword) {
      setError("As senhas não conferem.");
      return false;
    }

    return true;
  };

  const validateAddressStep = (): boolean => {
    const requiredFields: (keyof addressFormsDataTypes)[] = [
      "zipCode",
      "state",
      "city",
      "neighborhood",
      "street",
      "number",
    ];

    const missing = requiredFields.some(
      (field) => !formsData.address[field]?.trim()
    );

    if (missing) {
      setError("Preencha todos os campos obrigatórios do endereço.");
      return false;
    }

    if (onlyDigits(formsData.address.zipCode).length !== 8) {
      setError("Informe um CEP válido.");
      return false;
    }

    return true;
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (isSubmitting) return;

    setError("");

    if (!isRegister) {
      await onSubmit?.({
        name: "",
        phone: "",
        email: formsData.email,
        password: formsData.password,
        cpf: "",
        address: formsData.address,
      });
      return;
    }

    if (step === 0) {
      if (!validatePersonalStep()) return;
      setStep(1);
      return;
    }

    if (step === 1) {
      if (!validatePasswordStep()) return;
      setStep(2);
      return;
    }

    if (!validateAddressStep()) return;

    const payload = normalizeAuthPayload({
      name: formsData.name,
      phone: formsData.phone,
      email: formsData.email,
      password: formsData.password,
      cpf: formsData.cpf,
      address: formsData.address,
    });

    await onSubmit?.(payload);
  };

  return (
    <FormContainer onSubmit={handleSubmit} noValidate>
      <Title>{title}</Title>

      {isRegister && (
        <div
          className="mb-2 w-full max-w-sm"
          aria-label={`Etapa ${step + 1} de 3: ${stepLabels[step]}`}
        >
          <div className="mb-2 flex justify-between text-xs font-medium text-gray-500">
            {stepLabels.map((label, index) => (
              <span
                key={label}
                className={index === step ? "text-gray-900" : ""}
              >
                {label}
              </span>
            ))}
          </div>

          <div className="flex gap-2" aria-hidden="true">
            {stepLabels.map((label, index) => (
              <span
                key={label}
                className={`h-1 flex-1 rounded-full ${
                  index <= step ? "bg-red-600" : "bg-gray-200"
                }`}
              />
            ))}
          </div>
        </div>
      )}

      {!isRegister && (
        <>
          <LabelInput
            value={formsData.email}
            onChange={handleChange}
            name="email"
            type="email"
            placeholder="user@example.com"
            label="Email"
            required
            autoComplete="email"
          />

          <LabelInput
            value={formsData.password}
            onChange={handleChange}
            name="password"
            type="password"
            placeholder="Senha"
            label="Senha"
            required
            autoComplete="current-password"
          />
        </>
      )}

      {isRegister && step === 0 && (
        <>
          <LabelInput
            value={formsData.name}
            onChange={handleChange}
            name="name"
            type="text"
            placeholder="Seu nome completo"
            label="Nome completo"
            required
            autoComplete="name"
            maxLength={120}
          />

          <LabelInput
            value={formsData.email}
            onChange={handleChange}
            name="email"
            type="email"
            placeholder="voce@exemplo.com"
            label="Email"
            required
            autoComplete="email"
            maxLength={254}
          />

          <LabelInput
            value={formsData.phone}
            onChange={handleChange}
            name="phone"
            type="tel"
            inputMode="tel"
            maxLength={15}
            placeholder="(00) 00000-0000"
            label="Telefone (opcional)"
            autoComplete="tel"
          />

          <LabelInput
            value={formsData.cpf}
            onChange={handleChange}
            name="cpf"
            type="text"
            inputMode="numeric"
            maxLength={14}
            placeholder="000.000.000-00"
            label="CPF"
            required
            autoComplete="off"
          />
        </>
      )}

      {isRegister && step === 1 && (
        <>
          <LabelInput
            value={formsData.password}
            onChange={handleChange}
            name="password"
            type="password"
            placeholder="Mínimo de 8 caracteres"
            label="Senha"
            required
            autoComplete="new-password"
            minLength={8}
            maxLength={128}
          />

          {formsData.password && (
            <div className="w-full max-w-sm" aria-live="polite">
              <div className="mb-1 flex justify-between text-xs text-gray-500">
                <span>Força da senha</span>
                <span>{passwordStrength.label}</span>
              </div>

              <div className="flex gap-1">
                {[0, 1, 2, 3, 4].map((index) => (
                  <span
                    key={index}
                    className={`h-1 flex-1 rounded ${
                      index < passwordStrength.score
                        ? "bg-red-600"
                        : "bg-gray-200"
                    }`}
                  />
                ))}
              </div>
            </div>
          )}

          <LabelInput
            value={formsData.confirmPassword}
            onChange={handleChange}
            name="confirmPassword"
            type="password"
            placeholder="Repita sua senha"
            label="Confirmar senha"
            required
            autoComplete="new-password"
            minLength={8}
            maxLength={128}
          />
        </>
      )}

      {isRegister && step === 2 && (
        <AddressForm
          addressFormsData={formsData.address}
          onChange={handleChange}
          isLoading={isCepLoading}
        />
      )}

      {(error || cepError) && (
        <p
          role="alert"
          className="mt-4 w-full max-w-sm text-sm text-red-600"
        >
          {error || cepError}
        </p>
      )}

      <div className="mt-3 flex w-full max-w-sm items-center gap-3">
        {isRegister && step > 0 && (
          <button
            type="button"
            onClick={() => {
              setError("");
              setStep((current) => current - 1);
            }}
            disabled={isSubmitting}
            className="flex items-center gap-1 px-3 py-2 text-sm font-semibold text-gray-600 disabled:opacity-50"
          >
            <ArrowLeft className="h-4 w-4" />
            Voltar
          </button>
        )}

        <Button
          type="submit"
          disabled={isSubmitting || isCepLoading}
          aria-live="polite"
          style={{
            marginTop: 0,
            width: "100%",
            opacity: isSubmitting || isCepLoading ? 0.7 : 1,
          }}
        >
          {isSubmitting ? (
            <span className="inline-flex items-center justify-center gap-2">
              <LoaderCircle className="h-4 w-4 animate-spin" />
              Cadastrando...
            </span>
          ) : isCepLoading && step === 2 ? (
            <span className="inline-flex items-center justify-center gap-2">
              <LoaderCircle className="h-4 w-4 animate-spin" />
              Consultando CEP...
            </span>
          ) : (
            buttonLabel ??
            (isRegister
              ? step < 2
                ? "Continuar"
                : "Cadastrar"
              : "Acessar")
          )}
        </Button>
      </div>
    </FormContainer>
  );
};
