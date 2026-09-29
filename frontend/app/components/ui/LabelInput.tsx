import styled from "styled-components";

interface LabelInputProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "width" | "name" | "value"> {
  name: string;
  value: string;
  width?: string;
  label: string;
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
  name,
  value,
  ...inputProps
}: LabelInputProps) {
  return (
    <>
      <FieldContainer inputWidth={width}>
        <StyledLabel aria-label={label} htmlFor={name}>
          {label}
        </StyledLabel>
        <StyledInput
          {...inputProps}
          name={name}
          id={inputProps.id ?? name}
          value={value}
        />
      </FieldContainer>
    </>
  );
}

