'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useCart } from '../context/CartContext';
import { StepDelivery, DeliveryData } from './components/StepDelivery';
import { StepPayment, PaymentData, PaymentMethod } from './components/StepPayment';
import { StepConfirmation } from './components/StepConfirmation';
import { OrderSummary } from './components/OrderSummary';
import Link from 'next/link';

type Step = 1 | 2 | 3;

const EMPTY_DELIVERY: DeliveryData = {
    zipCode: '', street: '', number: '', complement: '',
    neighborhood: '', city: '', state: '',
};

const EMPTY_PAYMENT: PaymentData = {
    method: 'CREDIT_CARD',
    cpfCnpj: '',
    installments: 1,
    card: { holderName: '', number: '', expiryMonth: '', expiryYear: '', ccv: '' },
};

interface PaymentResult {
    status: string;
    pixQrCode?: string;
    pixCopyPaste?: string;
    boletoUrl?: string;
    boletoBarcode?: string;
}

const STEP_LABELS = ['Entrega', 'Pagamento', 'Confirmação'];

export default function CheckoutPage() {
    const { items, total, clearCart } = useCart();
    const router = useRouter();

    const [step, setStep] = useState<Step>(1);
    const [delivery, setDelivery] = useState<DeliveryData>(EMPTY_DELIVERY);
    const [payment, setPayment] = useState<PaymentData>(EMPTY_PAYMENT);
    const [orderId, setOrderId] = useState<string>('');
    const [paymentResult, setPaymentResult] = useState<PaymentResult | null>(null);

    if (items.length === 0 && step !== 3) {
        return (
            <div className="min-h-screen bg-gray-50 pt-32 pb-20 text-center">
                <h1 className="text-3xl font-bold mb-4 text-brand-primary">Seu carrinho está vazio</h1>
                <Link href="/loja" className="inline-block bg-brand-primary text-white font-bold px-8 py-4 rounded-xl hover:bg-brand-secondary transition-colors">
                    Ir para a Loja
                </Link>
            </div>
        );
    }

    const handleConfirmPayment = async (): Promise<PaymentResult | null> => {
        const token = localStorage.getItem('token');

        try {
            // 1. Criar pedido
            const orderRes = await fetch('/api/orders', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
                body: JSON.stringify({
                    zipCode: delivery.zipCode.replace(/\D/g, ''),
                    shippingType: 'PAC',
                }),
            });

            if (!orderRes.ok) throw new Error('Erro ao criar pedido');
            const order = await orderRes.json();
            setOrderId(order.id);

            // 2. Iniciar pagamento
            const paymentRes = await fetch('/api/payment/init', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
                body: JSON.stringify({
                    orderId: order.id,
                    amount: total,
                    paymentMethod: payment.method,
                    cpfCnpj: payment.cpfCnpj.replace(/\D/g, ''),
                    installments: payment.installments,
                    card: (payment.method === 'CREDIT_CARD' || payment.method === 'DEBIT_CARD')
                        ? {
                            holderName: payment.card.holderName,
                            number: payment.card.number.replace(/\s/g, ''),
                            expiryMonth: payment.card.expiryMonth,
                            expiryYear: payment.card.expiryYear,
                            ccv: payment.card.ccv,
                        }
                        : null,
                }),
            });

            if (!paymentRes.ok) throw new Error('Erro ao processar pagamento');
            const result: PaymentResult = await paymentRes.json();

            if (result.status === 'PAID') clearCart();
            return result;

        } catch (e) {
            console.error(e);
            return null;
        }
    };

    return (
        <div className="min-h-screen bg-gray-50 pt-24 pb-20">
            <div className="container mx-auto px-4 max-w-5xl">

                {/* Progress bar */}
                <div className="flex items-center gap-2 mb-10">
                    {STEP_LABELS.map((label, i) => {
                        const n = (i + 1) as Step;
                        const active = step === n;
                        const done = step > n;
                        return (
                            <div key={label} className="flex items-center gap-2 flex-1">
                                <div className={`flex items-center gap-2 ${active || done ? 'text-brand-primary' : 'text-gray-400'}`}>
                                    <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold shrink-0 transition-colors ${
                                        done ? 'bg-green-500 text-white' :
                                        active ? 'bg-brand-primary text-white' :
                                        'bg-gray-200 text-gray-400'
                                    }`}>
                                        {done ? '✓' : n}
                                    </div>
                                    <span className="text-sm font-semibold hidden sm:block">{label}</span>
                                </div>
                                {i < STEP_LABELS.length - 1 && (
                                    <div className={`flex-1 h-0.5 mx-2 rounded transition-colors ${done ? 'bg-green-500' : 'bg-gray-200'}`} />
                                )}
                            </div>
                        );
                    })}
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                    {/* Steps */}
                    <div className="lg:col-span-2 bg-white rounded-2xl border border-gray-100 shadow-sm p-6 md:p-8">
                        {step === 1 && (
                            <StepDelivery
                                data={delivery}
                                onChange={setDelivery}
                                onNext={() => setStep(2)}
                            />
                        )}
                        {step === 2 && (
                            <StepPayment
                                data={payment}
                                onChange={setPayment}
                                total={total}
                                onConfirm={handleConfirmPayment}
                                onNext={(result) => {
                                    setPaymentResult(result);
                                    setStep(3);
                                }}
                                onBack={() => setStep(1)}
                            />
                        )}
                        {step === 3 && paymentResult && (
                            <StepConfirmation
                                orderId={orderId}
                                paymentMethod={payment.method}
                                result={paymentResult}
                            />
                        )}
                    </div>

                    {/* Sidebar */}
                    <div className="lg:col-span-1">
                        <OrderSummary items={items} total={total} />
                    </div>
                </div>
            </div>
        </div>
    );
}
