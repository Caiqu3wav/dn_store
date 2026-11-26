'use client';

import { useId, useMemo, useState } from 'react';
import useSWR from 'swr';
import { Search, X } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { qs } from '@/services/stockService';
import type { PageView, StockVariant } from '@/types/stock';
import { cn } from '../../../components/ui/Button';
import { Checkbox, controlClass } from '../../_components/form';
import { Skeleton } from '../../_components/ui';
import { useDebounced, variantLabel } from './shared';

export type PickerValue =
    | { kind: 'variant'; variant: StockVariant }
    | { kind: 'product'; productId: string; productName: string };

type Option =
    | { key: string; kind: 'product'; productId: string; productName: string; inactive: boolean }
    | { key: string; kind: 'variant'; variant: StockVariant };

type Props = {
    label: string;
    value: PickerValue | null;
    onChange: (value: PickerValue | null) => void;
    /** Mostra, antes das variações de cada produto, a opção "todas as variações" (filtros do log). */
    allowProduct?: boolean;
    placeholder?: string;
    disabled?: boolean;
    /** Variações já usadas em outras linhas; aparecem marcadas e não podem ser escolhidas de novo. */
    takenVariantIds?: string[];
    invalid?: boolean;
    id?: string;
};

export function describePicker(value: PickerValue) {
    return value.kind === 'variant' ? variantLabel(value.variant) : `${value.productName} (todas as variações)`;
}

/**
 * Seletor de produto/variação com busca no servidor. Só mostra itens ativos, a menos que
 * "Mostrar inativos" esteja marcado — os inativos aparecem como "(inativo)".
 */
