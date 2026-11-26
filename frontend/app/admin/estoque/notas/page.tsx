'use client';

import { useState } from 'react';
import Link from 'next/link';
import useSWR from 'swr';
import { FileText, Plus } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { qs } from '@/services/stockService';
import type { EntrySummary, PageView } from '@/types/stock';
import { Field, Input } from '../../_components/form';
import { Table, TableWrap, Tbody, Td, Th, Thead, rowClass } from '../../_components/table';
import { Button, Card, CardHeader, EmptyState, ErrorState, LinkButton, TableSkeletonRows } from '../../_components/ui';
import { Pager } from '../_components/shared';
import { SupplierSelect } from '../_components/SupplierSelect';
import { formatDate, formatDateTime, formatMoney, invoiceLabel } from '../_lib/format';

export default function StockEntriesPage() {
    const [supplierId, setSupplierId] = useState('');
    const [q, setQ] = useState('');
    const [from, setFrom] = useState('');
    const [to, setTo] = useState('');
    const [applied, setApplied] = useState({ q: '', from: '', to: '' });
    const [page, setPage] = useState(0);

    const { data, error, isLoading, mutate } = useSWR<PageView<EntrySummary>>(
        `/admin/stock/entries${qs({ supplierId, ...applied, page, size: 20 })}`,
        fetcher,
        { keepPreviousData: true },
    );
    const filtered = Boolean(supplierId || applied.q || applied.from || applied.to);

    const apply = (event: React.FormEvent) => {
        event.preventDefault();
        setApplied({ q: q.trim(), from, to });
        setPage(0);
    };

    return (
        <div className="space-y-6">
            <Card as="section" aria-label="Filtros das notas">
                <CardHeader
                    title="Notas de entrada"
                    description="Cada nota registra o recebimento de um fornecedor, com uma ou mais linhas."
                    actions={<LinkButton href="/admin/estoque/notas/nova" variant="primary"><Plus className="h-4 w-4" aria-hidden="true" /> Nova entrada</LinkButton>}
                />
                <form onSubmit={apply} className="grid gap-3 p-5 sm:grid-cols-2 lg:grid-cols-5 lg:items-end">
                    <div className="lg:col-span-2"><SupplierSelect allowAll value={supplierId} onChange={id => { setSupplierId(id); setPage(0); }} /></div>
                    <Field label="Número da nota"><Input value={q} onChange={event => setQ(event.target.value)} placeholder="Buscar" /></Field>
                    <Field label="Nota de"><Input type="date" value={from} onChange={event => setFrom(event.target.value)} /></Field>
                    <div className="flex items-end gap-2">
                        <Field label="até" className="flex-1"><Input type="date" value={to} onChange={event => setTo(event.target.value)} /></Field>
                        <Button type="submit" variant="primary">Filtrar</Button>
                    </div>
                </form>
            </Card>

            <Card as="section" aria-label="Notas registradas" className="overflow-hidden">
                {error ? (
                    <ErrorState description="Não foi possível carregar as notas." onRetry={() => mutate()} />
                ) : (
                    <TableWrap>
                        <Table>
                            <Thead>
                                <tr><Th>Nota</Th><Th>Data da nota</Th><Th>Fornecedor</Th><Th className="text-right">Linhas</Th><Th className="text-right">Unidades</Th><Th className="text-right">Custo total</Th><Th>Registrada por</Th></tr>
                            </Thead>
                            <Tbody>
                                {isLoading && !data && <TableSkeletonRows columns={7} />}
                                {data?.items.map(entry => (
                                    <tr key={entry.id} className={rowClass()}>
                                        <Td className="whitespace-nowrap font-medium">
                                            <Link href={`/admin/estoque/notas/${entry.id}`} className="text-adm-accent-ink underline">NF {invoiceLabel(entry.invoiceSeries, entry.invoiceNumber)}</Link>
                                        </Td>
                                        <Td className="whitespace-nowrap text-adm-ink-muted">{formatDate(entry.invoiceDate)}</Td>
                                        <Td>{entry.supplierName}</Td>
                                        <Td className="text-right tabular-nums">{entry.itemCount}</Td>
                                        <Td className="text-right tabular-nums">{entry.totalQuantity}</Td>
                                        <Td className="text-right tabular-nums">{formatMoney(entry.totalCost)}</Td>
                                        <Td className="text-adm-ink-muted"><span className="block">{entry.createdBy}</span><span className="text-xs text-adm-ink-subtle">{formatDateTime(entry.createdAt)}</span></Td>
                                    </tr>
                                ))}
                            </Tbody>
                        </Table>
                        {data && data.items.length === 0 && (
                            <EmptyState
                                icon={FileText}
                                title={filtered ? 'Nenhuma nota com esses filtros' : 'Nenhuma nota registrada'}
                                description={filtered ? 'Ajuste os filtros.' : 'Registre o recebimento de mercadoria para ver as notas aqui.'}
                                action={!filtered ? <LinkButton href="/admin/estoque/notas/nova" variant="primary">Nova entrada</LinkButton> : undefined}
                            />
                        )}
                    </TableWrap>
                )}
                {data && <Pager page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />}
            </Card>
        </div>
    );
}
