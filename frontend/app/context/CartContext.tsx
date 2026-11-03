'use client';

import { createContext, useContext, useMemo, useSyncExternalStore, ReactNode } from 'react';

export interface CartItem {
    id: string;
    productId?: string;
    productVariantId?: string;
    availableStock?: number;
    name: string;
    price: number;
    image: string;
    quantity: number;
    size?: string;
    category?: string;
}

interface CartContextType {
    items: CartItem[];
    addItem: (item: CartItem) => boolean;
    removeItem: (id: string) => void;
    updateQuantity: (id: string, quantity: number) => void;
    clearCart: () => void;
    total: number;
    itemCount: number;
    updateSize: (id: string, size: string) => void;
}

const CartContext = createContext<CartContextType | undefined>(undefined);

const cartListeners = new Set<() => void>();
const SERVER_CART_SNAPSHOT = '[]';
const CART_STORAGE_KEY = 'dn-store-cart-v2';

function getCartSnapshot() {
    return typeof window === 'undefined'
        ? SERVER_CART_SNAPSHOT
        : localStorage.getItem(CART_STORAGE_KEY) ?? SERVER_CART_SNAPSHOT;
}

function subscribeToCart(listener: () => void) {
    if (typeof window === 'undefined') return () => {};
    cartListeners.add(listener);
    const handleStorage = () => listener();
    window.addEventListener('storage', handleStorage);
    return () => {
        cartListeners.delete(listener);
        window.removeEventListener('storage', handleStorage);
    };
}

function saveCart(items: CartItem[]) {
    localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(items));
    cartListeners.forEach(listener => listener());
}

function parseCart(snapshot: string): CartItem[] {
    try {
        const parsed: unknown = JSON.parse(snapshot);
        return Array.isArray(parsed) ? parsed.filter(isCartItem) : [];
    } catch (error) {
        console.error('Failed to parse cart', error);
        return [];
    }
}

function isCartItem(value: unknown): value is CartItem {
    if (typeof value !== 'object' || value === null
        || !('id' in value) || typeof value.id !== 'string'
        || !('name' in value) || typeof value.name !== 'string'
        || !('price' in value) || typeof value.price !== 'number'
        || !('image' in value) || typeof value.image !== 'string'
        || !('quantity' in value) || typeof value.quantity !== 'number') {
        return false;
    }
    return (!('size' in value) || value.size === undefined || typeof value.size === 'string')
        && (!('productId' in value) || value.productId === undefined || typeof value.productId === 'string')
        && (!('productVariantId' in value) || value.productVariantId === undefined
            || typeof value.productVariantId === 'string')
        && (!('availableStock' in value) || value.availableStock === undefined
            || typeof value.availableStock === 'number');
}

export function CartProvider({ children }: { children: ReactNode }) {
    const snapshot = useSyncExternalStore(subscribeToCart, getCartSnapshot, () => SERVER_CART_SNAPSHOT);
    const items = useMemo(() => parseCart(snapshot), [snapshot]);

    const key = (item: CartItem) => item.productVariantId
        ?? `${item.productId ?? item.id}:${item.size ?? ''}`;

    const addItem = (newItem: CartItem) => {
        const existingItem = items.find((item) => key(item) === key(newItem));
        if (newItem.availableStock != null
            && (existingItem?.quantity ?? 0) + newItem.quantity > newItem.availableStock) {
            return false;
        }
        if (existingItem) {
            saveCart(items.map((item) =>
                key(item) === key(newItem)
                    ? { ...item, quantity: item.quantity + newItem.quantity }
                    : item
            ));
        } else {
            saveCart([...items, newItem]);
        }
        return true;
    };

    const removeItem = (id: string) => {
        saveCart(items.filter((item) => item.id !== id));
    };

    const updateQuantity = (id: string, quantity: number) => {
        if (quantity < 1) return;
        saveCart(items.map((item) => (item.id === id
                ? { ...item, quantity: item.availableStock == null ? quantity : Math.min(quantity, item.availableStock) }
                : item)));
    };

    const updateSize = (id: string, size: string) => {
        saveCart(items.map((item) =>
                item.id === id
                    ? { ...item, size }
                    : item
            ));
    };

    const clearCart = () => {
        saveCart([]);
    };

    const total = items.reduce((sum, item) => sum + item.price * item.quantity, 0);
    const itemCount = items.reduce((sum, item) => sum + item.quantity, 0);

    return (
        <CartContext.Provider value={{ items, addItem, removeItem, updateQuantity, updateSize, clearCart, total, itemCount }}>
            {children}
        </CartContext.Provider>
    );
}

export function useCart() {
    const context = useContext(CartContext);
    if (context === undefined) {
        throw new Error('useCart must be used within a CartProvider');
    }
    return context;
}
