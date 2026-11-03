'use client';

import { motion } from 'framer-motion';
import Link from 'next/link';
import useSWR from 'swr';
import { fetcher } from '@/services/productService';
import { Category } from '@/types';

export function Categories() {
    const {
        data: categories,
        error,
        isLoading,
        mutate,
    } = useSWR<Category[]>('/categories', fetcher);

    return (
        <section className="py-12 bg-white pb-24">
            <div className="container mx-auto px-4 lg:px-8">
                <div className="text-center mb-8">
                    <h3 style={{ fontFamily: 'DN'  }}
                    className="text-3xl text-brand uppercase tracking-widest">
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

                    {isLoading ? (
                        <p role="status" className="w-full text-center text-sm text-gray-600">
                            Carregando categorias...
                        </p>
                    ) : error ? (
                        <div role="alert" className="w-full text-center text-sm text-red-700">
                            <p>Não foi possível carregar as categorias.</p>
                            <button
                                type="button"
                                onClick={() => void mutate()}
                                className="mt-2 font-semibold underline"
                            >
                                Tentar novamente
                            </button>
                        </div>
                    ) : categories?.length === 0 ? (
                        <p className="w-full text-center text-sm text-gray-600">
                            Nenhuma categoria disponível no momento.
                        </p>
                    ) : (
                    (categories ?? []).map((category, index) => (
                        <Link key={category.id} href={`/produtos?categoryId=${encodeURIComponent(category.id)}`} className="group">
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
                    )))}
                </div>
            </div>
        </section>
    );
}
