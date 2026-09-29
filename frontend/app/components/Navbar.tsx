'use client';

import Link from 'next/link';
import Image from 'next/image';
import { ShoppingCart, Menu, X, Search, UserRound, Heart, ChevronDown, Package, MapPin, LogOut, LogIn, UserPlus, ShieldCheck } from 'lucide-react';
import { useState, useEffect, useRef } from 'react';
import { cn } from './ui/Button';
import { useCart } from '../context/CartContext';
import { useFavorites } from '../context/FavoritesContext';
import { useAuth } from '../context/AuthContext';

function AccountDropdown({ mobile = false, onNavigate }: { mobile?: boolean; onNavigate?: () => void }) {
    const { user, logout } = useAuth();
    const [isOpen, setIsOpen] = useState(false);
    const dropdownRef = useRef<HTMLDivElement>(null);
    const menuId = mobile ? 'mobile-account-menu' : 'desktop-account-menu';

    useEffect(() => {
        if (!isOpen) return;

        const closeOnOutsideClick = (event: PointerEvent) => {
            if (!dropdownRef.current?.contains(event.target as Node)) setIsOpen(false);
        };
        const closeOnEscape = (event: KeyboardEvent) => {
            if (event.key === 'Escape') setIsOpen(false);
        };

        document.addEventListener('pointerdown', closeOnOutsideClick);
        document.addEventListener('keydown', closeOnEscape);
        return () => {
            document.removeEventListener('pointerdown', closeOnOutsideClick);
            document.removeEventListener('keydown', closeOnEscape);
        };
    }, [isOpen]);

    const itemClass = mobile
        ? 'flex w-full items-center gap-3 px-4 py-3 text-sm text-gray-700 hover:bg-gray-50'
        : 'flex w-full items-center gap-3 px-4 py-3 text-sm text-gray-700 hover:bg-gray-50';
    const closeMenu = () => {
        setIsOpen(false);
        onNavigate?.();
    };

    return (
        <div ref={dropdownRef} className={mobile ? 'w-full' : 'relative'}>
            <button
                type="button"
                aria-label="Opções da conta"
                aria-expanded={isOpen}
                aria-controls={menuId}
                onClick={() => setIsOpen(open => !open)}
                className={mobile
                    ? 'flex w-full items-center justify-between rounded-lg px-3 py-3 text-gray-800 hover:bg-gray-50'
                    : 'flex items-center gap-1 rounded-md p-2 text-white transition-colors hover:text-brand-secondary'}
            >
                <span className={mobile ? 'flex items-center gap-3' : 'flex items-center gap-1'}>
                    <UserRound className="h-5 w-5" />
                    {mobile && <span className="font-medium">{user ? 'Minha conta' : 'Entrar ou cadastrar'}</span>}
                </span>
                <ChevronDown className={cn('h-4 w-4 transition-transform', isOpen && 'rotate-180')} />
            </button>

            {isOpen && (
                <div
                    id={menuId}
                    className={mobile
                        ? 'mt-1 overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm'
                        : 'absolute right-0 top-full z-50 mt-2 w-56 overflow-hidden rounded-lg border border-gray-200 bg-white py-1 text-gray-800 shadow-lg'}
                >
                    {user ? (
                        <>
                            <div className="border-b border-gray-100 px-4 py-3">
                                <p className="truncate text-sm font-semibold text-gray-900">{user.name}</p>
                                <p className="truncate text-xs text-gray-500">{user.email}</p>
                            </div>
                            <Link href="/conta#dados" className={itemClass} onClick={closeMenu}>
                                <UserRound className="h-4 w-4" /> Meus dados
                            </Link>
                            <Link href="/conta#pedidos" className={itemClass} onClick={closeMenu}>
                                <Package className="h-4 w-4" /> Meus pedidos
                            </Link>
                            <Link href="/conta#enderecos" className={itemClass} onClick={closeMenu}>
                                <MapPin className="h-4 w-4" /> Meus endereços
                            </Link>
                            <Link href="/favoritos" className={itemClass} onClick={closeMenu}>
                                <Heart className="h-4 w-4" /> Favoritos
                            </Link>
                            {user.role === 'ADMIN' && (
                                <Link href="/admin" className={itemClass} onClick={closeMenu}>
                                    <ShieldCheck className="h-4 w-4" /> Painel administrativo
                                </Link>
                            )}
                            <div className="border-t border-gray-100">
                                <button
                                    type="button"
                                    onClick={() => { closeMenu(); logout(); }}
                                    className={`${itemClass} text-red-600 hover:bg-red-50`}
                                >
                                    <LogOut className="h-4 w-4" /> Sair
                                </button>
                            </div>
                        </>
                    ) : (
                        <>
                            <Link href="/auth" className={itemClass} onClick={closeMenu}>
                                <LogIn className="h-4 w-4" /> Entrar
                            </Link>
                            <Link href="/auth?mode=register" className={itemClass} onClick={closeMenu}>
                                <UserPlus className="h-4 w-4" /> Criar conta
                            </Link>
                        </>
                    )}
                </div>
            )}
        </div>
    );
}

