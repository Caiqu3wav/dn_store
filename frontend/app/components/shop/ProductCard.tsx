'use client';

import Link from 'next/link';
import { motion } from 'framer-motion';
import { ShoppingCart } from 'lucide-react';
import { Button } from '../ui/Button';

interface ProductProps {
    id: string;
    name: string;
    price: number;
    image: string;
    category: string;
}

export function ProductCard({ product }: { product: ProductProps }) {
    return (
        <motion.div
            whileHover={{ y: -5 }}
            className="group relative bg-white rounded-lg overflow-hidden shadow-sm hover:shadow-md transition-all"
        >
            <Link href={`/produto/${product.id}`} className="block relative aspect-[4/5] overflow-hidden bg-gray-100">
                <div
                    className="absolute inset-0 bg-cover bg-center transition-transform duration-500 group-hover:scale-105"
                    style={{ backgroundImage: `url(${product.image})` }}
                />
                {/* Quick Add Overlay */}
                <div className="absolute inset-x-0 bottom-0 p-4 translate-y-full group-hover:translate-y-0 transition-transform duration-300 bg-gradient-to-t from-black/80 to-transparent">
                    <Button size="sm" className="w-full bg-brand-red hover:bg-red-700 text-white border-none">
                        <ShoppingCart className="w-4 h-4 mr-2" />
                        Adicionar
                    </Button>
                </div>
            </Link>

            <div className="p-4">
                <p className="text-xs text-gray-500 mb-1 uppercase tracking-wider">{product.category}</p>
                <Link href={`/produto/${product.id}`}>
                    <h3 className="font-bold text-gray-900 mb-2 group-hover:text-brand-red transition-colors line-clamp-1">
                        {product.name}
                    </h3>
                </Link>
                <p className="text-lg font-bold text-gray-900">
                    {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(product.price)}
                </p>
            </div>
        </motion.div>
    );
}
