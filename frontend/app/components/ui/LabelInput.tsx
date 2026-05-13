import styled from "styled-components";

interface LabelInputProps {
    label: string;
    placeholder?: string;
    type?: string;
    name: string;
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

export function LabelInput({ label, placeholder, type, name }: LabelInputProps) {
    return (
      <>
        <StyledLabel aria-label={label} htmlFor={name}>
          {label}
        </StyledLabel>
        <StyledInput
          name={name}
          id={name}
          placeholder={placeholder}
          type={type}
          required
        />
      </>
    );
}

