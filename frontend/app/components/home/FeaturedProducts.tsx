'use client';

import { motion } from 'framer-motion';
import { useRef, useState, useEffect } from 'react';
import { FEATURED_PRODUCTS } from '../../../lib/data';
import { ProductCard } from '../ui/ProductCard';
import Link from 'next/link';
import { ArrowRight, ChevronLeft, ChevronRight } from 'lucide-react';

export function FeaturedProducts() {
    const scrollRef = useRef<HTMLDivElement>(null);
    const [canScrollLeft, setCanScrollLeft] = useState(false);
    const [canScrollRight, setCanScrollRight] = useState(false);

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
    }, []);

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

    return (
        <section id="s" className="py-24 bg-white">
            <div className="container mx-auto px-4 lg:px-8">
                <div className="flex flex-col md:flex-row md:items-end justify-between mb-12 gap-4">
                    <div>
                        <motion.h2 
                            initial={{ opacity: 0, x: -20 }}
                            whileInView={{ opacity: 1, x: 0 }}
                            viewport={{ once: true }}
                            className="text-3xl md:text-5xl font-black text-brand tracking-tight mb-2"
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
                            className="absolute left-0 top-1/2 -translate-y-1/2 z-10 bg-background hover:bg-white hover:text-foreground shadow-lg rounded-full p-2 transition-all duration-200"
                            aria-label="Scroll left"
                        >
                            <ChevronLeft className="w-6 h-6 text-brand-primary" />
                        </button>
                    )}

                    {/* Right Arrow */}
                    {canScrollRight && (
                        <button
                            onClick={() => scroll('right')}
                            className="absolute right-0 top-1/2 -translate-y-1/2 z-10 bg-background hover:bg-white hover:text-foreground shadow-lg rounded-full p-2 transition-all duration-200"
                            aria-label="Scroll right"
                        >
                            <ChevronRight className="w-6 h-6 text-brand-primary" />
                        </button>
                    )}

                    {/* Carousel Container */}
                    <div
                        ref={scrollRef}
                        onScroll={checkScroll}
                        className="flex gap-6 overflow-x-auto scrollbar-hide scroll-smooth pb-4"
                        style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
                    >
                        {FEATURED_PRODUCTS.map((product, index) => (
                            <motion.div
                                key={product.id}
                                initial={{ opacity: 0, y: 20 }}
                                whileInView={{ opacity: 1, y: 0 }}
                                viewport={{ once: true }}
                                transition={{ delay: index * 0.1 }}
                                className="flex-shrink-0 w-80"
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
