'use client';

import { useState } from 'react';
import { authService } from '@/services/authService';
import { getApiErrorMessage } from '@/utils/apiError';

export default function RecoverPasswordPage() {
    const [email, setEmail] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [isSent, setIsSent] = useState(false);
    const [error, setError] = useState('');

    const handleSendLink = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setIsLoading(true);
        try {
            await authService.forgotPassword({ email });
            setIsSent(true);
        } catch (err: unknown) {
            setError(getApiErrorMessage(err, 'Não foi possível solicitar a redefinição agora. Tente novamente.', {
                400: 'Informe um e-mail válido.',
                429: 'Você já solicitou um link há pouco. Aguarde alguns minutos para solicitar outro.',
                502: 'Não foi possível enviar o e-mail agora. Tente novamente em instantes.',
                503: 'O envio de e-mails está indisponível no momento. Tente novamente mais tarde.',
            }));
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div
            style={{
                minHeight: '100vh',
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                padding: 20,
                backgroundColor: '#ffffff',
                color: '#111111',
            }}
        >
            <div style={{ width: '100%', maxWidth: 520 }}>
                <h1
                    style={{
                        fontSize: 28,
                        fontWeight: 700,
                        marginBottom: 12,
                        color: '#111111',
                    }}
                >
                    Esqueci minha senha
                </h1>

                <p
                    role={isSent ? 'status' : undefined}
                    style={{
                        color: '#444444',
                        marginBottom: 20,
                    }}
                >
                    {isSent
                        ? 'Se houver uma conta associada a este e-mail, você receberá um link para redefinir sua senha.'
                        : 'Informe seu e-mail e enviaremos um link seguro para redefinir sua senha.'}
                </p>

                <form
                    onSubmit={handleSendLink}
                    style={{
                        background: '#f7f7f7',
                        padding: 20,
                        borderRadius: 10,
                        boxShadow: '0 6px 20px rgba(0,0,0,0.10)',
                    }}
                >
                    <label
                        htmlFor="recover-email"
                        style={{
                            display: 'block',
                            fontWeight: 700,
                            marginBottom: 8,
                            color: '#111111',
                        }}
                    >
                        Email
                    </label>

                    <input
                        id="recover-email"
                        type="email"
                        autoComplete="email"
                        placeholder="user@example.com"
                        value={email}
                        onChange={(event) => setEmail(event.target.value)}
                        required
                        disabled={isLoading || isSent}
                        style={{
                            width: '100%',
                            padding: 12,
                            borderRadius: 6,
                            border: '1px solid #ccc',
                            outline: 'none',
                            boxSizing: 'border-box',
                            backgroundColor: '#ffffff',
                            color: '#111111',
                        }}
                    />

                    {error && <p role="alert" style={{ color: '#b91c1c', marginTop: 12 }}>{error}</p>}

                    <button
                        type="submit"
                        disabled={isLoading || isSent}
                        style={{
                            marginTop: 16,
                            width: '100%',
                            padding: 12,
                            border: 'none',
                            borderRadius: 6,
                            background: isSent ? '#166534' : '#ff0000',
                            color: '#ffffff',
                            cursor: 'pointer',
                            fontSize: 16,
                            fontWeight: 700,
                        }}
                    >
                        {isLoading ? 'Enviando...' : isSent ? 'Solicitação enviada' : 'Enviar link'}
                    </button>

                    {isSent && (
                        <button
                            type="button"
                            onClick={() => { setIsSent(false); setError(''); }}
                            style={{
                                display: 'block',
                                margin: '12px auto 0',
                                border: 'none',
                                background: 'transparent',
                                color: '#0070f3',
                                cursor: 'pointer',
                                fontSize: 14,
                            }}
                        >
                            Não recebeu? Enviar novamente ou usar outro e-mail
                        </button>
                    )}

                    <button
                        type="button"
                        onClick={() => window.location.assign('/auth')}
                        style={{
                            display: 'block',
                            margin: '16px auto 0',
                            border: 'none',
                            background: 'transparent',
                            color: '#0070f3',
                            cursor: 'pointer',
                            fontSize: 14,
                        }}
                    >
                        Voltar
                    </button>
                </form>
            </div>
        </div>
    );
}