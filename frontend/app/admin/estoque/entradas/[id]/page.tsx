'use client';

import { use } from 'react';
import Link from 'next/link';
import useSWR from 'swr';
import { ArrowLeft } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { getApiErrorStatus } from '@/utils/apiError';
import type { StockEntry } from '@/types/stock';
import { Card, CardHeader, ErrorState, LinkButton, PageHeader, Skeleton } from '../../../_components/ui';
import { Table, TableWrap, Tbody, Td, Th, Thead, rowClass } from '../../../_components/table';
import { formatDate, formatDateTime, variantLabel } from '../../_components';

const money = (value: number | null) =>
    value === null ? '—' : value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

export default function StockEntryDetailPage({ params }: { params: Promise<{ id: string }> }) {
    const { id } = use(params);
    const { data: entry, error, mutate } = useSWR<StockEntry>(`/admin/stock/entries/${id}`, fetcher);

    const back = (
        <LinkButton href="/admin/estoque/entradas" variant="ghost" size="sm"><ArrowLeft className="h-4 w-4" aria-hidden="true" /> Notas de entrada</LinkButton>
    );

    if (error) {
        const notFound = getApiErrorStatus(error) === 404;
        return (
            <div className="space-y-4">
                {back}
                <Card>
                    <ErrorState
                        title={notFound ? 'Nota não encontrada' : 'Não foi possível carregar a nota'}
                        description={notFound ? 'Ela pode ter sido digitada errado no endereço.' : undefined}
                        onRetry={notFound ? undefined : () => void mutate()}
                    />
                </Card>
            </div>
        );
    }

    if (!entry) {
        return (
            <div className="space-y-4" aria-busy="true">
                {back}
                <Skeleton className="h-8 w-64" />
                <Skeleton className="h-40 w-full" />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {back}
            <PageHeader
                eyebrow="Nota de entrada"
                title={`Nota ${entry.invoiceNumber}`}
                description={`${entry.supplier.name} · recebida em ${formatDateTime(entry.receivedAt)}`}
                actions={<LinkButton href={`/admin/estoque?referenceId=${entry.id}`}>Ver no log de movimentações</LinkButton>}
            />
            <Card>
                <dl className="grid gap-4 p-5 text-sm sm:grid-cols-2 lg:grid-cols-4">
                    <div><dt className="text-xs text-adm-ink-subtle">Fornecedor</dt><dd className="mt-0.5 font-medium text-adm-ink">{entry.supplier.name}</dd></div>
                    <div><dt className="text-xs text-adm-ink-subtle">Emissão</dt><dd className="mt-0.5 text-adm-ink">{formatDate(entry.issueDate)}</dd></div>
                    <div><dt className="text-xs text-adm-ink-subtle">Registrada por</dt><dd className="mt-0.5 text-adm-ink">{entry.createdBy?.name ?? entry.createdBy?.email ?? '—'}</dd></div>
                    <div><dt className="text-xs text-adm-ink-subtle">Total</dt><dd className="mt-0.5 text-adm-ink">{entry.totalUnits} unidades{entry.totalCost !== null && <> · {money(entry.totalCost)}</>}</dd></div>
                    {entry.notes && <div className="sm:col-span-2 lg:col-span-4"><dt className="text-xs text-adm-ink-subtle">Observação</dt><dd className="mt-0.5 whitespace-pre-line text-adm-ink">{entry.notes}</dd></div>}
                </dl>
            </Card>
            <Card>
                <CardHeader title="Itens recebidos" description="O saldo antes e depois é o estoque da variação no momento do recebimento." />
                <TableWrap>
                    <Table>
                        <Thead><tr><Th>Produto</Th><Th className="text-right">Quantidade</Th><Th className="text-right">Custo unitário</Th><Th className="text-right">Saldo</Th></tr></Thead>
                        <Tbody>
                            {entry.items.map(item => (
                                <tr key={item.variant.id} className={rowClass()}>
                                    <Td><span className="font-medium">{variantLabel(item.variant)}</span>{item.variant.sku && <span className="block text-xs text-adm-ink-subtle">{item.variant.sku}</span>}</Td>
                                    <Td className="text-right font-semibold tabular-nums text-adm-success">+{item.quantity}</Td>
                                    <Td className="text-right tabular-nums text-adm-ink-muted">{money(item.unitCost)}</Td>
                                    <Td className="whitespace-nowrap text-right tabular-nums text-adm-ink-muted">{item.balanceBefore ?? '—'} → <span className="font-medium text-adm-ink">{item.balanceAfter ?? '—'}</span></Td>
                                </tr>
                            ))}
                        </Tbody>
                    </Table>
                </TableWrap>
            </Card>
            <p className="text-xs text-adm-ink-subtle">
                Notas registradas não podem ser editadas nem apagadas. Se algo foi lançado errado, faça um{' '}
                <Link href="/admin/estoque" className="text-adm-accent-ink underline-offset-2 hover:underline">ajuste de estoque</Link> com o motivo.
            </p>
        </div>
    );
}
