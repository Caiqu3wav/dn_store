'use client';

import { useEffect, useRef, useState } from 'react';
import useSWR from 'swr';
import { ChevronLeft, ChevronRight, Search, X } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { fetcher } from '@/services/productService';
import { buildQuery, stockService } from '@/services/stockService';
import { getApiErrorMessage } from '@/utils/apiError';
import type {
    PageResponse,
    StockAdjustmentReason,
    StockMovementType,
    StockVariant,
} from '@/types/stock';
import { Badge, Button, type BadgeTone } from '../_components/ui';
import { Field, Input, Select, Textarea } from '../_components/form';
import { Modal } from '../_components/Modal';
import { useConfirm } from '../_components/confirm';

/* ----------------------------------------------------------------- helpers */

export const MOVEMENT_TYPES: Record<StockMovementType, { label: string; tone: BadgeTone }> = {
    ENTRADA_NOTA: { label: 'Entrada por nota', tone: 'success' },
    SAIDA_VENDA: { label: 'Venda', tone: 'info' },
    DEVOLUCAO_PEDIDO: { label: 'Devolução de pedido', tone: 'violet' },
    AJUSTE: { label: 'Ajuste manual', tone: 'warning' },
    EDICAO_CADASTRO: { label: 'Edição no cadastro', tone: 'neutral' },
    SALDO_INICIAL: { label: 'Saldo inicial', tone: 'accent' },
};

export const ADJUSTMENT_REASONS: Record<StockAdjustmentReason, string> = {
    CORRECAO_INVENTARIO: 'Correção de inventário (contagem)',
    PERDA: 'Perda',
    AVARIA: 'Avaria',
    DEVOLUCAO_CLIENTE: 'Devolução de cliente',
};

export function MovementTypeBadge({ type }: { type: StockMovementType }) {
    const meta = MOVEMENT_TYPES[type];
    return <Badge tone={meta.tone}>{meta.label}</Badge>;
}

export function formatDateTime(value: string | null | undefined): string {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.getTime())
        ? '—'
        : date.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export function formatDate(value: string | null | undefined): string {
    if (!value) return '—';
    // Datas puras (yyyy-MM-dd) sem fuso: evita voltar um dia ao converter para UTC.
    const [year, month, day] = value.slice(0, 10).split('-');
    return year && month && day ? `${day}/${month}/${year}` : '—';
}

export function formatDelta(delta: number): string {
    return delta > 0 ? `+${delta}` : String(delta);
}

export function variantLabel(variant: { productName: string; size: string | null }): string {
    return variant.size ? `${variant.productName} · ${variant.size}` : variant.productName;
}

/** Chave de idempotência: gerada uma vez por abertura do formulário e reutilizada nas retentativas. */
export function newIdempotencyKey(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID();
    return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
}

export function useDebouncedValue<T>(value: T, delay = 300): T {
    const [debounced, setDebounced] = useState(value);
    useEffect(() => {
        const timer = setTimeout(() => setDebounced(value), delay);
        return () => clearTimeout(timer);
    }, [value, delay]);
    return debounced;
}

/* -------------------------------------------------------------- pagination */

export function Pagination({ page, totalPages, totalElements, onChange }: { page: number; totalPages: number; totalElements: number; onChange: (page: number) => void }) {
    if (totalElements === 0) return null;
    return (
        <nav aria-label="Paginação" className="flex flex-wrap items-center justify-between gap-3 border-t border-adm-line px-4 py-3 text-sm text-adm-ink-muted">
            <span>{totalElements} {totalElements === 1 ? 'registro' : 'registros'} · página {page + 1} de {Math.max(totalPages, 1)}</span>
            <div className="flex gap-2">
                <Button size="sm" disabled={page <= 0} onClick={() => onChange(page - 1)} aria-label="Página anterior">
                    <ChevronLeft className="h-4 w-4" aria-hidden="true" /> Anterior
                </Button>
                <Button size="sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)} aria-label="Próxima página">
                    Próxima <ChevronRight className="h-4 w-4" aria-hidden="true" />
                </Button>
            </div>
        </nav>
    );
}

/* ----------------------------------------------------------- variant picker */

