'use client';

import { motion } from 'framer-motion';
import { CATEGORIES } from '../../../lib/data';
import Link from 'next/link';

export function Categories() {
    return (
        <section className="py-12 bg-white pb-24">
            <div className="container mx-auto px-4 lg:px-8">
                <div className="text-center mb-8">
                    <h3 className="text-xl font-bold text-brand uppercase tracking-widest">
                        Navegue por Categoria
                    </h3>
                </div>

                <div className="flex flex-wrap justify-center gap-4">
                    <Link href="/produtos" className="group">
                        <motion.span
                            whileHover={{ scale: 1.05 }}
                            whileTap={{ scale: 0.95 }}
                            className="inline-flex items-center justify-center px-6 py-3 rounded-full bg-background text-brand-primary font-bold transition-all hover:bg-brand-red-primary hover:text-white hover:shadow-lg"
                        >
                            Todos os Produtos
                        </motion.span>
                    </Link>

                    {CATEGORIES.map((category, index) => (
                        <Link key={category.id} href={`/produtos?categoria=${category.slug}`} className="group">
                            <motion.span
                                initial={{ opacity: 0, y: 10 }}
                                whileInView={{ opacity: 1, y: 0 }}
                                viewport={{ once: true }}
                                transition={{ delay: index * 0.1 }}
                                whileHover={{ scale: 1.05 }}
                                whileTap={{ scale: 0.95 }}
                                className="inline-flex items-center justify-center px-6 py-3 rounded-full bg-background text-brand-primary font-bold transition-all hover:bg-brand-red-primary hover:text-white hover:shadow-lg"
                            >
                                {category.name}
                            </motion.span>
                        </Link>
                    ))}
                </div>
            </div>
        </section>
    );
}
