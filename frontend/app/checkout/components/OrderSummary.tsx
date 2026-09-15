'use client';

import { CartItem } from '../../context/CartContext';

interface Props {
    items: CartItem[];
    total: number;
}

const fmt = (v: number) =>
    new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v);

export function OrderSummary({ items, total }: Props) {
    return (
        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 sticky top-24 space-y-5">
            <h3 className="font-bold text-brand-primary text-lg">Resumo do Pedido</h3>

            <div className="space-y-3 max-h-64 overflow-y-auto pr-1">
                {items.map(item => (
                    <div key={`${item.id}-${item.size}`} className="flex gap-3 items-center">
                        <div
                            className="w-14 h-14 rounded-xl bg-cover bg-center bg-gray-100 shrink-0"
                            style={{ backgroundImage: `url(${item.image})` }}
                        />
                        <div className="flex-1 min-w-0">
                            <p className="text-sm font-semibold text-brand-primary truncate">{item.name}</p>
                            {item.size && <p className="text-xs text-gray-400">Tam: {item.size}</p>}
                            <p className="text-xs text-gray-500">Qtd: {item.quantity}</p>
                        </div>
                        <p className="text-sm font-bold text-brand-primary shrink-0">
                            {fmt(item.price * item.quantity)}
                        </p>
                    </div>
                ))}
            </div>

            <div className="border-t border-gray-100 pt-4 space-y-2 text-sm">
                <div className="flex justify-between text-gray-500">
                    <span>Subtotal</span>
                    <span>{fmt(total)}</span>
                </div>
                <div className="flex justify-between text-gray-500">
                    <span>Frete</span>
                    <span className="text-green-600 font-medium">Grátis</span>
                </div>
                <div className="flex justify-between font-black text-base text-brand-primary pt-2 border-t border-gray-100">
                    <span>Total</span>
                    <span>{fmt(total)}</span>
                </div>
            </div>
        </div>
    );
}
