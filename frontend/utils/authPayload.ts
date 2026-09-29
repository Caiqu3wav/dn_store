import { addressFormsDataTypes } from "../app/components/ui/AddressForm";
import { onlyDigits, normalizeEmail } from "./authFormatters";

export interface AuthFormSubmission {
  name: string;
  phone: string;
  email: string;
  password: string;
  cpf: string;
  address: addressFormsDataTypes;
}

export function normalizeAuthPayload(
  data: AuthFormSubmission
): AuthFormSubmission {
  return {
    name: data.name.trim(),
    phone: onlyDigits(data.phone),
    email: normalizeEmail(data.email),
    password: data.password,
    cpf: onlyDigits(data.cpf),
    address: {
      ...data.address,
      zipCode: onlyDigits(data.address.zipCode),
      state: data.address.state.trim().toUpperCase(),
      city: data.address.city.trim(),
      neighborhood: data.address.neighborhood.trim(),
      street: data.address.street.trim(),
      number: data.address.number.trim(),
      complement: data.address.complement?.trim() ?? "",
    },
  };
}
