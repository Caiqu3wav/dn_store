'use client';

import { useFavorites } from '@/app/context/FavoritesContext';
import { FavoriteCard } from '@/app/components/FavoriteCard';
import Link from 'next/link';
import { Heart } from 'lucide-react';

export default function FavoritosPage() {
    const { favorites, favoritesCount } = useFavorites();

    return (
        <div className="min-h-screen bg-white pt-32 pb-16">
            <div className="container mx-auto px-4 lg:px-8">
                {/* Header */}
                <div className="mb-12">
                    <div className="flex items-center gap-3 mb-4">
                        <Heart className="w-8 h-8 text-brand-secondary fill-brand-secondary" />
                        <h1 className="text-4xl font-bold text-black">Meus Favoritos</h1>
                    </div>
                    <p className="text-gray-400">
                        {favoritesCount} {favoritesCount === 1 ? 'item' : 'itens'} marcado{favoritesCount === 1 ? '' : 's'}
                    </p>
                </div>

                {/* Content */}
                {favoritesCount === 0 ? (
                    <div className="text-center py-20">
                        <Heart className="w-16 h-16 text-gray-600 mx-auto mb-6 opacity-50" />
                        <h2 className="text-2xl font-bold text-black-300 mb-3">Nenhum favorito ainda</h2>
                        <p className="text-gray-500 mb-8">
                            Clique no ícone de coração nos produtos para marcá-los como favoritos
                        </p>
                        <Link
                            href="/produtos"
                            className="inline-block bg-brand-secondary hover:bg-brand-secondary/90 text-white font-semibold py-3 px-8 rounded-lg transition-colors"
                        >
                            Explorar Produtos
                        </Link>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                        {favorites.map((product) => (
                            <FavoriteCard key={product.id} product={product} />
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
