'use client';

import { useState } from 'react';
import { MapPin, Loader2 } from 'lucide-react';
import api from '@/lib/axios';

export interface ShippingOption {
    typeName: string;
    cost: number;
    deadLineDays: number;
    source: 'SIMULATED' | 'CORREIOS';
}

export interface DeliveryData {
    zipCode: string;
    street: string;
    number: string;
    complement: string;
    neighborhood: string;
    city: string;
    state: string;
}

interface Props {
    items: { id: string; size?: string; quantity: number }[];
    data: DeliveryData;
    onChange: (data: DeliveryData) => void;
    shipping: ShippingOption | null;
    onShippingChange: (shipping: ShippingOption | null) => void;
    onNext: () => void;
}

const STATES = [
    'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG',
    'PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'
];

export function StepDelivery({ items, data, onChange, shipping, onShippingChange, onNext }: Props) {
    const [loadingCep, setLoadingCep] = useState(false);
    const [shippingOptions, setShippingOptions] = useState<ShippingOption[]>([]);
    const [shippingError, setShippingError] = useState('');

    const set = (field: keyof DeliveryData, value: string) => {
        onChange({ ...data, [field]: value });
        if (field === 'zipCode') {
            setShippingOptions([]);
            onShippingChange(null);
        }
    };

    const handleCepBlur = async () => {
        const cep = data.zipCode.replace(/\D/g, '');
        if (cep.length !== 8) return;
        setLoadingCep(true);
        setShippingError('');
        try {
            await api.put('/cart/sync', items.map(item => ({
                productId: item.id,
                size: item.size,
                quantity: item.quantity,
            })));
            const [addressResult, quoteResult] = await Promise.allSettled([
                fetch(`https://viacep.com.br/ws/${cep}/json/`).then(response => response.json()),
                api.post<ShippingOption[]>('/orders/shipping-quotes', { zipCode: cep }),
            ]);

            if (addressResult.status === 'fulfilled' && !addressResult.value.erro) {
                const address = addressResult.value;
                onChange({
                    ...data,
                    street: address.logradouro || '',
                    neighborhood: address.bairro || '',
                    city: address.localidade || '',
                    state: address.uf || '',
                });
            }

            if (quoteResult.status === 'fulfilled') {
                const options = quoteResult.value.data;
                setShippingOptions(options);
                onShippingChange(options[0] ?? null);
            } else {
                setShippingOptions([]);
                onShippingChange(null);
                setShippingError('Não foi possível cotar o frete para este CEP. Tente novamente.');
            }
        } catch {
            setShippingOptions([]);
            onShippingChange(null);
            setShippingError('Não foi possível consultar o CEP. Verifique o número e tente novamente.');
        } finally {
            setLoadingCep(false);
        }
    };

    const isValid = data.zipCode && data.street && data.number && data.city && data.state && shipping;
    const formatCurrency = (value: number) => new Intl.NumberFormat('pt-BR', {
        style: 'currency', currency: 'BRL',
    }).format(value);

    return (
        <div className="space-y-6">
            <div className="flex items-center gap-3 mb-2">
                <div className="w-8 h-8 rounded-full bg-brand-primary text-white flex items-center justify-center text-sm font-bold">1</div>
                <h2 className="text-xl font-bold text-brand-primary">Endereço de Entrega</h2>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* CEP */}
                <div className="relative">
                    <label className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">CEP *</label>
                    <div className="relative">
                        <input
                            type="text"
                            value={data.zipCode}
                            onChange={e => set('zipCode', e.target.value)}
                            onBlur={handleCepBlur}
                            placeholder="00000-000"
                            maxLength={9}
                            className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-brand-secondary focus:border-transparent transition pr-10"
                        />
                        {loadingCep && (
                            <Loader2 className="w-4 h-4 animate-spin text-gray-400 absolute right-3 top-1/2 -translate-y-1/2" />
                        )}
                        {!loadingCep && (
                            <MapPin className="w-4 h-4 text-gray-300 absolute right-3 top-1/2 -translate-y-1/2" />
                        )}
                    </div>
                    <p className="text-xs text-gray-400 mt-1">Preenchimento automático</p>
                </div>

                {/* Estado */}
                <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Estado *</label>
                    <select
                        value={data.state}
                        onChange={e => set('state', e.target.value)}
                        className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-brand-secondary focus:border-transparent transition"
                    >
                        <option value="">Selecione</option>
                        {STATES.map(s => <option key={s} value={s}>{s}</option>)}
                    </select>
                </div>

                {/* Rua */}
                <div className="md:col-span-2">
                    <label className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Rua *</label>
                    <input
                        type="text"
                        value={data.street}
                        onChange={e => set('street', e.target.value)}
                        placeholder="Nome da rua"
                        className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-brand-secondary focus:border-transparent transition"
                    />
                </div>

                {/* Número */}
                <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Número *</label>
                    <input
                        type="text"
                        value={data.number}
                        onChange={e => set('number', e.target.value)}
                        placeholder="123"
                        className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-brand-secondary focus:border-transparent transition"
                    />
                </div>

                {/* Complemento */}
                <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Complemento</label>
                    <input
                        type="text"
                        value={data.complement}
                        onChange={e => set('complement', e.target.value)}
                        placeholder="Apto, bloco... (opcional)"
                        className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-brand-secondary focus:border-transparent transition"
                    />
                </div>

                {/* Bairro */}
                <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Bairro</label>
                    <input
                        type="text"
                        value={data.neighborhood}
                        onChange={e => set('neighborhood', e.target.value)}
                        placeholder="Bairro"
                        className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-brand-secondary focus:border-transparent transition"
                    />
                </div>

                {/* Cidade */}
                <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Cidade *</label>
                    <input
                        type="text"
                        value={data.city}
                        onChange={e => set('city', e.target.value)}
                        placeholder="Cidade"
                        className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-brand-secondary focus:border-transparent transition"
                    />
                </div>
            </div>

            <section aria-labelledby="shipping-options-title" className="space-y-3">
                <h3 id="shipping-options-title" className="text-sm font-bold uppercase text-gray-600">Opções de entrega</h3>
                {loadingCep && <p role="status" className="text-sm text-gray-500">Consultando CEP e estimativas...</p>}
                {shippingError && <p role="alert" className="text-sm text-red-700">{shippingError}</p>}
                {!loadingCep && shippingOptions.length > 0 && (
                    <>
                        <p className="text-xs text-amber-700">Estimativas temporárias para teste. A tarifa oficial dos Correios depende da configuração da API.</p>
                        <div className="grid gap-3 sm:grid-cols-2">
                            {shippingOptions.map(option => {
                                const selected = shipping?.typeName === option.typeName;
                                return (
                                    <button
                                        key={option.typeName}
                                        type="button"
                                        aria-pressed={selected}
                                        onClick={() => onShippingChange(option)}
                                        className={`rounded-lg border p-4 text-left transition ${selected ? 'border-red-700 bg-red-50 ring-1 ring-red-700' : 'border-gray-200 hover:border-gray-400'}`}
                                    >
                                        <span className="block font-bold text-gray-900">{option.typeName}</span>
                                        <span className="mt-1 block text-sm text-gray-600">Até {option.deadLineDays} dias úteis</span>
                                        <span className="mt-2 block font-semibold text-gray-900">{formatCurrency(option.cost)}</span>
                                    </button>
                                );
                            })}
                        </div>
                    </>
                )}
            </section>

            <button
                onClick={onNext}
                disabled={!isValid || loadingCep}
                className="w-full bg-brand-primary text-white font-bold py-4 rounded-xl hover:bg-brand-secondary transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
                Continuar para Pagamento →
            </button>
        </div>
    );
}
