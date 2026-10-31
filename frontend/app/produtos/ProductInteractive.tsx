'use client';

import { useState } from 'react';
import { Button } from '../components/ui/Button';
import { useCart } from '../context/CartContext';
import {
    ShoppingCart,
    Heart,
    Share2
} from 'lucide-react';

import { Product } from '../../types';
import { getProductGalleryImages } from '@/utils/productImage';

interface Props {
    product: Product;
}

export default function ProductInteractive({
    product
}: Props) {
    const productImages = getProductGalleryImages(product);

    const variants = product.variants?.filter(variant => variant.active) ?? [];
    const [selectedVariantId, setSelectedVariantId] = useState(
        variants.find(variant => variant.active && variant.stock > 0)?.id ?? ''
    );
    const selectedVariant = variants.find(variant => variant.id === selectedVariantId);

    const [currentImage, setCurrentImage] = useState(0);

    const { addItem } = useCart();

    const handleAddToCart = () => {
        if (!selectedVariant || !selectedVariant.active || selectedVariant.stock < 1) return;
        const added = addItem({
            id: selectedVariant.id,
            productId: product.id,
            productVariantId: selectedVariant.id,
            availableStock: selectedVariant.stock,
            name: product.name,
            price: product.price,
            image: productImages[0] ?? '',
            quantity: 1,
            size: selectedVariant.size
        });

        alert(added ? 'Produto adicionado ao carrinho!' : 'Estoque insuficiente para o tamanho selecionado.');
    };

    return (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-12">

            {/* Gallery */}
            <div className="space-y-4">

                <div className="aspect-square bg-gray-100 rounded-lg overflow-hidden relative">
                    {productImages[currentImage] ? (
                        <div
                            className="absolute inset-0 bg-cover bg-center"
                            style={{ backgroundImage: `url(${productImages[currentImage]})` }}
                        />
                    ) : (
                        <div className="absolute inset-0 flex items-center justify-center text-sm text-gray-500">
                            Fotografia do produto indisponível
                        </div>
                    )}
                </div>

                {productImages.length > 1 && (
                    <div className="flex gap-4 overflow-x-auto pb-2">
                        {productImages.map((img, index) => (
                            <button
                                key={img}
                                type="button"
                                onClick={() =>
                                    setCurrentImage(index)
                                }
                                className={`
                                    w-20 h-20 shrink-0
                                    rounded-md overflow-hidden border-2
                                    ${
                                        currentImage === index
                                            ? 'border-brand-red'
                                            : 'border-transparent'
                                    }
                                `}
                            >
                                <div
                                    className="w-full h-full bg-cover bg-center"
                                    style={{
                                        backgroundImage: `url(${img})`
                                    }}
                                />
                            </button>
                        ))}
                    </div>
                )}

            </div>

            {/* Product info */}
            <div>

                <span className="text-brand-red font-bold tracking-wider uppercase text-sm">
                    {typeof product.category === 'object' &&
                    product.category !== null
                        ? product.category.name
                        : product.category || 'Geral'}
                </span>

                <h1 className="text-4xl font-bold mt-2 mb-4">
                    {product.name}
                </h1>

                <p className="text-xl font-bold mb-6">
                    {new Intl.NumberFormat(
                        'pt-BR',
                        {
                            style: 'currency',
                            currency: 'BRL'
                        }
                    ).format(product.price)}
                </p>

                <div className="prose prose-gray mb-8">
                    <p>{product.description}</p>
                </div>

                {/* Sizes */}
                <div className="mb-8">
                    <h3 className="font-bold mb-3">
                        Tamanho
                    </h3>

                    <div className="flex flex-wrap gap-3">
                        {variants.map((variant) => {
                            const available = variant.active && variant.stock > 0;
                            return (
                                <button
                                    key={variant.id}
                                    type="button"
                                    disabled={!available}
                                    aria-pressed={selectedVariantId === variant.id}
                                    aria-label={`${variant.size}${available ? '' : ', esgotado'}`}
                                    onClick={() => setSelectedVariantId(variant.id)}
                                    className={`
                                        min-w-12 min-h-12 px-3 rounded-full
                                        inline-flex flex-col items-center justify-center
                                        border font-medium transition-all
                                        ${
                                            selectedVariantId === variant.id
                                                ? 'bg-black text-white border-black'
                                                : available
                                                    ? 'bg-white text-black border-gray-200 hover:border-black'
                                                    : 'bg-gray-100 text-gray-400 border-gray-200 cursor-not-allowed'
                                        }
                                    `}
                                >
                                    <span className={!available ? 'line-through' : ''}>{variant.size}</span>
                                    {!available && <span className="text-[9px] uppercase">Esgotado</span>}
                                </button>
                            );
                        })}
                    </div>
                    {variants.length === 0 && (
                        <p role="status" className="text-sm text-amber-700">Tamanhos ainda não cadastrados para este produto.</p>
                    )}
                    {selectedVariant && selectedVariant.stock > 0 && (
                        <p className="mt-2 text-sm text-gray-500">Disponível: {selectedVariant.stock}</p>
                    )}
                </div>

                {/* Actions */}
                <div className="flex gap-4 mb-8">

                    <Button
                        size="lg"
                        className="flex-1 text-lg h-14"
                        disabled={!selectedVariant || !selectedVariant.active || selectedVariant.stock < 1}
                        onClick={handleAddToCart}
                    >
                        <ShoppingCart className="w-5 h-5 mr-2" />

                        {selectedVariant && selectedVariant.stock > 0 ? 'Adicionar ao Carrinho' : 'Indisponível'}
                    </Button>

                    <Button
                        variant="outline"
                        size="lg"
                        className="h-14 w-14 p-0"
                    >
                        <Heart className="w-6 h-6" />
                    </Button>

                </div>

                <div className="flex items-center gap-2 text-sm text-gray-500">
                    <Share2 className="w-4 h-4" />

                    Compartilhar este produto
                </div>

            </div>

        </div>
    );
}