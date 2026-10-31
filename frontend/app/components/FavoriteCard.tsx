'use client';

import Image from 'next/image';
import Link from 'next/link';
import { Trash2, ShoppingCart } from 'lucide-react';
import { Product } from '@/types';
import { useFavorites } from '@/app/context/FavoritesContext';
import { useCart } from '@/app/context/CartContext';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { getProductImage } from '@/utils/productImage';

interface FavoriteCardProps {
    product: Product;
}

export function FavoriteCard({ product }: FavoriteCardProps) {
    const { removeFavorite } = useFavorites();
    const { addItem } = useCart();
    const [isAdded, setIsAdded] = useState(false);
    const router = useRouter();
    const image = getProductImage(product);

    const catName = typeof product.category === 'object' && product.category !== null 
        ? product.category.name 
        : (product.category || 'Geral');

    const handleAddToCart = () => {
        const activeVariants = product.variants?.filter(variant => variant.active) ?? [];
        const variant = activeVariants.length === 1 && activeVariants[0].stock > 0
            ? activeVariants[0]
            : undefined;
        if (!variant) {
            router.push(`/produtos/${product.id}`);
            return;
        }
        const added = addItem({
            id: variant.id,
            productId: product.id,
            productVariantId: variant.id,
            availableStock: variant.stock,
            name: product.name,
            price: product.price,
            image: image ?? '',
            quantity: 1,
            size: variant.size,
            category: catName,
        });
        if (!added) {
            router.push(`/produtos/${product.id}`);
            return;
        }
        
        setIsAdded(true);
        setTimeout(() => setIsAdded(false), 2000);
    };

    const handleRemoveFavorite = () => {
        removeFavorite(product.id);
    };

    return (
        <div className="bg-white border border-gray-200 rounded-lg overflow-hidden hover:shadow-lg transition-shadow duration-300 flex flex-col h-full">
            {/* Product Image */}
            <Link href={`/produtos/${product.id}`} className="relative w-full h-48 overflow-hidden bg-white group">
                {image ? (
                    <Image
                        src={image}
                        alt={product.name}
                        fill
                        className="object-cover group-hover:scale-105 transition-transform duration-300"
                    />
                ) : (
                    <span className="absolute inset-0 flex items-center justify-center text-sm text-gray-500">
                        Imagem indisponível
                    </span>
                )}
                <button
                    onClick={(e) => {
                        e.preventDefault();
                        handleRemoveFavorite();
                    }}
                    className="absolute top-2 right-2 bg-red-500 hover:bg-red-600 p-2 rounded-full transition-colors"
                    aria-label="Remover dos favoritos"
                >
                    <Trash2 className="w-4 h-4 text-black" />
                </button>
            </Link>

            {/* Product Info */}
            <div className="p-4 flex-grow flex flex-col">
                <Link href={`/produtos/${product.id}`} className="group">
                    <h3 className="text-sm font-semibold text-black group-hover:text-brand-secondary transition-colors mb-1 line-clamp-2">
                        {product.name}
                    </h3>
                </Link>

                <p className="text-xs text-black mb-2">{catName}</p>
                <p className="text-xs text-black mb-3">Cor: {product.color}</p>

               

                {/* Price */}
                <div className="mt-auto mb-3">
                    {product.promotionalPrice ? (
                        <>
                            <span className="text-xs line-through text-gray-400 block">R$ {product.price.toFixed(2)}</span>
                            <span className="text-lg font-bold text-green-600 block">
                                R$ {product.promotionalPrice.toFixed(2)}
                            </span>
                        </>
                    ) : (
                        <p className="text-lg font-bold text-black">
                            R$ {product.price.toFixed(2)}
                        </p>
                    )}
                </div>

                {/* Add to Cart Button */}
                <button
                    onClick={handleAddToCart}
                    className={`w-full py-2 px-3 rounded font-medium text-sm transition-all duration-300 flex items-center justify-center gap-2 ${
                        isAdded
                            ? 'bg-green-600 text-black'
                            : 'bg-brand-secondary hover:bg-brand-secondary/90 text-black disabled:bg-gray-600 disabled:cursor-not-allowed'
                    }`}
                >
                    <ShoppingCart className="w-4 h-4" />
                    {isAdded ? 'Adicionado!' : 'Adicionar / escolher tamanho'}
                </button>
            </div>
        </div>
    );
}
