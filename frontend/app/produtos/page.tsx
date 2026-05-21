'use client';

import { useState, useMemo, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Search, SlidersHorizontal, X, ChevronDown } from 'lucide-react';
import { FEATURED_PRODUCTS, CATEGORIES } from '../../lib/data';
import { ProductCard } from '../components/ui/ProductCard';

type SortOption = 'relevance' | 'price-asc' | 'price-desc' | 'name-asc' | 'name-desc';

export default function ProdutosPage() {
  const [isMobileFiltersOpen, setIsMobileFiltersOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [priceRange, setPriceRange] = useState<{ min: string; max: string }>({ min: '', max: '' });
  const [sortBy, setSortBy] = useState<SortOption>('relevance');

  // Prevent background scroll when mobile filters are open
  useEffect(() => {
    if (isMobileFiltersOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'unset';
    }
  }, [isMobileFiltersOpen]);

  const toggleCategory = (categoryName: string) => {
    setSelectedCategories(prev =>
      prev.includes(categoryName)
        ? prev.filter(c => c !== categoryName)
        : [...prev, categoryName]
    );
  };

  const filteredAndSortedProducts = useMemo(() => {
    let result = [...FEATURED_PRODUCTS];

    // Search
    if (searchQuery) {
      const lowerQuery = searchQuery.toLowerCase();
      result = result.filter(
        p => p.name.toLowerCase().includes(lowerQuery) || p.category.toLowerCase().includes(lowerQuery)
      );
    }

    // Categories
    if (selectedCategories.length > 0) {
      result = result.filter(p => selectedCategories.includes(p.category));
    }

    // Price
    const minPrice = priceRange.min ? parseFloat(priceRange.min) : 0;
    const maxPrice = priceRange.max ? parseFloat(priceRange.max) : Infinity;

    if (minPrice > 0 || maxPrice < Infinity) {
      result = result.filter(p => p.price >= minPrice && p.price <= maxPrice);
    }

    // Sorting
    switch (sortBy) {
      case 'price-asc':
        result.sort((a, b) => a.price - b.price);
        break;
      case 'price-desc':
        result.sort((a, b) => b.price - a.price);
        break;
      case 'name-asc':
        result.sort((a, b) => a.name.localeCompare(b.name));
        break;
      case 'name-desc':
        result.sort((a, b) => b.name.localeCompare(a.name));
        break;
      default:
        // relevance - keep original order
        break;
    }

    return result;
  }, [searchQuery, selectedCategories, priceRange, sortBy]);

  const allCategoryNames = Array.from(new Set(FEATURED_PRODUCTS.map(p => p.category)));

  return (
    <div className="min-h-screen bg-white pt-24 pb-16">
      <div className="container mx-auto px-4 lg:px-8">

        {/* Header Section */}
        <div className="mb-8 md:mb-12 mt-12">
          <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
            <div>
              <h1 className="text-4xl md:text-5xl font-black tracking-tight mb-2">
                 Produtos
              </h1>
              <p className="text-gray-600 text-sm md:text-base max-w-2xl">
                Explore nossa coleção completa de equipamentos e vestuário para sua próxima aventura.
                Filtre por categoria, preço ou busque o que você precisa.
              </p>
            </div>
          </div>
        </div>

        <div className="flex flex-col lg:flex-row gap-8">

          {/* Mobile Filter Toggle */}
          <div className="lg:hidden flex items-center justify-between bg-gray-50 p-4 rounded-2xl shadow-sm border border-gray-100">
            <span className="font-bold text-[#1A1B1D]">
              {filteredAndSortedProducts.length} produtos
            </span>
            <button
              onClick={() => setIsMobileFiltersOpen(true)}
              className="flex items-center gap-2 text-brand-secondary font-bold px-4 py-2 rounded-xl bg-brand-secondary/10 hover:bg-brand-secondary/20 transition-colors"
            >
              <SlidersHorizontal className="w-5 h-5" />
              Filtros
            </button>
          </div>

          {/* Desktop Sidebar & Mobile Drawer */}
          <AnimatePresence>
            {(isMobileFiltersOpen || typeof window !== 'undefined' && window.innerWidth >= 1024) && (
              <>
                {/* Mobile Backdrop */}
                {isMobileFiltersOpen && (
                  <motion.div
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    onClick={() => setIsMobileFiltersOpen(false)}
                    className="fixed inset-0 bg-black/40 z-40 lg:hidden backdrop-blur-sm"
                  />
                )}

                <motion.aside
                  initial={isMobileFiltersOpen ? { x: '100%' } : false}
                  animate={{ x: 0 }}
                  exit={isMobileFiltersOpen ? { x: '100%' } : undefined}
                  transition={{ type: 'spring', damping: 25, stiffness: 200 }}
                  className={`
                    fixed lg:static inset-y-0 right-0 z-50 w-[300px] lg:w-72 bg-white lg:bg-transparent shadow-2xl lg:shadow-none p-6 lg:p-0 overflow-y-auto lg:overflow-visible shrink-0
                    ${!isMobileFiltersOpen ? 'hidden lg:block' : 'block'}
                  `}
                >
                  <div className="lg:sticky lg:top-28 space-y-8">

                    {/* Mobile Header */}
                    <div className="flex items-center justify-between lg:hidden mb-6">
                      <h2 className="text-xl font-black text-[#1A1B1D]">Filtros</h2>
                      <button
                        onClick={() => setIsMobileFiltersOpen(false)}
                        className="p-2 hover:bg-gray-100 rounded-full transition-colors"
                      >
                        <X className="w-6 h-6 text-gray-600" />
                      </button>
                    </div>

                    {/* Sidebar Header with Clear All */}
                    <div className="flex items-center justify-between">
                      <h3 className="font-bold text-[#1A1B1D] uppercase text-sm tracking-wider">Filtros</h3>
                      {(searchQuery || selectedCategories.length > 0 || priceRange.min || priceRange.max) && (
                        <button
                          onClick={() => {
                            setSearchQuery('');
                            setSelectedCategories([]);
                            setPriceRange({ min: '', max: '' });
                          }}
                          className="text-xs font-bold text-brand-secondary hover:underline transition-all"
                        >
                          Limpar tudo
                        </button>
                      )}
                    </div>

                    {/* Search */}
                    <div className="space-y-3">
                      <h4 className="font-semibold text-gray-500 text-xs uppercase tracking-tight">Buscar</h4>
                      <div className="relative">
                        <input
                          type="text"
                          placeholder="Ex: Camiseta, Boné..."
                          value={searchQuery}
                          onChange={(e) => setSearchQuery(e.target.value)}
                          className="w-full bg-white border border-gray-200 rounded-xl py-3 pl-10 pr-4 text-sm text-[#1A1B1D] outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary transition-all"
                        />
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500" />
                      </div>
                    </div>

                    {/* Categories */}
                    <div className="space-y-3">
                      <h4 className="font-semibold text-gray-500 text-xs uppercase tracking-tight">Categorias</h4>
                      <div className="space-y-2">
                        {allCategoryNames.map(category => (
                          <label key={category} className="flex items-center gap-3 cursor-pointer group">
                            <div className={`
                              w-5 h-5 rounded border flex items-center justify-center transition-all duration-200
                              ${selectedCategories.includes(category) ? 'bg-brand-secondary border-brand-secondary' : 'bg-white border-gray-300 group-hover:border-brand-secondary'}
                            `}>
                              {selectedCategories.includes(category) && <X className="w-3 h-3 text-white" />}
                            </div>
                            <span className={`text-sm transition-colors ${selectedCategories.includes(category) ? 'text-[#1A1B1D] font-medium' : 'text-gray-600 group-hover:text-[#1A1B1D]'}`}>
                              {category}
                            </span>
                          </label>
                        ))}
                      </div>
                    </div>

                    {/* Price Range */}
                    <div className="space-y-3">
                      <h4 className="font-semibold text-gray-500 text-xs uppercase tracking-tight">Preço (R$)</h4>
                      <div className="flex items-center gap-3">
                        <input
                          type="number"
                          placeholder="Min"
                          value={priceRange.min}
                          onChange={(e) => setPriceRange({ ...priceRange, min: e.target.value })}
                          className="w-full bg-white border border-gray-200 rounded-xl py-2 px-3 text-sm text-[#1A1B1D] outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary transition-all"
                        />
                        <span className="text-gray-300">-</span>
                        <input
                          type="number"
                          placeholder="Máx"
                          value={priceRange.max}
                          onChange={(e) => setPriceRange({ ...priceRange, max: e.target.value })}
                          className="w-full bg-white border border-gray-200 rounded-xl py-2 px-3 text-sm text-[#1A1B1D] outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary transition-all"
                        />
                      </div>
                    </div>

                    {/* Mobile Apply Button */}
                    <div className="pt-6 lg:hidden">
                      <button
                        onClick={() => setIsMobileFiltersOpen(false)}
                        className="w-full bg-brand-secondary text-white py-3 rounded-xl font-bold hover:bg-[#1A1B1D] transition-colors"
                      >
                        Ver {filteredAndSortedProducts.length} produtos
                      </button>
                    </div>

                  </div>
                </motion.aside>
              </>
            )}
          </AnimatePresence>

          {/* Main Content */}
          <div className="flex-1">

            {/* Top Bar Desktop */}
            <div className="hidden lg:flex items-center justify-between bg-gray-50 p-4 rounded-2xl shadow-sm border border-gray-100 mb-8">
              <p className="text-gray-600 text-sm">
                Mostrando <strong className="text-[#1A1B1D]">{filteredAndSortedProducts.length}</strong> produtos
              </p>

              <div className="flex items-center gap-3">
                <span className="text-sm font-medium text-gray-600">Ordenar por:</span>
                <div className="relative">
                  <select
                    value={sortBy}
                    onChange={(e) => setSortBy(e.target.value as SortOption)}
                    className="appearance-none bg-white border border-gray-200 text-[#1A1B1D] text-sm font-medium rounded-xl py-2 pl-4 pr-10 outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary transition-all cursor-pointer"
                  >
                    <option value="relevance">Relevância</option>
                    <option value="price-asc">Menor Preço</option>
                    <option value="price-desc">Maior Preço</option>
                    <option value="name-asc">Nome (A-Z)</option>
                    <option value="name-desc">Nome (Z-A)</option>
                  </select>
                  <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-600 pointer-events-none" />
                </div>
              </div>
            </div>

            {/* Mobile Sort (Visible only on mobile) */}
            <div className="flex lg:hidden items-center justify-between mb-6">
              <span className="text-sm font-medium text-gray-600">Ordenar:</span>
              <div className="relative flex-1 ml-3">
                <select
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value as SortOption)}
                  className="w-full appearance-none bg-white border border-gray-200 text-[#1A1B1D] text-sm font-medium rounded-xl py-2.5 pl-4 pr-10 outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary transition-all cursor-pointer shadow-sm"
                >
                  <option value="relevance">Relevância</option>
                  <option value="price-asc">Menor Preço</option>
                  <option value="price-desc">Maior Preço</option>
                  <option value="name-asc">Nome (A-Z)</option>
                  <option value="name-desc">Nome (Z-A)</option>
                </select>
                <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-600 pointer-events-none" />
              </div>
            </div>

            {/* Product Grid */}
            {filteredAndSortedProducts.length > 0 ? (
              <motion.div
                layout
                className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-6"
              >
                <AnimatePresence>
                  {filteredAndSortedProducts.map((product) => (
                    <motion.div
                      layout
                      initial={{ opacity: 0, scale: 0.9 }}
                      animate={{ opacity: 1, scale: 1 }}
                      exit={{ opacity: 0, scale: 0.9 }}
                      transition={{ duration: 0.2 }}
                      key={product.id}
                      className="h-full"
                    >
                      <ProductCard product={product} />
                    </motion.div>
                  ))}
                </AnimatePresence>
              </motion.div>
            ) : (
              <div className="flex flex-col items-center justify-center py-20 px-4 text-center bg-gray-50 rounded-3xl border border-gray-100 shadow-sm">
                <div className="w-20 h-20 bg-gray-100 rounded-full flex items-center justify-center mb-4">
                  <Search className="w-8 h-8 text-gray-300" />
                </div>
                <h3 className="text-xl font-bold text-[#1A1B1D] mb-2">Nenhum produto encontrado</h3>
                <p className="text-gray-600 max-w-md">
                  Não encontramos nenhum produto que corresponda aos filtros selecionados.
                  Tente ajustar sua busca ou limpar os filtros.
                </p>
                <button
                  onClick={() => {
                    setSearchQuery('');
                    setSelectedCategories([]);
                    setPriceRange({ min: '', max: '' });
                  }}
                  className="mt-6 font-bold text-brand-secondary hover:text-[#1A1B1D] transition-colors border-b-2 border-brand-secondary hover:border-[#1A1B1D] pb-1"
                >
                  Limpar todos os filtros
                </button>
              </div>
            )}

          </div>
        </div>
      </div>
    </div>
  );
}