'use client';

import Image from 'next/image';
import dynamic from 'next/dynamic';
import { Plus, Search, Edit, Trash2, Filter, X, Package, Check, Image as ImageIcon } from 'lucide-react';
import { useState } from 'react';
import useSWR from 'swr';
import { toast } from 'react-hot-toast';
import { fetcher, productService } from '@/services/productService';
import { Category, Product } from '@/types';
import { productColorOptions } from '@/utils/productColor';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { Badge, Button, EmptyState, ErrorState, IconButton, IconLink, LinkButton, PageHeader, Skeleton, TableSkeletonRows } from '../_components/ui';
import { Field, Input, Select } from '../_components/form';
import { Table, Tbody, Td, Th, Thead, rowClass } from '../_components/table';
import { useConfirm } from '../_components/confirm';

// O modal de padrões (~440 linhas) só é usado ao clicar no botão; fica fora do bundle inicial da lista.
// O placeholder reproduz o botão para o cabeçalho não mudar de tamanho enquanto o chunk carrega.
const ProductDefaultsManager = dynamic(() => import('./ProductDefaultsManager'), {
    ssr: false,
    loading: () => (
        <Button size="lg" disabled>
            <Check className="h-4 w-4" aria-hidden="true" /> Definir padrões
        </Button>
    ),
});

