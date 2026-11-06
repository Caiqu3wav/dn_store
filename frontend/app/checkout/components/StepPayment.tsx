'use client';

import { useState } from 'react';
import { FileText, Loader2, QrCode } from 'lucide-react';

export type PaymentMethod = 'PIX' | 'BOLETO';

export interface PaymentData {
    method: PaymentMethod;
    cpfCnpj: string;
    idempotencyKey: string;
}

interface PaymentResult {
    status: string;
    paymentMethod: PaymentMethod;
    pixQrCode?: string;
    pixCopyPaste?: string;
    boletoUrl?: string;
    boletoBarcode?: string;
}

interface Props {
    data: PaymentData;
    onChange: (data: PaymentData) => void;
    onConfirm: (idempotencyKey: string) => Promise<PaymentResult | null>;
    onNext: (result: PaymentResult) => void;
    onBack: () => void;
}

const METHODS: { id: PaymentMethod; label: string; icon: React.ReactNode }[] = [
    { id: 'PIX', label: 'Pix', icon: <QrCode className="w-5 h-5" /> },
    { id: 'BOLETO', label: 'Boleto', icon: <FileText className="w-5 h-5" /> },
];

function formatDocument(value: string) {
    const digits = value.replace(/\D/g, '').slice(0, 14);
    if (digits.length <= 11) {
        return digits
            .replace(/(\d{3})(\d)/, '$1.$2')
            .replace(/(\d{3})(\d)/, '$1.$2')
            .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
    }
    return digits
        .replace(/^(\d{2})(\d)/, '$1.$2')
        .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
        .replace(/\.(\d{3})(\d)/, '.$1/$2')
        .replace(/(\d{4})(\d)/, '$1-$2');
}

export function StepPayment({ data, onChange, onConfirm, onNext, onBack }: Props) {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const isValid = data.cpfCnpj.replace(/\D/g, '').length === 11
        || data.cpfCnpj.replace(/\D/g, '').length === 14;

    const handleConfirm = async () => {
        setError('');
        setLoading(true);
        const idempotencyKey = data.idempotencyKey || crypto.randomUUID();
        if (idempotencyKey !== data.idempotencyKey) {
            onChange({ ...data, idempotencyKey });
        }
        try {
            const result = await onConfirm(idempotencyKey);
            if (result) onNext(result);
            else setError('Não foi possível processar o pagamento. Tente novamente.');
        } catch (exception) {
            setError(exception instanceof Error
                ? exception.message
                : 'Erro ao conectar com o servidor. Tente novamente.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="space-y-6">
            <div className="flex items-center gap-3 mb-2">
                <div className="w-8 h-8 rounded-full bg-brand-primary text-white flex items-center justify-center text-sm font-bold">2</div>
                <h2 className="text-xl font-bold text-brand-primary">Pagamento</h2>
            </div>

            <div>
                <label className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">CPF / CNPJ *</label>
                <input
                    type="text"
                    value={data.cpfCnpj}
                    onChange={event => onChange({
                        ...data,
                        cpfCnpj: formatDocument(event.target.value),
                        idempotencyKey: '',
                    })}
                    placeholder="000.000.000-00"
                    autoComplete="off"
                    className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-brand-secondary transition"
                />
            </div>

            <div className="grid grid-cols-2 gap-3">
                {METHODS.map(method => (
                    <button
                        key={method.id}
                        type="button"
                        onClick={() => onChange({
                            ...data,
                            method: method.id,
                            idempotencyKey: '',
                        })}
                        className={`flex items-center gap-3 p-4 rounded-xl border-2 text-sm font-semibold transition-all ${
                            data.method === method.id
                                ? 'border-brand-secondary bg-brand-secondary/5 text-brand-secondary'
                                : 'border-gray-200 text-gray-600 hover:border-gray-300'
                        }`}
                    >
                        {method.icon}
                        {method.label}
                    </button>
                ))}
            </div>

            {data.method === 'PIX' ? (
                <div className="p-5 bg-green-50 rounded-2xl border border-green-100 text-sm text-green-800 space-y-1">
                    <p className="font-semibold">Pagamento instantâneo via Pix</p>
                    <p className="text-green-600">O QR code será exibido após confirmar.</p>
                </div>
            ) : (
                <div className="p-5 bg-blue-50 rounded-2xl border border-blue-100 text-sm text-blue-800 space-y-1">
                    <p className="font-semibold">Boleto bancário</p>
                    <p className="text-blue-600">A aprovação depende da confirmação do pagamento pelo banco.</p>
                </div>
            )}

            {error && (
                <p role="alert" className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-xl px-4 py-3">
                    {error}
                </p>
            )}

            <div className="flex gap-3">
                <button
                    type="button"
                    onClick={onBack}
                    className="flex-1 border-2 border-gray-200 text-gray-600 font-bold py-4 rounded-xl hover:border-gray-300 transition"
                >
                    ← Voltar
                </button>
                <button
                    type="button"
                    onClick={handleConfirm}
                    disabled={!isValid || loading}
                    className="flex-[2] bg-brand-secondary text-white font-bold py-4 rounded-xl hover:bg-brand-primary hover:text-gray-600 transition-colors disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                >
                    {loading ? <><Loader2 className="w-4 h-4 animate-spin" /> Processando...</> : 'Confirmar Pagamento'}
                </button>
            </div>
        </div>
    );
}
