'use client';

import { memo, useCallback, useState } from 'react';
import useSWR from 'swr';
import { BadgeCheck, Calendar, Eye, History, MapPin, Package, ShoppingBag, User } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { fetcher } from '@/services/productService';
import { adminService, AdminOrder, OrderStatusAuditEntry } from '@/services/adminService';
import { isUnpaidOrder, orderStatusLabel as statusLabel } from '@/utils/orderStatus';
import { Badge, Button, Card, CardHeader, EmptyState, ErrorState, IconButton, PageHeader, TableSkeletonRows } from '../_components/ui';
import { OrderStatusBadge } from '../_components/OrderStatusBadge';
import { Select, Textarea } from '../_components/form';
import { Table, TableWrap, Tbody, Td, Th, Thead, rowClass } from '../_components/table';
import { cn } from '../../components/ui/Button';

const ADMIN_SETTABLE_STATUSES = ['PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
const MANUAL_CONFIRMABLE_STATUSES = ['PENDING_PAYMENT', 'PAYMENT_FAILED'];
const MIN_REASON_LENGTH = 10;
const ORDERS_SWR_OPTIONS = { revalidateOnFocus: true, focusThrottleInterval: 30_000 };

const fmt = (value?: number) =>
    new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value ?? 0);

type OrderRowProps = {
    order: AdminOrder;
    selected: boolean;
    onSelect: (order: AdminOrder) => void;
    onStatusChange: (orderId: string, status: string) => void;
};

const OrderRow = memo(function OrderRow({ order, selected, onSelect, onStatusChange }: OrderRowProps) {
    return (
        <tr className={cn(rowClass(selected), 'cursor-pointer')} onClick={() => onSelect(order)}>
            <Td className="whitespace-nowrap px-3">
                <div className="font-mono text-xs text-adm-ink-muted">{order.id.slice(0, 8)}...</div>
                <div className="text-xs text-adm-ink-subtle">
                    {order.createdAt ? new Date(order.createdAt).toLocaleDateString('pt-BR') : '-'}
                </div>
            </Td>
            <Td className="max-w-36 px-3">
                <div className="truncate font-medium">{order.user?.name || 'Cliente'}</div>
                <div className="truncate text-xs text-adm-ink-subtle">{order.user?.email}</div>
            </Td>
            <Td className="whitespace-nowrap px-3 font-medium tabular-nums">{fmt(order.total)}</Td>
            <Td className="px-3" onClick={(event) => event.stopPropagation()}>
                <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                    <OrderStatusBadge status={order.status} />
                    {order.manualPaymentConfirmed && (
                        <Badge tone="warning" className="px-2 py-0 text-[10px]">
                            <span title="Pagamento confirmado manualmente por um administrador">manual</span>
                        </Badge>
                    )}
                </div>
                <Select
                    value={order.status}
                    aria-label={`Alterar status do pedido ${order.id.slice(0, 8)}`}
                    onChange={(event) => onStatusChange(order.id, event.target.value)}
                    className="mt-1.5 min-h-9 w-36 truncate py-1 text-xs font-medium"
                >
                    {!ADMIN_SETTABLE_STATUSES.includes(order.status) && (
                        <option value={order.status} disabled>{statusLabel(order.status)}</option>
                    )}
                    {ADMIN_SETTABLE_STATUSES.map((status) => (
                        <option key={status} value={status}>{statusLabel(status)}</option>
                    ))}
                </Select>
            </Td>
            <Td className="px-3 text-right" onClick={(event) => event.stopPropagation()}>
                <IconButton label="Visualizar detalhes" onClick={() => onSelect(order)}>
                    <Eye className="h-4 w-4" aria-hidden="true" />
                </IconButton>
            </Td>
        </tr>
    );
});

