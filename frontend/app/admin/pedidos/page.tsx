'use client';

import { memo, useCallback, useEffect, useRef, useState } from 'react';
import useSWR from 'swr';
import { ShoppingBag, Eye, Calendar, User, MapPin, BadgeCheck, History } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { adminService, AdminOrder, OrderStatusAuditEntry } from '@/services/adminService';
import { toast } from 'react-hot-toast';
import { isUnpaidOrder, orderStatusLabel as statusLabel } from '@/utils/orderStatus';
import { Badge, Button, Card, CardHeader, EmptyState, ErrorState, IconButton, PageHeader, TableSkeletonRows } from '../_components/ui';
import { OrderStatusBadge } from '../_components/OrderStatusBadge';
import { Select, Textarea } from '../_components/form';
import { Table, TableWrap, Tbody, Td, Th, Thead, rowClass } from '../_components/table';
import { cn } from '../../components/ui/Button';

// Status que o administrador pode definir diretamente. "Pago" só pela confirmação manual (com justificativa);
// aguardando/falha/expirado/revisão são controlados pelo sistema.
const ADMIN_SETTABLE_STATUSES = ['PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
const MANUAL_CONFIRMABLE_STATUSES = ['PENDING_PAYMENT', 'PAYMENT_FAILED'];
const MIN_REASON_LENGTH = 10;
// O status do pedido muda por webhook de pagamento; mantém a revalidação ao focar a janela, mas com
// intervalo maior que o padrão do SWR para não rebaixar a lista inteira a cada troca de aba.
const ORDERS_SWR_OPTIONS = { revalidateOnFocus: true, focusThrottleInterval: 30_000 };

const errorMessage = (error: unknown, fallback: string) => {
    const message = (error as { response?: { data?: { message?: unknown } } })?.response?.data?.message;
    return typeof message === 'string' && message.trim() ? message : fallback;
};

type OrderRowProps = {
    order: AdminOrder;
    selected: boolean;
    onSelect: (order: AdminOrder) => void;
    onStatusChange: (orderId: string, status: string) => void;
};

// Memoizada: clicar numa linha muda `selectedOrder` e antes re-renderizava todas as linhas (cada uma com um
// <select>). Com callbacks estáveis, só a linha que muda de seleção ou de dados é redesenhada.
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
            <Td className="whitespace-nowrap px-3 font-medium tabular-nums">
                R$ {order.total?.toFixed(2).replace('.', ',')}
            </Td>
            <Td className="px-3" onClick={(e) => e.stopPropagation()}>
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
                    onChange={(e) => onStatusChange(order.id, e.target.value)}
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
            <Td className="px-3 text-right" onClick={(e) => e.stopPropagation()}>
                <IconButton label="Visualizar Detalhes" onClick={() => onSelect(order)}>
                    <Eye className="h-4 w-4" aria-hidden="true" />
                </IconButton>
            </Td>
        </tr>
    );
});

