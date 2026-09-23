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

interface Props {
    product: Product;
}

export default function ProductInteractive({
    product
}: Props) {
    const productImages = product.images?.length
        ? product.images.map((img) =>
            typeof img === 'string'
                ? img
                : img.imageUrl
        )
        : ['/assets/products/placeholder.png'];

    const sizes = product.size?.length
        ? product.size
        : ['Único'];

    const [selectedSize, setSelectedSize] = useState(
        sizes[0]
    );

    const [currentImage, setCurrentImage] = useState(0);

    const { addItem } = useCart();

    const handleAddToCart = () => {
        addItem({
            id: product.id,
            name: product.name,
            price: product.price,
            image:
                productImages[0] ??
                '/assets/products/placeholder.png',
            quantity: 1,
            size: selectedSize
        });

        alert('Produto adicionado ao carrinho!');
    };

    return (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-12">

            {/* Gallery */}
            <div className="space-y-4">

                <div className="aspect-square bg-gray-100 rounded-lg overflow-hidden relative">
                    <div
                        className="absolute inset-0 bg-cover bg-center"
                        style={{
                            backgroundImage: `url(${productImages[currentImage]})`
                        }}
                    />
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

                    <div className="flex gap-3">
                        {sizes.map((size) => (
                            <button
                                key={size}
                                type="button"
                                onClick={() =>
                                    setSelectedSize(size)
                                }
                                className={`
                                    w-12 h-12 rounded-full
                                    flex items-center justify-center
                                    border font-medium transition-all

                                    ${
                                        selectedSize === size
                                            ? 'bg-black text-white border-black'
                                            : 'bg-white text-black border-gray-200 hover:border-black'
                                    }
                                `}
                            >
                                {size}
                            </button>
                        ))}
                    </div>
                </div>

                {/* Actions */}
                <div className="flex gap-4 mb-8">

                    <Button
                        size="lg"
                        className="flex-1 text-lg h-14"
                        onClick={handleAddToCart}
                    >
                        <ShoppingCart className="w-5 h-5 mr-2" />

                        Adicionar ao Carrinho
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