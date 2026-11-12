'use client';

import Link from 'next/link';
import { useState } from 'react';
import { authService } from '@/services/authService';
import api from '@/lib/axios';

type Step = 'form' | 'sent';

export default function RecoverPasswordPage() {
    const [email, setEmail] = useState('');
    const [step, setStep] = useState<Step>('form');
    const [isLoading, setIsLoading] = useState(false);
    const [isCheckingEmail, setIsCheckingEmail] = useState(false);
    const [emailStatus, setEmailStatus] = useState<'idle' | 'found' | 'not_found'>('idle');
    const [error, setError] = useState('');

    const checkEmailExists = async (value: string) => {
        const trimmed = value.trim();
        if (!trimmed || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
            setEmailStatus('idle');
            return;
        }
        setIsCheckingEmail(true);
        try {
            const res = await api.get<{ exists: boolean }>(`/auth/check-email?email=${encodeURIComponent(trimmed)}`);
            setEmailStatus(res.data.exists ? 'found' : 'not_found');
        } catch {
            setEmailStatus('idle');
        } finally {
            setIsCheckingEmail(false);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (emailStatus === 'not_found') return;
        setError('');
        setIsLoading(true);
        try {
            await authService.forgotPassword({ email: email.trim() });
            setStep('sent');
        } catch {
            setError('Não foi possível solicitar a redefinição agora. Tente novamente.');
        } finally {
            setIsLoading(false);
        }
    };

    const inputBorder =
        emailStatus === 'found' ? 'border-emerald-500 focus:ring-emerald-200' :
        emailStatus === 'not_found' ? 'border-red-400 focus:ring-red-100' :
        'border-gray-300 focus:ring-brand-secondary/20 focus:border-brand-secondary';

    return (
        <div className="min-h-screen flex items-center justify-center bg-white px-4 py-12">
            <div className="w-full max-w-md">
                <div className="mb-8 text-center">
                    <h1 className="text-3xl font-bold text-gray-900 mb-2">Esqueci minha senha</h1>
                    <p className="text-gray-500 text-sm">
                        {step === 'sent'
                            ? 'Verifique sua caixa de entrada.'
                            : 'Informe seu e-mail para receber o link de redefinição.'}
                    </p>
                </div>

                <div className="bg-gray-50 rounded-2xl border border-gray-200 shadow-sm p-8">
                    {step === 'sent' ? (
                        <div className="text-center space-y-4">
                            <div className="w-14 h-14 rounded-full bg-emerald-100 flex items-center justify-center mx-auto">
                                <svg className="w-7 h-7 text-emerald-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                                </svg>
                            </div>
                            <p className="text-gray-700 text-sm leading-relaxed">
                                Se houver uma conta associada a <strong>{email}</strong>, você receberá um link para redefinir sua senha em breve.
                            </p>
                            <p className="text-xs text-gray-400">Não recebeu? Verifique a pasta de spam.</p>
                            <Link href="/auth" className="block mt-4 text-sm font-semibold text-brand-secondary hover:underline">
                                Voltar para o login
                            </Link>
                        </div>
                    ) : (
                        <form onSubmit={handleSubmit} className="space-y-5">
                            <div className="space-y-1.5">
                                <label htmlFor="recover-email" className="block text-sm font-semibold text-gray-800">
                                    E-mail
                                </label>
                                <div className="relative">
                                    <input
                                        id="recover-email"
                                        type="email"
                                        placeholder="seu@email.com"
                                        value={email}
                                        onChange={e => { setEmail(e.target.value); setEmailStatus('idle'); }}
                                        onBlur={e => checkEmailExists(e.target.value)}
                                        required
                                        disabled={isLoading}
                                        className={`w-full rounded-lg border bg-white px-4 py-3 text-sm text-gray-900 outline-none transition focus:ring-2 pr-10 ${inputBorder}`}
                                    />
                                    <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm">
                                        {isCheckingEmail && (
                                            <svg className="w-4 h-4 animate-spin text-gray-400" fill="none" viewBox="0 0 24 24">
                                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
                                            </svg>
                                        )}
                                        {!isCheckingEmail && emailStatus === 'found' && (
                                            <svg className="w-4 h-4 text-emerald-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                                                <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                                            </svg>
                                        )}
                                        {!isCheckingEmail && emailStatus === 'not_found' && (
                                            <svg className="w-4 h-4 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                                                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                                            </svg>
                                        )}
                                    </span>
                                </div>
                                {emailStatus === 'found' && (
                                    <p className="text-xs text-emerald-600">Conta encontrada.</p>
                                )}
                                {emailStatus === 'not_found' && (
                                    <p className="text-xs text-red-500">Nenhuma conta encontrada com este e-mail.</p>
                                )}
                            </div>

                            {error && <p role="alert" className="text-sm text-red-600">{error}</p>}

                            <button
                                type="submit"
                                disabled={isLoading || emailStatus === 'not_found' || isCheckingEmail}
                                className="w-full rounded-lg bg-brand-secondary py-3 text-sm font-semibold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                {isLoading ? 'Enviando...' : 'Enviar link de redefinição'}
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
