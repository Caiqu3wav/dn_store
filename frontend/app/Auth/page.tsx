"use client";
import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { toast } from "react-hot-toast";
import { Container, Login, Logo } from "./page.style";
import { AuthForm, AuthFormSubmission } from "../components/ui/AuthForm";
import { OnClickText } from '../components/ui/Text';
import { useAuth } from "@/app/context/AuthContext";
import { authService } from "@/services/authService";
import { accountService } from "@/services/accountService";
import type { AuthChallengeResponse, AuthResponse, AuthResult } from "@/services/authService";

const responseMessage = (error: unknown, fallback: string) => {
  const responseData = (error as { response?: { data?: { message?: string } | string } }).response?.data;
  return typeof responseData === 'string' ? responseData : responseData?.message || fallback;
};

function Auth() {
  const searchParams = useSearchParams();
  const [pagetype, setPagetype] = useState<"login" | "register">(() =>
    searchParams.get("mode") === "register" ? "register" : "login"
  );
  const [errorMsg, setErrorMsg] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [challenge, setChallenge] = useState<AuthChallengeResponse | null>(null);
  const [code, setCode] = useState('');
  const [challengeError, setChallengeError] = useState('');
  const [challengeMessage, setChallengeMessage] = useState('');
  const [isChallengeLoading, setIsChallengeLoading] = useState(false);
  const [isRegistrationChallenge, setIsRegistrationChallenge] = useState(false);
  const [showMfaOffer, setShowMfaOffer] = useState(false);
  const [isSavingMfa, setIsSavingMfa] = useState(false);
  const [mfaOfferError, setMfaOfferError] = useState('');
  const { login } = useAuth();
  const router = useRouter();
  const nextPath = searchParams.get('next') || '/';

  const completeLogin = (data: AuthResponse) => {
    login(data.token, data.user);
    toast.success(data.user.role === 'ADMIN' ? "Login realizado com sucesso!" : "Acesso confirmado!");
    router.push(data.user.role === 'ADMIN' ? "/admin" : nextPath);
  };

  const handleAuthResult = (result: AuthResult) => {
    if ('token' in result) {
      completeLogin(result);
      return;
    }
    setChallenge(result);
    setCode('');
    setChallengeError('');
    setChallengeMessage('');
  };

  const handleLoginSubmit = async ({ email, password }: AuthFormSubmission) => {
    setIsRegistrationChallenge(false);
    setErrorMsg("");
    setIsSubmitting(true);
    try {
      const result = await authService.login({ email, password });
      handleAuthResult(result);
    } catch (err: unknown) {
      setErrorMsg(responseMessage(err, "Erro ao fazer login. Verifique suas credenciais."));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRegisterSubmit = async (formData: AuthFormSubmission) => {
    setErrorMsg("");
    setIsRegistrationChallenge(true);
    setIsSubmitting(true);
    try {
      const result = await authService.register(formData);
      handleAuthResult(result);
    } catch (err: unknown) {
      setIsRegistrationChallenge(false);
      setErrorMsg(responseMessage(err, "Erro ao cadastrar."));
    } finally {
      setIsSubmitting(false);
    }
  };

  const verifyChallenge = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!challenge) return;
    setIsChallengeLoading(true);
    setChallengeError('');
    try {
      const result = challenge.status === 'MFA_REQUIRED'
        ? await authService.verifyMfa({ challengeToken: challenge.challengeToken!, code })
        : await authService.verifyEmail({ email: challenge.email, code });
      if (challenge.status === 'EMAIL_VERIFICATION_REQUIRED' && isRegistrationChallenge) {
        login(result.token, result.user);
        setChallenge(null);
        setShowMfaOffer(true);
      } else {
        completeLogin(result);
      }
    } catch (error: unknown) {
      setChallengeError(responseMessage(error, 'Não foi possível validar o código. Confira e tente novamente.'));
    } finally {
      setIsChallengeLoading(false);
    }
  };

  const enableMfaAfterSignup = async () => {
    setIsSavingMfa(true);
    setMfaOfferError('');
    try {
      await accountService.setEmailMfaEnabled(true);
      toast.success('Autenticação em duas etapas ativada.');
      router.push(nextPath);
    } catch (error: unknown) {
      setMfaOfferError(responseMessage(error, 'Não foi possível ativar agora. Você pode configurar isso na sua conta.'));
    } finally {
      setIsSavingMfa(false);
    }
  };

  const resendChallenge = async () => {
    if (!challenge) return;
    setIsChallengeLoading(true);
    setChallengeError('');
    setChallengeMessage('');
    try {
      if (challenge.status === 'MFA_REQUIRED') {
        await authService.resendMfa({ challengeToken: challenge.challengeToken! });
      } else {
        await authService.resendVerification({ email: challenge.email });
      }
      setChallengeMessage('Se a solicitação for válida, um novo código foi enviado.');
    } catch (error: unknown) {
      setChallengeError(responseMessage(error, 'Não foi possível enviar outro código agora.'));
    } finally {
      setIsChallengeLoading(false);
    }
  };

  return (
    <Container>
      <Login>
        <Logo />
        {showMfaOffer ? (
          <section className="w-full max-w-sm" aria-labelledby="mfa-offer-title">
            <h1 id="mfa-offer-title" className="mb-2 text-center text-2xl font-bold text-gray-900">Proteja seu acesso</h1>
            <p className="mb-6 text-center text-sm text-gray-600">Ative um código por e-mail sempre que entrar. Você também poderá alterar esta opção em Minha conta.</p>
            {mfaOfferError && <p role="alert" className="mb-4 text-sm text-red-700">{mfaOfferError}</p>}
            <button type="button" onClick={enableMfaAfterSignup} disabled={isSavingMfa} className="w-full rounded-md bg-red-600 px-4 py-3 font-semibold text-white disabled:opacity-60">
              {isSavingMfa ? 'Ativando...' : 'Ativar código por e-mail'}
            </button>
            <button type="button" onClick={() => router.push(nextPath)} disabled={isSavingMfa} className="mt-3 w-full px-4 py-3 text-sm font-semibold text-gray-600">
              Agora não
            </button>
          </section>
        ) : challenge ? (
          <section className="w-full max-w-sm" aria-labelledby="challenge-title">
            <h1 id="challenge-title" className="mb-2 text-center text-2xl font-bold text-gray-900">
              {challenge.status === 'MFA_REQUIRED' ? 'Confirme seu acesso' : 'Confirme seu e-mail'}
            </h1>
            <p className="mb-6 text-center text-sm text-gray-600">
              Enviamos um código de 6 dígitos para <strong>{challenge.email}</strong>. Ele expira em 10 minutos.
            </p>
            <form onSubmit={verifyChallenge} className="space-y-4">
              <label htmlFor="email-code" className="block text-sm font-semibold text-gray-800">Código de segurança</label>
              <input
                id="email-code"
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                pattern="[0-9]{6}"
                maxLength={6}
                value={code}
                onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="000000"
                required
                autoFocus
                className="w-full rounded-md border border-gray-300 bg-white px-4 py-3 text-center text-2xl tracking-[0.35em] text-gray-900 outline-none focus:border-red-600 focus:ring-2 focus:ring-red-100"
              />
              {challengeError && <p role="alert" className="text-sm text-red-700">{challengeError}</p>}
              {challengeMessage && <p role="status" className="text-sm text-green-700">{challengeMessage}</p>}
              <button type="submit" disabled={isChallengeLoading || code.length !== 6} className="w-full rounded-md bg-red-600 px-4 py-3 font-semibold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-60">
                {isChallengeLoading ? 'Validando...' : 'Confirmar código'}
              </button>
            </form>
            <button type="button" onClick={resendChallenge} disabled={isChallengeLoading} className="mt-4 w-full text-sm font-semibold text-red-700 disabled:opacity-50">
              Enviar outro código
            </button>
            <button type="button" onClick={() => { setChallenge(null); setCode(''); }} className="mt-3 w-full text-sm text-gray-600 hover:text-gray-900">
              Voltar
            </button>
          </section>
        ) : <>
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
              isSubmitting={isSubmitting}
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
              isSubmitting={isSubmitting}
            />
            <p className="mt-4 text-gray-400">
              Já tem conta? <OnClickText onClick={() => setPagetype("login")}>Entrar</OnClickText>
            </p>
          </>
        )}
        </>}
      </Login>
    </Container>
  );
}

export default Auth;
