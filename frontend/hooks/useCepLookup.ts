import { useCallback, useState } from "react";
import { addressFormsDataTypes } from "../app/components/ui/AddressForm";
import { onlyDigits } from "../utils/authFormatters";

interface ViaCepResponse {
  logradouro?: string;
  bairro?: string;
  localidade?: string;
  uf?: string;
  erro?: boolean;
}

export function useCepLookup(
  onAddressFound: (data: Partial<addressFormsDataTypes>) => void
) {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");

  const lookupCep = useCallback(
    async (value: string) => {
      const cep = onlyDigits(value);

      if (cep.length !== 8) return false;

      setIsLoading(true);
      setError("");

      try {
        const response = await fetch(
          `https://viacep.com.br/ws/${cep}/json/`
        );

        if (!response.ok) {
          throw new Error("CEP lookup failed");
        }

        const data: ViaCepResponse = await response.json();

        if (data.erro) {
          setError("CEP não encontrado.");
          return false;
        }

        onAddressFound({
          zipCode: value,
          street: data.logradouro ?? "",
          neighborhood: data.bairro ?? "",
          city: data.localidade ?? "",
          state: data.uf ?? "",
        });

        return true;
      } catch {
        setError("Não foi possível consultar o CEP.");
        return false;
      } finally {
        setIsLoading(false);
      }
    },
    [onAddressFound]
  );

  return {
    lookupCep,
    isLoading,
    error,
    clearError: () => setError(""),
  };
}
