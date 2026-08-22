'use client';

import { useEffect, useState } from 'react';
import { CheckCircle2, Clock, XCircle, Copy, Check, ExternalLink, Loader2 } from 'lucide-react';
import Image from 'next/image';
import Link from 'next/link';

interface PaymentResult {
    status: string;
    pixQrCode?: string;
    pixCopyPaste?: string;
    boletoUrl?: string;
    boletoBarcode?: string;
}

interface Props {
    orderId: string;
    paymentMethod: string;
    result: PaymentResult;
}

export function StepConfirmation({ orderId, paymentMethod, result }: Props) {
    const [status, setStatus] = useState(result.status);
    const [copied, setCopied] = useState(false);

    // Polling para Pix e Boleto — verifica status a cada 5s
    useEffect(() => {
        if (status === 'PAID' || paymentMethod === 'CREDIT_CARD' || paymentMethod === 'DEBIT_CARD') return;

        const interval = setInterval(async () => {
            try {
                const res = await fetch(`/api/payment/order/${orderId}`, {
                    headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
                });
                if (res.ok) {
                    const data = await res.json();
                    setStatus(data.status);
                    if (data.status === 'PAID') clearInterval(interval);
                }
            } catch {
                // silently ignore polling errors
            }
        }, 5000);

        return () => clearInterval(interval);
    }, [orderId, paymentMethod, status]);

    const copy = (text: string) => {
        navigator.clipboard.writeText(text);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    if (status === 'PAID') {
        return (
            <div className="text-center space-y-4 py-8">
                <CheckCircle2 className="w-16 h-16 text-green-500 mx-auto" />
                <h2 className="text-2xl font-black text-brand-primary">Pagamento Confirmado!</h2>
                <p className="text-gray-500">Seu pedido foi aprovado e está sendo preparado.</p>
                <p className="text-xs text-gray-400">Pedido #{orderId}</p>
                <Link
                    href="/"
                    className="inline-block mt-4 bg-brand-primary text-white font-bold px-8 py-4 rounded-xl hover:bg-brand-secondary transition-colors"
                >
                    Voltar para a Loja
                </Link>
            </div>
        );
    }

    if (status === 'FAILED') {
        return (
            <div className="text-center space-y-4 py-8">
                <XCircle className="w-16 h-16 text-red-500 mx-auto" />
                <h2 className="text-2xl font-black text-brand-primary">Pagamento Recusado</h2>
                <p className="text-gray-500">Verifique os dados do cartão ou tente outro método.</p>
                <Link href="/checkout" className="inline-block mt-4 border-2 border-brand-primary text-brand-primary font-bold px-8 py-4 rounded-xl hover:bg-gray-50 transition-colors">
                    Tentar Novamente
                </Link>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            <div className="flex items-center gap-3 mb-2">
                <div className="w-8 h-8 rounded-full bg-brand-primary text-white flex items-center justify-center text-sm font-bold">3</div>
                <h2 className="text-xl font-bold text-brand-primary">Finalizar Pagamento</h2>
            </div>

            {/* Pix */}
            {paymentMethod === 'PIX' && result.pixQrCode && (
                <div className="space-y-5">
                    <div className="flex items-center gap-2 text-sm text-amber-700 bg-amber-50 border border-amber-100 rounded-xl px-4 py-3">
                        <Clock className="w-4 h-4 shrink-0" />
                        Aguardando pagamento... <Loader2 className="w-3 h-3 animate-spin ml-auto" />
                    </div>

                    <div className="flex flex-col items-center gap-4 p-6 bg-gray-50 rounded-2xl border border-gray-100">
                        <p className="text-sm font-semibold text-gray-600">Escaneie o QR Code com seu banco</p>
                        <div className="relative w-48 h-48 bg-white rounded-xl p-2 shadow-sm border">
                            <Image
                                src={`data:image/png;base64,${result.pixQrCode}`}
                                alt="QR Code Pix"
                                fill
                                className="object-contain p-2"
                            />
                        </div>
                        <p className="text-xs text-gray-400">ou use o código copia e cola</p>
                    </div>

                    {result.pixCopyPaste && (
                        <div className="flex items-center gap-2 bg-white border border-gray-200 rounded-xl p-3">
                            <p className="text-xs text-gray-500 font-mono flex-1 truncate">{result.pixCopyPaste}</p>
                            <button
                                onClick={() => copy(result.pixCopyPaste!)}
                                className="shrink-0 flex items-center gap-1 text-xs font-semibold text-brand-secondary hover:text-brand-primary transition-colors"
                            >
                                {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                                {copied ? 'Copiado!' : 'Copiar'}
                            </button>
                        </div>
                    )}
                </div>
            )}

            {/* Boleto */}
            {paymentMethod === 'BOLETO' && (
                <div className="space-y-5">
                    <div className="flex items-center gap-2 text-sm text-blue-700 bg-blue-50 border border-blue-100 rounded-xl px-4 py-3">
                        <Clock className="w-4 h-4 shrink-0" />
                        Aguardando pagamento do boleto...
                    </div>

                    {result.boletoBarcode && (
                        <div className="flex items-center gap-2 bg-white border border-gray-200 rounded-xl p-3">
                            <p className="text-xs text-gray-500 font-mono flex-1 truncate">{result.boletoBarcode}</p>
                            <button
                                onClick={() => copy(result.boletoBarcode!)}
                                className="shrink-0 flex items-center gap-1 text-xs font-semibold text-brand-secondary hover:text-brand-primary transition-colors"
                            >
                                {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                                {copied ? 'Copiado!' : 'Copiar'}
                            </button>
                        </div>
                    )}

                    {result.boletoUrl && (
                        <a
                            href={result.boletoUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="flex items-center justify-center gap-2 w-full bg-brand-primary text-white font-bold py-4 rounded-xl hover:bg-brand-secondary transition-colors"
                        >
                            <ExternalLink className="w-4 h-4" />
                            Abrir Boleto
                        </a>
                    )}

                    <p className="text-xs text-center text-gray-400">
                        Vencimento em 1 dia útil. Aprovação em até 3 dias após o pagamento.
                    </p>
                </div>
            )}

            {/* Cartão pendente (análise de risco) */}
            {(paymentMethod === 'CREDIT_CARD' || paymentMethod === 'DEBIT_CARD') && status === 'PENDING' && (
                <div className="text-center space-y-4 py-8">
                    <Loader2 className="w-12 h-12 animate-spin text-brand-secondary mx-auto" />
                    <h3 className="text-lg font-bold text-brand-primary">Analisando pagamento...</h3>
                    <p className="text-sm text-gray-500">Isso pode levar alguns instantes.</p>
                </div>
            )}
        </div>
    );
}
