import styled from "styled-components";

export const Container = styled.div`
  display: flex;
  width: 100%;
  min-height: 100vh;
  padding: 20px;
  align-items: center;
  justify-content: center;
  background: #ffffff;
`;
export const Login = styled.div`
  background-color: #f7f7f7;
  padding: 2rem;
  border-radius: 10%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.6);
  width: 25%;
  animation: fadeIn 0.5s ease-in-out;
  @keyframes fadeIn {
    from {
      opacity: 0;
    }
    to {
      opacity: 1;
    }
  }
`;

export const Logo = styled.div`
  width: 400px;
  height: 200px;
  background-image: url("/assets/Logo.jpeg");
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
`;

export const Button = styled.button`
  margin-top: 1rem;
  width: 90%;
  padding: 0.5rem;
  border: none;
  border-radius: 0.25rem;
  font-size: 1rem;
  background-color: #ff0000;
  color: white;
  cursor: pointer;
  box-sizing: border-box;
  transition: background-color 0.3s ease;

  &:hover {
    outline: 2px solid #000000;
  }

  &:active {
    background-color: #c70808;
    transform: translateY(2px) scale(0.98);
  }
`;
