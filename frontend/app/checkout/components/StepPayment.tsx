'use client';

import { useState } from 'react';
import { CreditCard, QrCode, FileText, Copy, Check, Loader2 } from 'lucide-react';
import Image from 'next/image';

export type PaymentMethod = 'CREDIT_CARD' | 'DEBIT_CARD' | 'PIX' | 'BOLETO';

export interface CardData {
    holderName: string;
    number: string;
    expiryMonth: string;
    expiryYear: string;
    ccv: string;
}

export interface PaymentData {
    method: PaymentMethod;
    cpfCnpj: string;
    installments: number;
    card: CardData;
}

interface PaymentResult {
    status: string;
    pixQrCode?: string;
    pixCopyPaste?: string;
    boletoUrl?: string;
    boletoBarcode?: string;
}

interface Props {
    data: PaymentData;
    onChange: (data: PaymentData) => void;
    total: number;
    onConfirm: () => Promise<PaymentResult | null>;
    onNext: (result: PaymentResult) => void;
    onBack: () => void;
}

const METHODS: { id: PaymentMethod; label: string; icon: React.ReactNode }[] = [
    { id: 'CREDIT_CARD', label: 'Cartão de Crédito', icon: <CreditCard className="w-5 h-5" /> },
    { id: 'DEBIT_CARD',  label: 'Cartão de Débito',  icon: <CreditCard className="w-5 h-5" /> },
    { id: 'PIX',         label: 'Pix',               icon: <QrCode className="w-5 h-5" /> },
    { id: 'BOLETO',      label: 'Boleto',             icon: <FileText className="w-5 h-5" /> },
];

function formatCardNumber(value: string) {
    return value.replace(/\D/g, '').slice(0, 16).replace(/(.{4})/g, '$1 ').trim();
}

function formatCpf(value: string) {
    return value.replace(/\D/g, '').slice(0, 11)
        .replace(/(\d{3})(\d)/, '$1.$2')
        .replace(/(\d{3})(\d)/, '$1.$2')
        .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
}

