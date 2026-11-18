'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useCart } from '../context/CartContext';
import { StepDelivery, DeliveryData, ShippingOption } from './components/StepDelivery';
import { StepPayment, PaymentData } from './components/StepPayment';
import { StepConfirmation } from './components/StepConfirmation';
import { OrderSummary } from './components/OrderSummary';
import Link from 'next/link';
import { useAuth } from '../context/AuthContext';
import api from '@/lib/axios';
import { getApiErrorCode, getApiErrorMessage } from '@/utils/apiError';
import { requestShippingQuotes } from './shippingQuote';

const SHIPPING_QUOTE_STALE = 'SHIPPING_QUOTE_STALE';

type Step = 1 | 2 | 3;

const EMPTY_DELIVERY: DeliveryData = {
    zipCode: '', street: '', number: '', complement: '',
    neighborhood: '', city: '', state: '',
};

const EMPTY_PAYMENT: PaymentData = {
    method: 'PIX',
    cpfCnpj: '',
    idempotencyKey: '',
};

interface PaymentResult {
    status: string;
    paymentMethod: PaymentData['method'];
    amount?: number;
    pixQrCode?: string;
    pixCopyPaste?: string;
    boletoUrl?: string;
    boletoBarcode?: string;
}

const STEP_LABELS = ['Entrega', 'Pagamento', 'Confirmação'];

