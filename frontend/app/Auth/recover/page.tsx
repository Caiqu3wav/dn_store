'use client';

import { useRouter } from 'next/navigation';

export default function RecoverPasswordPage() {
    const router = useRouter();

    const handleSendLink = (e: React.FormEvent) => {
        e.preventDefault();

        // Por enquanto, apenas vai para a página de redefinição.
        // Futuramente o backend enviará o link por e-mail.
        router.push('/Auth/recover/reset');
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
                    style={{
                        color: '#444444',
                        marginBottom: 20,
                    }}
                >
                    Informe seu e-mail para receber um link de redefinição.
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
                        type="email"
                        placeholder="user@example.com"
                        required
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

                    <button
                        type="submit"
                        style={{
                            marginTop: 16,
                            width: '100%',
                            padding: 12,
                            border: 'none',
                            borderRadius: 6,
                            background: '#ff0000',
                            color: '#ffffff',
                            cursor: 'pointer',
                            fontSize: 16,
                            fontWeight: 700,
                        }}
                    >
                        Enviar link
                    </button>

                    <button
                        type="button"
                        onClick={() => router.push('/Auth')}
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