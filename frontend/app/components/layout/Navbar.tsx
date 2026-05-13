'use client';

import Link from 'next/link';
import Image from 'next/image';
import { ShoppingCart, Menu, X, Search, User, Heart } from 'lucide-react';
import { useState, useEffect } from 'react';
import { cn } from '../ui/Button';
import { useCart } from '../../context/CartContext';

export function Navbar() {
    const [isScrolled, setIsScrolled] = useState(false);
    const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
    const { itemCount } = useCart();

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
                'fixed top-0 left-0 right-0 z-50 transition-all duration-300 border-b',
                isScrolled 
                    ? 'bg-[#1A1B1D] backdrop-blur-lg border-gray-200 py-3 shadow-sm' 
                    : 'bg-background border-transparent py-4'
            )}
        >
            <div className="container mx-auto px-4 lg:px-8 flex items-center justify-between gap-4">
                {/* 1. Logo */}
                <Link href="/" className="flex items-center gap-2 shrink-0">
                    <div className="relative w-10 h-10 overflow-hidden rounded-xl shadow-sm  flex items-center justify-center">
                        <Image 
                            src="/assets/Images/logo_transparente.png" 
                            alt="DN Store Logo" 
                            fill 
                            className="object-contain "
                        />
                    </div>
                    <span className="hidden sm:block text-xl font-bold tracking-tight text-brand-primary">
                        DN 
                    </span>
                    <span className="hidden sm:block text-xl font-bold tracking-tight text-brand-secondary">
                        STORE
                    </span>
                </Link>

                {/* 2. Search Bar (Central) */}
                <div className="hidden md:flex flex-1 max-w-md mx-4">
                    <div className="relative w-full">
                        <input
                            type="text"
                            placeholder="Buscar produtos..."
                            className="w-full bg-gray-100 border-transparent focus:bg-white focus:border-brand-secondary focus:ring-2 focus:ring-brand-secondary/20 rounded-full py-2.5 pl-11 pr-4 text-sm transition-all outline-none text-brand"
                        />
                        <Search className="w-4 h-4 text-gray-400 absolute left-4 top-1/2 -translate-y-1/2" />
                    </div>
                </div>

                {/* 3. Navigation & Icons */}
                <div className="flex items-center gap-6 md:gap-8">
                    {/* Desktop Links */}
                    <nav className="hidden md:flex items-center gap-6 text-sm font-medium text-white">
                        <Link href="/" className="hover:text-brand-secondary transition-colors">Home</Link>
                        <Link href="/produtos" className="hover:text-brand-secondary transition-colors">Produtos</Link>
                        <Link href="/eventos" className="hover:text-brand-secondary transition-colors">Eventos</Link>
                        <Link href="/sobre" className="hover:text-brand-secondary transition-colors">Sobre</Link>
                    </nav>

                    {/* Icons */}
                    <div className="flex items-center gap-4 text-white">
                        <Link href="/Auth" className="hidden sm:block hover:text-brand-secondary transition-colors" aria-label="Conta">
                            <User className="w-5 h-5" />
                        </Link>
                        <button className="hidden sm:block hover:text-brand-secondary transition-colors" aria-label="Favoritos">
                            <Heart className="w-5 h-5" />
                        </button>
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
                            className="md:hidden p-1 hover:bg-gray-100 rounded-md transition-colors"
                            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
                        >
                            {isMobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
                        </button>
                    </div>
                </div>
            </div>

            {/* Mobile Menu Dropdown */}
            {isMobileMenuOpen && (
                <div className="md:hidden absolute top-full left-0 right-0 bg-white border-b border-gray-200 shadow-lg py-4 px-4 flex flex-col gap-4">
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

                    <div className="flex items-center justify-around pt-4 pb-2">
                        <button className="flex flex-col items-center gap-1 text-white">
                            <Link href="/Auth">
                                <User className="w-5 h-5" />
                            </Link>
                            <span className="text-xs">Conta</span>
                        </button>
                        <button className="flex flex-col items-center gap-1 text-white">
                            <Heart className="w-5 h-5" />
                            <span className="text-xs">Favoritos</span>
                        </button>
                    </div>
                </div>
            )}
        </header>
    );
}
