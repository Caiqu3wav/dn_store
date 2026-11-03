'use client';

import { useCart } from '../context/CartContext';
import { Button } from '../components/ui/Button';
import Link from 'next/link';
import { Trash2, ArrowRight, Minus, Plus } from 'lucide-react';
import { getProductImageUrl } from '@/utils/productImage';

export default function CartPage() {
    const { items, removeItem, updateQuantity, total } = useCart();

    if (items.length === 0) {
        return (
            <div className="min-h-screen bg-gray-50 pt-32 pb-20 text-center">
                <h1 className="text-3xl font-bold mb-4">Seu carrinho está vazio</h1>
                <p className="text-gray-600 mb-8">Adicione produtos para começar a comprar.</p>
                <Link href="/produtos">
                    <Button>Ir para a Loja</Button>
                </Link>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-50 pt-24 pb-20">
            <div className="container mx-auto px-4">
                <h1 style={{ fontFamily: 'DN'  }}
                className="text-4xl  mb-8">Carrinho de Compras</h1>

                <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                    <div className="lg:col-span-2 space-y-4">
                        {items.map((item) => {
                            const image = item.image || getProductImageUrl(item.name);
                            return (
                                <div key={item.productVariantId ?? `${item.productId ?? item.id}:${item.size ?? ''}`} className="bg-white p-4 rounded-lg shadow-sm flex flex-col gap-4 sm:flex-row sm:items-center">
                                    {image ? (
                                        <div
                                            className="w-20 h-20 sm:w-24 sm:h-24 bg-cover bg-center rounded-md bg-gray-100 flex-shrink-0"
                                            style={{ backgroundImage: `url(${image})` }}
                                        />
                                    ) : (
                                        <div className="w-20 h-20 sm:w-24 sm:h-24 rounded-md bg-gray-100 flex-shrink-0 flex items-center justify-center text-center text-xs text-gray-500" role="img" aria-label="Fotografia do produto indisponível">
                                            Imagem indisponível
                                        </div>
                                    )}
                                    <div className="min-w-0 flex-1">
                                        <h3 className="font-bold text-lg">{item.name}</h3>
                                        <p className="text-sm text-gray-500">Tamanho: {item.size}</p>
                                        <p className="font-bold text-brand-red mt-1">
                                            {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(item.price)}
                                        </p>
                                        <p className="mt-1 text-sm text-gray-600">
                                            Subtotal do item: {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(item.price * item.quantity)}
                                        </p>
                                    </div>
                                    <div className="flex items-center justify-between gap-4 sm:flex-col sm:items-end">
                                        <div className="flex items-center gap-2" role="group" aria-label={`Quantidade de ${item.name}${item.size ? `, tamanho ${item.size}` : ''}`}>
                                            <button
                                                type="button"
                                                onClick={() => updateQuantity(item, item.quantity - 1)}
                                                disabled={item.quantity <= 1 || (item.availableStock != null && item.availableStock < 1)}
                                                aria-label={`Diminuir quantidade de ${item.name}${item.size ? `, tamanho ${item.size}` : ''}`}
                                                className="inline-flex h-11 w-11 items-center justify-center rounded-lg border border-gray-300 text-gray-800 transition-colors hover:bg-gray-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-red-primary disabled:cursor-not-allowed disabled:opacity-40"
                                            >
                                                <Minus aria-hidden="true" className="h-4 w-4" />
                                            </button>
                                            <output
                                                aria-label={`Quantidade atual de ${item.name}${item.size ? `, tamanho ${item.size}` : ''}`}
                                                aria-live="polite"
                                                className="min-w-8 text-center font-semibold tabular-nums"
                                            >
                                                {item.quantity}
                                            </output>
                                            <button
                                                type="button"
                                                onClick={() => updateQuantity(item, item.quantity + 1)}
                                                disabled={item.availableStock != null && (item.availableStock < 1 || item.quantity >= item.availableStock)}
                                                aria-label={`Aumentar quantidade de ${item.name}${item.size ? `, tamanho ${item.size}` : ''}`}
                                                className="inline-flex h-11 w-11 items-center justify-center rounded-lg border border-gray-300 text-gray-800 transition-colors hover:bg-gray-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-red-primary disabled:cursor-not-allowed disabled:opacity-40"
                                            >
                                                <Plus aria-hidden="true" className="h-4 w-4" />
                                            </button>
                                        </div>
                                        <button
                                            type="button"
                                            onClick={() => removeItem(item)}
                                            aria-label={`Remover ${item.name}${item.size ? `, tamanho ${item.size}` : ''} do carrinho`}
                                            className="inline-flex h-11 w-11 items-center justify-center rounded-lg text-gray-500 transition-colors hover:bg-red-50 hover:text-red-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600"
                                        >
                                            <Trash2 aria-hidden="true" className="w-5 h-5" />
                                        </button>
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    <div className="lg:col-span-1">
                        <div className="bg-white p-6 rounded-lg shadow-sm sticky top-24">
                            <h2 className="text-xl font-bold mb-6">Resumo do Pedido</h2>

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

                            <Link href="/checkout">
                                <Button className="w-full py-6 text-lg text-white">
                                    Finalizar Compra <ArrowRight className="w-5 h-5 ml-2" />
                                </Button>
                            </Link>

                            <Link href="/produtos">
                                <Button variant="outline" className="w-full mt-4">
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
