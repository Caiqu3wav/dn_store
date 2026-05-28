'use client';

import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { Product } from '@/types';

interface FavoritesContextType {
    favorites: Product[];
    addFavorite: (product: Product) => void;
    removeFavorite: (id: string) => void;
    isFavorite: (id: string) => boolean;
    favoritesCount: number;
}

const FavoritesContext = createContext<FavoritesContextType | undefined>(undefined);

export function FavoritesProvider({ children }: { children: ReactNode }) {
    const [favorites, setFavorites] = useState<Product[]>(() => {
        const savedFavorites = localStorage.getItem('favorites');
        if (savedFavorites) {
            try {
                return JSON.parse(savedFavorites);
            } catch (e) {
                console.error('Failed to parse favorites', e);
            }
        }
        return [];
    });

    // Save favorites to localStorage whenever they change
    useEffect(() => {
        localStorage.setItem('favorites', JSON.stringify(favorites));
    }, [favorites]);

    const addFavorite = (product: Product) => {
        setFavorites((currentFavorites) => {
            const exists = currentFavorites.find((fav) => fav.id === product.id);
            if (exists) {
                return currentFavorites;
            }
            return [...currentFavorites, product];
        });
    };

    const removeFavorite = (id: string) => {
        setFavorites((currentFavorites) =>
            currentFavorites.filter((fav) => fav.id !== id)
        );
    };

    const isFavorite = (id: string) => {
        return favorites.some((fav) => fav.id === id);
    };

    const favoritesCount = favorites.length;

    return (
        <FavoritesContext.Provider value={{ favorites, addFavorite, removeFavorite, isFavorite, favoritesCount }}>
            {children}
        </FavoritesContext.Provider>
    );
}

export function useFavorites() {
    const context = useContext(FavoritesContext);
    if (context === undefined) {
        throw new Error('useFavorites must be used within a FavoritesProvider');
    }
    return context;
}
