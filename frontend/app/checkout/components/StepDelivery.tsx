'use client';

import { useState } from 'react';
import { MapPin, Loader2 } from 'lucide-react';
import { getApiErrorCode, getApiErrorMessage } from '@/utils/apiError';
import { formatCep } from '@/utils/authFormatters';
import type { Address } from '@/services/accountService';
import { QuoteCartItem, requestShippingQuotes } from '../shippingQuote';

export interface ShippingOption {
    id: string;          // UUID da ShippingQuoteOptionEntity — enviado no checkout
    serviceId: string;
    typeName: string;
    cost: number;
    deadLineDays: number;
    source: 'SIMULATED' | 'MELHOR_ENVIO' | 'SHIPPING_GATEWAY';
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
    items: QuoteCartItem[];
    data: DeliveryData;
    onChange: (data: DeliveryData) => void;
    shipping: ShippingOption | null;
    onShippingChange: (shipping: ShippingOption | null) => void;
    /** Opções e aviso ficam no checkout para sobreviverem a uma nova cotação feita fora desta etapa. */
    shippingOptions: ShippingOption[];
    onShippingOptionsChange: (options: ShippingOption[]) => void;
    shippingNotice: string;
    onShippingNoticeChange: (notice: string) => void;
    savedAddresses: Address[];
    selectedAddressId: string | null;
    addressLoadError: string;
    onSavedAddressChange: (addressId: string | null) => void;
    onNext: () => void;
}

const EMPTY_DELIVERY: DeliveryData = {
    zipCode: '',
    street: '',
    number: '',
    complement: '',
    neighborhood: '',
    city: '',
    state: '',
};

const STATES = [
    'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG',
    'PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'
];