/** Busca variações por produto ou SKU e mostra o saldo atual. `value` é a variação escolhida. */
export function VariantPicker({ value, onChange, label = 'Produto / variação', invalid = false }: { value: StockVariant | null; onChange: (variant: StockVariant | null) => void; label?: string; invalid?: boolean }) {
    const [query, setQuery] = useState('');
    const [open, setOpen] = useState(false);
    const debounced = useDebouncedValue(query.trim());
    const containerRef = useRef<HTMLDivElement>(null);
    const { data, isLoading } = useSWR<PageResponse<StockVariant>>(
        open && !value ? `/admin/stock/variants${buildQuery({ q: debounced, size: 8 })}` : null,
        fetcher,
    );

    useEffect(() => {
        if (!open) return;
        const closeOutside = (event: MouseEvent) => {
            if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
        };
        document.addEventListener('mousedown', closeOutside);
        return () => document.removeEventListener('mousedown', closeOutside);
    }, [open]);

    if (value) {
        return (
            <div className="space-y-1.5">
                <span className="block text-xs font-medium text-adm-ink-muted">{label}</span>
                <div className={`flex min-h-10 items-center justify-between gap-2 rounded-adm-control border bg-adm-sunken px-3 py-2 text-sm ${invalid ? 'border-adm-danger' : 'border-adm-line-strong'}`}>
                    <span className="min-w-0 truncate text-adm-ink">
                        {variantLabel(value)}
                        <span className="ml-2 text-xs text-adm-ink-subtle">saldo {value.stock}</span>
                    </span>
                    <button type="button" onClick={() => { onChange(null); setQuery(''); }} aria-label={`Trocar ${variantLabel(value)}`} className="rounded p-1 text-adm-ink-muted hover:bg-adm-hover hover:text-adm-ink">
                        <X className="h-4 w-4" aria-hidden="true" />
                    </button>
                </div>
            </div>
        );
    }

    const results = data?.content ?? [];
    return (
        <div ref={containerRef} className="relative">
            <Field label={label}>
                <span className="relative block">
                    <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-adm-ink-subtle" aria-hidden="true" />
                    <Input
                        value={query}
                        onChange={event => { setQuery(event.target.value); setOpen(true); }}
                        onFocus={() => setOpen(true)}
                        placeholder="Buscar por nome ou SKU"
                        className={`pl-9 ${invalid ? 'border-adm-danger' : ''}`}
                        autoComplete="off"
                    />
                </span>
            </Field>
            {open && (
                <ul role="listbox" aria-label="Variações encontradas" className="absolute z-20 mt-1 max-h-64 w-full overflow-auto rounded-adm-control border border-adm-line-strong bg-adm-surface shadow-adm-card">
                    {isLoading && <li className="px-3 py-2 text-sm text-adm-ink-muted">Buscando…</li>}
                    {!isLoading && results.length === 0 && <li className="px-3 py-2 text-sm text-adm-ink-muted">Nenhuma variação encontrada.</li>}
                    {results.map(variant => (
                        <li key={variant.id} role="option" aria-selected={false}>
                            <button
                                type="button"
                                className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-adm-hover"
                                onClick={() => { onChange(variant); setOpen(false); }}
                            >
                                <span className="min-w-0 truncate text-adm-ink">
                                    {variantLabel(variant)}
                                    {(!variant.active || !variant.productActive) && <span className="ml-2 text-xs text-adm-warning">inativo</span>}
                                </span>
                                <span className="shrink-0 text-xs text-adm-ink-subtle">saldo {variant.stock}</span>
                            </button>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}

/* ---------------------------------------------------------- adjustment modal */

type AdjustProps = {
    open: boolean;
    onClose: () => void;
    /** Variação já escolhida (ex.: clique numa linha do log). Sem ela, o modal oferece a busca. */
    variantId?: string | null;
    onDone: () => void;
};

export function AdjustStockModal({ open, onClose, variantId = null, onDone }: AdjustProps) {
    // O conteúdo só existe com o modal aberto: cada abertura começa com estado novo e uma nova chave.
    return (
        <Modal open={open} onClose={onClose} title="Ajustar estoque" description="Correção de inventário, perda, avaria ou devolução de cliente. Fica registrada no log com seu usuário." size="md">
            {open && <AdjustStockForm onClose={onClose} variantId={variantId} onDone={onDone} />}
        </Modal>
    );
}

function AdjustStockForm({ onClose, variantId, onDone }: Omit<AdjustProps, 'open'>) {
    const confirm = useConfirm();
    const idempotencyKey = useRef(newIdempotencyKey());
    const [picked, setPicked] = useState<StockVariant | null>(null);
    const [reasonCode, setReasonCode] = useState<StockAdjustmentReason>('CORRECAO_INVENTARIO');
    const [amount, setAmount] = useState('');
    const [reason, setReason] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const { data: preselected, mutate: reloadPreselected } = useSWR<StockVariant>(
        variantId ? `/admin/stock/variants/${variantId}` : null,
        fetcher,
    );
    const variant = picked ?? preselected ?? null;

    const parsed = amount.trim() === '' ? null : Number(amount);
    const isCount = reasonCode === 'CORRECAO_INVENTARIO';
    const valid = parsed !== null && Number.isInteger(parsed) && parsed >= 0 && (isCount || parsed > 0);
    let delta = 0;
    if (variant && valid && parsed !== null) {
        if (isCount) delta = parsed - variant.stock;
        else delta = reasonCode === 'DEVOLUCAO_CLIENTE' ? parsed : -parsed;
    }
    const after = variant ? variant.stock + delta : 0;
    const reasonOk = reason.trim().length >= 10;
    const canSubmit = !!variant && valid && delta !== 0 && after >= 0 && reasonOk && !submitting;

    const submit = async () => {
        if (!variant || !canSubmit) return;
        const confirmed = await confirm({
            title: 'Confirmar ajuste de estoque',
            message: (
                <>
                    <strong>{variantLabel(variant)}</strong>: {variant.stock} → <strong>{after}</strong> ({formatDelta(delta)}).
                    <br />O ajuste fica registrado no log e não pode ser apagado; para desfazer, faça um novo ajuste.
                </>
            ),
            confirmLabel: 'Ajustar estoque',
        });
        if (!confirmed) return;
        setSubmitting(true);
        try {
            await stockService.adjust({
                variantId: variant.id,
                delta,
                expectedBalance: variant.stock,
                reasonCode,
                reason: reason.trim(),
                idempotencyKey: idempotencyKey.current,
            });
            toast.success('Estoque ajustado.');
            onDone();
            onClose();
        } catch (error) {
            toast.error(getApiErrorMessage(error, 'Não foi possível ajustar o estoque.'));
            // Saldo desatualizado (409): recarrega o saldo atual para o admin conferir antes de tentar de novo.
            if (variantId) void reloadPreselected();
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <form className="space-y-4" onSubmit={event => { event.preventDefault(); void submit(); }}>
            {variantId ? (
                <p className="rounded-adm-control border border-adm-line bg-adm-sunken px-3 py-2 text-sm text-adm-ink">
                    {variant ? <>{variantLabel(variant)} <span className="text-adm-ink-subtle">· saldo atual {variant.stock}</span></> : 'Carregando variação…'}
                </p>
            ) : (
                <VariantPicker value={picked} onChange={setPicked} />
            )}
            <Field label="Categoria">
                <Select value={reasonCode} onChange={event => { setReasonCode(event.target.value as StockAdjustmentReason); setAmount(''); }}>
                    {Object.entries(ADJUSTMENT_REASONS).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
                </Select>
            </Field>
            <Field label={isCount ? 'Quantidade contada (novo saldo)' : 'Quantidade (unidades)'} hint={variant && valid && delta !== 0 ? `Saldo: ${variant.stock} → ${after} (${formatDelta(delta)})` : undefined}>
                <Input type="number" min={isCount ? 0 : 1} step={1} inputMode="numeric" value={amount} onChange={event => setAmount(event.target.value)} />
            </Field>
            <Field label="Motivo (mínimo 10 caracteres)">
                <Textarea rows={3} maxLength={500} value={reason} onChange={event => setReason(event.target.value)} placeholder="Ex.: contagem física de novembro, caixa molhada no depósito…" />
            </Field>
            {variant && valid && after < 0 && <p role="alert" className="text-sm text-adm-danger">O estoque não pode ficar negativo.</p>}
            {variant && valid && delta === 0 && <p className="text-sm text-adm-ink-muted">O saldo contado é igual ao atual; não há o que ajustar.</p>}
            <div className="flex justify-end gap-2 border-t border-adm-line pt-4">
                <Button onClick={onClose} disabled={submitting}>Cancelar</Button>
                <Button type="submit" variant="primary" loading={submitting} disabled={!canSubmit}>Ajustar estoque</Button>
            </div>
        </form>
    );
}
