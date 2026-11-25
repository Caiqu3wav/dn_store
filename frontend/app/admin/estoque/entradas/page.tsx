'use client';

import { useState } from 'react';
import Link from 'next/link';
import useSWR from 'swr';
import { FileText, Plus } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { buildQuery } from '@/services/stockService';
import type { PageResponse, StockEntrySummary, Supplier } from '@/types/stock';
import { Card, CardHeader, EmptyState, ErrorState, LinkButton, PageHeader, TableSkeletonRows, Button } from '../../_components/ui';
import { Field, Input, Select } from '../../_components/form';
import { Table, TableWrap, Tbody, Td, Th, Thead, rowClass } from '../../_components/table';
import { Pagination, formatDate, formatDateTime, useDebouncedValue } from '../_components';

const PAGE_SIZE = 20;

export default function StockEntriesPage() {
    const [supplierId, setSupplierId] = useState('');
    const [invoice, setInvoice] = useState('');
    const [from, setFrom] = useState('');
    const [to, setTo] = useState('');
    const [page, setPage] = useState(0);
    const debouncedInvoice = useDebouncedValue(invoice.trim());

    const { data, error, isLoading, mutate } = useSWR<PageResponse<StockEntrySummary>>(
        `/admin/stock/entries${buildQuery({ supplierId, invoiceNumber: debouncedInvoice, from, to, page, size: PAGE_SIZE })}`,
        fetcher,
    );
    const { data: suppliers } = useSWR<PageResponse<Supplier>>('/admin/suppliers?size=100', fetcher);
    const rows = data?.content ?? [];
    const hasFilters = !!(supplierId || invoice || from || to);
    const reset = (setter: (value: string) => void) => (value: string) => { setter(value); setPage(0); };

    return (
        <div className="space-y-6">
            <PageHeader
                eyebrow="Estoque"
                title="Notas de entrada"
                description="Recebimentos de mercadoria de fornecedores. Cada nota pode ter várias linhas e não pode ser editada depois de registrada."
                actions={<LinkButton href="/admin/estoque/entradas/nova" variant="primary"><Plus className="h-4 w-4" aria-hidden="true" /> Nova entrada de nota</LinkButton>}
            />
            <Card>
                <div className="grid gap-3 border-b border-adm-line p-4 sm:grid-cols-2 lg:grid-cols-4">
                    <Field label="Fornecedor">
                        <Select value={supplierId} onChange={event => reset(setSupplierId)(event.target.value)}>
                            <option value="">Todos</option>
                            {(suppliers?.content ?? []).map(supplier => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}
                        </Select>
                    </Field>
                    <Field label="Número da nota"><Input value={invoice} onChange={event => reset(setInvoice)(event.target.value)} placeholder="Ex.: NF-1234" /></Field>
                    <Field label="De"><Input type="date" value={from} max={to || undefined} onChange={event => reset(setFrom)(event.target.value)} /></Field>
                    <Field label="Até"><Input type="date" value={to} min={from || undefined} onChange={event => reset(setTo)(event.target.value)} /></Field>
                    {hasFilters && <div><Button variant="ghost" onClick={() => { setSupplierId(''); setInvoice(''); setFrom(''); setTo(''); setPage(0); }}>Limpar filtros</Button></div>}
                </div>
                <CardHeader title="Notas registradas" description={data ? `${data.totalElements} notas encontradas` : undefined} />
                {error ? (
                    <ErrorState description="Não foi possível carregar as notas de entrada." onRetry={() => void mutate()} />
                ) : !isLoading && rows.length === 0 ? (
                    <EmptyState
                        icon={FileText}
                        title={hasFilters ? 'Nenhuma nota com esses filtros' : 'Nenhuma nota de entrada registrada'}
                        description={hasFilters ? 'Ajuste ou limpe os filtros.' : 'Registre o recebimento de mercadoria de um fornecedor para aumentar o estoque.'}
                        action={!hasFilters && <LinkButton href="/admin/estoque/entradas/nova" variant="primary">Nova entrada de nota</LinkButton>}
                    />
                ) : (
                    <TableWrap>
                        <Table>
                            <Thead><tr><Th>Nota</Th><Th>Fornecedor</Th><Th>Emissão</Th><Th>Recebida em</Th><Th className="text-right">Linhas</Th><Th className="text-right">Unidades</Th><Th>Registrada por</Th></tr></Thead>
                            <Tbody>
                                {isLoading ? <TableSkeletonRows columns={7} /> : rows.map(entry => (
                                    <tr key={entry.id} className={rowClass()}>
                                        <Td className="font-medium"><Link className="text-adm-accent-ink underline-offset-2 hover:underline" href={`/admin/estoque/entradas/${entry.id}`}>{entry.invoiceNumber}</Link></Td>
                                        <Td>{entry.supplier.name}</Td>
                                        <Td className="text-adm-ink-muted">{formatDate(entry.issueDate)}</Td>
                                        <Td className="whitespace-nowrap text-adm-ink-muted">{formatDateTime(entry.receivedAt)}</Td>
                                        <Td className="text-right tabular-nums">{entry.itemCount}</Td>
                                        <Td className="text-right tabular-nums">{entry.totalUnits}</Td>
                                        <Td className="text-adm-ink-muted">{entry.createdBy?.name ?? entry.createdBy?.email ?? '—'}</Td>
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