export default function AdminOrdersPage() {
    const { data: allOrders, error: ordersError, mutate } = useSWR<AdminOrder[]>('/orders/all', fetcher, ORDERS_SWR_OPTIONS);
    const [showUnpaid, setShowUnpaid] = useState(false);
    const unpaidCount = allOrders?.filter((order) => isUnpaidOrder(order.status)).length ?? 0;
    const orders = showUnpaid ? allOrders : allOrders?.filter((order) => !isUnpaidOrder(order.status));
    const [selectedOrder, setSelectedOrder] = useState<AdminOrder | null>(null);
    const [confirmingPayment, setConfirmingPayment] = useState(false);
    const [paymentReason, setPaymentReason] = useState('');
    const [submittingPayment, setSubmittingPayment] = useState(false);
    const { data: auditEntries, mutate: reloadAudit } = useSWR<OrderStatusAuditEntry[]>(
        selectedOrder ? `/orders/${selectedOrder.id}/status-audit` : null,
        fetcher
    );

    const selectOrder = useCallback((order: AdminOrder) => {
        setSelectedOrder(order);
        setConfirmingPayment(false);
        setPaymentReason('');
    }, []);

    // Link vindo do módulo de estoque (?pedido=<id>): abre o pedido assim que a lista carrega, uma única vez.
    const deepLinkHandled = useRef(false);
    useEffect(() => {
        if (deepLinkHandled.current || !allOrders) return;
        deepLinkHandled.current = true;
        const orderId = new URLSearchParams(window.location.search).get('pedido');
        const order = orderId ? allOrders.find((item) => item.id === orderId) : undefined;
        if (order) {
            selectOrder(order);
            if (isUnpaidOrder(order.status)) setShowUnpaid(true);
        }
    }, [allOrders, selectOrder]);

    const handleUpdateStatus = useCallback(async (orderId: string, newStatus: string) => {
        try {
            await adminService.updateOrderStatus(orderId, newStatus);
            toast.success('Status do pedido atualizado!');
            mutate();
            setSelectedOrder((previousOrder) => (
                previousOrder?.id === orderId
                    ? { ...previousOrder, status: newStatus }
                    : previousOrder
            ));
        } catch (error) {
            toast.error(errorMessage(error, 'Erro ao atualizar status do pedido.'));
        }
    }, [mutate]);

    const handleConfirmPayment = async () => {
        if (!selectedOrder || submittingPayment) return;
        setSubmittingPayment(true);
        try {
            const updated = await adminService.confirmPaymentManually(selectedOrder.id, paymentReason.trim());
            toast.success('Pagamento confirmado manualmente.');
            setSelectedOrder({
                ...selectedOrder,
                status: updated.status,
                manualPaymentConfirmed: updated.manualPaymentConfirmed,
            });
            setConfirmingPayment(false);
            setPaymentReason('');
            mutate();
            reloadAudit();
        } catch (error) {
            toast.error(errorMessage(error, 'Não foi possível confirmar o pagamento.'));
        } finally {
            setSubmittingPayment(false);
        }
    };

    return (
        <div className="mx-auto max-w-7xl space-y-6 pb-12">
            <PageHeader title="Pedidos" description="Gerencie os pedidos dos clientes e acompanhe as entregas." />

            <div className="grid grid-cols-1 items-start gap-6 xl:grid-cols-3">
                {/* List of Orders */}
                <Card as="section" aria-label="Histórico de pedidos" className="overflow-hidden xl:col-span-2">
                    <CardHeader
                        title="Histórico de Pedidos"
                        actions={
                            <>
                                <Button
                                    size="sm"
                                    aria-pressed={showUnpaid}
                                    onClick={() => {
                                        setShowUnpaid((previous) => !previous);
                                        setSelectedOrder(null);
                                    }}
                                    className={showUnpaid ? 'border-adm-accent-ink/60 bg-adm-accent/10 text-adm-accent-ink hover:bg-adm-accent/20' : undefined}
                                >
                                    {showUnpaid ? 'Ocultar não pagos' : `Mostrar não pagos (${unpaidCount})`}
                                </Button>
                                <span className="text-sm text-adm-ink-muted">Total: {orders?.length || 0}</span>
                            </>
                        }
                    />

                    <TableWrap>
                        <Table>
                            <Thead>
                                <tr>
                                    <Th className="px-3">Cód Pedido / Data</Th>
                                    <Th className="px-3">Cliente</Th>
                                    <Th className="px-3">Total</Th>
                                    <Th className="px-3">Status</Th>
                                    <Th className="px-3 text-right">Ações</Th>
                                </tr>
                            </Thead>
                            <Tbody>
                                {ordersError && !allOrders ? (
                                    <tr>
                                        <td colSpan={5}>
                                            <ErrorState title="Não foi possível carregar os pedidos." onRetry={() => { void mutate(); }} />
                                        </td>
                                    </tr>
                                ) : !orders ? (
                                    <TableSkeletonRows rows={8} columns={5} />
                                ) : orders.length === 0 ? (
                                    <tr>
                                        <td colSpan={5}>
                                            <EmptyState
                                                icon={ShoppingBag}
                                                title={showUnpaid || unpaidCount === 0
                                                    ? 'Nenhum pedido realizado ainda.'
                                                    : 'Nenhum pedido pago ainda.'}
                                                description={showUnpaid || unpaidCount === 0
                                                    ? undefined
                                                    : 'Use "Mostrar não pagos" para ver pedidos aguardando pagamento ou expirados.'}
                                            />
                                        </td>
                                    </tr>
                                ) : (
                                    orders.map((order) => (
                                        <OrderRow
                                            key={order.id}
                                            order={order}
                                            selected={selectedOrder?.id === order.id}
                                            onSelect={selectOrder}
                                            onStatusChange={handleUpdateStatus}
                                        />
                                    ))
                                )}
                            </Tbody>
                        </Table>
                    </TableWrap>
                </Card>

                {/* Details Panel */}
                <Card as="aside" aria-label="Detalhes do pedido" className="space-y-6 p-5 xl:sticky xl:top-6">
                    <div className="border-b border-adm-line pb-4">
                        <h2 className="flex items-center gap-2 text-base font-semibold">
                            <ShoppingBag className="h-5 w-5 text-adm-accent-ink" aria-hidden="true" />
                            Detalhes do Pedido
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
                            {/* General info */}
                            <div className="grid grid-cols-2 gap-4 rounded-adm-control bg-adm-sunken p-4 text-sm">
                                <div className="space-y-1">
                                    <div className="text-xs font-semibold text-adm-ink-subtle">CÓDIGO</div>
                                    <div className="break-all font-mono text-xs">{selectedOrder.id}</div>
                                </div>
                                <div className="space-y-1">
                                    <div className="text-xs font-semibold text-adm-ink-subtle">DATA</div>
                                    <div className="flex items-center gap-1.5 text-xs text-adm-ink-muted">
                                        <Calendar className="h-3.5 w-3.5" aria-hidden="true" />
                                        {selectedOrder.createdAt ? new Date(selectedOrder.createdAt).toLocaleString('pt-BR') : '-'}
                                    </div>
                                </div>
                            </div>

                            {/* Client Info */}
                            <div className="space-y-3">
                                <h3 className="flex items-center gap-2 text-sm font-semibold text-adm-ink-muted">
                                    <User className="h-4 w-4" aria-hidden="true" />
                                    Informações do Cliente
                                </h3>
                                <div className="space-y-1 rounded-adm-control bg-adm-sunken p-4 text-sm">
                                    <div className="font-medium">{selectedOrder.user?.name}</div>
                                    <div className="text-adm-ink-muted">{selectedOrder.user?.email}</div>
                                    <div className="text-adm-ink-muted">Tel: {selectedOrder.user?.phone || '-'}</div>
                                </div>
                            </div>

                            {/* Delivery Address */}
                            <div className="space-y-3">
                                <h3 className="flex items-center gap-2 text-sm font-semibold text-adm-ink-muted">
                                    <MapPin className="h-4 w-4" aria-hidden="true" />
                                    Endereço de Entrega
                                </h3>
                                <div className="space-y-1 rounded-adm-control bg-adm-sunken p-4 text-sm text-adm-ink-muted">
                                    <div>{selectedOrder.address?.street}, {selectedOrder.address?.number}</div>
                                    <div>{selectedOrder.address?.neighborhood} - {selectedOrder.address?.city}/{selectedOrder.address?.state}</div>
                                    <div className="text-xs text-adm-ink-subtle">CEP: {selectedOrder.address?.zipCode}</div>
                                </div>
                            </div>

                            {/* Items */}
                            <div className="space-y-3">
                                <h3 className="text-sm font-semibold text-adm-ink-muted">Itens do Pedido</h3>
                                <div className="max-h-60 space-y-2 overflow-y-auto pr-1">
                                    {selectedOrder.items?.map((item) => (
                                        <div key={item.id} className="flex items-center justify-between gap-3 rounded-adm-control bg-adm-sunken p-3 text-sm">
                                            <div className="min-w-0 space-y-0.5">
                                                <div className="font-medium">
                                                    {item.productName || 'Produto'}
                                                </div>
                                                <div className="text-xs text-adm-ink-muted">
                                                    Qtd: {item.quantity} x R$ {item.price.toFixed(2).replace('.', ',')}
                                                </div>
                                            </div>
                                            <div className="shrink-0 font-semibold tabular-nums">
                                                R$ {item.totalPrice.toFixed(2).replace('.', ',')}
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            {/* Order Total summary */}
                            <div className="space-y-2 border-t border-adm-line pt-4 text-sm">
                                <div className="flex justify-between text-adm-ink-muted">
                                    <span>Desconto Cupom</span>
                                    <span className="tabular-nums">- R$ {selectedOrder.discountAmount?.toFixed(2).replace('.', ',') || '0,00'}</span>
                                </div>
                                <div className="flex justify-between border-t border-adm-line pt-2 text-lg font-semibold">
                                    <span>Valor Total</span>
                                    <span className="tabular-nums text-adm-success">
                                        R$ {selectedOrder.total?.toFixed(2).replace('.', ',')}
                                    </span>
                                </div>
                            </div>

                            {/* Status e confirmação manual de pagamento */}
                            <div className="space-y-3 border-t border-adm-line pt-4">
                                <div className="flex items-center justify-between text-sm">
                                    <span className="text-xs font-semibold text-adm-ink-subtle">STATUS ATUAL</span>
                                    <OrderStatusBadge status={selectedOrder.status} />
                                </div>
                                {selectedOrder.manualPaymentConfirmed && (
                                    <div className="flex items-start gap-2 rounded-adm-control border border-adm-warning/25 bg-adm-warning/10 p-3 text-xs text-adm-warning">
                                        <BadgeCheck className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                                        <span>Pagamento confirmado <strong>manualmente</strong> por um administrador (não pelo Asaas).</span>
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
                                            Use somente quando o pagamento foi recebido <strong>fora do Asaas</strong>. A confirmação fica registrada
                                            com o seu usuário, data, horário e esta justificativa, e não pode ser desfeita por aqui.
                                        </p>
                                        <label htmlFor="manual-payment-reason" className="block text-xs font-semibold text-adm-ink-muted">
                                            JUSTIFICATIVA (mín. {MIN_REASON_LENGTH} caracteres)
                                        </label>
                                        <Textarea
                                            id="manual-payment-reason"
                                            value={paymentReason}
                                            onChange={(e) => setPaymentReason(e.target.value)}
                                            maxLength={500}
                                            rows={3}
                                            placeholder="Ex.: Transferência bancária conferida no extrato em 17/11."
                                        />
                                        <div className="flex gap-2">
                                            <Button
                                                size="sm"
                                                className="flex-1"
                                                onClick={() => { setConfirmingPayment(false); setPaymentReason(''); }}
                                                disabled={submittingPayment}
                                            >
                                                Cancelar
                                            </Button>
                                            <Button
                                                variant="caution"
                                                size="sm"
                                                className="flex-1"
                                                onClick={handleConfirmPayment}
                                                disabled={submittingPayment || paymentReason.trim().length < MIN_REASON_LENGTH}
                                            >
                                                {submittingPayment ? 'Confirmando...' : 'Confirmar pagamento'}
                                            </Button>
                                        </div>
                                    </div>
                                )}
                            </div>

                            {/* Quick Action Status Change */}
                            <div className="space-y-2 pt-2">
                                <span className="block text-xs font-semibold text-adm-ink-subtle">ALTERAR STATUS</span>
                                <div className="flex gap-2">
                                    <Button
                                        size="sm"
                                        className="flex-1 border-adm-violet/40 bg-adm-violet/15 text-adm-violet hover:bg-adm-violet/25"
                                        onClick={() => handleUpdateStatus(selectedOrder.id, 'SHIPPED')}
                                    >
                                        Marcar como Enviado
                                    </Button>
                                    <Button
                                        size="sm"
                                        className="flex-1 border-adm-success/40 bg-adm-success/15 text-adm-success hover:bg-adm-success/25"
                                        onClick={() => handleUpdateStatus(selectedOrder.id, 'DELIVERED')}
                                    >
                                        Marcar como Entregue
                                    </Button>
                                </div>
                            </div>

                            {/* Auditoria de alterações administrativas */}
                            {auditEntries && auditEntries.length > 0 && (
                                <div className="space-y-2 border-t border-adm-line pt-4">
                                    <h3 className="flex items-center gap-2 text-sm font-semibold text-adm-ink-muted">
                                        <History className="h-4 w-4" aria-hidden="true" />
                                        Histórico administrativo
                                    </h3>
                                    <ul className="space-y-2">
                                        {auditEntries.map((entry) => (
                                            <li key={entry.id} className="space-y-1 rounded-adm-control bg-adm-sunken p-3 text-xs text-adm-ink-muted">
                                                <div className="font-semibold text-adm-ink">
                                                    {statusLabel(entry.previousStatus)} → {statusLabel(entry.newStatus)}
                                                    <span className="ml-2 font-normal text-adm-warning">(confirmação manual)</span>
                                                </div>
                                                <div className="text-adm-ink-subtle">
                                                    {entry.actorName} · {new Date(entry.createdAt).toLocaleString('pt-BR')}
                                                </div>
                                                <div>{entry.reason}</div>
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                            )}
                        </div>
                    )}
                </Card>
            </div>
        </div>
    );
}
