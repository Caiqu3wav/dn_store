'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import useSWR from 'swr';
import { ArrowLeft, History } from 'lucide-react';
import { fetcher } from '@/services/productService';
import type { EntryDetail } from '@/types/stock';
import { Table, TableWrap, Tbody, Td, Th, Thead, rowClass } from '../../../_components/table';
import { Badge, Card, CardHeader, ErrorState, LinkButton, PageHeader, Skeleton } from '../../../_components/ui';
import { Delta, MovementBadge, Notice } from '../../_components/shared';
import { formatDate, formatDateTime, formatMoney, invoiceLabel } from '../../_lib/format';

export default function StockEntryDetailPage() {
    const { id } = useParams<{ id: string }>();
    const { data, error, isLoading, mutate } = useSWR<EntryDetail>(id ? `/admin/stock/entries/${id}` : null, fetcher);

    if (error) {
        const notFound = (error as { response?: { status?: number } })?.response?.status === 404;
        return (
            <Card>
                <ErrorState
                    title={notFound ? 'Nota não encontrada' : 'Não foi possível carregar a nota'}
                    description={<Link href="/admin/estoque/notas" className="text-adm-accent-ink underline">Voltar para as notas</Link>}
                    onRetry={notFound ? undefined : () => mutate()}
                />
            </Card>
        );
    }
    if (isLoading || !data) {
        return (
            <div className="space-y-4" aria-busy="true" aria-label="Carregando nota">
                <Skeleton className="h-10 w-1/2" /><Skeleton className="h-32 w-full" /><Skeleton className="h-48 w-full" />
            </div>
        );
    }

    const { summary, items, movements } = data;
    return (
        <div className="space-y-6">
            <PageHeader
                leading={<LinkButton href="/admin/estoque/notas" size="sm" variant="ghost" aria-label="Voltar para as notas"><ArrowLeft className="h-4 w-4" aria-hidden="true" /></LinkButton>}
                eyebrow="Nota de entrada"
                title={`NF ${invoiceLabel(summary.invoiceSeries, summary.invoiceNumber)}`}
                description={`${summary.supplierName} · nota de ${formatDate(summary.invoiceDate)}`}
                actions={<LinkButton href={`/admin/estoque?entryId=${summary.id}`}><History className="h-4 w-4" aria-hidden="true" /> Ver no log</LinkButton>}
            />
            <Notice tone="info">Esta nota é imutável. Se algo estiver errado, corrija o saldo com um <Link className="font-medium text-adm-accent-ink underline" href="/admin/estoque/ajuste">ajuste manual</Link> (categoria “Correção de lançamento”).</Notice>

            <Card as="section" aria-label="Resumo da nota">
                <dl className="grid gap-4 p-5 sm:grid-cols-2 lg:grid-cols-4">
                    <Summary label="Fornecedor" value={summary.supplierName} />
                    <Summary label="Unidades" value={`${summary.totalQuantity} em ${summary.itemCount} ${summary.itemCount === 1 ? 'linha' : 'linhas'}`} />
                    <Summary label="Custo total" value={formatMoney(summary.totalCost)} hint={summary.totalCost == null ? 'Há linhas sem custo' : undefined} />
                    <Summary label="Registrada" value={`${summary.createdBy}, ${formatDateTime(summary.createdAt)}`} />
                    {data.notes && <div className="sm:col-span-2 lg:col-span-4"><Summary label="Observações" value={data.notes} /></div>}
                </dl>
            </Card>

            <Card as="section" aria-label="Itens da nota" className="overflow-hidden">
                <CardHeader title="Itens recebidos" />
                <TableWrap>
                    <Table>
                        <Thead><tr><Th>Produto</Th><Th>Variação</Th><Th className="text-right">Quantidade</Th><Th className="text-right">Custo unitário</Th><Th className="text-right">Total da linha</Th></tr></Thead>
                        <Tbody>
                            {items.map(item => (
                                <tr key={item.variantId} className={rowClass()}>
                                    <Td>{item.productName}</Td>
                                    <Td className="text-adm-ink-muted">{[item.size, item.color].filter(Boolean).join(' · ')}</Td>
                                    <Td className="text-right tabular-nums">{item.quantity}</Td>
                                    <Td className="text-right tabular-nums">{formatMoney(item.unitCost)}</Td>
                                    <Td className="text-right tabular-nums">{formatMoney(item.lineTotal)}</Td>
                                </tr>
                            ))}
                        </Tbody>
                    </Table>
                </TableWrap>
            </Card>

            <Card as="section" aria-label="Movimentos gerados" className="overflow-hidden">
                <CardHeader title="Movimentos gerados" description="O que esta nota fez no saldo de cada variação." />
                <TableWrap>
                    <Table>
                        <Thead><tr><Th>Tipo</Th><Th>Variação</Th><Th className="text-right">Mov.</Th><Th className="text-right">Saldo</Th></tr></Thead>
                        <Tbody>
                            {movements.map(m => (
                                <tr key={m.id} className={rowClass()}>
                                    <Td><MovementBadge type={m.type} /></Td>
                                    <Td>{m.productName} <span className="text-adm-ink-muted">· {m.variantSize}{m.variantColor ? ` · ${m.variantColor}` : ''}</span></Td>
                                    <Td className="text-right"><Delta value={m.delta} /></Td>
                                    <Td className="whitespace-nowrap text-right tabular-nums"><span className="text-adm-ink-muted">{m.stockBefore ?? '—'} → </span><span className="font-semibold">{m.stockAfter}</span></Td>
                                </tr>
                            ))}
                        </Tbody>
                    </Table>
                </TableWrap>
            </Card>
            {data.replayed && <Badge tone="info">Reenvio reconhecido: nenhuma segunda entrada foi lançada.</Badge>}
        </div>
    );
}

function Summary({ label, value, hint }: { label: string; value: string; hint?: string }) {
    return (
        <div>
            <dt className="text-xs font-medium text-adm-ink-muted">{label}</dt>
            <dd className="mt-1 text-sm text-adm-ink">{value}</dd>
            {hint && <dd className="text-xs text-adm-ink-subtle">{hint}</dd>}
        </div>
    );
}
