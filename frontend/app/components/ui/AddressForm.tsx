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

const AddressFormRow = styled.div`
    display: flex;
    justify-content: space-between;
    width: 100%;
    flex-direction: row;

`

export function AddressForm({
    addressFormsData,
    onChange,

}: AddressFormProps
) {
  return (
    <AddressFormContainer>
      <AddressFormTitle>Endereço</AddressFormTitle>
      <AddressFormRow>
        <LabelInput
          label="Cidade"
          name="city"
          placeholder="Digite sua cidade"
          type="text"
          value={addressFormsData.city}
          onChange={onChange}
          required
        />

        <LabelInput
          label="Estado"
          name="state"
          placeholder="Digite seu estado"
          type="text"
          value={addressFormsData.state}
          onChange={onChange}
          required
        />
      </AddressFormRow>

      <LabelInput
        width="25%"
        value={addressFormsData.zipCode}
        onChange={onChange}
        name="zipCode"
        type="text"
        placeholder="CEP"
        label="CEP"
        required
      />

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

      <AddressFormRow>
        <LabelInput
          width="31%"
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