'use client';

import { useCart } from '../context/CartContext';
import { Button } from '../components/ui/Button';
import Link from 'next/link';
import { Trash2, ArrowRight } from 'lucide-react';

export default function CartPage() {
    const { items, removeItem, updateQuantity, total } = useCart();

    if (items.length === 0) {
        return (
            <div className="min-h-screen bg-background pt-32 pb-20 text-center text-foreground">
                <h1 className="text-3xl font-bold mb-4">Seu carrinho está vazio</h1>
                <p className="text-gray-400 mb-8">Adicione produtos para começar a comprar.</p>
                <Link href="/produtos">
                    <Button>Ir para a Loja</Button>
                </Link>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-background pt-24 pb-20 text-brand-primary">
            <div className="container mx-auto px-4">
                <h1 className="text-3xl font-bold mb-8">Carrinho de Compras</h1>

                <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                    <div className="lg:col-span-2 space-y-4">
                        {items.map((item) => (
                            <div key={`${item.id}-${item.size}`} className="bg-[#2A2A2A] border border-white/5 p-4 rounded-lg shadow-sm flex gap-4 items-center transition-all hover:border-white/10">
                                <div
                                    className="w-24 h-24 bg-cover bg-center rounded-md bg-black/40 flex-shrink-0"
                                    style={{ backgroundImage: `url(${item.image})` }}
                                />
                                <div className="flex-1">
                                    <h3 className="font-bold text-lg">{item.name}</h3>
                                    <p className="text-sm text-gray-400">Tamanho: {item.size}</p>
                                    <p className="font-bold text-brand-red mt-1">
                                        {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(item.price)}
                                    </p>
                                </div>
                                <div className="flex flex-col items-end gap-2">
                                    <button
                                        onClick={() => removeItem(item.id)}
                                        className="text-gray-400 hover:text-brand-secondary p-2 transition-colors"
                                    >
                                        <Trash2 className="w-5 h-5" />
                                    </button>
                                    <div className="flex items-center gap-2">
                                        <button
                                            onClick={() => updateQuantity(item.id, item.quantity - 1)}
                                            className="w-8 h-8 rounded-full border border-white/10 flex items-center justify-center hover:bg-white/10 transition-colors"
                                        >
                                            -
                                        </button>
                                        <span className="w-8 text-center font-medium">{item.quantity}</span>
                                        <button
                                            onClick={() => updateQuantity(item.id, item.quantity + 1)}
                                            className="w-8 h-8 rounded-full border border-white/10 flex items-center justify-center hover:bg-white/10 transition-colors"
                                        >
                                            +
                                        </button>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>

                    <div className="lg:col-span-1">
                        <div className="bg-[#2A2A2A] border border-white/5 p-6 rounded-lg shadow-sm sticky top-24">
                            <h2 className="text-xl font-bold mb-6">Resumo do Pedido</h2>

                            <div className="space-y-2 mb-6 text-sm">
                                <div className="flex justify-between">
                                    <span className="text-gray-400">Subtotal</span>
                                    <span>{new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(total)}</span>
                                </div>
                                <div className="flex justify-between">
                                    <span className="text-gray-400">Frete</span>
                                    <span className="text-brand-highlight">Grátis</span>
                                </div>
                                <div className="flex justify-between font-bold text-lg pt-4 border-t border-white/10">
                                    <span>Total</span>
                                    <span>{new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(total)}</span>
                                </div>
                            </div>

                            <Link href="/checkout">
                                <Button className="w-full py-6 text-lg">
                                    Finalizar Compra <ArrowRight className="w-5 h-5 ml-2" />
                                </Button>
                            </Link>

                            <Link href="/produtos">
                                <Button variant="outline" className="w-full mt-4 border-white/20 text-white hover:bg-white/10 hover:text-white">
                                    Continuar Comprando
                                </Button>
                            </Link>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
