'use client';

import { Suspense, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { authService } from '@/services/authService';

function ResetPasswordForm() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const token = searchParams.get('token') || '';

    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [error, setError] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [isComplete, setIsComplete] = useState(false);

    const handleSubmit = async (e: React.FormEvent) => {
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
        <div className="min-h-screen flex items-center justify-center bg-white px-4 py-12">
            <div className="w-full max-w-md">
                <div className="mb-8 text-center">
                    <h1 className="text-3xl font-bold text-gray-900 mb-2">
                        {isComplete ? 'Senha atualizada' : 'Criar nova senha'}
                    </h1>
                    <p className="text-gray-500 text-sm">
                        {isComplete
                            ? 'Sua senha foi atualizada com sucesso.'
                            : 'Crie uma senha segura para sua conta.'}
                    </p>
                </div>

                <div className="bg-gray-50 rounded-2xl border border-gray-200 shadow-sm p-8">
                    {isComplete ? (
                        <div className="text-center space-y-4">
                            <div className="w-14 h-14 rounded-full bg-emerald-100 flex items-center justify-center mx-auto">
                                <svg className="w-7 h-7 text-emerald-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                                </svg>
                            </div>
                            <p className="text-gray-700 text-sm">Agora você pode entrar com a nova senha.</p>
                            <button
                                type="button"
                                onClick={() => router.push('/auth')}
                                className="w-full rounded-lg bg-brand-secondary py-3 text-sm font-semibold text-white transition hover:bg-red-700"
                            >
                                Ir para o login
                            </button>
                        </div>
                    ) : (
                        <form onSubmit={handleSubmit} className="space-y-5">
                            {!token && (
                                <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
                                    Link inválido. <Link href="/auth/recover" className="font-semibold underline">Solicite um novo link.</Link>
                                </div>
                            )}

                            <div className="space-y-1.5">
                                <label htmlFor="new-password" className="block text-sm font-semibold text-gray-800">
                                    Nova senha
                                </label>
                                <input
                                    id="new-password"
                                    type="password"
                                    placeholder="Mínimo 8 caracteres"
                                    value={password}
                                    onChange={e => setPassword(e.target.value)}
                                    required
                                    minLength={8}
                                    maxLength={128}
                                    disabled={isLoading || !token}
                                    className="w-full rounded-lg border border-gray-300 bg-white px-4 py-3 text-sm text-gray-900 outline-none transition focus:border-brand-secondary focus:ring-2 focus:ring-brand-secondary/20"
                                />
                            </div>

                            <div className="space-y-1.5">
                                <label htmlFor="confirm-password" className="block text-sm font-semibold text-gray-800">
                                    Confirmar senha
                                </label>
                                <input
                                    id="confirm-password"
                                    type="password"
                                    placeholder="Repita a senha"
                                    value={confirmPassword}
                                    onChange={e => setConfirmPassword(e.target.value)}
                                    required
                                    minLength={8}
                                    maxLength={128}
                                    disabled={isLoading || !token}
                                    className="w-full rounded-lg border border-gray-300 bg-white px-4 py-3 text-sm text-gray-900 outline-none transition focus:border-brand-secondary focus:ring-2 focus:ring-brand-secondary/20"
                                />
                            </div>

                            {error && <p role="alert" className="text-sm text-red-600">{error}</p>}

                            <button
                                type="submit"
                                disabled={isLoading || !token}
                                className="w-full rounded-lg bg-brand-secondary py-3 text-sm font-semibold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                {isLoading ? 'Salvando...' : 'Redefinir senha'}
                            </button>

                            <Link href="/auth" className="block text-center text-sm text-gray-500 hover:text-gray-800 transition">
                                Voltar para o login
                            </Link>
                        </form>
                    )}
                </div>
            </div>
        </div>
    );
}

export default function ResetPasswordPage() {
    return (
        <Suspense fallback={<div className="min-h-screen" />}>
            <ResetPasswordForm />
        </Suspense>
    );
}
