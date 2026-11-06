'use client';

import { useMemo, useState } from 'react';
import { Check, Loader2, Plus, Trash2, X } from 'lucide-react';
import useSWR from 'swr';
import { toast } from 'react-hot-toast';
import { fetcher, productService } from '@/services/productService';
import {
    Category,
    Product,
    ProductDefaultRule,
    ProductDefaultRuleCriteria,
    ProductDefaultRuleValues,
    PRODUCT_VARIANT_SIZES
} from '@/types';
import { productColorOptions } from '@/utils/productColor';

type Props = {
    products: Product[];
    loading: boolean;
    categories: Category[];
    filters: {
        search: string;
        categoryId: string;
        color: string;
        minPrice: string;
        maxPrice: string;
    };
    onApplied: () => void;
};

type FieldKey =
    | 'name'
    | 'description'
    | 'price'
    | 'promotionalPrice'
    | 'active'
    | 'color'
    | 'categoryId'
    | 'weight'
    | 'width'
    | 'height'
    | 'depth'
    | 'sizes';

type FormValues = {
    name: string;
    description: string;
    price: string;
    promotionalPrice: string;
    clearPromotionalPrice: boolean;
    active: string;
    color: string;
    clearColor: boolean;
    categoryId: string;
    clearCategory: boolean;
    weight: string;
    width: string;
    height: string;
    depth: string;
    sizes: string[];
};

const initialValues: FormValues = {
    name: '',
    description: '',
    price: '',
    promotionalPrice: '',
    clearPromotionalPrice: false,
    active: '',
    color: '',
    clearColor: false,
    categoryId: '',
    clearCategory: false,
    weight: '',
    width: '',
    height: '',
    depth: '',
    sizes: []
};

const fieldClass = 'mt-1 min-h-10 w-full rounded-md border border-white/10 bg-[#1A1B1D] px-3 text-sm text-white disabled:opacity-50';

function getErrorMessage(error: unknown) {
    if (typeof error === 'object' && error !== null && 'response' in error) {
        const response = error.response;
        if (typeof response === 'object' && response !== null && 'data' in response) {
            const data = response.data;
            if (typeof data === 'object' && data !== null && 'message' in data
                && typeof data.message === 'string') {
                return data.message;
            }
        }
    }
    return error instanceof Error ? error.message : 'Não foi possível salvar o padrão.';
}

