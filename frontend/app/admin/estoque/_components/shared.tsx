'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { ChevronLeft, ChevronRight, Info, TriangleAlert } from 'lucide-react';
import type { MovementType, StockVariant } from '@/types/stock';
import { cn } from '../../../components/ui/Button';
import { Badge, Button } from '../../_components/ui';
import { MOVEMENT_LABEL, MOVEMENT_TONE } from '../_lib/format';

/** Valor que só muda depois de `delay` ms sem alteração (busca sem uma requisição por tecla). */
export function useDebounced<T>(value: T, delay = 300): T {
    const [debounced, setDebounced] = useState(value);
    useEffect(() => {
        const timer = setTimeout(() => setDebounced(value), delay);
        return () => clearTimeout(timer);
    }, [value, delay]);
    return debounced;
}

export function MovementBadge({ type }: { type: MovementType }) {
    return <Badge tone={MOVEMENT_TONE[type] ?? 'neutral'}>{MOVEMENT_LABEL[type] ?? type}</Badge>;
}

/** "Camiseta DN · M · Preto (inativo)" — o sufixo aparece quando a variação ou o produto está inativo. */
export function variantLabel(v: Pick<StockVariant, 'productName' | 'size' | 'color' | 'active' | 'productActive'>) {
    const parts = [v.productName, v.size, v.color].filter(Boolean).join(' · ');
    return v.active && v.productActive ? parts : `${parts} (inativo)`;
}

export function Pager({ page, totalPages, totalElements, onPage }: { page: number; totalPages: number; totalElements: number; onPage: (page: number) => void }) {
    if (totalElements === 0) return null;
    return (
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-adm-line px-4 py-3 text-sm text-adm-ink-muted">
            <span aria-live="polite">
                Página {page + 1} de {Math.max(totalPages, 1)} · {totalElements} {totalElements === 1 ? 'registro' : 'registros'}
            </span>
            <div className="flex items-center gap-2">
                <Button size="sm" disabled={page <= 0} onClick={() => onPage(page - 1)}>
                    <ChevronLeft className="h-4 w-4" aria-hidden="true" /> Anterior
                </Button>
                <Button size="sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
                    Próxima <ChevronRight className="h-4 w-4" aria-hidden="true" />
                </Button>
            </div>
        </div>
    );
}

const noticeTones = {
    info: 'border-adm-info/30 bg-adm-info/10 text-adm-info',
    warning: 'border-adm-warning/30 bg-adm-warning/10 text-adm-warning',
    danger: 'border-adm-danger/30 bg-adm-danger/10 text-adm-danger',
} as const;

/** Aviso em linha (erro de envio, divergência, nota duplicada). */
export function Notice({ tone = 'info', children, className }: { tone?: keyof typeof noticeTones; children: ReactNode; className?: string }) {
    const Icon = tone === 'info' ? Info : TriangleAlert;
    return (
        <div role={tone === 'info' ? 'status' : 'alert'} className={cn('flex items-start gap-2.5 rounded-adm-control border px-3.5 py-3 text-sm', noticeTones[tone], className)}>
            <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <div className="min-w-0 text-adm-ink">{children}</div>
        </div>
    );
}

/** Delta com sinal e cor: entrada verde, saída vermelha, zero neutro. */
export function Delta({ value }: { value: number }) {
    const tone = value > 0 ? 'text-adm-success' : value < 0 ? 'text-adm-danger' : 'text-adm-ink-muted';
    return <span className={cn('font-semibold tabular-nums', tone)}>{value > 0 ? `+${value}` : value}</span>;
}
