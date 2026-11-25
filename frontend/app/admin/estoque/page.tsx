'use client';

import { Suspense, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import useSWR from 'swr';
import { AlertTriangle, History, SlidersHorizontal } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { buildQuery } from '@/services/stockService';
import type { PageResponse, Reconciliation, StockMovement, StockMovementType } from '@/types/stock';
import type { User } from '@/types';
import { Badge, Button, Card, CardHeader, EmptyState, ErrorState, IconButton, PageHeader, TableSkeletonRows } from '../_components/ui';
import { Field, Input, Select } from '../_components/form';
import { Table, TableWrap, Tbody, Td, Th, Thead, rowClass } from '../_components/table';
import {
    AdjustStockModal,
    MOVEMENT_TYPES,
    MovementTypeBadge,
    Pagination,
    VariantPicker,
    formatDateTime,
    formatDelta,
    variantLabel,
} from './_components';
import type { StockVariant } from '@/types/stock';

const PAGE_SIZE = 25;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type Filters = { type: string; from: string; to: string; actorId: string; reference: string };
const EMPTY_FILTERS: Filters = { type: '', from: '', to: '', actorId: '', reference: '' };

export default function StockMovementsPage() {
    // useSearchParams exige Suspense no App Router (os filtros iniciais vêm de links de nota/pedido).
    return (
        <Suspense fallback={null}>
            <StockMovementsContent />
        </Suspense>
    );
}

function StockMovementsContent() {
    const params = useSearchParams();
    const [filters, setFilters] = useState<Filters>(() => ({
        ...EMPTY_FILTERS,
        reference: params.get('referenceId') ?? params.get('invoiceNumber') ?? '',
    }));
    const [variant, setVariant] = useState<StockVariant | null>(null);
    const [page, setPage] = useState(0);
    const [adjusting, setAdjusting] = useState<{ open: boolean; variantId: string | null }>({ open: false, variantId: null });

    const reference = filters.reference.trim();
    const query = buildQuery({
        variantId: variant?.id,
        type: filters.type,
        from: filters.from,
        to: filters.to,
        actorId: filters.actorId,
        referenceId: UUID_PATTERN.test(reference) ? reference : undefined,
        invoiceNumber: reference && !UUID_PATTERN.test(reference) ? reference : undefined,
        page,
        size: PAGE_SIZE,
    });
    const { data, error, isLoading, mutate } = useSWR<PageResponse<StockMovement>>(`/admin/stock/movements${query}`, fetcher);
    const { data: reconciliation } = useSWR<Reconciliation>('/admin/stock/reconciliation', fetcher);
    const { data: users } = useSWR<User[]>('/users', fetcher);
    const admins = (users ?? []).filter(user => user.role === 'ADMIN');

    const update = (patch: Partial<Filters>) => { setFilters(current => ({ ...current, ...patch })); setPage(0); };
    const hasFilters = !!variant || Object.values(filters).some(Boolean);
    const rows = data?.content ?? [];

    return (
        <div className="space-y-6">
            <PageHeader
                eyebrow="Estoque"
                title="Movimentações"
                description="Histórico de todas as entradas, vendas, devoluções e ajustes, com o saldo antes e depois de cada movimento."
                actions={
                    <>
                        <Button onClick={() => setAdjusting({ open: true, variantId: null })}>
                            <SlidersHorizontal className="h-4 w-4" aria-hidden="true" /> Ajustar estoque
                        </Button>
                        <Link href="/admin/estoque/entradas/nova" className="inline-flex min-h-10 items-center justify-center gap-2 rounded-adm-control bg-adm-accent px-4 text-sm font-medium text-white transition-colors hover:bg-adm-accent-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-adm-accent-ink">
                            Nova entrada de nota
                        </Link>
                    </>
                }
            />

            {reconciliation && reconciliation.divergences.length > 0 && <ReconciliationAlert report={reconciliation} />}

            <Card>
                <div className="grid gap-3 border-b border-adm-line p-4 sm:grid-cols-2 lg:grid-cols-6">
                    <div className="sm:col-span-2">
                        <VariantPicker value={variant} onChange={next => { setVariant(next); setPage(0); }} label="Produto / variação" />
                    </div>
                    <Field label="Tipo">
                        <Select value={filters.type} onChange={event => update({ type: event.target.value })}>
                            <option value="">Todos</option>
                            {(Object.keys(MOVEMENT_TYPES) as StockMovementType[]).map(type => <option key={type} value={type}>{MOVEMENT_TYPES[type].label}</option>)}
                        </Select>
                    </Field>
                    <Field label="De"><Input type="date" value={filters.from} max={filters.to || undefined} onChange={event => update({ from: event.target.value })} /></Field>
                    <Field label="Até"><Input type="date" value={filters.to} min={filters.from || undefined} onChange={event => update({ to: event.target.value })} /></Field>
                    <Field label="Responsável (admin)">
                        <Select value={filters.actorId} onChange={event => update({ actorId: event.target.value })}>
                            <option value="">Todos</option>
                            {admins.map(admin => <option key={admin.id} value={admin.id}>{admin.name}</option>)}
                        </Select>
                    </Field>
                    <Field label="Referência" className="sm:col-span-2" hint="Número da nota ou id do pedido">
                        <Input value={filters.reference} onChange={event => update({ reference: event.target.value })} placeholder="Ex.: NF-1234" />
                    </Field>
                    {hasFilters && (
                        <div className="flex items-end">
                            <Button variant="ghost" onClick={() => { setFilters(EMPTY_FILTERS); setVariant(null); setPage(0); }}>Limpar filtros</Button>
                        </div>
                    )}
                </div>

                <CardHeader title="Registros" description={data ? `${data.totalElements} movimentações encontradas` : undefined} />
                {error ? (
                    <ErrorState description="Não foi possível carregar as movimentações." onRetry={() => void mutate()} />
                ) : !isLoading && rows.length === 0 ? (
                    <EmptyState icon={History} title={hasFilters ? 'Nenhuma movimentação com esses filtros' : 'Ainda não há movimentações'} description={hasFilters ? 'Ajuste ou limpe os filtros para ver mais registros.' : 'As próximas vendas, entradas e ajustes aparecerão aqui.'} />
                ) : (
                    <TableWrap>
                        <Table>
                            <Thead>
                                <tr>
                                    <Th>Data</Th><Th>Tipo</Th><Th>Produto</Th><Th className="text-right">Movimento</Th>
                                    <Th className="text-right">Saldo</Th><Th>Referência</Th><Th>Responsável</Th><Th>Motivo</Th><Th><span className="sr-only">Ações</span></Th>
                                </tr>
                            </Thead>
                            <Tbody>
                                {isLoading ? <TableSkeletonRows columns={9} /> : rows.map(movement => (
                                    <tr key={movement.id} className={rowClass()}>
                                        <Td className="whitespace-nowrap text-adm-ink-muted">{formatDateTime(movement.createdAt)}</Td>
                                        <Td><MovementTypeBadge type={movement.type} /></Td>
                                        <Td className="min-w-48">
                                            <span className="font-medium">{variantLabel(movement.variant)}</span>
                                            {movement.variant.sku && <span className="block text-xs text-adm-ink-subtle">{movement.variant.sku}</span>}
                                        </Td>
                                        <Td className={`text-right font-semibold tabular-nums ${movement.quantityDelta > 0 ? 'text-adm-success' : 'text-adm-danger'}`}>{formatDelta(movement.quantityDelta)}</Td>
                                        <Td className="whitespace-nowrap text-right tabular-nums text-adm-ink-muted">{movement.balanceBefore} → <span className="font-medium text-adm-ink">{movement.balanceAfter}</span></Td>
                                        <Td className="min-w-40">
                                            {movement.reference ? (
                                                <Link className="text-adm-accent-ink underline-offset-2 hover:underline" href={movement.reference.type === 'ORDER' ? `/admin/pedidos?pedido=${movement.reference.id}` : `/admin/estoque/entradas/${movement.reference.id}`}>
                                                    {movement.reference.label}
                                                </Link>
                                            ) : <span className="text-adm-ink-subtle">—</span>}
                                        </Td>
                                        <Td className="text-adm-ink-muted">{movement.actor ? (movement.actor.name ?? movement.actor.email) : <Badge>Sistema</Badge>}</Td>
                                        <Td className="max-w-64 text-adm-ink-muted">{movement.reasonText ?? '—'}</Td>
                                        <Td>
                                            <IconButton label={`Ajustar estoque de ${variantLabel(movement.variant)}`} onClick={() => setAdjusting({ open: true, variantId: movement.variant.id })}>
                                                <SlidersHorizontal className="h-4 w-4" aria-hidden="true" />
                                            </IconButton>
                                        </Td>
                                    </tr>
                                ))}
                            </Tbody>
                        </Table>
                    </TableWrap>
                )}
                {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />}
            </Card>

            <AdjustStockModal
                open={adjusting.open}
                variantId={adjusting.variantId}
                onClose={() => setAdjusting({ open: false, variantId: null })}
                onDone={() => void mutate()}
            />
        </div>
    );
}

function ReconciliationAlert({ report }: { report: Reconciliation }) {
    return (
        <div role="alert" className="rounded-adm-card border border-adm-warning/40 bg-adm-warning/10 p-4 text-sm">
            <div className="flex items-start gap-3">
                <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-adm-warning" aria-hidden="true" />
                <div className="min-w-0 space-y-2">
                    <p className="font-medium text-adm-ink">
                        {report.divergences.length} {report.divergences.length === 1 ? 'variação com saldo diferente do livro' : 'variações com saldo diferente do livro'}
                    </p>
                    <p className="text-adm-ink-muted">
                        Pequenas diferenças logo após uma publicação são esperadas: durante a troca de instância do servidor, vendas
                        feitas pela versão antiga não entram no livro. Confira o estoque físico e, se necessário, corrija com um ajuste.
                        Diferenças que continuam crescendo merecem investigação.
                    </p>
                    <ul className="space-y-1 text-adm-ink">
                        {report.divergences.slice(0, 5).map(item => (
                            <li key={item.variant.id}>
                                {variantLabel(item.variant)}: estoque <strong>{item.currentStock}</strong>, livro <strong>{item.ledgerBalance}</strong> ({formatDelta(item.difference)})
                            </li>
                        ))}
                        {report.divergences.length > 5 && <li className="text-adm-ink-muted">e mais {report.divergences.length - 5}…</li>}
                    </ul>
                </div>
            </div>
        </div>
    );
}
