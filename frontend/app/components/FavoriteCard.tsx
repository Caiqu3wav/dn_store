'use client';

import Image from 'next/image';
import Link from 'next/link';
import { Trash2, Heart, ShoppingCart } from 'lucide-react';
import { Product } from '@/types';
import { useFavorites } from '@/app/context/FavoritesContext';
import { useCart } from '@/app/context/CartContext';
import { useState } from 'react';

interface FavoriteCardProps {
    product: Product;
}

export function FavoriteCard({ product }: FavoriteCardProps) {
    const { removeFavorite } = useFavorites();
    const { addItem } = useCart();
    const [selectedSize, setSelectedSize] = useState<string>(product.size?.[0] || '');
    const [isAdded, setIsAdded] = useState(false);

    const catName = typeof product.category === 'object' && product.category !== null 
        ? product.category.name 
        : (product.category || 'Geral');

    const handleAddToCart = () => {
        if (!selectedSize) return;
        
        addItem({
            id: product.id,
            name: product.name,
            price: product.price,
            image: product.images?.[0]?.imageUrl || '/placeholder.png',
            quantity: 1,
            category: catName,
        });
        
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
                <Image
                    src={product.images?.[0]?.imageUrl || '/placeholder.png'}
                    alt={product.name}
                    fill
                    className="object-cover group-hover:scale-105 transition-transform duration-300"
                />
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
                    disabled={!selectedSize}
                    className={`w-full py-2 px-3 rounded font-medium text-sm transition-all duration-300 flex items-center justify-center gap-2 ${
                        isAdded
                            ? 'bg-green-600 text-black'
                            : 'bg-brand-secondary hover:bg-brand-secondary/90 text-black disabled:bg-gray-600 disabled:cursor-not-allowed'
                    }`}
                >
                    <ShoppingCart className="w-4 h-4" />
                    {isAdded ? 'Adicionado!' : 'Adicionar ao Carrinho'}
                </button>
            </div>
        </div>
    );
}
