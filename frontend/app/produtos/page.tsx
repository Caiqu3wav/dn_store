'use client';

import { Suspense, useState, useEffect, useRef, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Search, SlidersHorizontal, X, ChevronDown, Loader2 } from 'lucide-react';
import { useSearchParams } from 'next/navigation';
import { ProductCard } from '../components/ui/ProductCard';
import useSWR from 'swr';
import { fetcher } from '@/services/productService';
import { Product, Category } from '@/types';

type SortOption = 'relevance' | 'priceAsc' | 'priceDesc' | 'name' | 'nameDesc';

const AVAILABLE_VARIANT_SIZES = ['PP', 'P', 'M', 'G', 'GG', 'BL P', 'BL M', 'BL G', 'Único'] as const;

function ProdutosCatalog() {
  const searchParams = useSearchParams();
  const [isMobileFiltersOpen, setIsMobileFiltersOpen] = useState(false);
  const mobileFilterTriggerRef = useRef<HTMLButtonElement>(null);
  const mobileFilterDialogRef = useRef<HTMLElement>(null);
  const mobileFilterCloseRef = useRef<HTMLButtonElement>(null);
  const wasMobileFiltersOpenRef = useRef(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [priceRange, setPriceRange] = useState<{ min: string; max: string }>({ min: '', max: '' });
  const [sortBy, setSortBy] = useState<SortOption>('relevance');
  const [selectedCategory, setSelectedCategory] = useState(searchParams.get('categoryId') ?? '');
  const [selectedSize, setSelectedSize] = useState('');

  const closeMobileFilters = useCallback(() => {
    setIsMobileFiltersOpen(false);
  }, []);

  // Keep the page scroll locked only while the mobile filter dialog is mounted.
  useEffect(() => {
    if (!isMobileFiltersOpen) return;

    const previousBodyOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    mobileFilterCloseRef.current?.focus();

    const handleFocusIn = (event: FocusEvent) => {
      if (!mobileFilterDialogRef.current?.contains(event.target as Node)) {
        mobileFilterCloseRef.current?.focus();
      }
    };

    const handleDialogKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeMobileFilters();
        return;
      }

      if (event.key !== 'Tab' || !mobileFilterDialogRef.current) return;

      const focusableElements = Array.from(
        mobileFilterDialogRef.current.querySelectorAll<HTMLElement>(
          'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
        )
      ).filter(element => element.getClientRects().length > 0);
      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];

      if (!firstElement || !lastElement) {
        event.preventDefault();
        mobileFilterDialogRef.current.focus();
      } else if (event.shiftKey && document.activeElement === firstElement) {
        event.preventDefault();
        lastElement.focus();
      } else if (!event.shiftKey && document.activeElement === lastElement) {
        event.preventDefault();
        firstElement.focus();
      }
    };

    document.addEventListener('keydown', handleDialogKeyDown);
    document.addEventListener('focusin', handleFocusIn);
    return () => {
      document.removeEventListener('keydown', handleDialogKeyDown);
      document.removeEventListener('focusin', handleFocusIn);
      document.body.style.overflow = previousBodyOverflow;
    };
  }, [isMobileFiltersOpen, closeMobileFilters]);

  useEffect(() => {
    if (isMobileFiltersOpen) {
      wasMobileFiltersOpenRef.current = true;
    } else if (wasMobileFiltersOpenRef.current) {
      wasMobileFiltersOpenRef.current = false;
      mobileFilterTriggerRef.current?.focus();
    }
  }, [isMobileFiltersOpen]);

  const {
    data: categories,
    error: categoriesError,
    isLoading: categoriesLoading,
    mutate: reloadCategories,
  } = useSWR<Category[]>('/categories', fetcher);

  // Monta a query URL baseada nos filtros
  const queryParams = new URLSearchParams();
  if (searchQuery) queryParams.append('search', searchQuery);
  if (selectedCategory) {
    queryParams.append('categoryId', selectedCategory);
  }
  if (priceRange.min) queryParams.append('minPrice', priceRange.min);
  if (priceRange.max) queryParams.append('maxPrice', priceRange.max);
  const apiSortBy = sortBy === 'nameDesc' ? 'name' : sortBy;
  if (apiSortBy !== 'relevance') queryParams.append('sortBy', apiSortBy);

  const { data: productsData, isLoading, error } = useSWR<Product[]>(
    `/products?${queryParams.toString()}`,
    fetcher
  );

  const availableSizes = AVAILABLE_VARIANT_SIZES.filter(size =>
    productsData?.some(product =>
      product.variants?.some(variant => variant.size === size && variant.active && variant.stock > 0)
    )
  );
  const matchingProducts = (productsData || []).filter(product =>
    !selectedSize || product.variants?.some(variant =>
      variant.size === selectedSize && variant.active && variant.stock > 0
    )
  );
  const filteredAndSortedProducts = sortBy === 'nameDesc'
    ? [...matchingProducts].reverse()
    : matchingProducts;


  return (
    <div className="min-h-screen bg-white pt-8 pb-16">
      <div className="container mx-auto px-4 lg:px-8">

        {/* Header Section */}
        <div className="mb-8 md:mb-12">
          <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
            <div>
              <h1 className="text-4xl md:text-5xl tracking-tight mb-2"
              style={{ fontFamily: 'DN'  }}>
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
              ref={mobileFilterTriggerRef}
              type="button"
              aria-expanded={isMobileFiltersOpen}
              aria-controls={isMobileFiltersOpen ? 'mobile-product-filters' : undefined}
              onClick={() => setIsMobileFiltersOpen(true)}
              className="flex items-center gap-2 text-brand-secondary font-bold px-4 py-2 rounded-xl bg-brand-secondary/10 hover:bg-brand-secondary/20 transition-colors"
            >
              <SlidersHorizontal className="w-5 h-5" />
              Filtros
            </button>
          </div>

          {/* Desktop Sidebar & Mobile Drawer */}
            {(isMobileFiltersOpen || typeof window !== 'undefined' && window.innerWidth >= 1024) && (
              <>
                {/* Mobile Backdrop */}
                {isMobileFiltersOpen && (
                  <motion.div
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    onClick={closeMobileFilters}
                    className="fixed inset-0 bg-black/40 z-40 lg:hidden backdrop-blur-sm"
                  />
                )}

                <motion.aside
                  ref={mobileFilterDialogRef}
                  id="mobile-product-filters"
                  role={isMobileFiltersOpen ? 'dialog' : undefined}
                  aria-modal={isMobileFiltersOpen ? true : undefined}
                  aria-labelledby={isMobileFiltersOpen ? 'mobile-product-filters-title' : undefined}
                  tabIndex={isMobileFiltersOpen ? -1 : undefined}
                  initial={isMobileFiltersOpen ? { x: '100%' } : false}
                  animate={{ x: 0 }}
                  transition={{ type: 'spring', damping: 25, stiffness: 200 }}
                  className={`
                    fixed lg:static inset-y-0 right-0 z-50 w-[300px] lg:w-72 bg-white lg:bg-transparent shadow-2xl lg:shadow-none p-6 lg:p-0 overflow-y-auto lg:overflow-visible shrink-0
                    ${!isMobileFiltersOpen ? 'hidden lg:block' : 'block'}
                  `}
                >
                  <div className="lg:sticky lg:top-28 space-y-8">

                    {/* Mobile Header */}
                    <div className="flex items-center justify-between lg:hidden mb-6">
                      <h2 id="mobile-product-filters-title" className="text-xl font-black text-[#1A1B1D]">Filtros</h2>
                      <button
                        ref={mobileFilterCloseRef}
                        type="button"
                        aria-label="Fechar filtros"
                        onClick={closeMobileFilters}
                        className="p-2 hover:bg-gray-100 rounded-full transition-colors"
                      >
                        <X className="w-6 h-6 text-gray-600" />
                      </button>
                    </div>

                    {/* Sidebar Header with Clear All */}
                    <div className="flex items-center justify-between">
                      <h3 className="font-bold text-[#1A1B1D] uppercase text-sm tracking-wider">Filtros</h3>
                      {(searchQuery || selectedCategory || priceRange.min || priceRange.max || selectedSize) && (
                        <button
                          onClick={() => {
                            setSearchQuery('');
                            setPriceRange({ min: '', max: '' });
                            setSelectedCategory('');
                            setSelectedSize('');
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
  <h4 className="font-semibold text-gray-500 text-xs uppercase tracking-tight">
    Categorias
  </h4>

  {categoriesLoading ? (
    <p role="status" className="text-sm text-gray-500">Carregando categorias...</p>
  ) : categoriesError ? (
    <div role="alert" className="space-y-2 text-sm text-red-700">
      <p>Não foi possível carregar as categorias.</p>
      <button
        type="button"
        onClick={() => void reloadCategories()}
        className="font-semibold underline"
      >
        Tentar novamente
      </button>
    </div>
  ) : (
    <div role="radiogroup" aria-label="Filtrar por categoria" className="space-y-2">
      {[{ id: '', name: 'Todos' }, ...(categories ?? [])].map(category => (
        <button
          key={category.id || 'todos'}
          type="button"
          role="radio"
          aria-checked={selectedCategory === category.id}
          onClick={() => {
            setSelectedCategory(category.id);
            setSelectedSize('');
          }}
          className="flex w-full items-center gap-3 text-left group"
        >
          <span
            aria-hidden="true"
            className={`
              w-5 h-5 rounded border flex items-center justify-center transition-all duration-200
              ${
                selectedCategory === category.id
                  ? 'bg-brand-secondary border-brand-secondary'
                  : 'bg-white border-gray-300 group-hover:border-brand-secondary'
              }
            `}
          >
            {selectedCategory === category.id && <X className="w-3 h-3 text-white" />}
          </span>
          <span className="text-sm text-gray-600 group-hover:text-[#1A1B1D]">
            {category.name}
          </span>
        </button>
      ))}
    </div>
  )}
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

                    {availableSizes.length > 0 && (
                      <div className="space-y-3">
                        <label htmlFor="product-size-filter" className="font-semibold text-gray-500 text-xs uppercase tracking-tight">
                          Tamanho disponível
                        </label>
                        <select
                          id="product-size-filter"
                          value={selectedSize}
                          onChange={(event) => setSelectedSize(event.target.value)}
                          className="w-full border border-gray-200 rounded-xl p-3"
                        >
                          <option value="">Todos</option>
                          {availableSizes.map(size => (
                            <option key={size} value={size}>{size}</option>
                          ))}
                        </select>
                      </div>
                    )}




                    {/* Mobile Apply Button */}
                    <div className="pt-6 lg:hidden">
                      <button
                        type="button"
                        onClick={closeMobileFilters}
                        className="w-full bg-brand-secondary text-white py-3 rounded-xl font-bold hover:bg-[#1A1B1D] transition-colors"
                      >
                        Ver {filteredAndSortedProducts.length} produtos
                      </button>
                    </div>

                  </div>
                </motion.aside>
              </>
            )}

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
                    <option value="priceAsc">Menor Preço</option>
                    <option value="priceDesc">Maior Preço</option>
                    <option value="name">Nome (A-Z)</option>
                    <option value="nameDesc">Nome (Z-A)</option>
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
                  <option value="priceAsc">Menor Preço</option>
                  <option value="priceDesc">Maior Preço</option>
                  <option value="name">Nome (A-Z)</option>
                  <option value="nameDesc">Nome (Z-A)</option>
                </select>
                <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-600 pointer-events-none" />
              </div>
            </div>

            {/* Product Grid */}
            {isLoading ? (
              <div role="status" className="flex items-center justify-center gap-3 py-20 text-gray-600">
                <Loader2 className="h-6 w-6 animate-spin" />
                <span>Carregando produtos...</span>
              </div>
            ) : error ? (
              <div role="alert" className="py-16 px-4 text-center bg-red-50 rounded-3xl border border-red-100">
                <h3 className="text-xl font-bold text-red-800 mb-2">Não foi possível carregar o catálogo</h3>
                <p className="text-red-700">Verifique sua conexão e tente novamente.</p>
              </div>
            ) : (productsData ?? []).length === 0 ? (
              <div className="py-16 px-4 text-center bg-gray-50 rounded-3xl border border-gray-100">
                <h3 className="text-xl font-bold text-[#1A1B1D] mb-2">Catálogo vazio</h3>
                <p className="text-gray-600">Não há produtos disponíveis no momento.</p>
              </div>
            ) : filteredAndSortedProducts.length > 0 ? (
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
                    setPriceRange({ min: '', max: '' });
                    setSelectedCategory('');
                    setSelectedSize('');
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

export default function ProdutosPage() {
  return (
    <Suspense fallback={<div className="min-h-screen bg-white pt-8 pb-16" />}>
      <ProdutosCatalog />
    </Suspense>
  );
}