export function StepPayment({ data, onChange, total, onConfirm, onNext, onBack }: Props) {
    const [loading, setLoading] = useState(false);
    const [copied, setCopied] = useState(false);
    const [error, setError] = useState('');

    const set = (field: keyof PaymentData, value: unknown) =>
        onChange({ ...data, [field]: value });

    const setCard = (field: keyof CardData, value: string) =>
        onChange({ ...data, card: { ...data.card, [field]: value } });

    const isCard = data.method === 'CREDIT_CARD' || data.method === 'DEBIT_CARD';

    const cardValid = !isCard || (
        data.card.holderName && data.card.number.replace(/\s/g, '').length === 16 &&
        data.card.expiryMonth && data.card.expiryYear && data.card.ccv.length >= 3
    );
    const isValid = data.cpfCnpj.replace(/\D/g, '').length >= 11 && cardValid;

    const handleConfirm = async () => {
        setError('');
        setLoading(true);
        try {
            const result = await onConfirm();
            if (result) onNext(result);
            else setError('Não foi possível processar o pagamento. Tente novamente.');
        } catch {
            setError('Erro ao conectar com o servidor. Tente novamente.');
        } finally {
            setLoading(false);
        }
    };

    const copyToClipboard = (text: string) => {
        navigator.clipboard.writeText(text);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    const installmentOptions = data.method === 'CREDIT_CARD'
        ? [1, 2, 3, 4].map(n => ({
            value: n,
            label: n === 1
                ? `1x de ${fmt(total)} (sem juros)`
                : `${n}x de ${fmt(total / n)} (sem juros)`
        }))
        : [];

    return (
        <div className="space-y-6">
            <div className="flex items-center gap-3 mb-2">
                <div className="w-8 h-8 rounded-full bg-brand-primary text-white flex items-center justify-center text-sm font-bold">2</div>
                <h2 className="text-xl font-bold text-brand-primary">Pagamento</h2>
            </div>

            {/* CPF/CNPJ — sempre visível */}
            <div>
                <label className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">CPF / CNPJ *</label>
                <input
                    type="text"
                    value={data.cpfCnpj}
                    onChange={e => set('cpfCnpj', formatCpf(e.target.value))}
                    placeholder="000.000.000-00"
                    className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-brand-secondary transition"
                />
            </div>

            {/* Seleção do método */}
            <div className="grid grid-cols-2 gap-3">
                {METHODS.map(m => (
                    <button
                        key={m.id}
                        onClick={() => set('method', m.id)}
                        className={`flex items-center gap-3 p-4 rounded-xl border-2 text-sm font-semibold transition-all ${
                            data.method === m.id
                                ? 'border-brand-secondary bg-brand-secondary/5 text-brand-secondary'
                                : 'border-gray-200 text-gray-600 hover:border-gray-300'
                        }`}
                    >
                        {m.icon}
                        {m.label}
                    </button>
                ))}
            </div>

            {/* Formulário de cartão */}
            {isCard && (
                <div className="space-y-4 p-5 bg-gray-50 rounded-2xl border border-gray-100">
                    <div>
                        <label className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Nome no Cartão *</label>
                        <input
                            type="text"
                            value={data.card.holderName}
                            onChange={e => setCard('holderName', e.target.value.toUpperCase())}
                            placeholder="NOME COMO NO CARTÃO"
                            className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-brand-secondary transition bg-white"
                        />
                    </div>
                    <div>
                        <label className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Número do Cartão *</label>
                        <input
                            type="text"
                            value={data.card.number}
                            onChange={e => setCard('number', formatCardNumber(e.target.value))}
                            placeholder="0000 0000 0000 0000"
                            className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-brand-secondary transition bg-white"
                        />
                    </div>
                    <div className="grid grid-cols-3 gap-3">
                        <div>
                            <label className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Mês *</label>
                            <select
                                value={data.card.expiryMonth}
                                onChange={e => setCard('expiryMonth', e.target.value)}
                                className="w-full border border-gray-200 rounded-xl px-3 py-3 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-brand-secondary transition"
                            >
                                <option value="">MM</option>
                                {Array.from({ length: 12 }, (_, i) => {
                                    const m = String(i + 1).padStart(2, '0');
                                    return <option key={m} value={m}>{m}</option>;
                                })}
                            </select>
                        </div>
                        <div>
                            <label className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Ano *</label>
                            <select
                                value={data.card.expiryYear}
                                onChange={e => setCard('expiryYear', e.target.value)}
                                className="w-full border border-gray-200 rounded-xl px-3 py-3 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-brand-secondary transition"
                            >
                                <option value="">AAAA</option>
                                {Array.from({ length: 10 }, (_, i) => {
                                    const y = String(new Date().getFullYear() + i);
                                    return <option key={y} value={y}>{y}</option>;
                                })}
                            </select>
                        </div>
                        <div>
                            <label className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">CVV *</label>
                            <input
                                type="text"
                                value={data.card.ccv}
                                onChange={e => setCard('ccv', e.target.value.replace(/\D/g, '').slice(0, 4))}
                                placeholder="000"
                                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-brand-secondary transition bg-white"
                            />
                        </div>
                    </div>

                    {/* Parcelas — só crédito */}
                    {data.method === 'CREDIT_CARD' && (
                        <div>
                            <label className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Parcelas *</label>
                            <select
                                value={data.installments}
                                onChange={e => set('installments', Number(e.target.value))}
                                className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-brand-secondary transition"
                            >
                                {installmentOptions.map(o => (
                                    <option key={o.value} value={o.value}>{o.label}</option>
                                ))}
                            </select>
                        </div>
                    )}
                </div>
            )}

            {/* Info Pix */}
            {data.method === 'PIX' && (
                <div className="p-5 bg-green-50 rounded-2xl border border-green-100 text-sm text-green-800 space-y-1">
                    <p className="font-semibold">Pagamento instantâneo via Pix</p>
                    <p className="text-green-600">O QR code será gerado após confirmar. O pedido é aprovado em segundos.</p>
                </div>
            )}

            {/* Info Boleto */}
            {data.method === 'BOLETO' && (
                <div className="p-5 bg-blue-50 rounded-2xl border border-blue-100 text-sm text-blue-800 space-y-1">
                    <p className="font-semibold">Boleto Bancário</p>
                    <p className="text-blue-600">Vencimento em 1 dia útil. A aprovação ocorre em até 3 dias após o pagamento.</p>
                </div>
            )}

            {error && (
                <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-xl px-4 py-3">{error}</p>
            )}

            <div className="flex gap-3">
                <button
                    onClick={onBack}
                    className="flex-1 border-2 border-gray-200 text-gray-600 font-bold py-4 rounded-xl hover:border-gray-300 transition"
                >
                    ← Voltar
                </button>
                <button
                    onClick={handleConfirm}
                    disabled={!isValid || loading}
                    className="flex-[2] bg-brand-primary text-white font-bold py-4 rounded-xl hover:bg-brand-secondary transition-colors disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                >
                    {loading ? <><Loader2 className="w-4 h-4 animate-spin" /> Processando...</> : 'Confirmar Pagamento'}
                </button>
            </div>
        </div>
    );
}

function fmt(value: number) {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
}
