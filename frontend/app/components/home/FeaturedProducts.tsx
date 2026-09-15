'use client';

import { motion } from 'framer-motion';
import { useRef, useState, useEffect } from 'react';
import { ProductCard } from '../ui/ProductCard';
import useSWR from 'swr';
import { fetcher } from '@/services/productService';
import { Product } from '@/types';
import Link from 'next/link';
import { ArrowRight, ChevronLeft, ChevronRight } from 'lucide-react';

export function FeaturedProducts() {
    const scrollRef = useRef<HTMLDivElement>(null);
    const [canScrollLeft, setCanScrollLeft] = useState(false);
    const [canScrollRight, setCanScrollRight] = useState(false);

    // Mouse drag scroll state
    const [isDown, setIsDown] = useState(false);
    const [startX, setStartX] = useState(0);
    const [scrollLeftState, setScrollLeftState] = useState(0);

    const { data: productsData } = useSWR<Product[]>('/products', fetcher);
    const featuredProducts = productsData?.slice(0, 8) || [];

    const checkScroll = () => {
        if (scrollRef.current) {
            const { scrollLeft, scrollWidth, clientWidth } = scrollRef.current;
            setCanScrollLeft(scrollLeft > 0);
            setCanScrollRight(scrollLeft < scrollWidth - clientWidth - 1);
        }
    };

    useEffect(() => {
        checkScroll();
        const handleResize = () => checkScroll();
        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, [productsData, featuredProducts.length]);

    const scroll = (direction: 'left' | 'right') => {
        if (scrollRef.current) {
            const scrollAmount = 320; // Approximate card width + gap
            const newScrollLeft = scrollRef.current.scrollLeft + (direction === 'left' ? -scrollAmount : scrollAmount);
            scrollRef.current.scrollTo({
                left: newScrollLeft,
                behavior: 'smooth'
            });
        }
    };

    const handleMouseDown = (e: React.MouseEvent) => {
        if (!scrollRef.current) return;
        setIsDown(true);
        setStartX(e.pageX - scrollRef.current.offsetLeft);
        setScrollLeftState(scrollRef.current.scrollLeft);
    };

    const handleMouseLeave = () => {
        setIsDown(false);
    };

    const handleMouseUp = () => {
        setIsDown(false);
    };

    const handleMouseMove = (e: React.MouseEvent) => {
        if (!isDown || !scrollRef.current) return;
        e.preventDefault();
        const x = e.pageX - scrollRef.current.offsetLeft;
        const walk = (x - startX) * 1.5; // scroll speed
        scrollRef.current.scrollLeft = scrollLeftState - walk;
    };

    return (
        <section id="s" className="py-24 bg-white">
            <div className="container mx-auto px-4 lg:px-8">
                <div className="flex flex-col md:flex-row md:items-end justify-between mb-12 gap-4">
                    <div>
                        <motion.h2 
                            initial={{ opacity: 0, x: -20 }}
                            whileInView={{ opacity: 1, x: 0 }}
                            viewport={{ once: true }}
                            style={{ fontFamily: 'DN'  }}
                            className="text-3xl md:text-5xl text-brand tracking-tight mb-2"
                        >
                            Produtos em destaque
                        </motion.h2>
                        <p className="text-gray-500 text-sm md:text-base">
                            Equipamentos essenciais aprovados pelos nossos atletas de elite.
                        </p>
                    </div>
                    
                    <Link href="/produtos" className="hidden md:flex items-center gap-2 text-brand-red-primary font-bold hover:text-brand transition-colors">
                        Ver loja completa
                        <ArrowRight className="w-5 h-5" />
                    </Link>
                </div>

                <div className="relative">
                    {/* Left Arrow */}
                    {canScrollLeft && (
                        <button
                            onClick={() => scroll('left')}
                            className="absolute left-2 top-1/2 -translate-y-1/2 z-10 bg-white hover:bg-brand-secondary hover:text-white shadow-lg rounded-full p-2.5 transition-all duration-200 border border-gray-100 text-black"
                            aria-label="Scroll left"
                        >
                            <ChevronLeft className="w-6 h-6" />
                        </button>
                    )}

                    {/* Right Arrow */}
                    {canScrollRight && (
                        <button
                            onClick={() => scroll('right')}
                            className="absolute right-2 top-1/2 -translate-y-1/2 z-10 bg-white hover:bg-brand-secondary hover:text-white shadow-lg rounded-full p-2.5 transition-all duration-200 border border-gray-100 text-black"
                            aria-label="Scroll right"
                        >
                            <ChevronRight className="w-6 h-6" />
                        </button>
                    )}

                    {/* Carousel Container */}
                    <div
                        ref={scrollRef}
                        onScroll={checkScroll}
                        onMouseDown={handleMouseDown}
                        onMouseLeave={handleMouseLeave}
                        onMouseUp={handleMouseUp}
                        onMouseMove={handleMouseMove}
                        className={`flex gap-6 overflow-x-auto scrollbar-hide scroll-smooth pb-4 ${
                            isDown ? 'cursor-grabbing select-none' : 'cursor-grab'
                        }`}
                        style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
                    >
                        {featuredProducts.map((product, index) => (
                            <motion.div
                                key={product.id}
                                initial={{ opacity: 0, y: 20 }}
                                whileInView={{ opacity: 1, y: 0 }}
                                viewport={{ once: true }}
                                transition={{ delay: index * 0.1 }}
                                className="flex-shrink-0 w-80 pointer-events-auto"
                            >
                                <ProductCard product={product} />
                            </motion.div>
                        ))}
                    </div>
                </div>

                <div className="mt-12 text-center md:hidden">
                    <Link href="/produtos" className="inline-flex items-center gap-2 text-brand-secondary font-bold hover:text-brand-primary transition-colors">
                        Ver loja completa
                        <ArrowRight className="w-5 h-5" />
                    </Link>
                </div>
            </div>
        </section>
    );
}
