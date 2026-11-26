'use client';

import { Suspense, useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import useSWR from 'swr';
import { History, X } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { qs } from '@/services/stockService';
import type { MovementType, PageView, ReconciliationRow, StockMovement } from '@/types/stock';
import { cn } from '../../components/ui/Button';
import { Field, Input } from '../_components/form';
import { Table, TableWrap, Tbody, Td, Th, Thead, rowClass } from '../_components/table';
import { Button, Card, CardHeader, EmptyState, ErrorState, TableSkeletonRows } from '../_components/ui';
import { Delta, MovementBadge, Notice, Pager } from './_components/shared';
import { VariantPicker, describePicker, type PickerValue } from './_components/VariantPicker';
import { VariantTimelineModal, type TimelineTarget } from './_components/VariantTimelineModal';
import { MOVEMENT_LABEL, formatDateTime, invoiceLabel, shortId } from './_lib/format';

const TYPES = Object.keys(MOVEMENT_LABEL) as MovementType[];
const PAGE_SIZE = 25;

export default function StockLogPage() {
    // useSearchParams exige Suspense no build estático do Next.
    return (
        <Suspense fallback={<Card className="p-5"><TableSkeletonRowsPlaceholder /></Card>}>
            <LogView />
        </Suspense>
    );
}

function TableSkeletonRowsPlaceholder() {
    return <div aria-busy="true" className="h-40 animate-pulse rounded bg-adm-raised" />;
}

function LogView() {
    const router = useRouter();
    const pathname = usePathname();
    const params = useSearchParams();
    const [picked, setPicked] = useState<PickerValue | null>(null);
    const [timeline, setTimeline] = useState<TimelineTarget | null>(null);

    const filters = {
        productId: params.get('productId') ?? '',
        variantId: params.get('variantId') ?? '',
        type: params.getAll('type'),
        from: params.get('from') ?? '',
        to: params.get('to') ?? '',
        performedBy: params.get('performedBy') ?? '',
        entryId: params.get('entryId') ?? '',
        orderId: params.get('orderId') ?? '',
        page: Math.max(Number(params.get('page') ?? 0) || 0, 0),
    };

    /** Atualiza a URL (os filtros ficam compartilháveis). Mudar qualquer filtro volta para a página 1. */
    const update = (patch: Record<string, string | string[] | null>) => {
        const next = new URLSearchParams(params.toString());
        for (const [key, value] of Object.entries(patch)) {
            next.delete(key);
            if (Array.isArray(value)) value.forEach(v => next.append(key, v));
            else if (value) next.set(key, value);
        }
        if (!('page' in patch)) next.delete('page');
        const text = next.toString();
        router.replace(text ? `${pathname}?${text}` : pathname, { scroll: false });
    };

    const key = `/admin/stock/movements${qs({ ...filters, page: filters.page, size: PAGE_SIZE })}`;
    const { data, error, isLoading, mutate } = useSWR<PageView<StockMovement>>(key, fetcher, { keepPreviousData: true });
    const { data: issues } = useSWR<PageView<ReconciliationRow>>('/admin/stock/reconciliation?onlyIssues=true&size=100', fetcher);
    const diverging = issues?.items.filter(row => row.status === 'DIVERGENT').length ?? 0;

    const hasFilters = Boolean(filters.productId || filters.variantId || filters.type.length || filters.from || filters.to
        || filters.performedBy || filters.entryId || filters.orderId);

    const onPick = (value: PickerValue | null) => {
        setPicked(value);
        update({
            productId: value?.kind === 'product' ? value.productId : null,
            variantId: value?.kind === 'variant' ? value.variant.id : null,
        });
    };

    const toggleType = (type: MovementType) =>
        update({ type: filters.type.includes(type) ? filters.type.filter(t => t !== type) : [...filters.type, type] });

    const applyText = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        update({
            from: String(form.get('from') ?? ''),
            to: String(form.get('to') ?? ''),
            performedBy: String(form.get('performedBy') ?? '').trim(),
        });
    };

    return (
        <div className="space-y-6">
            {diverging > 0 && (
                <Notice tone="warning">
                    {diverging === 1 ? '1 variação está' : `${diverging}${issues && issues.totalElements > 100 ? '+' : ''} variações estão`} com saldo diferente do último
                    movimento do livro.{' '}
                    <Link href="/admin/estoque/reconciliacao" className="font-medium text-adm-accent-ink underline">Ver reconciliação</Link>
                </Notice>
            )}

            <Card as="section" aria-label="Filtros do log">
                <CardHeader title="Filtros" actions={hasFilters ? (
                    <Button size="sm" variant="ghost" onClick={() => { setPicked(null); router.replace(pathname, { scroll: false }); }}>Limpar filtros</Button>
                ) : undefined} />
                <div className="space-y-4 p-5">
                    {/* Sem <Field> (um <label>): o clique numa opção seria redirecionado ao input e reabriria a lista. */}
                    <div className="space-y-1.5">
                        <span className="block text-xs font-medium text-adm-ink-muted">Produto ou variação</span>
                        <VariantPicker label="Produto ou variação" allowProduct value={picked} onChange={onPick} />
                    </div>
                    {(filters.variantId || filters.productId) && !picked && (
                        <p className="text-xs text-adm-ink-muted">
                            Filtrando por {filters.variantId ? `a variação ${shortId(filters.variantId)}` : `o produto ${shortId(filters.productId)}`} (vindo de um link).
                        </p>
                    )}
                    <fieldset>
                        <legend className="mb-1.5 text-xs font-medium text-adm-ink-muted">Tipo de movimento</legend>
                        <div className="flex flex-wrap gap-2">
                            {TYPES.map(type => {
                                const on = filters.type.includes(type);
                                return (
                                    <button
                                        key={type}
                                        type="button"
                                        aria-pressed={on}
                                        onClick={() => toggleType(type)}
                                        className={cn(
                                            'min-h-8 rounded-full border px-3 text-xs font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-adm-accent-ink',
                                            on ? 'border-adm-accent-ink bg-adm-accent/15 text-adm-accent-ink' : 'border-adm-line-strong text-adm-ink-muted hover:bg-adm-hover',
                                        )}
                                    >
                                        {MOVEMENT_LABEL[type]}
                                    </button>
                                );
                            })}
                        </div>
                    </fieldset>
                    <form key={`${filters.from}|${filters.to}|${filters.performedBy}`} onSubmit={applyText} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 lg:items-end">
                        <Field label="De"><Input type="date" name="from" defaultValue={filters.from} /></Field>
                        <Field label="Até"><Input type="date" name="to" defaultValue={filters.to} /></Field>
                        <Field label="Usuário (e-mail)"><Input name="performedBy" defaultValue={filters.performedBy} placeholder="admin@…" /></Field>
                        <Button type="submit" variant="primary">Aplicar</Button>
                    </form>
                    {(filters.entryId || filters.orderId) && (
                        <div className="flex flex-wrap gap-2">
                            {filters.entryId && <RefChip label={`Nota ${shortId(filters.entryId)}`} onClear={() => update({ entryId: null })} />}
                            {filters.orderId && <RefChip label={`Pedido ${shortId(filters.orderId)}`} onClear={() => update({ orderId: null })} />}
                        </div>
                    )}
                    {picked && <p className="sr-only" aria-live="polite">Filtro aplicado: {describePicker(picked)}</p>}
                </div>
            </Card>

            <Card as="section" aria-label="Movimentações" className="overflow-hidden">
                <CardHeader title="Movimentações" description="Mais recentes primeiro. “Saldo” mostra o estoque antes → depois de cada movimento." />
                {error ? (
                    <ErrorState description="Não foi possível carregar o log." onRetry={() => mutate()} />
                ) : (
                    <TableWrap>
                        <Table>
                            <Thead>
                                <tr>
                                    <Th>Quando</Th><Th>Tipo</Th><Th>Produto / variação</Th><Th className="text-right">Mov.</Th>
                                    <Th className="text-right">Saldo</Th><Th>Referência</Th><Th>Usuário</Th><Th>Motivo</Th>
                                </tr>
                            </Thead>
                            <Tbody>
                                {isLoading && !data && <TableSkeletonRows columns={8} />}
                                {data?.items.map(m => (
                                    <tr key={m.id} className={rowClass()}>
                                        <Td className="whitespace-nowrap text-adm-ink-muted">{formatDateTime(m.createdAt)}</Td>
                                        <Td><MovementBadge type={m.type} /></Td>
                                        <Td className="min-w-48">
                                            <button
                                                type="button"
                                                className="group flex items-center gap-2 text-left hover:text-adm-accent-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-adm-accent-ink"
                                                title="Ver linha do tempo desta variação"
                                                onClick={() => setTimeline({
                                                    variantId: m.variantId,
                                                    title: [m.productName, m.variantSize, m.variantColor].filter(Boolean).join(' · '),
                                                })}
                                            >
                                                <History className="h-3.5 w-3.5 shrink-0 text-adm-ink-subtle group-hover:text-adm-accent-ink" aria-hidden="true" />
                                                <span>{m.productName ?? '—'} <span className="text-adm-ink-muted">· {m.variantSize}{m.variantColor ? ` · ${m.variantColor}` : ''}</span></span>
                                            </button>
                                        </Td>
                                        <Td className="text-right"><Delta value={m.delta} /></Td>
                                        <Td className="whitespace-nowrap text-right tabular-nums">
                                            <span className="text-adm-ink-muted">{m.stockBefore ?? '—'} → </span>
                                            <span className="font-semibold">{m.stockAfter}</span>
                                        </Td>
                                        <Td className="whitespace-nowrap">
                                            {m.entryId ? (
                                                <Link href={`/admin/estoque/notas/${m.entryId}`} className="text-adm-accent-ink underline">NF {invoiceLabel('', m.invoiceNumber ?? '')}</Link>
                                            ) : m.orderId ? (
                                                <button type="button" className="text-adm-accent-ink underline" onClick={() => update({ orderId: m.orderId })}>Pedido {shortId(m.orderId)}</button>
                                            ) : <span className="text-adm-ink-subtle">—</span>}
                                        </Td>
                                        <Td className="max-w-40 truncate text-adm-ink-muted" title={m.performedBy ?? undefined}>{m.performedBy ?? '—'}</Td>
                                        <Td className="max-w-56 truncate text-adm-ink-muted" title={m.reason ?? undefined}>{m.reason ?? '—'}</Td>
                                    </tr>
                                ))}
                            </Tbody>
                        </Table>
                        {data && data.items.length === 0 && (
                            <EmptyState
                                icon={History}
                                title={hasFilters ? 'Nenhum movimento com esses filtros' : 'Ainda não há movimentações'}
                                description={hasFilters ? 'Ajuste ou limpe os filtros.' : 'Entradas, vendas e ajustes aparecem aqui assim que acontecerem.'}
                            />
                        )}
                    </TableWrap>
                )}
                {data && <Pager page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={page => update({ page: page === 0 ? null : String(page) })} />}
            </Card>

            <VariantTimelineModal target={timeline} onClose={() => setTimeline(null)} />
        </div>
    );
}

function RefChip({ label, onClear }: { label: string; onClear: () => void }) {
    return (
        <span className="inline-flex items-center gap-1.5 rounded-full border border-adm-line-strong bg-adm-raised py-1 pl-3 pr-1.5 text-xs text-adm-ink">
            {label}
            <button type="button" aria-label={`Remover filtro ${label}`} onClick={onClear} className="grid h-5 w-5 place-items-center rounded-full text-adm-ink-muted hover:bg-adm-hover hover:text-adm-ink">
                <X className="h-3 w-3" aria-hidden="true" />
            </button>
        </span>
    );
}
