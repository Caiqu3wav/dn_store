'use client';

import { useState } from 'react';
import Link from 'next/link';
import useSWR from 'swr';
import { ShoppingCart } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { buildQuery } from '@/services/stockService';
import type { PageResponse, SaleOutflow } from '@/types/stock';
import { Badge, Button, Card, CardHeader, EmptyState, ErrorState, PageHeader, TableSkeletonRows } from '../../_components/ui';
import { Field, Input } from '../../_components/form';
import { Table, TableWrap, Tbody, Td, Th, Thead, rowClass } from '../../_components/table';
import { OrderStatusBadge } from '../../_components/OrderStatusBadge';
import { Pagination, formatDateTime } from '../_components';

const PAGE_SIZE = 20;

export default function StockSalesOutflowPage() {
    const [from, setFrom] = useState('');
    const [to, setTo] = useState('');
    const [page, setPage] = useState(0);
    const { data, error, isLoading, mutate } = useSWR<PageResponse<SaleOutflow>>(
        `/admin/stock/sales-outflows${buildQuery({ from, to, page, size: PAGE_SIZE })}`,
        fetcher,
    );
    const rows = data?.content ?? [];
    const hasFilters = !!(from || to);

    return (
        <div className="space-y-6">
            <PageHeader
                eyebrow="Estoque"
                title="Pedidos que baixaram estoque"
                description="A baixa acontece quando o pedido é criado (reserva). Se o pedido expira ou é cancelado antes do pagamento, as unidades voltam ao estoque."
            />
            <Card>
                <div className="grid gap-3 border-b border-adm-line p-4 sm:grid-cols-3">
                    <Field label="De"><Input type="date" value={from} max={to || undefined} onChange={event => { setFrom(event.target.value); setPage(0); }} /></Field>
                    <Field label="Até"><Input type="date" value={to} min={from || undefined} onChange={event => { setTo(event.target.value); setPage(0); }} /></Field>
                    {hasFilters && <div className="flex items-end"><Button variant="ghost" onClick={() => { setFrom(''); setTo(''); setPage(0); }}>Limpar filtros</Button></div>}
                </div>
                <CardHeader title="Pedidos" description={data ? `${data.totalElements} pedidos com baixa registrada no livro` : undefined} />
                {error ? (
                    <ErrorState description="Não foi possível carregar os pedidos." onRetry={() => void mutate()} />
                ) : !isLoading && rows.length === 0 ? (
                    <EmptyState icon={ShoppingCart} title={hasFilters ? 'Nenhum pedido neste período' : 'Nenhum pedido com baixa registrada'} description="Pedidos feitos antes da ativação do livro de estoque não aparecem aqui." />
                ) : (
                    <TableWrap>
                        <Table>
                            <Thead><tr><Th>Pedido</Th><Th>Baixa em</Th><Th>Cliente</Th><Th className="text-right">Linhas</Th><Th className="text-right">Unidades</Th><Th>Status</Th><Th>Estoque</Th><Th><span className="sr-only">Movimentos</span></Th></tr></Thead>
                            <Tbody>
                                {isLoading ? <TableSkeletonRows columns={8} /> : rows.map(order => (
                                    <tr key={order.orderId} className={rowClass()}>
                                        <Td className="font-medium"><Link className="text-adm-accent-ink underline-offset-2 hover:underline" href={`/admin/pedidos?pedido=${order.orderId}`}>{order.orderLabel}</Link></Td>
                                        <Td className="whitespace-nowrap text-adm-ink-muted">{formatDateTime(order.movedAt)}</Td>
                                        <Td className="text-adm-ink-muted">{order.customer?.name ?? order.customer?.email ?? '—'}</Td>
                                        <Td className="text-right tabular-nums">{order.lines}</Td>
                                        <Td className="text-right tabular-nums">{order.units}</Td>
                                        <Td>{order.status ? <OrderStatusBadge status={order.status} /> : '—'}</Td>
                                        <Td>{order.stockReleased ? <Badge tone="violet">Devolvido</Badge> : <Badge tone="info">Baixado</Badge>}</Td>
                                        <Td><Link className="text-xs text-adm-accent-ink underline-offset-2 hover:underline" href={`/admin/estoque?referenceId=${order.orderId}`}>Ver movimentos</Link></Td>
                                    </tr>
                                ))}
                            </Tbody>
                        </Table>
                    </TableWrap>
                )}
                {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />}
            </Card>
        </div>
    );
}
