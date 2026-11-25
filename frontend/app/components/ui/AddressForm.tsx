import styled from 'styled-components';
import { LabelInput } from './LabelInput';

export interface addressFormsDataTypes {
    zipCode: string;
    city: string;
    state: string;
    neighborhood: string;
    street: string;
    number: string;
    complement?: string;
    inputWidth?: string;
}

interface AddressFormProps {
    addressFormsData: addressFormsDataTypes;
    onChange?: (event: React.ChangeEvent<HTMLInputElement>) => void;
  isLoading?: boolean;
}

const AddressFormContainer = styled.div`
  display: flex;
  flex-direction: column;
    width: 100%;
    margin-top: 1rem;
`

const AddressFormTitle = styled.h3`
  margin-bottom: 1rem;
  font-size: 1.2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
`

const AddressFormRow = styled.div<{ $columns?: string }>`
    display: grid;
    grid-template-columns: ${({ $columns }) => $columns ?? "1fr 1fr"};
    column-gap: 1rem;
    width: 100%;
`

export function AddressForm({
    addressFormsData,
    onChange,
  isLoading = false,

}: AddressFormProps
) {
  return (
    <AddressFormContainer>
      <AddressFormTitle>Endereço</AddressFormTitle>
      {isLoading && (
        <p role="status" aria-live="polite">
          Consultando CEP...
        </p>
      )}
      <LabelInput
        value={addressFormsData.zipCode}
        onChange={onChange}
        name="zipCode"
        type="text"
        placeholder="CEP"
        label="CEP"
        required
      />

      <AddressFormRow>
        <LabelInput
          label="Estado"
          name="state"
          placeholder="Digite seu estado"
          type="text"
          value={addressFormsData.state}
          onChange={onChange}
          required
        />

        <LabelInput
          label="Cidade"
          name="city"
          placeholder="Digite sua cidade"
          type="text"
          value={addressFormsData.city}
          onChange={onChange}
          required
        />
      </AddressFormRow>

      <LabelInput
        label="Bairro"
        name="neighborhood"
        placeholder="Digite seu bairro"
        type="text"
        value={addressFormsData.neighborhood}
        onChange={onChange}
        required
      />

      <LabelInput
        label="Rua"
        name="street"
        placeholder="Digite sua rua"
        type="text"
        value={addressFormsData.street}
        onChange={onChange}
        required
      />

      <AddressFormRow $columns="1fr 2fr">
        <LabelInput
          label="Número"
          name="number"
          placeholder="XXX"
          type="number"
          max={10}
          value={addressFormsData.number}
          onChange={onChange}
          required
        />

        <LabelInput
          label="Complemento (opcional)"
          name="complement"
          placeholder="apartamento, bloco, etc..."
          type="text"
          value={addressFormsData.complement ?? ""}
          onChange={onChange}
        />
      </AddressFormRow>
    </AddressFormContainer>
  );
}