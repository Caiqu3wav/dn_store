import styled from "styled-components";

export const Container = styled.div`
  display: flex;
  width: 100%;
  min-height: 100vh;
  padding: 20px;
  align-items: center;
  justify-content: center;
  background: #dbdbdb;
  background: linear-gradient(
    180deg,
    rgba(219, 219, 219, 1) 0%,
    rgba(224, 224, 224, 1) 22%,
    rgba(250, 250, 250, 1) 40%,
    rgba(250, 250, 250, 1) 60%,
    rgba(224, 224, 224, 1) 78%,
    rgba(219, 219, 219, 1) 100%
  );
  animation: moveBackground 50s alternate infinite;

  @keyframes moveBackground {
    from {
      background-position: 0 0;
    }
    to {
      background-position: 0 10000px;
    }
  }
`;
export const Login = styled.div`
  background-color: #f9f9f9;
  padding: 2rem;
  border-radius: 10%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  box-shadow: 0 6px 10px rgba(0, 0, 0, 0.4);
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
  width: 200px;
  height: 200px;
  background-image: url("http://localhost:3000/_next/image?url=%2Fassets%2Fdn_store2.png&w=1920&q=75");
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
  transform: rotate(17.7deg);
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
