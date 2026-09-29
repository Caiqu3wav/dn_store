'use client';

import { useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { authService } from '@/services/authService';

export default function ResetPasswordPage() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const token = searchParams.get('token') || '';

    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [error, setError] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [isComplete, setIsComplete] = useState(false);

    const handleResetPassword = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        if (!token) {
            setError('Este link de redefinição é inválido. Solicite um novo link.');
            return;
        }

        if (password !== confirmPassword) {
            setError('As senhas não coincidem.');
            return;
        }

        if (password.length < 8 || !/[A-Za-z]/.test(password) || !/\d/.test(password)) {
            setError('Use pelo menos 8 caracteres, incluindo uma letra e um número.');
            return;
        }

        setIsLoading(true);
        try {
            await authService.resetPassword({ token, newPassword: password });
            setIsComplete(true);
        } catch {
            setError('Este link é inválido ou expirou. Solicite uma nova redefinição.');
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
                color: '#000000',
            }}
        >
            <div
                style={{
                    width: '100%',
                    maxWidth: 520,
                }}
            >
                <h1
                    style={{
                        fontSize: 28,
                        fontWeight: 700,
                        marginBottom: 12,
                        textAlign: 'center',
                        color: '#111111'
                    }}
                >
                    Criar nova senha
                </h1>

                <p
                    style={{
                        color: '#444444',
                        marginBottom: 20,
                         textAlign: 'center',
                    }}
                >
                    {isComplete ? 'Sua senha foi atualizada. Agora você pode entrar com a nova senha.' : 'Crie uma senha segura para sua conta.'}
                </p>

                <form
                    onSubmit={handleResetPassword}
                    style={{
                        backgroundColor: '#f7f7f7',
                        padding: 20,
                        borderRadius: 10,
                        boxShadow: '0 6px 20px rgba(0,0,0,0.10)',
                    }}
                >
                    <label
                        style={{
                            display: 'block',
                            fontWeight: 700,
                            marginBottom: 8,
                            color: '#000000',
                        }}
                    >
                        Nova senha
                    </label>

                    <input
                        type="password"
                        placeholder="Digite sua nova senha"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                        minLength={8}
                        maxLength={128}
                        disabled={isLoading || isComplete || !token}
                        style={{
                            width: '100%',
                            padding: 12,
                            borderRadius: 6,
                            border: '1px solid #cccccc',
                            outline: 'none',
                            boxSizing: 'border-box',
                            marginBottom: 16,
                            backgroundColor: '#ffffff',
                            color: '#000000',
                        }}
                    />

                    <label
                        style={{
                            display: 'block',
                            fontWeight: 700,
                            marginBottom: 8,
                            color: '#000000',
                        }}
                    >
                        Confirmar senha
                    </label>

                    <input
                        type="password"
                        placeholder="Digite a senha novamente"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        required
                        minLength={8}
                        maxLength={128}
                        disabled={isLoading || isComplete || !token}
                        style={{
                            width: '100%',
                            padding: 12,
                            borderRadius: 6,
                            border: '1px solid #cccccc',
                            outline: 'none',
                            boxSizing: 'border-box',
                            backgroundColor: '#ffffff',
                            color: '#000000',
                        }}
                    />

                    {error && (
                        <p
                            style={{
                                color: '#ff0000',
                                marginTop: 12,
                                marginBottom: 0,
                                fontSize: 14,
                            }}
                        >
                            {error}
                        </p>
                    )}

                    <button
                        type="submit"
                        disabled={isLoading || isComplete || !token}
                        style={{
                            marginTop: 16,
                            width: '100%',
                            padding: 12,
                            border: 'none',
                            borderRadius: 6,
                            backgroundColor: isComplete ? '#166534' : '#ff0000',
                            color: '#ffffff',
                            cursor: 'pointer',
                            fontSize: 16,
                        }}
                    >
                        {isLoading ? 'Salvando...' : isComplete ? 'Senha atualizada' : 'Redefinir senha'}
                    </button>

                    <button
                        type="button"
                        onClick={() => router.push('/auth')}
                        style={{
                            display: 'block',
                            margin: '16px auto 0',
                            border: 'none',
                            backgroundColor: 'transparent',
                            color: '#0070f3',
                            cursor: 'pointer',
                            fontSize: 14,
                        }}
                    >
                        Voltar para o login
                    </button>
                </form>
            </div>
        </div>
    );
}