'use client';

import Link from 'next/link';
import { Plus, Search, Edit, Trash2, Filter } from 'lucide-react';
import { useState } from 'react';

const mockProducts = [
    { id: '1', name: 'Camiseta Classic DN', category: 'Vestuário', price: 129.90, stock: 45, status: 'Ativo' },
    { id: '2', name: 'Jaqueta Corta-Vento Trail', category: 'Vestuário', price: 299.90, stock: 12, status: 'Ativo' },
    { id: '3', name: 'Calça Cargo Explorer', category: 'Vestuário', price: 199.90, stock: 0, status: 'Esgotado' },
    { id: '4', name: 'Boné Signature', category: 'Acessórios', price: 89.90, stock: 80, status: 'Ativo' },
    { id: '5', name: 'Mochila Trekking 40L', category: 'Equipamentos', price: 459.90, stock: 5, status: 'Ativo' },
];

export default function AdminProductsPage() {
    const [searchTerm, setSearchTerm] = useState('');

    const filteredProducts = mockProducts.filter(p => 
        p.name.toLowerCase().includes(searchTerm.toLowerCase())
    );

    return (
        <div className="space-y-6 text-white">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                    <h1 className="text-3xl font-bold">Produtos</h1>
                    <p className="text-gray-400 mt-1">Gerencie o catálogo da sua loja.</p>
                </div>
                <Link 
                    href="/admin/produtos/novo"
                    className="inline-flex items-center justify-center gap-2 px-4 py-2 bg-brand-secondary text-white font-medium rounded-lg hover:bg-red-700 transition-colors"
                >
                    <Plus className="w-5 h-5" />
                    Novo Produto
                </Link>
            </div>

            {/* Filters & Search */}
            <div className="flex flex-col md:flex-row gap-4">
                <div className="relative flex-1">
                    <input 
                        type="text" 
                        placeholder="Buscar produtos..." 
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        className="w-full bg-[#2A2A2A] border border-white/10 rounded-lg py-2.5 pl-10 pr-4 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary"
                    />
                    <Search className="w-4 h-4 text-gray-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
                </div>
                <button className="inline-flex items-center gap-2 px-4 py-2.5 bg-[#2A2A2A] border border-white/10 text-gray-300 rounded-lg hover:bg-[#333] transition-colors">
                    <Filter className="w-4 h-4" />
                    Filtros
                </button>
            </div>

            {/* Products Table */}
            <div className="bg-[#2A2A2A] border border-white/5 rounded-xl shadow-sm overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse">
                        <thead>
                            <tr className="bg-[#1A1B1D] text-gray-400 text-sm">
                                <th className="p-4 font-medium">Nome do Produto</th>
                                <th className="p-4 font-medium">Categoria</th>
                                <th className="p-4 font-medium">Preço</th>
                                <th className="p-4 font-medium">Estoque</th>
                                <th className="p-4 font-medium">Status</th>
                                <th className="p-4 font-medium text-right">Ações</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-white/5">
                            {filteredProducts.map((product) => (
                                <tr key={product.id} className="hover:bg-white/[0.02] transition-colors group">
                                    <td className="p-4 font-medium text-white">{product.name}</td>
                                    <td className="p-4 text-gray-400">{product.category}</td>
                                    <td className="p-4 text-white">
                                        {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(product.price)}
                                    </td>
                                    <td className="p-4 text-gray-300">
                                        {product.stock} un.
                                    </td>
                                    <td className="p-4">
                                        <span className={`px-2 py-1 text-xs font-medium rounded-full border ${
                                            product.status === 'Ativo' 
                                                ? 'bg-green-500/10 text-green-500 border-green-500/20' 
                                                : 'bg-red-500/10 text-red-500 border-red-500/20'
                                        }`}>
                                            {product.status}
                                        </span>
                                    </td>
                                    <td className="p-4 text-right">
                                        <div className="flex items-center justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                            <Link 
                                                href={`/admin/produtos/${product.id}`}
                                                className="p-2 text-gray-400 hover:text-white hover:bg-white/10 rounded-lg transition-colors"
                                            >
                                                <Edit className="w-4 h-4" />
                                            </Link>
                                            <button className="p-2 text-gray-400 hover:text-red-500 hover:bg-red-500/10 rounded-lg transition-colors">
                                                <Trash2 className="w-4 h-4" />
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                            {filteredProducts.length === 0 && (
                                <tr>
                                    <td colSpan={6} className="p-8 text-center text-gray-500">
                                        Nenhum produto encontrado.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
