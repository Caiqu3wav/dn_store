"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "react-hot-toast";
import { Container, Login, Logo } from "./page.style";
import { AuthForm } from "../components/ui/AuthForm";
import { OnClickText } from '../components/ui/Text';
import { useAuth } from "@/app/context/AuthContext";
import { authService } from "@/services/authService";

function Auth() {
  const [pagetype, setPagetype] = useState<"login" | "register">("login");
  const [errorMsg, setErrorMsg] = useState("");
  const { login } = useAuth();
  const router = useRouter();

  const handleLoginSubmit = async (email: string, password: string) => {
    setErrorMsg("");
    try {
      const data = await authService.login({ email, password });
      login(data.token, data.user);
      
      toast.success("Login realizado com sucesso!");
      
      // Redirect based on role
      if (data.user.role === 'ADMIN') {
        router.push("/admin");
      } else {
        router.push("/");
      }
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || "Erro ao fazer login. Verifique suas credenciais.");
    }
  };

  const handleRegisterSubmit = async (email: string, password: string) => {
    setErrorMsg("");
    try {
      // Assuming register takes name as well, but AuthForm only provides email and password currently.
      // For now, let's pass a placeholder name if AuthForm isn't updated.
      const name = email.split('@')[0];
      await authService.register({ name, email, password });
      // After registration, automatically login
      const data = await authService.login({ email, password });
      login(data.token, data.user);
      
      toast.success("Conta criada com sucesso!");
      router.push("/");
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || "Erro ao cadastrar.");
    }
  };

  return (
    <Container>
      <Login>
        <Logo />
        
        {errorMsg && (
          <div className="bg-red-500/10 border border-red-500/50 text-red-500 p-3 rounded-lg text-sm text-center mb-4 w-full max-w-sm">
            {errorMsg}
          </div>
        )}

        {pagetype === "login" ? (
          <>
            <AuthForm 
              title="Entrar" 
              onSubmit={handleLoginSubmit} 
            />
            <p className="mt-4 text-gray-400">
              Não tem conta? <OnClickText onClick={() => setPagetype("register")}>Cadastrar</OnClickText>
            </p>
            <p className="mt-2">
              <a href="/auth/recover" style={{ color: "#007bff", textDecoration: "none", fontSize: "0.9rem" }}>
                Esqueci minha senha
              </a>
            </p>
          </>
        ) : (
          <>
            <AuthForm 
              title="Cadastro" 
              onSubmit={handleRegisterSubmit} 
            />
            <p className="mt-4 text-gray-400">
              Já tem conta? <OnClickText onClick={() => setPagetype("login")}>Entrar</OnClickText>
            </p>
          </>
        )}
      </Login>
    </Container>
  );
}

export default Auth;
