'use client';

import { useState } from 'react';
import { useCart } from '../context/CartContext';
import { Button } from '../components/ui/Button';
import { useRouter } from 'next/navigation';
import { Trash2, CreditCard, QrCode } from 'lucide-react';

export default function CheckoutPage() {
    const { items, removeItem, updateQuantity, total, clearCart } = useCart();
    const [paymentMethod, setPaymentMethod] = useState<'credit' | 'pix'>('credit');
    const [isProcessing, setIsProcessing] = useState(false);
    const router = useRouter();

    const handleCheckout = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsProcessing(true);

        // Simulate API call
        await new Promise(resolve => setTimeout(resolve, 2000));

        clearCart();
        router.push('/checkout/sucesso');
    };

    if (items.length === 0) {
        return (
            <div className="min-h-screen bg-gray-50 pt-32 pb-20 text-center">
                <h1 className="text-3xl font-bold mb-4">Seu carrinho está vazio</h1>
                <Button onClick={() => router.push('/produtos')}>Voltar para a Loja</Button>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-50 pt-24 pb-20">
            <div className="container mx-auto px-4">
                <h1 className="text-3xl font-bold mb-8">Finalizar Compra</h1>

                <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                    {/* Order Summary */}
                    <div className="lg:col-span-2 space-y-6">
                        <div className="bg-white p-6 rounded-lg shadow-sm">
                            <h2 className="text-xl font-bold mb-4">Itens do Pedido</h2>
                            <div className="space-y-4">
                                {items.map((item) => (
                                    <div key={`${item.id}-${item.size}`} className="flex gap-4 py-4 border-b last:border-0">
                                        <div
                                            className="w-20 h-20 bg-cover bg-center rounded-md bg-gray-100"
                                            style={{ backgroundImage: `url(${item.image})` }}
                                        />
                                        <div className="flex-1">
                                            <h3 className="font-bold">{item.name}</h3>
                                            <p className="text-sm text-gray-500">Tamanho: {item.size}</p>
                                            <p className="font-medium text-brand-red">
                                                {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(item.price)}
                                            </p>
                                        </div>
                                        <div className="flex flex-col items-end justify-between">
                                            <button
                                                onClick={() => removeItem(item.id)}
                                                className="text-gray-400 hover:text-red-500"
                                            >
                                                <Trash2 className="w-5 h-5" />
                                            </button>
                                            <div className="flex items-center gap-2">
                                                <button
                                                    onClick={() => updateQuantity(item.id, item.quantity - 1)}
                                                    className="w-6 h-6 rounded-full border flex items-center justify-center hover:bg-gray-100"
                                                >
                                                    -
                                                </button>
                                                <span className="w-8 text-center">{item.quantity}</span>
                                                <button
                                                    onClick={() => updateQuantity(item.id, item.quantity + 1)}
                                                    className="w-6 h-6 rounded-full border flex items-center justify-center hover:bg-gray-100"
                                                >
                                                    +
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>

                        <div className="bg-white p-6 rounded-lg shadow-sm">
                            <h2 className="text-xl font-bold mb-4">Dados de Entrega</h2>
                            <form className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <input type="text" placeholder="CEP" className="p-3 border rounded-md w-full" required />
                                <input type="text" placeholder="Rua" className="p-3 border rounded-md w-full md:col-span-2" required />
                                <input type="text" placeholder="Número" className="p-3 border rounded-md w-full" required />
                                <input type="text" placeholder="Complemento" className="p-3 border rounded-md w-full" />
                                <input type="text" placeholder="Cidade" className="p-3 border rounded-md w-full" required />
                                <input type="text" placeholder="Estado" className="p-3 border rounded-md w-full" required />
                            </form>
                        </div>
                    </div>

                    {/* Payment */}
                    <div className="lg:col-span-1">
                        <div className="bg-white p-6 rounded-lg shadow-sm sticky top-24">
                            <h2 className="text-xl font-bold mb-6">Pagamento</h2>

                            <div className="space-y-4 mb-8">
                                <button
                                    onClick={() => setPaymentMethod('credit')}
                                    className={`w-full p-4 border rounded-lg flex items-center gap-3 transition-colors ${paymentMethod === 'credit' ? 'border-brand-red bg-red-50 text-brand-red' : 'hover:bg-gray-50'
                                        }`}
                                >
                                    <CreditCard className="w-5 h-5" />
                                    Cartão de Crédito
                                </button>
                                <button
                                    onClick={() => setPaymentMethod('pix')}
                                    className={`w-full p-4 border rounded-lg flex items-center gap-3 transition-colors ${paymentMethod === 'pix' ? 'border-brand-red bg-red-50 text-brand-red' : 'hover:bg-gray-50'
                                        }`}
                                >
                                    <QrCode className="w-5 h-5" />
                                    Pix
                                </button>
                            </div>

                            <div className="space-y-2 mb-6 text-sm">
                                <div className="flex justify-between">
                                    <span className="text-gray-600">Subtotal</span>
                                    <span>{new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(total)}</span>
                                </div>
                                <div className="flex justify-between">
                                    <span className="text-gray-600">Frete</span>
                                    <span className="text-green-600">Grátis</span>
                                </div>
                                <div className="flex justify-between font-bold text-lg pt-4 border-t">
                                    <span>Total</span>
                                    <span>{new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(total)}</span>
                                </div>
                            </div>

                            <Button
                                onClick={handleCheckout}
                                className="w-full py-6 text-lg"
                                disabled={isProcessing}
                            >
                                {isProcessing ? 'Processando...' : 'Finalizar Compra'}
                            </Button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