export default function CheckoutPage() {
    const { items, total, clearCart } = useCart();
    const { user, loading: authLoading } = useAuth();
    const router = useRouter();

    const [step, setStep] = useState<Step>(1);
    const [delivery, setDelivery] = useState<DeliveryData>(EMPTY_DELIVERY);
    const [shipping, setShipping] = useState<ShippingOption | null>(null);
    const [shippingOptions, setShippingOptions] = useState<ShippingOption[]>([]);
    const [shippingNotice, setShippingNotice] = useState('');
    const [payment, setPayment] = useState<PaymentData>(EMPTY_PAYMENT);
    const [orderId, setOrderId] = useState<string>('');
    const [paymentResult, setPaymentResult] = useState<PaymentResult | null>(null);
    const [confirmedItems, setConfirmedItems] = useState(items);
    const [confirmedTotal, setConfirmedTotal] = useState(total);

    useEffect(() => {
        if (!authLoading && !user) {
            router.replace('/auth?next=/checkout');
        }
    }, [authLoading, router, user]);

    useEffect(() => {
        if (authLoading || !user) return;
        const savedOrderId = sessionStorage.getItem('dn-store-payment-order');
        if (!savedOrderId) return;

        api.get(`/payment/order/${savedOrderId}`)
            .then(({ data }) => {
                setOrderId(savedOrderId);
                setPaymentResult(data as PaymentResult);
                if (items.length > 0) setConfirmedItems(items);
                setConfirmedTotal(data.amount ?? total);
                setStep(3);
            })
            .catch(() => {
                setOrderId(savedOrderId);
                setStep(2);
            });
    }, [authLoading, items, total, user]);

    if (authLoading || !user) {
        return (
            <div className="min-h-screen bg-gray-50 pt-32 text-center text-gray-600">
                Verificando sua sessão...
            </div>
        );
    }

    if (items.length === 0 && step !== 3 && !orderId) {
        return (
            <div className="min-h-screen bg-gray-50 pt-32 pb-20 text-center">
                <h1 className="text-3xl font-bold mb-4 text-brand-primary">Seu carrinho está vazio</h1>
                <Link href="/produtos" className="inline-block bg-brand-primary text-white font-bold px-8 py-4 rounded-xl hover:bg-brand-secondary transition-colors">
                    Ir para a Loja
                </Link>
            </div>
        );
    }

    // A cotação vale por pouco tempo e é validada no servidor. Se venceu (ou o carrinho mudou),
    // volta para a entrega, cota de novo com o mesmo CEP e pede só a escolha da opção.
    const requoteAfterStaleShipping = async () => {
        const cep = delivery.zipCode.replace(/\D/g, '');
        setShipping(null);
        setShippingOptions([]);
        setShippingNotice('O frete escolhido expirou ou o carrinho mudou. Atualizando as opções de entrega...');
        setStep(1);
        try {
            const options = await requestShippingQuotes(items, cep);
            setShippingOptions(options);
            setShippingNotice('O frete foi atualizado. Escolha a opção de entrega novamente para continuar.');
        } catch (error: unknown) {
            setShippingNotice(getApiErrorCode(error) === 'SHIPPING_NOT_AVAILABLE'
                ? 'Não encontramos entrega disponível para este CEP com os itens do carrinho. Confira o CEP ou informe outro endereço.'
                : 'O frete expirou e não foi possível atualizá-lo agora. Clique no campo CEP e saia dele para cotar de novo.');
        }
    };

    const handleConfirmPayment = async (idempotencyKey: string): Promise<PaymentResult | null> => {
        try {
            let currentOrderId = orderId;
            if (!currentOrderId) {
                const orderRes = await api.post('/orders', {
                    zipCode: delivery.zipCode.replace(/\D/g, ''),
                    street: delivery.street,
                    number: delivery.number,
                    complement: delivery.complement,
                    neighborhood: delivery.neighborhood,
                    city: delivery.city,
                    state: delivery.state,
                    shippingOptionId: shipping?.id,
                });
                currentOrderId = orderRes.data.id as string;
                setOrderId(currentOrderId);
                sessionStorage.setItem('dn-store-payment-order', currentOrderId);
            }

            const paymentRes = await api.post('/payment/init', {
                orderId: currentOrderId,
                idempotencyKey,
                paymentMethod: payment.method,
                cpfCnpj: payment.cpfCnpj.replace(/\D/g, ''),
                installments: null,
            });

            if (items.length > 0) setConfirmedItems([...items]);
            setConfirmedTotal(paymentRes.data.amount ?? total + (shipping?.cost ?? 0));
            clearCart();

            return paymentRes.data as PaymentResult;
        } catch (error: unknown) {
            if (getApiErrorCode(error) === SHIPPING_QUOTE_STALE) {
                await requoteAfterStaleShipping();
                throw new Error('O frete expirou e foi atualizado. Escolha a opção de entrega novamente.');
            }
            throw new Error(getApiErrorMessage(error, 'Não foi possível criar o pedido ou processar o pagamento.'));
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
                                        active ? 'bg-gray-100 text-gray-600' :
                                        'bg-gray-200 text-gray-600'
                                    }`}>
                                        {done ? '✓' : n}
                                    </div>
                                    <span className="text-sm text-black font-semibold hidden sm:block">{label}</span>
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
                                items={items}
                                data={delivery}
                                onChange={setDelivery}
                                shipping={shipping}
                                onShippingChange={setShipping}
                                shippingOptions={shippingOptions}
                                onShippingOptionsChange={setShippingOptions}
                                shippingNotice={shippingNotice}
                                onShippingNoticeChange={setShippingNotice}
                                onNext={() => setStep(2)}
                            />
                        )}
                        {step === 2 && (
                            <StepPayment
                                data={payment}
                                onChange={setPayment}
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
                                paymentMethod={paymentResult.paymentMethod}
                                result={paymentResult}
                                onRetry={() => {
                                    setPayment(current => ({
                                        ...current,
                                        idempotencyKey: crypto.randomUUID(),
                                    }));
                                    setStep(2);
                                }}
                            />
                        )}
                    </div>

                    {/* Sidebar */}
                    <div className="lg:col-span-1">
                        <OrderSummary
                        items={step === 3 ? confirmedItems : items}
                        total={step === 3
                            ? confirmedItems.reduce((sum, item) => sum + item.price * item.quantity, 0)
                            : total}
                        shipping={shipping}
                        finalTotal={step === 3 ? confirmedTotal : undefined}
                        />
                    </div>
                </div>
            </div>
        </div>
    );
}
