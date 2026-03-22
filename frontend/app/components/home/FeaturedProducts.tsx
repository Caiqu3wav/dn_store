'use client';

import { motion } from 'framer-motion';
import { FEATURED_PRODUCTS } from '../../../lib/data';
import { ProductCard } from '../ui/ProductCard';
import Link from 'next/link';
import { ArrowRight } from 'lucide-react';

export function FeaturedProducts() {
    return (
        <section id="destaques" className="py-24 bg-white mt-10">
            <div className="container mx-auto px-4 lg:px-8">
                <div className="flex flex-col md:flex-row md:items-end justify-between mb-12 gap-4">
                    <div>
                        <motion.h2 
                            initial={{ opacity: 0, x: -20 }}
                            whileInView={{ opacity: 1, x: 0 }}
                            viewport={{ once: true }}
                            className="text-3xl md:text-5xl font-black text-brand-primary tracking-tight mb-2"
                        >
                            Destaques Profissionais
                        </motion.h2>
                        <p className="text-gray-500 text-sm md:text-base">
                            Equipamentos essenciais aprovados pelos nossos atletas de elite.
                        </p>
                    </div>
                    
                    <Link href="/produtos" className="hidden md:flex items-center gap-2 text-brand-secondary font-bold hover:text-brand-primary transition-colors">
                        Ver loja completa
                        <ArrowRight className="w-5 h-5" />
                    </Link>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
                    {FEATURED_PRODUCTS.map((product, index) => (
                        <motion.div
                            key={product.id}
                            initial={{ opacity: 0, y: 20 }}
                            whileInView={{ opacity: 1, y: 0 }}
                            viewport={{ once: true }}
                            transition={{ delay: index * 0.1 }}
                            className="flex"
                        >
                            <ProductCard {...product} />
                        </motion.div>
                    ))}
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
