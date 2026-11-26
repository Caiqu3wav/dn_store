'use client';

import { useState } from 'react';
import Link from 'next/link';
import useSWR from 'swr';
import { ShoppingCart } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { qs } from '@/services/stockService';
import type { OrderStock, PageView } from '@/types/stock';
import { OrderStatusBadge } from '../../_components/OrderStatusBadge';
import { Table, TableWrap, Tbody, Td, Th, Thead, rowClass } from '../../_components/table';
import { Badge, Card, CardHeader, EmptyState, ErrorState, TableSkeletonRows } from '../../_components/ui';
import { Pager } from '../_components/shared';
import { formatDateTime, formatMoney, shortId } from '../_lib/format';

export default function StockOrdersPage() {
    const [page, setPage] = useState(0);
    const { data, error, isLoading, mutate } = useSWR<PageView<OrderStock>>(
        `/admin/stock/orders${qs({ page, size: 20 })}`,
        fetcher,
        { keepPreviousData: true },
    );

    return (
        <Card as="section" aria-label="Pedidos que movimentaram estoque" className="overflow-hidden">
            <CardHeader
                title="Pedidos que baixaram estoque"
                description="A venda baixa o estoque ao criar o pedido; cancelamento e expiração devolvem. Ordenado pelo movimento mais recente."
            />
            {error ? (
                <ErrorState description="Não foi possível carregar os pedidos." onRetry={() => mutate()} />
            ) : (
                <TableWrap>
                    <Table>
                        <Thead>
                            <tr><Th>Pedido</Th><Th>Status</Th><Th className="text-right">Total</Th><Th className="text-right">Baixado</Th><Th className="text-right">Devolvido</Th><Th>Estoque</Th><Th>Último movimento</Th></tr>
                        </Thead>
                        <Tbody>
                            {isLoading && !data && <TableSkeletonRows columns={7} />}
                            {data?.items.map(order => (
                                <tr key={order.orderId} className={rowClass()}>
                                    <Td className="whitespace-nowrap font-medium">
                                        <Link href={`/admin/estoque?orderId=${order.orderId}`} className="text-adm-accent-ink underline" title="Ver os movimentos deste pedido no log">#{shortId(order.orderId)}</Link>
                                    </Td>
                                    <Td><OrderStatusBadge status={order.status} /></Td>
                                    <Td className="text-right tabular-nums">{formatMoney(order.total)}</Td>
                                    <Td className="text-right tabular-nums">{order.reservedQuantity}</Td>
                                    <Td className="text-right tabular-nums">{order.releasedQuantity}</Td>
                                    <Td>{order.stockReleased ? <Badge tone="warning">Devolvido</Badge> : <Badge tone="success">Reservado</Badge>}</Td>
                                    <Td className="whitespace-nowrap text-adm-ink-muted">{formatDateTime(order.lastMovementAt)}</Td>
                                </tr>
                            ))}
                        </Tbody>
                    </Table>
                    {data && data.items.length === 0 && <EmptyState icon={ShoppingCart} title="Nenhum pedido movimentou estoque ainda" description="Quando um pedido for criado, ele aparece aqui." />}
                </TableWrap>
            )}
            {data && <Pager page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />}
        </Card>
    );
}
