'use client';

import { useState } from 'react';
import { MapPin, Loader2 } from 'lucide-react';

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
    data: DeliveryData;
    onChange: (data: DeliveryData) => void;
    onNext: () => void;
}

const STATES = [
    'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG',
    'PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'
];

export function StepDelivery({ data, onChange, onNext }: Props) {
    const [loadingCep, setLoadingCep] = useState(false);

    const set = (field: keyof DeliveryData, value: string) =>
        onChange({ ...data, [field]: value });

    const handleCepBlur = async () => {
        const cep = data.zipCode.replace(/\D/g, '');
        if (cep.length !== 8) return;
        setLoadingCep(true);
        try {
            const res = await fetch(`https://viacep.com.br/ws/${cep}/json/`);
            const json = await res.json();
            if (!json.erro) {
                onChange({
                    ...data,
                    street: json.logradouro || '',
                    neighborhood: json.bairro || '',
                    city: json.localidade || '',
                    state: json.uf || '',
                });
            }
        } catch {
            // silently ignore
        } finally {
            setLoadingCep(false);
        }
    };

    const isValid = data.zipCode && data.street && data.number && data.city && data.state;

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

            <button
                onClick={onNext}
                disabled={!isValid}
                className="w-full bg-brand-primary text-white font-bold py-4 rounded-xl hover:bg-brand-secondary transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
                Continuar para Pagamento →
            </button>
        </div>
    );
}
