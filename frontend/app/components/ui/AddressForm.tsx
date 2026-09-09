import styled from 'styled-components';
import { LabelInput } from './LabelInput';

export interface addressFormsDataTypes {
    city: string;
    state: string;
    neighborhood: string;
    street: string;
    number: string;
    complement?: string;
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
            />

            <LabelInput
            label="Estado"
            name="state"
            placeholder="Digite seu estado"
            type="text"
            value={addressFormsData.state}
            onChange={onChange}
            />
        </AddressFormRow>

      <LabelInput
        label="Bairro"
        name="Bairro"
        placeholder="Digite seu bairro"
        type="text"
        value={addressFormsData.neighborhood}
        onChange={onChange}
      />

      <LabelInput
        label="Rua"
        name="Rua"
        placeholder="Digite sua rua"
        type="text"
        value={addressFormsData.street}
        onChange={onChange}
      />

      <LabelInput
        label="Número"
        name="Número"
        placeholder="Digite o número da sua casa"
        type="text"
        value={addressFormsData.number}
        onChange={onChange}
      />

      <LabelInput
        label="Complemento"
        name="complementt"
        placeholder="Digite o complemento"
        type="text"
        value={addressFormsData.complement ?? ""}
        onChange={onChange}
      />
    </AddressFormContainer>
  );
}