export function Navbar() {
    const [isScrolled, setIsScrolled] = useState(false);
    const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
    const { itemCount } = useCart();
    const { favoritesCount } = useFavorites();

    useEffect(() => {
        const handleScroll = () => {
            setIsScrolled(window.scrollY > 20);
        };
        window.addEventListener('scroll', handleScroll);
        return () => window.removeEventListener('scroll', handleScroll);
    }, []);

    return (
        <header
            className={cn(
                'sticky top-0 left-0 right-0 z-50 transition-all duration-300 border-b',
                isScrolled 
                    ? 'bg-foreground/80 backdrop-blur-lg border-gray-200 py-3 shadow-sm' 
                    : 'bg-background border-transparent py-4'
            )}
        >
           <div className="container mx-auto px-4 lg:px-8 relative flex items-center justify-between gap-4 h-12">
                {/* 1. Logo */}
                  <Link href="/" className="flex items-center gap-1 sm:gap-2 shrink-0">
                      <div className="relative w-10 h-10 sm:w-15 sm:h-15 overflow-hidden rounded-xl shadow-sm flex items-center justify-center">
                        <Image 
                            src="/assets/Images/logo_transparente.png" 
                            alt="DN Store Logo" 
                            fill 
                            className="object-contain "
                        />
                    </div>
                    {/*  
                    <span className="hidden sm:block text-xl font-bold tracking-tight text-brand-primary">
                        DN
                    </span>
                    */}
                    <span 
                    style={{ fontFamily: 'DN'  }}
                    className="block text-lg sm:text-3xl tracking-tight text-outline text-brand-secondary whitespace-nowrap">
                        STORE
                    </span>
                </Link>

                {/* Search Bar Centralizada */}
<div className="
    mr-3
    flex
    min-w-0
    flex-1
    justify-center
    sm:mr-6
    md:w-[20rem]
    xl:mr-0
    xl:absolute
    xl:left-1/2
    xl:-translate-x-[20rem]
    xl:w-[28rem]
    xl:max-w-[calc(100%-32rem)]
"><div className="relative w-full">
                    <input 
                        type="text" 
                        placeholder="Buscar produtos..." 
                        className="w-full bg-gray-100 border-transparent focus:bg-white focus:border-brand-secondary focus:ring-2 focus:ring-brand-secondary/20 rounded-full py-2 sm:py-2.5 pl-9 sm:pl-11 pr-2 sm:pr-4 text-xs sm:text-sm transition-all outline-none text-brand"
                    />
                     <Search className="w-4 h-4 text-gray-400 absolute left-3 sm:left-4 top-1/2 -translate-y-1/2" />
                    </div>
                </div>

                {/* Navigation & Icons */}
                    <div className="flex items-center gap-4 xl:gap-6 ml-auto shrink-0">
                        
                    {/* Desktop Links */}
                    <nav className="hidden xl:flex items-center gap-6 text-sm font-medium text-white">
                        <Link href="/" className="hover:text-brand-secondary transition-colors">Home</Link>
                        <Link href="/produtos" className="hover:text-brand-secondary transition-colors">Produtos</Link>
                        <Link href="/eventos" className="hover:text-brand-secondary transition-colors">Eventos</Link>
                        <Link href="/sobre" className="hover:text-brand-secondary transition-colors">Sobre</Link>
                    </nav>

                    {/* Icons */}
                    <div className="flex items-center gap-4 text-white">
                        <div className="hidden sm:block">
                            <AccountDropdown />
                        </div>
                        <Link href='/favoritos' className="hidden sm:flex hover:text-brand-secondary transition-colors relative items-center" aria-label="Favoritos">
                            <Heart className="w-5 h-5" />
                            {favoritesCount > 0 && (
                                <span className="absolute -top-2 -right-2 bg-brand-secondary text-white text-[10px] font-bold rounded-full w-4 h-4 flex items-center justify-center">
                                    {favoritesCount}
                                </span>
                            )}
                        </Link>
                        <Link href="/carrinho" className="hover:text-brand-secondary transition-colors relative flex items-center" aria-label="Carrinho">
                            <ShoppingCart className="w-5 h-5" />
                            {itemCount > 0 && (
                                <span className="absolute -top-2 -right-2 bg-brand-secondary text-white text-[10px] font-bold rounded-full w-4 h-4 flex items-center justify-center">
                                    {itemCount}
                                </span>
                            )}
                        </Link>

                        {/* Mobile Menu Toggle */}
                        <button
                            className="xl:hidden p-1 hover:bg-gray-100 rounded-md transition-colors"
                            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
                        >
                            {isMobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
                        </button>
                    </div>
                </div>
            </div>

            {/* Mobile Menu Dropdown */}
            {isMobileMenuOpen && (
                <div className="xl:hidden absolute top-full left-0 right-0 bg-white border-b border-gray-200 shadow-lg py-4 px-4 flex flex-col gap-4">
                    <div className="relative w-full mb-2">
                        <input 
                            type="text" 
                            placeholder="Buscar produtos..." 
                            className="w-full bg-gray-100 rounded-full py-2.5 pl-11 pr-4 text-sm outline-none"
                        />
                        <Search className="w-4 h-4 text-gray-400 absolute left-4 top-1/2 -translate-y-1/2" />
                    </div>
                    <Link href="/" className="text-gray-800 font-medium py-2 border-b border-gray-100">Home</Link>
                    <Link href="/produtos" className="text-gray-800 font-medium py-2 border-b border-gray-100">Produtos</Link>
                    <Link href="/eventos" className="text-gray-800 font-medium py-2 border-b border-gray-100">Eventos</Link>
                    <Link href="/sobre" className="text-gray-800 font-medium py-2 border-b border-gray-100">Sobre</Link>
                    
                    <div className="border-t border-gray-100 pt-3">
                        <AccountDropdown mobile onNavigate={() => setIsMobileMenuOpen(false)} />
                    </div>
                </div>
            )}
        </header>
    );
}
