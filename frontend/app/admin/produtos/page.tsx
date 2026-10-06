'use client';

import Link from 'next/link';
import { Plus, Search, Edit, Trash2, Filter, X, Package, Image as ImageIcon } from 'lucide-react';
import { useState } from 'react';
import useSWR from 'swr';
import { fetcher, productService } from '@/services/productService';
import { Category, Product } from '@/types';

export default function AdminProductsPage() {
    const [searchTerm, setSearchTerm] = useState('');
    const [filtersOpen, setFiltersOpen] = useState(false);
    const [categoryId, setCategoryId] = useState('');
    const [minPrice, setMinPrice] = useState('');
    const [maxPrice, setMaxPrice] = useState('');
    const { data: categories } = useSWR<Category[]>('/categories', fetcher);

    const productQuery = new URLSearchParams();
    if (searchTerm) productQuery.set('search', searchTerm);
    if (categoryId) productQuery.set('categoryId', categoryId);
    if (minPrice) productQuery.set('minPrice', minPrice);
    if (maxPrice) productQuery.set('maxPrice', maxPrice);

    const { data: productsData, mutate, isLoading } = useSWR<Product[]>(
        `/products?${productQuery.toString()}`,
        fetcher
    );

    const filteredProducts = productsData || [];
    const activeFilters = [categoryId, minPrice, maxPrice].filter(Boolean).length;
    const resetFilters = () => {
        setCategoryId('');
        setMinPrice('');
        setMaxPrice('');
    };

    const getCategoryName = (product: Product) =>
        typeof product.category === 'object' && product.category !== null
            ? product.category.name
            : product.category || 'Sem categoria';

    const formatPrice = (price: number) =>
        new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(price);

    const handleDelete = async (id: string) => {
        if (confirm('Tem certeza que deseja remover este produto?')) {
            try {
                await productService.deleteProduct(id);
                mutate();
            } catch (error) {
                console.error("Erro ao deletar", error);
                alert('Erro ao deletar produto.');
            }
        }
    };

    return (
        <div className="flex h-[calc(100dvh-6rem)] min-h-96 flex-col gap-5 text-white lg:h-[calc(100dvh-4rem)]">
            <header className="flex flex-col gap-4 border-b border-white/10 pb-5 sm:flex-row sm:items-center sm:justify-between">
                <div>
                    <p className="mb-1 text-xs font-semibold uppercase tracking-wider text-brand-secondary">Catálogo</p>
                    <h1 className="text-2xl font-bold sm:text-3xl">Produtos</h1>
                    <p className="mt-1 text-sm text-gray-400">Gerencie preços, estoque e disponibilidade.</p>
                </div>
                <div className="grid grid-cols-2 gap-2 sm:flex">
                    <Link href="/admin/produtos/lote" className="inline-flex min-h-11 items-center justify-center rounded-lg border border-white/15 px-3 text-sm font-medium text-gray-200 transition hover:bg-white/5 sm:px-4">
                        Upload em lote
                    </Link>
                    <Link href="/admin/produtos/novo" className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg bg-brand-secondary px-3 text-sm font-semibold text-white transition hover:bg-red-700 sm:px-4">
                        <Plus className="h-4 w-4" /> Novo produto
                    </Link>
                </div>
            </header>

            <section className="space-y-3" aria-label="Busca e filtros de produtos">
                <div className="flex flex-col gap-2 sm:flex-row">
                    <div className="relative min-w-0 flex-1">
                        <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-500" />
                        <input type="search" placeholder="Buscar por nome..." value={searchTerm} onChange={e => setSearchTerm(e.target.value)} className="min-h-11 w-full rounded-lg border border-white/10 bg-[#1A1B1D] py-2.5 pl-10 pr-4 text-sm text-white placeholder-gray-500 outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary" />
                    </div>
                    <button type="button" onClick={() => setFiltersOpen(open => !open)} aria-expanded={filtersOpen} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg border border-white/10 bg-[#2A2A2A] px-4 text-sm text-gray-200 transition hover:bg-white/10">
                        <Filter className="h-4 w-4" /> Filtros{activeFilters > 0 && <span className="grid h-5 min-w-5 place-items-center rounded-full bg-brand-secondary px-1 text-xs font-bold text-white">{activeFilters}</span>}
                    </button>
                </div>
                {filtersOpen && <div className="grid grid-cols-1 gap-3 rounded-lg border border-white/10 bg-[#222325] p-3 sm:grid-cols-3">
                    <label className="space-y-1 text-xs text-gray-400">Categoria
                        <select value={categoryId} onChange={e => setCategoryId(e.target.value)} className="min-h-10 w-full rounded-md border border-white/10 bg-[#1A1B1D] px-3 text-sm text-white">
                            <option value="">Todas as categorias</option>
                            {categories?.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}
                        </select>
                    </label>
                    <label className="space-y-1 text-xs text-gray-400">Preço mínimo
                        <input type="number" min="0" step="0.01" value={minPrice} onChange={e => setMinPrice(e.target.value)} placeholder="R$ 0,00" className="min-h-10 w-full rounded-md border border-white/10 bg-[#1A1B1D] px-3 text-sm text-white placeholder-gray-500" />
                    </label>
                    <label className="space-y-1 text-xs text-gray-400">Preço máximo
                        <input type="number" min="0" step="0.01" value={maxPrice} onChange={e => setMaxPrice(e.target.value)} placeholder="Sem limite" className="min-h-10 w-full rounded-md border border-white/10 bg-[#1A1B1D] px-3 text-sm text-white placeholder-gray-500" />
                    </label>
                    {activeFilters > 0 && <button type="button" onClick={resetFilters} className="inline-flex items-center gap-1 text-left text-xs font-medium text-brand-secondary hover:text-red-300 sm:col-span-3"><X className="h-3.5 w-3.5" /> Limpar filtros</button>}
                </div>}
            </section>

            <section className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg border border-white/10 bg-[#222325]" aria-label="Lista de produtos">
                <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
                    <div className="flex items-center gap-2 text-sm font-semibold"><Package className="h-4 w-4 text-brand-secondary" /> Catálogo</div>
                    <span className="text-xs text-gray-400">{isLoading ? 'Carregando...' : `${filteredProducts.length} ${filteredProducts.length === 1 ? 'produto' : 'produtos'}`}</span>
                </div>
                <div className="min-h-0 flex-1 overflow-auto">
                    <table className="hidden w-full min-w-[760px] border-collapse text-left md:table">
                        <thead className="sticky top-0 z-10 bg-[#1A1B1D] text-xs uppercase text-gray-400 shadow-sm">
                            <tr><th className="px-4 py-3 font-medium">Produto</th><th className="px-4 py-3 font-medium">Categoria</th><th className="px-4 py-3 text-right font-medium">Preço</th><th className="px-4 py-3 text-right font-medium">Estoque</th><th className="px-4 py-3 font-medium">Status</th><th className="px-4 py-3 text-right font-medium">Ações</th></tr>
                        </thead>
                        <tbody className="divide-y divide-white/5">
                            {filteredProducts.map(product => <tr key={product.id} className="transition hover:bg-white/3">
                                <td className="px-4 py-2.5">
                                    <div className="flex min-w-0 items-center gap-3">
                                        <div className="grid h-10 w-10 shrink-0 place-items-center overflow-hidden rounded-md border border-white/10 bg-[#1A1B1D]">
                                            {product.images?.[0]?.imageUrl ? <img src={product.images[0].imageUrl} alt="" className="h-full w-full object-cover" /> : <ImageIcon className="h-4 w-4 text-gray-600" />}
                                        </div>
                                        <div className="min-w-0"><p className="max-w-[280px] truncate text-sm font-medium text-white">{product.name}</p><p className="text-xs text-gray-500">#{product.id.slice(0, 8)}</p></div>
                                    </div>
                                </td>
                                <td className="px-4 py-2.5 text-sm text-gray-400">{getCategoryName(product)}</td>
                                <td className="px-4 py-2.5 text-right text-sm font-medium tabular-nums text-white">{formatPrice(product.price)}</td>
                                <td className="px-4 py-2.5 text-right text-sm tabular-nums text-gray-300">{product.stock ?? 0}<span className="ml-1 text-xs text-gray-500">un.</span></td>
                                <td className="px-4 py-2.5"><span className={`inline-flex items-center gap-1.5 text-xs ${product.active ? 'text-emerald-400' : 'text-gray-500'}`}><span className={`h-1.5 w-1.5 rounded-full ${product.active ? 'bg-emerald-400' : 'bg-gray-500'}`} />{product.active ? 'Ativo' : 'Inativo'}</span></td>
                                <td className="px-4 py-2.5"><div className="flex justify-end gap-1">
                                    <Link href={`/admin/produtos/${product.id}`} title="Editar produto" aria-label={`Editar ${product.name}`} className="grid h-9 w-9 place-items-center rounded-md text-gray-400 transition hover:bg-white/10 hover:text-white"><Edit className="h-4 w-4" /></Link>
                                    <button type="button" onClick={() => handleDelete(product.id)} title="Excluir produto" aria-label={`Excluir ${product.name}`} className="grid h-9 w-9 place-items-center rounded-md text-gray-400 transition hover:bg-red-500/10 hover:text-red-400"><Trash2 className="h-4 w-4" /></button>
                                </div></td>
                            </tr>)}
                        </tbody>
                    </table>

                    <div className="divide-y divide-white/5 md:hidden">
                        {filteredProducts.map(product => <article key={product.id} className="flex items-start gap-3 p-3">
                            <div className="grid h-14 w-14 shrink-0 place-items-center overflow-hidden rounded-md border border-white/10 bg-[#1A1B1D]">
                                {product.images?.[0]?.imageUrl ? <img src={product.images[0].imageUrl} alt="" className="h-full w-full object-cover" /> : <ImageIcon className="h-5 w-5 text-gray-600" />}
                            </div>
                            <div className="min-w-0 flex-1">
                                <div className="flex items-start justify-between gap-2"><h2 className="truncate text-sm font-semibold">{product.name}</h2><span className={`shrink-0 text-xs ${product.active ? 'text-emerald-400' : 'text-gray-500'}`}>{product.active ? 'Ativo' : 'Inativo'}</span></div>
                                <p className="mt-0.5 truncate text-xs text-gray-500">{getCategoryName(product)}</p>
                                <div className="mt-2 flex items-center justify-between gap-2"><p className="text-sm font-semibold tabular-nums">{formatPrice(product.price)} <span className="font-normal text-gray-500">· {product.stock ?? 0} un.</span></p><div className="flex gap-1">
                                    <Link href={`/admin/produtos/${product.id}`} aria-label={`Editar ${product.name}`} className="grid h-9 w-9 place-items-center rounded-md text-gray-400 hover:bg-white/10 hover:text-white"><Edit className="h-4 w-4" /></Link>
                                    <button type="button" onClick={() => handleDelete(product.id)} aria-label={`Excluir ${product.name}`} className="grid h-9 w-9 place-items-center rounded-md text-gray-400 hover:bg-red-500/10 hover:text-red-400"><Trash2 className="h-4 w-4" /></button>
                                </div></div>
                            </div>
                        </article>)}
                    </div>

                    {!isLoading && filteredProducts.length === 0 && <div className="flex min-h-52 flex-col items-center justify-center px-4 text-center">
                        <Package className="mb-3 h-8 w-8 text-gray-600" />
                        <p className="text-sm font-medium text-gray-300">Nenhum produto encontrado</p>
                        <p className="mt-1 text-xs text-gray-500">Revise a busca ou os filtros selecionados.</p>
                    </div>}
                </div>
            </section>
        </div>
    );
}