export default function AdminOrdersPage() {
    const {
        data: allOrders,
        error: ordersError,
        mutate,
    } = useSWR<AdminOrder[]>('/orders/all', fetcher, ORDERS_SWR_OPTIONS);
    const [showUnpaid, setShowUnpaid] = useState(false);
    const [selectedOrder, setSelectedOrder] = useState<AdminOrder | null>(null);
    const [confirmingPayment, setConfirmingPayment] = useState(false);
    const [paymentReason, setPaymentReason] = useState('');
    const [submittingPayment, setSubmittingPayment] = useState(false);
    const {
        data: auditEntries,
        error: auditError,
        mutate: reloadAudit,
    } = useSWR<OrderStatusAuditEntry[]>(
        selectedOrder ? `/orders/${selectedOrder.id}/status-audit` : null,
        fetcher
    );

    const unpaidCount = allOrders?.filter((order) => isUnpaidOrder(order.status)).length ?? 0;
    const orders = showUnpaid ? allOrders : allOrders?.filter((order) => !isUnpaidOrder(order.status));

    const selectOrder = useCallback((order: AdminOrder) => {
        setSelectedOrder(order);
        setConfirmingPayment(false);
        setPaymentReason('');
    }, []);

    const handleUpdateStatus = useCallback(async (orderId: string, status: string) => {
        try {
            await adminService.updateOrderStatus(orderId, status);
            toast.success('Status atualizado!');
            await mutate();
            setSelectedOrder((previous) =>
                previous?.id === orderId ? { ...previous, status } : previous
            );
        } catch {
            toast.error('Erro ao atualizar status do pedido.');
        }
    }, [mutate]);

    const handleConfirmPayment = async () => {
        if (!selectedOrder || paymentReason.trim().length < MIN_REASON_LENGTH) return;

        setSubmittingPayment(true);
        try {
            const updatedOrder = await adminService.confirmPaymentManually(
                selectedOrder.id,
                paymentReason.trim()
            );
            setSelectedOrder(updatedOrder);
            setConfirmingPayment(false);
            setPaymentReason('');
            await Promise.all([mutate(), reloadAudit()]);
            toast.success('Pagamento confirmado e registrado na auditoria.');
        } catch {
            toast.error('Não foi possível confirmar o pagamento manualmente.');
        } finally {
            setSubmittingPayment(false);
        }
    };

    return (
        <div className="mx-auto max-w-7xl space-y-6 pb-12">
            <PageHeader
                title="Pedidos"
                description="Gerencie os pedidos dos clientes e acompanhe as entregas."
            />

            <div className="grid grid-cols-1 items-start gap-6 xl:grid-cols-3">
                <Card as="section" aria-label="Histórico de pedidos" className="overflow-hidden xl:col-span-2">
                    <CardHeader
                        title="Histórico de pedidos"
                        actions={
                            <>
                                <Button
                                    size="sm"
                                    aria-pressed={showUnpaid}
                                    onClick={() => {
                                        setShowUnpaid((previous) => !previous);
                                        setSelectedOrder(null);
                                    }}
                                    className={showUnpaid
                                        ? 'border-adm-accent-ink/60 bg-adm-accent/10 text-adm-accent-ink hover:bg-adm-accent/20'
                                        : undefined}
                                >
                                    {showUnpaid ? 'Ocultar não pagos' : `Mostrar não pagos (${unpaidCount})`}
                                </Button>
                                <span className="text-sm text-adm-ink-muted">Total: {orders?.length ?? 0}</span>
                            </>
                        }
                    />

                    <TableWrap>
                        <Table>
                            <Thead>
                                <tr>
                                    <Th className="px-3">Pedido / Data</Th>
                                    <Th className="px-3">Cliente</Th>
                                    <Th className="px-3">Total</Th>
                                    <Th className="px-3">Status</Th>
                                    <Th className="px-3 text-right">Ações</Th>
                                </tr>
                            </Thead>
                            <Tbody>
                                {ordersError && !allOrders ? (
                                    <tr>
                                        <Td colSpan={5} className="p-0">
                                            <ErrorState
                                                title="Não foi possível carregar os pedidos."
                                                onRetry={() => { void mutate(); }}
                                            />
                                        </Td>
                                    </tr>
                                ) : !orders ? (
                                    <TableSkeletonRows rows={8} columns={5} />
                                ) : orders.length === 0 ? (
                                    <tr>
                                        <Td colSpan={5} className="p-0">
                                            <EmptyState
                                                icon={ShoppingBag}
                                                title={showUnpaid || unpaidCount === 0
                                                    ? 'Nenhum pedido realizado ainda.'
                                                    : 'Nenhum pedido pago ainda.'}
                                                description={showUnpaid || unpaidCount === 0
                                                    ? undefined
                                                    : 'Use "Mostrar não pagos" para ver pedidos aguardando pagamento ou expirados.'}
                                            />
                                        </Td>
                                    </tr>
                                ) : orders.map((order) => (
                                    <OrderRow
                                        key={order.id}
                                        order={order}
                                        selected={selectedOrder?.id === order.id}
                                        onSelect={selectOrder}
                                        onStatusChange={handleUpdateStatus}
                                    />
                                ))}
                            </Tbody>
                        </Table>
                    </TableWrap>
                </Card>

                <Card as="aside" aria-label="Detalhes do pedido" className="space-y-6 p-5 xl:sticky xl:top-6">
                    <div className="border-b border-adm-line pb-4">
                        <h2 className="flex items-center gap-2 text-base font-semibold">
                            <ShoppingBag className="h-5 w-5 text-adm-accent-ink" aria-hidden="true" />
                            Detalhes do pedido
                        </h2>
                    </div>

                    {!selectedOrder ? (
                        <EmptyState
                            className="py-10"
                            title="Nenhum pedido selecionado"
                            description="Selecione um pedido na lista para visualizar os detalhes completos."
                        />
                    ) : (
                        <div className="space-y-6">
                            <div className="grid grid-cols-2 gap-4 rounded-adm-control bg-adm-sunken p-4 text-sm">
                                <div className="space-y-1">
                                    <div className="text-xs font-semibold text-adm-ink-subtle">CÓDIGO</div>
                                    <div className="break-all font-mono text-xs">{selectedOrder.id}</div>
                                </div>
                                <div className="space-y-1">
                                    <div className="text-xs font-semibold text-adm-ink-subtle">DATA</div>
                                    <div className="flex items-center gap-1.5 text-xs text-adm-ink-muted">
                                        <Calendar className="h-3.5 w-3.5" aria-hidden="true" />
                                        {selectedOrder.createdAt
                                            ? new Date(selectedOrder.createdAt).toLocaleString('pt-BR')
                                            : '-'}
                                    </div>
                                </div>
                            </div>

                            <section className="space-y-3">
                                <h3 className="flex items-center gap-2 text-sm font-semibold text-adm-ink-muted">
                                    <User className="h-4 w-4" aria-hidden="true" />
                                    Informações do cliente
                                </h3>
                                <div className="space-y-1 rounded-adm-control bg-adm-sunken p-4 text-sm">
                                    <div className="font-medium">{selectedOrder.user?.name || 'Cliente'}</div>
                                    <div className="text-adm-ink-muted">{selectedOrder.user?.email}</div>
                                    <div className="text-adm-ink-muted">Telefone: {selectedOrder.user?.phone || '-'}</div>
                                </div>
                            </section>

                            {selectedOrder.address && (
                                <section className="space-y-3">
                                    <h3 className="flex items-center gap-2 text-sm font-semibold text-adm-ink-muted">
                                        <MapPin className="h-4 w-4" aria-hidden="true" />
                                        Endereço de entrega
                                    </h3>
                                    <div className="space-y-1 rounded-adm-control bg-adm-sunken p-4 text-sm text-adm-ink-muted">
                                        <div>{selectedOrder.address.street}, {selectedOrder.address.number}</div>
                                        <div>
                                            {selectedOrder.address.neighborhood} - {selectedOrder.address.city}/{selectedOrder.address.state}
                                        </div>
                                        <div className="text-xs text-adm-ink-subtle">
                                            CEP: {selectedOrder.address.zipCode}
                                        </div>
                                    </div>
                                </section>
                            )}

                            {selectedOrder.items && selectedOrder.items.length > 0 && (
                                <section className="space-y-3">
                                    <h3 className="flex items-center gap-2 text-sm font-semibold text-adm-ink-muted">
                                        <Package className="h-4 w-4" aria-hidden="true" />
                                        Itens do pedido
                                    </h3>
                                    <div className="max-h-60 space-y-2 overflow-y-auto pr-1">
                                        {selectedOrder.items.map((item) => (
                                            <div
                                                key={item.id}
                                                className="flex items-center justify-between gap-3 rounded-adm-control bg-adm-sunken p-3 text-sm"
                                            >
                                                <div className="min-w-0 space-y-0.5">
                                                    <div className="font-medium">{item.productName || 'Produto'}</div>
                                                    <div className="text-xs text-adm-ink-muted">
                                                        Qtd: {item.quantity} × {fmt(item.price)}
                                                    </div>
                                                </div>
                                                <div className="shrink-0 font-semibold tabular-nums">
                                                    {fmt(item.totalPrice)}
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </section>
                            )}

                            <section className="space-y-2 border-t border-adm-line pt-4 text-sm">
                                <div className="flex justify-between text-adm-ink-muted">
                                    <span>Desconto de cupom</span>
                                    <span className="tabular-nums">- {fmt(selectedOrder.discountAmount)}</span>
                                </div>
                                <div className="flex justify-between border-t border-adm-line pt-2 text-lg font-semibold">
                                    <span>Valor total</span>
                                    <span className="tabular-nums text-adm-success">{fmt(selectedOrder.total)}</span>
                                </div>
                            </section>

                            <section className="space-y-3 border-t border-adm-line pt-4">
                                <div className="flex items-center justify-between gap-2 text-sm">
                                    <span className="text-xs font-semibold text-adm-ink-subtle">STATUS ATUAL</span>
                                    <OrderStatusBadge status={selectedOrder.status} />
                                </div>

                                {selectedOrder.manualPaymentConfirmed && (
                                    <div className="flex items-start gap-2 rounded-adm-control border border-adm-warning/25 bg-adm-warning/10 p-3 text-xs text-adm-warning">
                                        <BadgeCheck className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                                        <span>
                                            Pagamento confirmado manualmente por um administrador (não pelo Asaas).
                                        </span>
                                    </div>
                                )}

                                {MANUAL_CONFIRMABLE_STATUSES.includes(selectedOrder.status) && !confirmingPayment && (
                                    <Button
                                        variant="warning"
                                        size="sm"
                                        className="w-full"
                                        onClick={() => setConfirmingPayment(true)}
                                    >
                                        Confirmar pagamento manualmente
                                    </Button>
                                )}

                                {confirmingPayment && (
                                    <div className="space-y-2 rounded-adm-control border border-adm-warning/30 bg-adm-sunken p-3">
                                        <p className="text-xs text-adm-ink-muted">
                                            Use somente quando o pagamento foi recebido fora do Asaas. A confirmação
                                            ficará registrada com seu usuário e esta justificativa.
                                        </p>
                                        <label
                                            htmlFor="manual-payment-reason"
                                            className="block text-xs font-semibold text-adm-ink-muted"
                                        >
                                            JUSTIFICATIVA (mín. {MIN_REASON_LENGTH} caracteres)
                                        </label>
                                        <Textarea
                                            id="manual-payment-reason"
                                            value={paymentReason}
                                            onChange={(event) => setPaymentReason(event.target.value)}
                                            maxLength={500}
                                            rows={3}
                                            placeholder="Ex.: Transferência bancária conferida no extrato."
                                            disabled={submittingPayment}
                                        />
                                        <div className="flex gap-2">
                                            <Button
                                                size="sm"
                                                className="flex-1"
                                                onClick={() => {
                                                    setConfirmingPayment(false);
                                                    setPaymentReason('');
                                                }}
                                                disabled={submittingPayment}
                                            >
                                                Cancelar
                                            </Button>
                                            <Button
                                                variant="caution"
                                                size="sm"
                                                className="flex-1"
                                                onClick={() => { void handleConfirmPayment(); }}
                                                disabled={submittingPayment || paymentReason.trim().length < MIN_REASON_LENGTH}
                                            >
                                                {submittingPayment ? 'Confirmando...' : 'Confirmar pagamento'}
                                            </Button>
                                        </div>
                                    </div>
                                )}

                                <div className="flex gap-2">
                                    <Button
                                        size="sm"
                                        className="flex-1 border-adm-violet/40 bg-adm-violet/15 text-adm-violet hover:bg-adm-violet/25"
                                        onClick={() => { void handleUpdateStatus(selectedOrder.id, 'SHIPPED'); }}
                                    >
                                        Marcar como enviado
                                    </Button>
                                    <Button
                                        size="sm"
                                        className="flex-1 border-adm-success/40 bg-adm-success/15 text-adm-success hover:bg-adm-success/25"
                                        onClick={() => { void handleUpdateStatus(selectedOrder.id, 'DELIVERED'); }}
                                    >
                                        Marcar como entregue
                                    </Button>
                                </div>
                            </section>

                            <section className="space-y-2 border-t border-adm-line pt-4">
                                <h3 className="flex items-center gap-2 text-sm font-semibold text-adm-ink-muted">
                                    <History className="h-4 w-4" aria-hidden="true" />
                                    Histórico administrativo
                                </h3>
                                {auditError ? (
                                    <p role="alert" className="text-xs text-adm-danger">
                                        Não foi possível carregar a auditoria do pedido.
                                    </p>
                                ) : !auditEntries ? (
                                    <p className="text-xs text-adm-ink-subtle">Carregando auditoria...</p>
                                ) : auditEntries.length === 0 ? (
                                    <p className="text-xs text-adm-ink-subtle">Nenhuma alteração administrativa registrada.</p>
                                ) : (
                                    <ul className="space-y-2">
                                        {auditEntries.map((entry: OrderStatusAuditEntry) => (
                                            <li
                                                key={entry.id}
                                                className="space-y-1 rounded-adm-control bg-adm-sunken p-3 text-xs text-adm-ink-muted"
                                            >
                                                <div className="font-semibold text-adm-ink">
                                                    {statusLabel(entry.previousStatus)} → {statusLabel(entry.newStatus)}
                                                    <span className="ml-2 font-normal text-adm-warning">
                                                        (confirmação manual)
                                                    </span>
                                                </div>
                                                <div className="text-adm-ink-subtle">
                                                    {entry.actorName} · {new Date(entry.createdAt).toLocaleString('pt-BR')}
                                                </div>
                                                <div>{entry.reason}</div>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </section>
                        </div>
                    )}
                </Card>
            </div>
        </div>
    );
}