export function StepDelivery({
    items, data, onChange, shipping, onShippingChange,
    shippingOptions, onShippingOptionsChange, shippingNotice, onShippingNoticeChange, onNext,
    savedAddresses, selectedAddressId, addressLoadError, onSavedAddressChange,
}: Props) {
    const [loadingCep, setLoadingCep] = useState(false);
    const [shippingError, setShippingError] = useState('');

    const set = (field: keyof DeliveryData, value: string) => {
        onSavedAddressChange(null);
        onChange({ ...data, [field]: value });
        if (field === 'zipCode') {
            onShippingOptionsChange([]);
            onShippingNoticeChange('');
            onShippingChange(null);
        }
    };

    const handleCepBlur = async () => {
        const cep = data.zipCode.replace(/\D/g, '');
        if (cep.length !== 8) return;
        setLoadingCep(true);
        setShippingError('');
        onShippingNoticeChange('');
        try {
            const [addressResult, quoteResult] = await Promise.allSettled([
                fetch(`https://viacep.com.br/ws/${cep}/json/`).then(response => response.json()),
                requestShippingQuotes(items, cep),
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
                const options = quoteResult.value;
                onShippingOptionsChange(options);
                onShippingChange(null);
                if (options.length === 0) {
                    setShippingError('Não há opções de entrega disponíveis para este CEP.');
                }
            } else {
                onShippingOptionsChange([]);
                onShippingChange(null);
                if (getApiErrorCode(quoteResult.reason) === 'SHIPPING_NOT_AVAILABLE') {
                    // O Melhor Envio respondeu, mas nenhum serviço atende: não é falha do site.
                    onShippingNoticeChange('Não encontramos entrega disponível para este CEP com os itens do carrinho. Confira o CEP ou informe outro endereço.');
                } else {
                    setShippingError(getApiErrorMessage(
                        quoteResult.reason,
                        'Não foi possível cotar o frete para este CEP. Tente novamente.',
                        { 400: 'CEP inválido ou sem cobertura de entrega. Confira o CEP informado.' },
                    ));
                }
            }
        } catch (error: unknown) {
            onShippingOptionsChange([]);
            onShippingChange(null);
            setShippingError(getApiErrorMessage(error, 'Não foi possível consultar o CEP. Verifique o número e tente novamente.'));
        } finally {
            setLoadingCep(false);
        }
    };

    const handleSavedAddressChange = async (addressId: string) => {
        onShippingChange(null);
        onShippingOptionsChange([]);
        onShippingNoticeChange('');
        setShippingError('');

        if (!addressId) {
            onSavedAddressChange(null);
            onChange(EMPTY_DELIVERY);
            return;
        }

        const address = savedAddresses.find(item => item.id === addressId);
        if (!address) {
            onSavedAddressChange(null);
            setShippingError('Não foi possível carregar o endereço selecionado. Atualize a página e tente novamente.');
            return;
        }

        onSavedAddressChange(addressId);
        onChange({
            zipCode: formatCep(address.zipCode),
            street: address.street,
            number: address.number,
            complement: address.complement ?? '',
            neighborhood: address.neighborhood ?? '',
            city: address.city,
            state: address.state,
        });

        const cep = address.zipCode.replace(/\D/g, '');
        setLoadingCep(true);
        try {
            const options = await requestShippingQuotes(items, cep);
            onShippingOptionsChange(options);
            if (options.length === 0) {
                setShippingError('Não há opções de entrega disponíveis para este CEP.');
            }
        } catch (error: unknown) {
            setShippingError(getApiErrorMessage(
                error,
                'Não foi possível cotar o frete para este endereço. Tente novamente.',
                { 400: 'CEP inválido ou sem cobertura de entrega. Confira o endereço salvo.' },
            ));
        } finally {
            setLoadingCep(false);
        }
    };

    const isValid = data.zipCode && data.street && data.number && data.city && data.state && shipping?.id;
    const formatCurrency = (value: number) => new Intl.NumberFormat('pt-BR', {
        style: 'currency', currency: 'BRL',
    }).format(value);
    const selectableAddresses = savedAddresses.filter(
        (address): address is Address & { id: string } => Boolean(address.id),
    );

    return (
        <div className="space-y-6">
            <div className="flex items-center gap-3 mb-2">
                <div className="w-8 h-8 rounded-full bg-brand-primary text-white flex items-center justify-center text-sm font-bold">1</div>
                <h2 className="text-xl font-bold text-brand-primary">Endereço de Entrega</h2>
            </div>

            {addressLoadError && (
                <p role="alert" className="text-sm text-red-700">{addressLoadError}</p>
            )}

            {selectableAddresses.length > 0 && (
                <div>
                    <label htmlFor="saved-address" className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">
                        Usar endereço salvo
                    </label>
                    <select
                        id="saved-address"
                        value={selectedAddressId ?? ''}
                        onChange={event => void handleSavedAddressChange(event.target.value)}
                        className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-brand-secondary"
                    >
                        <option value="">Informar um novo endereço</option>
                        {selectableAddresses.map(address => (
                            <option key={address.id} value={address.id}>
                                {address.street}, {address.number} - {address.city}/{address.state} ({formatCep(address.zipCode)})
                            </option>
                        ))}
                    </select>
                </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* CEP */}
                <div className="relative">
                    <label htmlFor="delivery-zip" className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">CEP *</label>
                    <div className="relative">
                        <input
                            id="delivery-zip"
                            type="text"
                            value={data.zipCode}
                            onChange={e => set('zipCode', formatCep(e.target.value))}
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
                    <label htmlFor="delivery-state" className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Estado *</label>
                    <select
                        id="delivery-state"
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
                    <label htmlFor="delivery-street" className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Rua *</label>
                    <input
                        type="text"
                        id="delivery-street"
                        value={data.street}
                        onChange={e => set('street', e.target.value)}
                        placeholder="Nome da rua"
                        className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-brand-secondary focus:border-transparent transition"
                    />
                </div>

                {/* Número */}
                <div>
                    <label htmlFor="delivery-number" className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Número *</label>
                    <input
                        type="text"
                        id="delivery-number"
                        value={data.number}
                        onChange={e => set('number', e.target.value)}
                        placeholder="123"
                        className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-brand-secondary focus:border-transparent transition"
                    />
                </div>

                {/* Complemento */}
                <div>
                    <label htmlFor="delivery-complement" className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Complemento</label>
                    <input
                        type="text"
                        id="delivery-complement"
                        value={data.complement}
                        onChange={e => set('complement', e.target.value)}
                        placeholder="Apto, bloco... (opcional)"
                        className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-brand-secondary focus:border-transparent transition"
                    />
                </div>

                {/* Bairro */}
                <div>
                    <label htmlFor="delivery-neighborhood" className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Bairro</label>
                    <input
                        type="text"
                        id="delivery-neighborhood"
                        value={data.neighborhood}
                        onChange={e => set('neighborhood', e.target.value)}
                        placeholder="Bairro"
                        className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-brand-secondary focus:border-transparent transition"
                    />
                </div>

                {/* Cidade */}
                <div>
                    <label htmlFor="delivery-city" className="block text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wide">Cidade *</label>
                    <input
                        type="text"
                        id="delivery-city"
                        value={data.city}
                        onChange={e => set('city', e.target.value)}
                        placeholder="Cidade"
                        className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-brand-secondary focus:border-transparent transition"
                    />
                </div>
            </div>

            <section aria-labelledby="shipping-options-title" className="space-y-3">
                <h3 id="shipping-options-title" className="text-sm font-bold uppercase text-gray-600">Opções de entrega</h3>
                {loadingCep && <p role="status" className="text-sm text-gray-500">Consultando opções de entrega...</p>}
                {shippingNotice && <p role="status" className="text-sm text-amber-700">{shippingNotice}</p>}
                {shippingError && <p role="alert" className="text-sm text-red-700">{shippingError}</p>}
                {!loadingCep && shippingOptions.length > 0 && (
                    <div className="grid gap-3 sm:grid-cols-2">
                        {shippingOptions.map(option => {
                            const selected = shipping?.id === option.id;
                            return (
                                <button
                                    key={option.id}
                                    type="button"
                                    aria-pressed={selected}
                                    onClick={() => onShippingChange(option)}
                                    className={`rounded-lg border p-4 text-left transition ${selected ? 'border-red-700 bg-red-50 ring-1 ring-red-700' : 'border-gray-200 hover:border-gray-400'}`}
                                >
                                    <span className="block font-bold text-gray-900">{option.typeName}</span>
                                    <span className="mt-1 block text-sm text-gray-600">Até {option.deadLineDays} dias úteis</span>
                                    <span className="mt-2 block font-semibold text-gray-900">{formatCurrency(option.cost)}</span>
                                    {option.source === 'SIMULATED' && (
                                        <span className="mt-1 block text-xs text-amber-700">Simulação local; não disponível para compra.</span>
                                    )}
                                </button>
                            );
                        })}
                    </div>
                )}
            </section>

            <button
                onClick={onNext}
                disabled={!isValid || loadingCep}
                className="w-full bg-red-500 text-white font-bold py-4 rounded-xl hover:bg-brand-secondary transition-colors disabled:cursor-not-allowed"
            >
                Continuar para Pagamento →
            </button>
        </div>
    );
}