export default function AdminProductsPage() {
    const confirm = useConfirm();
    const [searchTerm, setSearchTerm] = useState('');
    // A lista (e os critérios do modal de padrões) usam os termos já "assentados", não cada tecla.
    const debouncedSearch = useDebouncedValue(searchTerm);
    const [filtersOpen, setFiltersOpen] = useState(false);
    const [categoryId, setCategoryId] = useState('');
    const [color, setColor] = useState('');
    const [minPrice, setMinPrice] = useState('');
    const [maxPrice, setMaxPrice] = useState('');
    const debouncedMinPrice = useDebouncedValue(minPrice);
    const debouncedMaxPrice = useDebouncedValue(maxPrice);
    const { data: categories } = useSWR<Category[]>('/categories', fetcher);

    const productQuery = new URLSearchParams();
    if (debouncedSearch) productQuery.set('search', debouncedSearch);
    if (categoryId) productQuery.set('categoryId', categoryId);
    if (color) productQuery.set('color', color);
    if (debouncedMinPrice) productQuery.set('minPrice', debouncedMinPrice);
    if (debouncedMaxPrice) productQuery.set('maxPrice', debouncedMaxPrice);

    const { data: productsData, error: productsError, mutate, isLoading } = useSWR<Product[]>(
        `/products/admin?${productQuery.toString()}`,
        fetcher
    );

    const filteredProducts = productsData || [];
    const activeFilters = [categoryId, color, minPrice, maxPrice].filter(Boolean).length;
    const resetFilters = () => {
        setCategoryId('');
        setColor('');
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
        const confirmed = await confirm({
            title: 'Remover produto',
            message: 'Tem certeza que deseja remover este produto?',
            confirmLabel: 'Remover',
        });
        if (!confirmed) return;

        try {
            const result = await productService.deleteProduct(id);
            if (result?.outcome === 'ARCHIVED') {
                toast.success(result.message);
            } else {
                toast.success('Produto removido.');
            }
            mutate();
        } catch (error) {
            console.error("Erro ao deletar", error);
            toast.error('Erro ao deletar produto.');
        }
    };

    const thumbnail = (product: Product, size: number) => (
        <div
            className="relative grid shrink-0 place-items-center overflow-hidden rounded-adm-control border border-adm-line bg-adm-sunken"
            style={{ width: size, height: size }}
        >
            {product.images?.[0]?.imageUrl
                ? <Image src={product.images[0].imageUrl} alt="" fill sizes={`${size}px`} unoptimized className="object-cover" />
                : <ImageIcon className="h-4 w-4 text-adm-ink-subtle" aria-hidden="true" />}
        </div>
    );

    const statusBadge = (product: Product) => (
        <Badge tone={product.active ? 'success' : 'neutral'} dot>{product.active ? 'Ativo' : 'Inativo'}</Badge>
    );

    const showSkeleton = isLoading && !productsError;

    return (
        <div className="mx-auto flex h-[calc(100dvh-5.5rem)] min-h-96 max-w-7xl flex-col gap-5 md:h-[calc(100dvh-7.5rem)] lg:h-[calc(100dvh-4rem)]">
            <PageHeader
                eyebrow="Catálogo"
                title="Produtos"
                description="Gerencie preços, estoque e disponibilidade."
                actions={
                    <>
                        <ProductDefaultsManager
                            products={filteredProducts}
                            loading={isLoading}
                            categories={categories || []}
                            filters={{ search: debouncedSearch, categoryId, color, minPrice: debouncedMinPrice, maxPrice: debouncedMaxPrice }}
                            onApplied={() => { void mutate(); }}
                        />
                        <LinkButton href="/admin/produtos/lote" size="lg">Upload em lote</LinkButton>
                        <LinkButton href="/admin/produtos/novo" variant="primary" size="lg">
                            <Plus className="h-4 w-4" aria-hidden="true" /> Novo produto
                        </LinkButton>
                    </>
                }
            />

            <section className="space-y-3" aria-label="Busca e filtros de produtos">
                <div className="flex flex-col gap-2 sm:flex-row">
                    <div className="relative min-w-0 flex-1">
                        <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-adm-ink-subtle" aria-hidden="true" />
                        <Input type="search" aria-label="Buscar produtos por nome" placeholder="Buscar por nome..." value={searchTerm} onChange={e => setSearchTerm(e.target.value)} className="min-h-11 pl-10" />
                    </div>
                    <Button size="lg" onClick={() => setFiltersOpen(open => !open)} aria-expanded={filtersOpen}>
                        <Filter className="h-4 w-4" aria-hidden="true" /> Filtros
                        {activeFilters > 0 && <span className="grid h-5 min-w-5 place-items-center rounded-full bg-adm-accent px-1 text-xs font-bold text-white">{activeFilters}</span>}
                    </Button>
                </div>
                {filtersOpen && <div className="grid grid-cols-1 gap-3 rounded-adm-card border border-adm-line bg-adm-surface p-4 sm:grid-cols-2 lg:grid-cols-4">
                    <Field label="Categoria">
                        <Select value={categoryId} onChange={e => setCategoryId(e.target.value)}>
                            <option value="">Todas as categorias</option>
                            {categories?.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}
                        </Select>
                    </Field>
                    <Field label="Cor">
                        <Select value={color} onChange={e => setColor(e.target.value)}>
                            <option value="">Todas as cores</option>
                            {productColorOptions.map(option => <option key={option} value={option}>{option}</option>)}
                        </Select>
                    </Field>
                    <Field label="Preço mínimo">
                        <Input type="number" min="0" step="0.01" value={minPrice} onChange={e => setMinPrice(e.target.value)} placeholder="R$ 0,00" />
                    </Field>
                    <Field label="Preço máximo">
                        <Input type="number" min="0" step="0.01" value={maxPrice} onChange={e => setMaxPrice(e.target.value)} placeholder="Sem limite" />
                    </Field>
                    {activeFilters > 0 && <button type="button" onClick={resetFilters} className="inline-flex items-center gap-1 rounded text-left text-xs font-medium text-adm-accent-ink hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-adm-accent-ink sm:col-span-2 lg:col-span-4"><X className="h-3.5 w-3.5" aria-hidden="true" /> Limpar filtros</button>}
                </div>}
            </section>

            <section className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-adm-card border border-adm-line bg-adm-surface shadow-adm-card" aria-label="Lista de produtos">
                <div className="flex items-center justify-between border-b border-adm-line px-4 py-3">
                    <h2 className="flex items-center gap-2 text-sm font-semibold"><Package className="h-4 w-4 text-adm-accent-ink" aria-hidden="true" /> Catálogo</h2>
                    <span className="text-xs text-adm-ink-muted" role="status">{isLoading ? 'Carregando...' : `${filteredProducts.length} ${filteredProducts.length === 1 ? 'produto' : 'produtos'}`}</span>
                </div>
                <div className="min-h-0 flex-1 overflow-auto">
                    <Table className="hidden min-w-[760px] md:table">
                        <Thead className="sticky top-0 z-10 shadow-sm">
                            <tr><Th>Produto</Th><Th>Categoria</Th><Th className="text-right">Preço</Th><Th className="text-right">Estoque</Th><Th>Status</Th><Th className="text-right">Ações</Th></tr>
                        </Thead>
                        <Tbody>
                            {showSkeleton && <TableSkeletonRows columns={6} rows={8} />}
                            {filteredProducts.map(product => <tr key={product.id} className={rowClass()}>
                                <Td className="py-2.5">
                                    <div className="flex min-w-0 items-center gap-3">
                                        {thumbnail(product, 40)}
                                        <div className="min-w-0"><p className="max-w-[280px] truncate text-sm font-medium">{product.name}</p><p className="text-xs text-adm-ink-subtle">#{product.id.slice(0, 8)}</p></div>
                                    </div>
                                </Td>
                                <Td className="py-2.5 text-adm-ink-muted">{getCategoryName(product)}</Td>
                                <Td className="py-2.5 text-right font-medium tabular-nums">{formatPrice(product.price)}</Td>
                                <Td className="py-2.5 text-right tabular-nums text-adm-ink-muted">{product.stock ?? 0}<span className="ml-1 text-xs text-adm-ink-subtle">un.</span></Td>
                                <Td className="py-2.5">{statusBadge(product)}</Td>
                                <Td className="py-2.5"><div className="flex justify-end gap-1">
                                    <IconLink href={`/admin/produtos/${product.id}`} label={`Editar ${product.name}`}><Edit className="h-4 w-4" aria-hidden="true" /></IconLink>
                                    <IconButton tone="danger" label={`Excluir ${product.name}`} onClick={() => handleDelete(product.id)}><Trash2 className="h-4 w-4" aria-hidden="true" /></IconButton>
                                </div></Td>
                            </tr>)}
                        </Tbody>
                    </Table>

                    <div className="divide-y divide-adm-line md:hidden">
                        {showSkeleton && Array.from({ length: 5 }, (_, index) => <div key={index} className="flex items-start gap-3 p-3" aria-hidden="true">
                            <Skeleton className="h-14 w-14 shrink-0" />
                            <div className="flex-1 space-y-2"><Skeleton className="h-4 w-3/4" /><Skeleton className="h-3 w-1/3" /><Skeleton className="h-4 w-1/2" /></div>
                        </div>)}
                        {filteredProducts.map(product => <article key={product.id} className="flex items-start gap-3 p-3">
                            {thumbnail(product, 56)}
                            <div className="min-w-0 flex-1">
                                <div className="flex items-start justify-between gap-2"><h3 className="truncate text-sm font-semibold">{product.name}</h3>{statusBadge(product)}</div>
                                <p className="mt-0.5 truncate text-xs text-adm-ink-subtle">{getCategoryName(product)}</p>
                                <div className="mt-2 flex items-center justify-between gap-2"><p className="text-sm font-semibold tabular-nums">{formatPrice(product.price)} <span className="font-normal text-adm-ink-subtle">· {product.stock ?? 0} un.</span></p><div className="flex gap-1">
                                    <IconLink href={`/admin/produtos/${product.id}`} label={`Editar ${product.name}`}><Edit className="h-4 w-4" aria-hidden="true" /></IconLink>
                                    <IconButton tone="danger" label={`Excluir ${product.name}`} onClick={() => handleDelete(product.id)}><Trash2 className="h-4 w-4" aria-hidden="true" /></IconButton>
                                </div></div>
                            </div>
                        </article>)}
                    </div>

                    {productsError && !productsData && <ErrorState title="Não foi possível carregar os produtos." onRetry={() => { void mutate(); }} />}
                    {!isLoading && !productsError && filteredProducts.length === 0 && <EmptyState
                        icon={Package}
                        title="Nenhum produto encontrado"
                        description="Revise a busca ou os filtros selecionados."
                    />}
                </div>
            </section>
        </div>
    );
}
