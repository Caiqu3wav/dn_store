'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState, ReactNode } from 'react';
import { Product } from '@/types';
import { favoriteService } from '@/services/favoriteService';
import { useAuth } from './AuthContext';

interface FavoritesContextType {
    favorites: Product[];
    loading: boolean;
    error: string | null;
    refreshFavorites: () => Promise<void>;
    addFavorite: (product: Product) => Promise<void>;
    removeFavorite: (id: string) => Promise<void>;
    isFavorite: (id: string) => boolean;
    favoritesCount: number;
}

const FavoritesContext = createContext<FavoritesContextType | undefined>(undefined);

function getErrorMessage(error: unknown) {
    const responseData = (error as { response?: { data?: { message?: string } | string } }).response?.data;
    return typeof responseData === 'string'
        ? responseData
        : responseData?.message || (error instanceof Error ? error.message : 'Não foi possível atualizar os favoritos.');
}

export function FavoritesProvider({ children }: { children: ReactNode }) {
    const { user, loading: authLoading } = useAuth();
    const [favorites, setFavorites] = useState<Product[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const refreshFavorites = useCallback(async () => {
        if (!user) {
            setFavorites([]);
            setError(null);
            setLoading(false);
            return;
        }

        setLoading(true);
        setError(null);
        try {
            const response = await favoriteService.getFavorites();
            setFavorites(response.map(favorite => favorite.product));
        } catch (requestError) {
            setError(getErrorMessage(requestError));
        } finally {
            setLoading(false);
        }
    }, [user]);

    useEffect(() => {
        if (authLoading) return;
        let cancelled = false;

        const loadFavorites = async () => {
            if (!user) {
                setFavorites([]);
                setError(null);
                setLoading(false);
                return;
            }

            setLoading(true);
            setError(null);
            try {
                const response = await favoriteService.getFavorites();
                if (!cancelled) setFavorites(response.map(favorite => favorite.product));
            } catch (requestError) {
                if (!cancelled) setError(getErrorMessage(requestError));
            } finally {
                if (!cancelled) setLoading(false);
            }
        };

        void loadFavorites();
        return () => {
            cancelled = true;
        };
    }, [authLoading, user]);

    const addFavorite = useCallback(async (product: Product) => {
        if (!user) throw new Error('Entre na sua conta para favoritar produtos.');
        const favorite = await favoriteService.addFavorite(product.id);
        setFavorites(current => current.some(item => item.id === favorite.product.id)
            ? current
            : [...current, favorite.product]);
    }, [user]);

    const removeFavorite = useCallback(async (id: string) => {
        if (!user) throw new Error('Entre na sua conta para gerenciar seus favoritos.');
        await favoriteService.removeFavorite(id);
        setFavorites(current => current.filter(product => product.id !== id));
    }, [user]);

    const isFavorite = useCallback(
        (id: string) => favorites.some(product => product.id === id),
        [favorites]
    );
    const favoritesCount = favorites.length;

    const value = useMemo(() => ({
        favorites,
        loading,
        error,
        refreshFavorites,
        addFavorite,
        removeFavorite,
        isFavorite,
        favoritesCount,
    }), [favorites, loading, error, refreshFavorites, addFavorite, removeFavorite, isFavorite, favoritesCount]);

    return <FavoritesContext.Provider value={value}>{children}</FavoritesContext.Provider>;
}

export function useFavorites() {
    const context = useContext(FavoritesContext);
    if (context === undefined) {
        throw new Error('useFavorites must be used within a FavoritesProvider');
    }
    return context;
}