export default function ProductDefaultsManager({ products, loading, categories, filters, onApplied }: Props) {
    const [open, setOpen] = useState(false);
    const [ruleName, setRuleName] = useState('');
    const [enabledFields, setEnabledFields] = useState<Set<FieldKey>>(new Set());
    const [values, setValues] = useState<FormValues>(initialValues);
    const [excludedIds, setExcludedIds] = useState<Set<string>>(new Set());
    const [saving, setSaving] = useState(false);
    const { data: rules, error: rulesError, mutate: mutateRules } = useSWR<ProductDefaultRule[]>(
        open ? '/products/admin/default-rules' : null,
        fetcher
    );

    const criteria = useMemo<ProductDefaultRuleCriteria>(() => ({
        ...(filters.search.trim() ? { nameContains: filters.search.trim() } : {}),
        ...(filters.categoryId ? { categoryId: filters.categoryId } : {}),
        ...(filters.color ? { color: filters.color } : {}),
        ...(filters.minPrice ? { minPrice: Number(filters.minPrice) } : {}),
        ...(filters.maxPrice ? { maxPrice: Number(filters.maxPrice) } : {})
    }), [filters]);

    const updateValue = <K extends keyof FormValues>(field: K, value: FormValues[K]) => {
        setValues(current => ({ ...current, [field]: value }));
    };

    const toggleField = (field: FieldKey) => {
        setEnabledFields(current => {
            const next = new Set(current);
            if (next.has(field)) next.delete(field);
            else next.add(field);
            return next;
        });
    };

    const toggleSize = (size: string) => {
        setValues(current => ({
            ...current,
            sizes: current.sizes.includes(size)
                ? current.sizes.filter(item => item !== size)
                : [...current.sizes, size]
        }));
    };

    const close = () => {
        setOpen(false);
        setRuleName('');
        setEnabledFields(new Set());
        setValues(initialValues);
        setExcludedIds(new Set());
    };

    const buildValues = (): ProductDefaultRuleValues => ({
        ...(enabledFields.has('name') ? { name: values.name.trim() } : {}),
        ...(enabledFields.has('description') ? { description: values.description } : {}),
        ...(enabledFields.has('price') ? { price: Number(values.price) } : {}),
        ...(enabledFields.has('promotionalPrice')
            ? values.clearPromotionalPrice
                ? { clearPromotionalPrice: true }
                : { promotionalPrice: Number(values.promotionalPrice) }
            : {}),
        ...(enabledFields.has('active') ? { active: values.active === 'true' } : {}),
        ...(enabledFields.has('color')
            ? values.clearColor ? { clearColor: true } : { color: values.color }
            : {}),
        ...(enabledFields.has('categoryId')
            ? values.clearCategory
                ? { clearCategory: true }
                : { categoryId: values.categoryId }
            : {}),
        ...(enabledFields.has('weight') ? { weight: Number(values.weight) } : {}),
        ...(enabledFields.has('width') ? { width: Number(values.width) } : {}),
        ...(enabledFields.has('height') ? { height: Number(values.height) } : {}),
        ...(enabledFields.has('depth') ? { depth: Number(values.depth) } : {}),
        ...(enabledFields.has('sizes') ? { sizes: values.sizes } : {})
    });

    const handleCreate = async () => {
        if (Object.keys(criteria).length === 0) {
            toast.error('Defina ao menos um filtro para identificar os produtos.');
            return;
        }
        if (enabledFields.size === 0) {
            toast.error('Selecione ao menos um campo para editar.');
            return;
        }
        if (enabledFields.has('name') && !values.name.trim()) {
            toast.error('Informe o novo nome dos produtos.');
            return;
        }
        if (enabledFields.has('price') && !values.price) {
            toast.error('Informe o preço padrão.');
            return;
        }
        if (enabledFields.has('promotionalPrice') && !values.clearPromotionalPrice && !values.promotionalPrice) {
            toast.error('Informe o preço promocional ou escolha removê-lo.');
            return;
        }
        if (enabledFields.has('active') && values.active === '') {
            toast.error('Escolha se os produtos ficarão ativos.');
            return;
        }
        if (enabledFields.has('color') && !values.clearColor && !values.color) {
            toast.error('Escolha a cor padrão.');
            return;
        }
        if (enabledFields.has('categoryId') && !values.clearCategory && !values.categoryId) {
            toast.error('Escolha a categoria padrão.');
            return;
        }
        if (enabledFields.has('sizes') && values.sizes.length === 0) {
            toast.error('Selecione ao menos um tamanho para adicionar.');
            return;
        }
        for (const field of ['weight', 'width', 'height', 'depth'] as const) {
            if (enabledFields.has(field) && (!values[field] || Number(values[field]) <= 0)) {
                toast.error('Peso e dimensões devem ser maiores que zero.');
                return;
            }
        }

        const name = ruleName.trim();
        if (!name) {
            toast.error('Dê um nome para este padrão.');
            return;
        }

        setSaving(true);
        try {
            const rule = await productService.createDefaultRule({
                name,
                criteria,
                values: buildValues(),
                excludedProductIds: [...excludedIds]
            });
            toast.success(`Padrão salvo e aplicado a ${rule.updatedProducts} produto(s).`);
            await mutateRules();
            onApplied();
            setRuleName('');
            setEnabledFields(new Set());
            setValues(initialValues);
            setExcludedIds(new Set());
        } catch (error) {
            toast.error(getErrorMessage(error));
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async (rule: ProductDefaultRule) => {
        if (!confirm(`Remover o padrão "${rule.name}"? Os produtos já editados não serão revertidos.`)) return;
        try {
            await productService.deleteDefaultRule(rule.id);
            await mutateRules();
            toast.success('Padrão removido.');
        } catch (error) {
            toast.error(getErrorMessage(error));
        }
    };

    const checkbox = (field: FieldKey, label: string) => (
        <label className="flex items-center gap-2 text-sm text-gray-200">
            <input type="checkbox" checked={enabledFields.has(field)} onChange={() => toggleField(field)} className="accent-red-600" />
            {label}
        </label>
    );

    return (
        <>
            <button type="button" onClick={() => setOpen(true)} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg border border-brand-secondary/60 px-3 text-sm font-medium text-red-200 transition hover:bg-brand-secondary/10 sm:px-4">
                <Check className="h-4 w-4" /> Definir padrões
            </button>

            {open && <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-3 sm:p-6">
                <section role="dialog" aria-modal="true" aria-labelledby="product-defaults-title" className="max-h-[92dvh] w-full max-w-4xl overflow-y-auto rounded-xl border border-white/10 bg-[#202123] p-4 text-white shadow-2xl sm:p-6">
                    <header className="mb-5 flex items-start justify-between gap-4 border-b border-white/10 pb-4">
                        <div>
                            <h2 id="product-defaults-title" className="text-xl font-bold">Definir padrões de produto</h2>
                            <p className="mt-1 text-sm text-gray-400">O padrão será aplicado agora aos produtos correspondentes e salvo para novos produtos que atendam aos filtros. Em caso de conflito, o padrão mais recente prevalece.</p>
                        </div>
                        <button type="button" aria-label="Fechar" onClick={close} className="grid h-9 w-9 shrink-0 place-items-center rounded-lg text-gray-400 hover:bg-white/10 hover:text-white"><X className="h-5 w-5" /></button>
                    </header>

                    <div className="space-y-5">
                        <section className="space-y-2">
                            <h3 className="text-sm font-semibold">Filtros usados para encontrar produtos</h3>
                            {Object.keys(criteria).length === 0
                                ? <p className="rounded-lg border border-amber-400/30 bg-amber-400/5 p-3 text-sm text-amber-200">Nenhum filtro definido. Feche esta janela e use a busca ou os filtros da lista antes de criar um padrão.</p>
                                : <div className="flex flex-wrap gap-2 text-xs">
                                    {criteria.nameContains && <span className="rounded-full bg-white/10 px-3 py-1">Nome contém “{criteria.nameContains}”</span>}
                                    {criteria.categoryId && <span className="rounded-full bg-white/10 px-3 py-1">Categoria: {categories.find(category => category.id === criteria.categoryId)?.name ?? 'selecionada'}</span>}
                                    {criteria.color && <span className="rounded-full bg-white/10 px-3 py-1">Cor: {criteria.color}</span>}
                                    {criteria.minPrice != null && <span className="rounded-full bg-white/10 px-3 py-1">Preço ≥ R$ {criteria.minPrice}</span>}
                                    {criteria.maxPrice != null && <span className="rounded-full bg-white/10 px-3 py-1">Preço ≤ R$ {criteria.maxPrice}</span>}
                                </div>}
                        </section>

                        <section className="space-y-2">
                            <div className="flex flex-wrap items-center justify-between gap-2">
                                <h3 className="text-sm font-semibold">Produtos encontrados: {products.length}</h3>
                                <span className="text-xs text-gray-400">Desmarque os produtos que devem ficar de fora.</span>
                            </div>
                            {loading
                                ? <p className="rounded-lg border border-white/10 p-3 text-sm text-gray-400">Carregando produtos correspondentes...</p>
                                : products.length > 0 && <div className="max-h-44 space-y-1 overflow-y-auto rounded-lg border border-white/10 bg-[#191A1B] p-2">
                                {products.map(product => <label key={product.id} className="flex cursor-pointer items-center gap-3 rounded px-2 py-2 text-sm hover:bg-white/5">
                                    <input
                                        type="checkbox"
                                        checked={!excludedIds.has(product.id)}
                                        onChange={() => setExcludedIds(current => {
                                            const next = new Set(current);
                                            if (next.has(product.id)) next.delete(product.id);
                                            else next.add(product.id);
                                            return next;
                                        })}
                                        className="accent-red-600"
                                    />
                                    <span className="min-w-0 flex-1 truncate">{product.name}</span>
                                    <span className="text-xs text-gray-500">{product.color || 'sem cor'}</span>
                                </label>)}
                            </div>}
                            {!loading && products.length === 0 && <p className="rounded-lg border border-white/10 p-3 text-sm text-gray-400">Nenhum produto atual corresponde. A regra ainda poderá ser aplicada a novos produtos.</p>}
                        </section>

                        <section className="space-y-3">
                            <h3 className="text-sm font-semibold">Campos para editar</h3>
                            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                                <div className="rounded-lg border border-white/10 p-3">
                                    {checkbox('name', 'Nome')}
                                    <input disabled={!enabledFields.has('name')} value={values.name} onChange={event => updateValue('name', event.target.value)} className={fieldClass} placeholder="Novo nome comum" />
                                </div>
                                <div className="rounded-lg border border-white/10 p-3">
                                    {checkbox('price', 'Preço')}
                                    <input disabled={!enabledFields.has('price')} type="number" min="0" step="0.01" value={values.price} onChange={event => updateValue('price', event.target.value)} className={fieldClass} placeholder="0,00" />
                                </div>
                                <div className="rounded-lg border border-white/10 p-3">
                                    {checkbox('promotionalPrice', 'Preço promocional')}
                                    <input disabled={!enabledFields.has('promotionalPrice') || values.clearPromotionalPrice} type="number" min="0" step="0.01" value={values.promotionalPrice} onChange={event => updateValue('promotionalPrice', event.target.value)} className={fieldClass} placeholder="0,00" />
                                    <label className="mt-2 flex items-center gap-2 text-xs text-gray-400">
                                        <input disabled={!enabledFields.has('promotionalPrice')} type="checkbox" checked={values.clearPromotionalPrice} onChange={event => updateValue('clearPromotionalPrice', event.target.checked)} className="accent-red-600" />
                                        Remover preço promocional
                                    </label>
                                </div>
                                <div className="rounded-lg border border-white/10 p-3">
                                    {checkbox('active', 'Disponibilidade')}
                                    <select disabled={!enabledFields.has('active')} value={values.active} onChange={event => updateValue('active', event.target.value)} className={fieldClass}>
                                        <option value="">Escolha...</option><option value="true">Ativo</option><option value="false">Inativo</option>
                                    </select>
                                </div>
                                <div className="rounded-lg border border-white/10 p-3">
                                    {checkbox('color', 'Cor')}
                                    <select disabled={!enabledFields.has('color') || values.clearColor} value={values.color} onChange={event => updateValue('color', event.target.value)} className={fieldClass}>
                                        <option value="">Escolha...</option>{productColorOptions.map(color => <option key={color} value={color}>{color}</option>)}
                                    </select>
                                    <label className="mt-2 flex items-center gap-2 text-xs text-gray-400">
                                        <input disabled={!enabledFields.has('color')} type="checkbox" checked={values.clearColor} onChange={event => updateValue('clearColor', event.target.checked)} className="accent-red-600" />
                                        Remover cor
                                    </label>
                                </div>
                                <div className="rounded-lg border border-white/10 p-3">
                                    {checkbox('categoryId', 'Categoria')}
                                    <select disabled={!enabledFields.has('categoryId') || values.clearCategory} value={values.categoryId} onChange={event => updateValue('categoryId', event.target.value)} className={fieldClass}>
                                        <option value="">Escolha...</option>{categories.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}
                                    </select>
                                    <label className="mt-2 flex items-center gap-2 text-xs text-gray-400">
                                        <input disabled={!enabledFields.has('categoryId')} type="checkbox" checked={values.clearCategory} onChange={event => updateValue('clearCategory', event.target.checked)} className="accent-red-600" />
                                        Remover categoria
                                    </label>
                                </div>
                                <div className="rounded-lg border border-white/10 p-3 sm:col-span-2 lg:col-span-3">
                                    {checkbox('description', 'Descrição')}
                                    <textarea disabled={!enabledFields.has('description')} rows={2} value={values.description} onChange={event => updateValue('description', event.target.value)} className={fieldClass} placeholder="Descrição comum para os produtos selecionados" />
                                </div>
                            </div>
                        </section>

                        <section className="space-y-3">
                            <h3 className="text-sm font-semibold">Peso e dimensões para frete</h3>
                            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                                {([
                                    ['weight', 'Peso (kg)'],
                                    ['width', 'Largura (cm)'],
                                    ['height', 'Altura (cm)'],
                                    ['depth', 'Comprimento (cm)']
                                ] as const).map(([field, label]) => <div key={field} className="rounded-lg border border-white/10 p-3">
                                    {checkbox(field, label)}
                                    <input disabled={!enabledFields.has(field)} type="number" min="0.01" step="0.01" value={values[field]} onChange={event => updateValue(field, event.target.value)} className={fieldClass} />
                                </div>)}
                            </div>
                        </section>

                        <section className="space-y-2">
                            <h3 className="text-sm font-semibold">Tamanhos</h3>
                            <div className="flex flex-wrap gap-2 rounded-lg border border-white/10 p-3">
                                {checkbox('sizes', 'Adicionar tamanhos ausentes')}
                                {PRODUCT_VARIANT_SIZES.map(size => <label key={size} className="flex items-center gap-1.5 rounded bg-white/5 px-2 py-1 text-xs text-gray-300">
                                    <input
                                        disabled={!enabledFields.has('sizes')}
                                        type="checkbox"
                                        checked={values.sizes.includes(size)}
                                        onChange={() => toggleSize(size)}
                                        className="accent-red-600"
                                    />
                                    {size}
                                </label>)}
                            </div>
                            <p className="text-xs text-gray-500">Tamanhos existentes e estoques são preservados. Os tamanhos adicionados começam com estoque zero.</p>
                        </section>

                        <section className="space-y-2">
                            <label className="block text-sm font-semibold">Nome do padrão
                                <input value={ruleName} onChange={event => setRuleName(event.target.value)} maxLength={150} className={fieldClass} placeholder="Ex.: Camisas DN Ciclismo" />
                            </label>
                        </section>

                        <div className="flex flex-col-reverse justify-end gap-2 border-t border-white/10 pt-4 sm:flex-row">
                            <button type="button" onClick={close} className="min-h-10 rounded-lg border border-white/15 px-4 text-sm text-gray-300 hover:bg-white/5">Fechar</button>
                            <button type="button" onClick={handleCreate} disabled={saving || loading} className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg bg-brand-secondary px-4 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-60">
                                {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
                                Salvar e aplicar padrão
                            </button>
                        </div>

                        <section className="space-y-2 border-t border-white/10 pt-4">
                            <h3 className="text-sm font-semibold">Padrões salvos</h3>
                            {rulesError
                                ? <p className="text-sm text-red-300">Não foi possível carregar os padrões salvos.</p>
                                : rules?.length
                                ? <ul className="space-y-2">{rules.map(rule => <li key={rule.id} className="flex items-center gap-3 rounded-lg border border-white/10 p-3">
                                    <div className="min-w-0 flex-1">
                                        <p className="truncate text-sm font-medium">{rule.name}</p>
                                        <p className="text-xs text-gray-500">{rule.updatedProducts} produto(s) alterado(s) ao criar · aplicado a novos produtos correspondentes</p>
                                    </div>
                                    <button type="button" onClick={() => handleDelete(rule)} title={`Remover padrão ${rule.name}`} className="grid h-9 w-9 shrink-0 place-items-center rounded-md text-gray-400 hover:bg-red-500/10 hover:text-red-400"><Trash2 className="h-4 w-4" /></button>
                                </li>)}</ul>
                                : <p className="text-sm text-gray-500">{rules ? 'Nenhum padrão salvo.' : 'Carregando padrões...'}</p>}
                        </section>
                    </div>
                </section>
            </div>}
        </>
    );
}
