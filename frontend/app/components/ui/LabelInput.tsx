import styled from "styled-components";

interface LabelInputProps {
  width?: string;
  label: string;
  placeholder?: string;
  type?: string;
  name: string;
  value: string;
  onChange?: (event: React.ChangeEvent<HTMLInputElement>) => void;
}

const StyledInput = styled.input`
  padding: 0.5rem;
  border: 1px solid #ccc;
  border-radius: 0.25rem;
  font-size: 1rem;
  outline: none;
  &:focus {
    border-color: #007bff;
  }
  width: 90%;
  :required {
    border-color: red;
  }
`;

const StyledLabel = styled.label`
  display: block;
  margin-top: 1rem;
  margin-bottom: 0.5rem;
  font-weight: bold;

`;

const FieldContainer = styled.div<{ inputWidth: string }>`
  display: flex;
  flex-direction: column;
  width: ${({ inputWidth }) => inputWidth};
  margin-bottom: 1rem;
`;

export function LabelInput({
  width = "100%",
  label,
  placeholder,
  type,
  name,
  value,
  onChange,
}: LabelInputProps) {
  return (
    <>
      <FieldContainer inputWidth={width}>
        <StyledLabel aria-label={label} htmlFor={name}>
          {label}
        </StyledLabel>
        <StyledInput
          name={name}
          id={name}
          placeholder={placeholder}
          type={type}
          value={value}
          onChange={onChange}
          required
        />
      </FieldContainer>
    </>
  );
}

