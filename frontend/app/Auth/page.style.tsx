import styled from "styled-components";

export const Container = styled.div`
    display: flex;
    width: 100%;
    min-height: 100vh;
    padding: 20px;
    align-items: center;
    justify-content: center;
`
export const Login = styled.div`
    background-color: #F9F9F9;
    padding:2rem;
    border-radius: 10%;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
    width: 25%;

`

export const Logo = styled.div`
    width: 200px;
    height: 200px;
    background-image: url('http://localhost:3000/_next/image?url=%2Fassets%2Fdn_store2.png&w=1920&q=75');
    background-size: contain;
    background-repeat: no-repeat;
    background-position: center;
`

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
    transition: background-color 0.3s ease;
    &:hover {
        background-color: #cc0000;
    }

    &:active {
        background-color: #990000;
        transform: translateY(2px) scale(0.90) rotate(-0.5deg);
    }
`