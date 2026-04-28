'use client';

import { useState } from 'react';
import { ProductCard } from '../components/ui/ProductCard';
import { Button } from '../components/ui/Button';
import { Filter, X } from 'lucide-react';

// Mock data (expanded)
const products = [
    {
        id: '1',
        name: 'Camisa Performance Trail',
        price: 189.90,
        category: 'Roupas',
        image: 'https://images.unsplash.com/photo-1578632292335-df3abbb0d586?q=80&w=1974&auto=format&fit=crop'
    },
    {
        id: '2',
        name: 'Capacete Enduro Pro',
        price: 459.90,
        category: 'Acessórios',
        image: 'https://images.unsplash.com/photo-1559348349-86f163cc8cef?q=80&w=2070&auto=format&fit=crop'
    },
    {
        id: '3',
        name: 'Luva Grip Master',
        price: 89.90,
        category: 'Acessórios',
        image: 'https://images.unsplash.com/photo-1591195853828-11db59a44f6b?q=80&w=2070&auto=format&fit=crop'
    },
    {
        id: '4',
        name: 'Bermuda MTB Comfort',
        price: 229.90,
        category: 'Roupas',
        image: 'https://images.unsplash.com/photo-1544117519-31a4b719223d?q=80&w=1952&auto=format&fit=crop'
    },
    {
        id: '5',
        name: 'Jaqueta Corta-Vento',
        price: 329.90,
        category: 'Roupas',
        image: 'https://images.unsplash.com/photo-1551488852-0801751acbe3?q=80&w=2070&auto=format&fit=crop'
    },
    {
        id: '6',
        name: 'Sapatilha Clip Pro',
        price: 599.90,
        category: 'Acessórios',
        image: 'https://images.unsplash.com/photo-1511556532299-8f662fc26c06?q=80&w=2070&auto=format&fit=crop'
    }
];

const categories = ['Todos', 'Roupas', 'Acessórios', 'Peças', 'Bicicletas'];

export default function StorePage() {
    const [selectedCategory, setSelectedCategory] = useState('Todos');
    const [isFilterOpen, setIsFilterOpen] = useState(false);

    const filteredProducts = selectedCategory === 'Todos'
        ? products
        : products.filter(p => p.category === selectedCategory);

    return (
        <div className="min-h-screen bg-gray-50 pt-24 pb-20">
            <div className="container mx-auto px-4">
                <div className="flex flex-col md:flex-row justify-between items-center mb-8">
                    <h1 className="text-4xl font-bold mb-4 md:mb-0">Loja Oficial</h1>

                    <div className="flex items-center gap-4">
                        <span className="text-gray-500">{filteredProducts.length} produtos</span>
                        <Button
                            variant="outline"
                            className="md:hidden"
                            onClick={() => setIsFilterOpen(!isFilterOpen)}
                        >
                            <Filter className="w-4 h-4 mr-2" /> Filtros
                        </Button>
                    </div>
                </div>

                <div className="flex flex-col md:flex-row gap-8">
                    {/* Sidebar Filters */}
                    <aside className={`
            md:w-64 flex-shrink-0 
            ${isFilterOpen ? 'block' : 'hidden md:block'}
          `}>
                        <div className="bg-white p-6 rounded-lg shadow-sm sticky top-24">
                            <div className="flex justify-between items-center mb-6 md:hidden">
                                <h3 className="font-bold text-lg">Filtros</h3>
                                <button onClick={() => setIsFilterOpen(false)}><X className="w-5 h-5" /></button>
                            </div>

                            <h3 className="font-bold text-lg mb-4">Categorias</h3>
                            <div className="space-y-2">
                                {categories.map(category => (
                                    <button
                                        key={category}
                                        onClick={() => setSelectedCategory(category)}
                                        className={`block w-full text-left px-3 py-2 rounded-md transition-colors ${selectedCategory === category
                                                ? 'bg-brand-red text-white'
                                                : 'hover:bg-gray-100 text-gray-600'
                                            }`}
                                    >
                                        {category}
                                    </button>
                                ))}
                            </div>
                        </div>
                    </aside>

                    {/* Product Grid */}
                    <div className="flex-1">
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                            {products.map(product => (
                                <ProductCard key={product.id} product={product} />
                            ))}
                        </div>

                        {filteredProducts.length === 0 && (
                            <div className="text-center py-20">
                                <p className="text-gray-500 text-lg">Nenhum produto encontrado nesta categoria.</p>
                                <Button
                                    variant="outline"
                                    className="mt-4"
                                    onClick={() => setSelectedCategory('Todos')}
                                >
                                    Ver todos os produtos
                                </Button>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