export function VariantPicker({ label, value, onChange, allowProduct = false, placeholder = 'Buscar por produto, tamanho, cor ou SKU', disabled, takenVariantIds = [], invalid, id }: Props) {
    const listId = useId();
    const [query, setQuery] = useState('');
    const [open, setOpen] = useState(false);
    const [includeInactive, setIncludeInactive] = useState(false);
    const [activeIndex, setActiveIndex] = useState(0);
    const debouncedQuery = useDebounced(query.trim());

    const { data, error, isLoading, mutate } = useSWR<PageView<StockVariant>>(
        open ? `/admin/stock/variants${qs({ q: debouncedQuery, includeInactive, size: 30 })}` : null,
        fetcher,
        { keepPreviousData: true },
    );

    const options = useMemo<Option[]>(() => {
        const result: Option[] = [];
        const seen = new Set<string>();
        for (const v of data?.items ?? []) {
            if (allowProduct && !seen.has(v.productId)) {
                seen.add(v.productId);
                result.push({ key: `p-${v.productId}`, kind: 'product', productId: v.productId, productName: v.productName, inactive: !v.productActive });
            }
            result.push({ key: `v-${v.id}`, kind: 'variant', variant: v });
        }
        return result;
    }, [data, allowProduct]);

    const isTaken = (option: Option) => option.kind === 'variant' && takenVariantIds.includes(option.variant.id);
    const current = options.length ? Math.min(activeIndex, options.length - 1) : -1;

    const choose = (option: Option) => {
        if (isTaken(option)) return;
        onChange(option.kind === 'variant'
            ? { kind: 'variant', variant: option.variant }
            : { kind: 'product', productId: option.productId, productName: option.productName });
        setOpen(false);
        setQuery('');
    };

    const onKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
        if (event.key === 'ArrowDown') {
            event.preventDefault();
            setOpen(true);
            setActiveIndex(Math.min(current + 1, options.length - 1));
        } else if (event.key === 'ArrowUp') {
            event.preventDefault();
            setActiveIndex(Math.max(current - 1, 0));
        } else if (event.key === 'Enter' && open && current >= 0) {
            event.preventDefault();
            choose(options[current]);
        } else if (event.key === 'Escape' && open) {
            event.stopPropagation();
            setOpen(false);
        }
    };

    if (value && !open) {
        return (
            <div className={cn(controlClass, 'flex items-center justify-between gap-2', invalid && 'border-adm-danger')} aria-label={label}>
                <span className="min-w-0 truncate" title={describePicker(value)}>{describePicker(value)}</span>
                {!disabled && (
                    <button
                        type="button"
                        aria-label={`Trocar ${label.toLowerCase()}`}
                        title="Trocar"
                        onClick={() => { onChange(null); setOpen(true); }}
                        className="grid h-6 w-6 shrink-0 place-items-center rounded text-adm-ink-muted hover:bg-adm-hover hover:text-adm-ink"
                    >
                        <X className="h-4 w-4" aria-hidden="true" />
                    </button>
                )}
            </div>
        );
    }

    return (
        <div
            className="relative"
            onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false); }}
        >
            <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-adm-ink-subtle" aria-hidden="true" />
                <input
                    id={id}
                    role="combobox"
                    aria-label={label}
                    aria-expanded={open}
                    aria-controls={listId}
                    aria-autocomplete="list"
                    aria-invalid={invalid || undefined}
                    aria-activedescendant={open && current >= 0 ? `${listId}-${current}` : undefined}
                    autoComplete="off"
                    disabled={disabled}
                    value={query}
                    placeholder={placeholder}
                    onFocus={() => setOpen(true)}
                    onChange={event => { setQuery(event.target.value); setActiveIndex(0); setOpen(true); }}
                    onKeyDown={onKeyDown}
                    className={cn(controlClass, 'pl-9', invalid && 'border-adm-danger')}
                />
            </div>

            {open && (
                <div className="absolute z-30 mt-1 w-full min-w-72 overflow-hidden rounded-adm-card border border-adm-line-strong bg-adm-surface shadow-adm-overlay">
                    <div className="flex items-center justify-between gap-2 border-b border-adm-line px-3 py-2">
                        <Checkbox checked={includeInactive} onChange={event => { setIncludeInactive(event.target.checked); setActiveIndex(0); }}>
                            Mostrar inativos
                        </Checkbox>
                        {data && <span className="text-xs text-adm-ink-subtle">{data.totalElements} {data.totalElements === 1 ? 'resultado' : 'resultados'}</span>}
                    </div>
                    <ul id={listId} role="listbox" aria-label={`Resultados de ${label.toLowerCase()}`} className="max-h-72 overflow-y-auto py-1" onMouseDown={event => event.preventDefault()}>
                        {isLoading && !data && Array.from({ length: 4 }, (_, i) => (
                            <li key={i} aria-hidden="true" className="px-3 py-2"><Skeleton className="h-4 w-full" /></li>
                        ))}
                        {error && (
                            <li className="px-3 py-3 text-sm text-adm-danger" role="alert">
                                Não foi possível buscar.{' '}
                                <button type="button" className="underline" onClick={() => mutate()}>Tentar de novo</button>
                            </li>
                        )}
                        {!error && data && options.length === 0 && (
                            <li className="px-3 py-3 text-sm text-adm-ink-muted">
                                Nada encontrado{!includeInactive ? ' entre os ativos' : ''}.
                                {!includeInactive && ' Marque “Mostrar inativos” para incluir os demais.'}
                            </li>
                        )}
                        {options.map((option, index) => {
                            const taken = isTaken(option);
                            return (
                                <li
                                    key={option.key}
                                    id={`${listId}-${index}`}
                                    role="option"
                                    aria-selected={index === current}
                                    aria-disabled={taken || undefined}
                                    onClick={() => choose(option)}
                                    onMouseEnter={() => setActiveIndex(index)}
                                    className={cn(
                                        'cursor-pointer px-3 py-2 text-sm',
                                        option.kind === 'variant' ? 'pl-6 text-adm-ink' : 'font-medium text-adm-ink-muted',
                                        index === current && 'bg-adm-hover',
                                        taken && 'cursor-not-allowed opacity-50',
                                    )}
                                >
                                    {option.kind === 'product' ? (
                                        <>Todas as variações de {option.productName}{option.inactive ? ' (inativo)' : ''}</>
                                    ) : (
                                        <span className="flex items-center justify-between gap-3">
                                            <span className="min-w-0 truncate">{variantLabel(option.variant)}</span>
                                            <span className="shrink-0 text-xs tabular-nums text-adm-ink-subtle">
                                                {taken ? 'já na nota' : `saldo ${option.variant.stock}`}
                                            </span>
                                        </span>
                                    )}
                                </li>
                            );
                        })}
                    </ul>
                </div>
            )}
        </div>
    );
}
