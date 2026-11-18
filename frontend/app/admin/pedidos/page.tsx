'use client';

import { useState } from 'react';
import useSWR from 'swr';
import { ShoppingBag, Eye, Loader2, Calendar, User, MapPin, BadgeCheck, History } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { adminService, AdminOrder, OrderStatusAuditEntry } from '@/services/adminService';
import { toast } from 'react-hot-toast';

// Os valores enviados à API são os status reais do backend (antes o seletor enviava 'enviado', 'entregue'...
// e todo pedido de alteração era recusado com 400).
const STATUS_LABELS: Record<string, string> = {
    PENDING_PAYMENT: 'Aguardando pagamento',
    PAID: 'Pago',
    PAYMENT_FAILED: 'Pagamento falhou',
    PAYMENT_REVIEW: 'Revisar pagamento',
    PROCESSING: 'Em separação',
    SHIPPED: 'Enviado',
    DELIVERED: 'Entregue',
    CANCELLED: 'Cancelado',
    EXPIRED: 'Expirado',
};

// Status que o administrador pode definir diretamente. "Pago" só pela confirmação manual (com justificativa);
// aguardando/falha/expirado/revisão são controlados pelo sistema.
const ADMIN_SETTABLE_STATUSES = ['PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
const MANUAL_CONFIRMABLE_STATUSES = ['PENDING_PAYMENT', 'PAYMENT_FAILED'];
const MIN_REASON_LENGTH = 10;

const statusLabel = (status: string) => STATUS_LABELS[status] ?? status;

const errorMessage = (error: unknown, fallback: string) => {
    const message = (error as { response?: { data?: { message?: unknown } } })?.response?.data?.message;
    return typeof message === 'string' && message.trim() ? message : fallback;
};

export default function AdminOrdersPage() {
    const { data: orders, mutate } = useSWR<AdminOrder[]>('/orders/all', fetcher);
    const [selectedOrder, setSelectedOrder] = useState<AdminOrder | null>(null);
    const [confirmingPayment, setConfirmingPayment] = useState(false);
    const [paymentReason, setPaymentReason] = useState('');
    const [submittingPayment, setSubmittingPayment] = useState(false);
    const { data: auditEntries, mutate: reloadAudit } = useSWR<OrderStatusAuditEntry[]>(
        selectedOrder ? `/orders/${selectedOrder.id}/status-audit` : null,
        fetcher
    );

    const selectOrder = (order: AdminOrder) => {
        setSelectedOrder(order);
        setConfirmingPayment(false);
        setPaymentReason('');
    };

    const handleUpdateStatus = async (orderId: string, newStatus: string) => {
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
    };

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

    const getStatusStyle = (status: string) => {
        switch (status) {
            case 'PENDING_PAYMENT':
            case 'PAYMENT_REVIEW':
                return 'bg-yellow-500/10 text-yellow-500 border-yellow-500/20';
            case 'PAID':
            case 'PROCESSING':
                return 'bg-blue-500/10 text-blue-400 border-blue-500/20';
            case 'SHIPPED':
                return 'bg-purple-500/10 text-purple-400 border-purple-500/20';
            case 'DELIVERED':
                return 'bg-green-500/10 text-green-500 border-green-500/20';
            default:
                return 'bg-red-500/10 text-red-500 border-red-500/20';
        }
    };

    return (
        <div className="space-y-6 text-white max-w-7xl mx-auto pb-12">
            <div>
                <h1 className="text-3xl font-bold">Pedidos</h1>
                <p className="text-gray-400 mt-1">Gerencie os pedidos dos clientes e acompanhe as entregas.</p>
            </div>

            <div className="grid grid-cols-1 xl:grid-cols-3 gap-8">
                {/* List of Orders */}
                <div className="xl:col-span-2 bg-[#2A2A2A] border border-white/5 rounded-xl overflow-hidden h-fit">
                    <div className="p-6 border-b border-white/5 flex items-center justify-between">
                        <h2 className="text-xl font-bold">Histórico de Pedidos</h2>
                        <span className="text-sm text-gray-400">Total: {orders?.length || 0}</span>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="w-full text-left border-collapse">
                            <thead>
                                <tr className="border-b border-white/5 text-gray-400 text-sm">
                                    <th className="p-4 font-semibold">Cód Pedido</th>
                                    <th className="p-4 font-semibold">Cliente</th>
                                    <th className="p-4 font-semibold">Data</th>
                                    <th className="p-4 font-semibold">Total</th>
                                    <th className="p-4 font-semibold">Status</th>
                                    <th className="p-4 font-semibold text-right">Ações</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-white/5 text-sm">
                                {!orders ? (
                                    <tr>
                                        <td colSpan={6} className="p-8 text-center text-gray-500">
                                            <Loader2 className="w-6 h-6 animate-spin mx-auto mb-2" />
                                            Carregando pedidos...
                                        </td>
                                    </tr>
                                ) : orders.length === 0 ? (
                                    <tr>
                                        <td colSpan={6} className="p-8 text-center text-gray-500">
                                            Nenhum pedido realizado ainda.
                                        </td>
                                    </tr>
                                ) : (
                                    orders.map((order) => (
                                        <tr 
                                            key={order.id} 
                                            className={`hover:bg-white/[0.01] transition-colors cursor-pointer ${
                                                selectedOrder?.id === order.id ? 'bg-white/[0.03]' : ''
                                            }`}
                                            onClick={() => selectOrder(order)}
                                        >
                                            <td className="p-4 font-mono text-xs text-gray-400">
                                                {order.id.slice(0, 8)}...
                                            </td>
                                            <td className="p-4">
                                                <div className="font-semibold">{order.user?.name || 'Cliente'}</div>
                                                <div className="text-xs text-gray-400">{order.user?.email}</div>
                                            </td>
                                            <td className="p-4 text-gray-300">
                                                {order.createdAt ? new Date(order.createdAt).toLocaleDateString('pt-BR') : '-'}
                                            </td>
                                            <td className="p-4 font-semibold">
                                                R$ {order.total?.toFixed(2).replace('.', ',')}
                                            </td>
                                            <td className="p-4">
                                                <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold border ${getStatusStyle(order.status)}`}>
                                                    {statusLabel(order.status)}
                                                </span>
                                                {order.manualPaymentConfirmed && (
                                                    <span className="ml-2 text-[10px] font-semibold text-amber-400" title="Pagamento confirmado manualmente por um administrador">
                                                        manual
                                                    </span>
                                                )}
                                            </td>
                                            <td className="p-4 text-right" onClick={(e) => e.stopPropagation()}>
                                                <div className="flex justify-end gap-2">
                                                    <button
                                                        onClick={() => selectOrder(order)}
                                                        className="p-1.5 bg-[#3A3A3A] hover:bg-white/10 rounded text-gray-300 hover:text-white transition-colors"
                                                        title="Visualizar Detalhes"
                                                    >
                                                        <Eye className="w-4 h-4" />
                                                    </button>
                                                    <select
                                                        value={order.status}
                                                        aria-label={`Alterar status do pedido ${order.id.slice(0, 8)}`}
                                                        onChange={(e) => handleUpdateStatus(order.id, e.target.value)}
                                                        className="w-32 truncate bg-[#3A3A3A] border border-white/5 rounded px-2 py-1 text-white text-xs font-semibold focus:outline-none focus:border-brand-secondary"
                                                    >
                                                        {!ADMIN_SETTABLE_STATUSES.includes(order.status) && (
                                                            <option value={order.status} disabled>{statusLabel(order.status)}</option>
                                                        )}
                                                        {ADMIN_SETTABLE_STATUSES.map((status) => (
                                                            <option key={status} value={status}>{statusLabel(status)}</option>
                                                        ))}
                                                    </select>
                                                </div>
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>

                {/* Details Panel */}
                <div className="bg-[#2A2A2A] border border-white/5 p-6 rounded-xl h-fit space-y-6">
                    <div className="border-b border-white/5 pb-4">
                        <h2 className="text-xl font-bold flex items-center gap-2">
                            <ShoppingBag className="w-5 h-5 text-brand-secondary" />
                            Detalhes do Pedido
                        </h2>
                    </div>

                    {!selectedOrder ? (
                        <div className="text-center py-12 text-gray-500">
                            Selecione um pedido na lista para visualizar os detalhes completos.
                        </div>
                    ) : (
                        <div className="space-y-6">
                            {/* General info */}
                            <div className="grid grid-cols-2 gap-4 bg-[#1A1B1D] p-4 rounded-lg text-sm">
                                <div className="space-y-1">
                                    <div className="text-gray-500 text-xs font-semibold">CÓDIGO</div>
                                    <div className="font-mono text-xs">{selectedOrder.id}</div>
                                </div>
                                <div className="space-y-1">
                                    <div className="text-gray-500 text-xs font-semibold">DATA</div>
                                    <div className="flex items-center gap-1.5 text-xs text-gray-300">
                                        <Calendar className="w-3.5 h-3.5" />
                                        {selectedOrder.createdAt ? new Date(selectedOrder.createdAt).toLocaleString('pt-BR') : '-'}
                                    </div>
                                </div>
                            </div>

                            {/* Client Info */}
                            <div className="space-y-3">
                                <h3 className="text-sm font-bold text-gray-400 flex items-center gap-2">
                                    <User className="w-4 h-4" />
                                    Informações do Cliente
                                </h3>
                                <div className="bg-[#1A1B1D] p-4 rounded-lg space-y-1 text-sm">
                                    <div className="font-semibold">{selectedOrder.user?.name}</div>
                                    <div className="text-gray-400">{selectedOrder.user?.email}</div>
                                    <div className="text-gray-400">Tel: {selectedOrder.user?.phone || '-'}</div>
                                </div>
                            </div>

                            {/* Delivery Address */}
                            <div className="space-y-3">
                                <h3 className="text-sm font-bold text-gray-400 flex items-center gap-2">
                                    <MapPin className="w-4 h-4" />
                                    Endereço de Entrega
                                </h3>
                                <div className="bg-[#1A1B1D] p-4 rounded-lg text-sm text-gray-300 space-y-1">
                                    <div>{selectedOrder.address?.street}, {selectedOrder.address?.number}</div>
                                    <div>{selectedOrder.address?.neighborhood} - {selectedOrder.address?.city}/{selectedOrder.address?.state}</div>
                                    <div className="text-xs text-gray-500">CEP: {selectedOrder.address?.zipCode}</div>
                                </div>
                            </div>

                            {/* Items */}
                            <div className="space-y-3">
                                <h3 className="text-sm font-bold text-gray-400">Itens do Pedido</h3>
                                <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
                                    {selectedOrder.items?.map((item) => (
                                        <div key={item.id} className="flex justify-between items-center bg-[#1A1B1D] p-3 rounded-lg text-sm">
                                            <div className="space-y-0.5">
                                                <div className="font-semibold text-white">
                                                    {item.productName || 'Produto'}
                                                </div>
                                                <div className="text-xs text-gray-400">
                                                    Qtd: {item.quantity} x R$ {item.price.toFixed(2).replace('.', ',')}
                                                </div>
                                            </div>
                                            <div className="font-bold text-white">
                                                R$ {item.totalPrice.toFixed(2).replace('.', ',')}
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            {/* Order Total summary */}
                            <div className="border-t border-white/5 pt-4 space-y-2 text-sm">
                                <div className="flex justify-between text-gray-400">
                                    <span>Desconto Cupom</span>
                                    <span>- R$ {selectedOrder.discountAmount?.toFixed(2).replace('.', ',') || '0,00'}</span>
                                </div>
                                <div className="flex justify-between text-lg font-bold text-white border-t border-white/5 pt-2">
                                    <span>Valor Total</span>
                                    <span className="text-green-500">
                                        R$ {selectedOrder.total?.toFixed(2).replace('.', ',')}
                                    </span>
                                </div>
                            </div>

                            {/* Status e confirmação manual de pagamento */}
                            <div className="space-y-3 border-t border-white/5 pt-4">
                                <div className="flex items-center justify-between text-sm">
                                    <span className="text-gray-400 font-semibold text-xs">STATUS ATUAL</span>
                                    <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold border ${getStatusStyle(selectedOrder.status)}`}>
                                        {statusLabel(selectedOrder.status)}
                                    </span>
                                </div>
                                {selectedOrder.manualPaymentConfirmed && (
                                    <div className="flex items-start gap-2 rounded-lg border border-amber-500/20 bg-amber-500/10 p-3 text-xs text-amber-300">
                                        <BadgeCheck className="mt-0.5 h-4 w-4 shrink-0" />
                                        <span>Pagamento confirmado <strong>manualmente</strong> por um administrador (não pelo Asaas).</span>
                                    </div>
                                )}

                                {MANUAL_CONFIRMABLE_STATUSES.includes(selectedOrder.status) && !confirmingPayment && (
                                    <button
                                        type="button"
                                        onClick={() => setConfirmingPayment(true)}
                                        className="w-full rounded border border-amber-500/40 py-2 text-xs font-bold text-amber-300 transition-colors hover:bg-amber-500/10"
                                    >
                                        Confirmar pagamento manualmente
                                    </button>
                                )}

                                {confirmingPayment && (
                                    <div className="space-y-2 rounded-lg border border-amber-500/30 bg-[#1A1B1D] p-3">
                                        <p className="text-xs text-gray-300">
                                            Use somente quando o pagamento foi recebido <strong>fora do Asaas</strong>. A confirmação fica registrada
                                            com o seu usuário, data, horário e esta justificativa, e não pode ser desfeita por aqui.
                                        </p>
                                        <label htmlFor="manual-payment-reason" className="block text-xs font-semibold text-gray-400">
                                            JUSTIFICATIVA (mín. {MIN_REASON_LENGTH} caracteres)
                                        </label>
                                        <textarea
                                            id="manual-payment-reason"
                                            value={paymentReason}
                                            onChange={(e) => setPaymentReason(e.target.value)}
                                            maxLength={500}
                                            rows={3}
                                            placeholder="Ex.: Transferência bancária conferida no extrato em 17/11."
                                            className="w-full rounded border border-white/10 bg-[#2A2A2A] p-2 text-sm text-white focus:border-brand-secondary focus:outline-none"
                                        />
                                        <div className="flex gap-2">
                                            <button
                                                type="button"
                                                onClick={() => { setConfirmingPayment(false); setPaymentReason(''); }}
                                                disabled={submittingPayment}
                                                className="flex-1 rounded bg-[#3A3A3A] py-2 text-xs font-bold text-white hover:bg-white/10 disabled:opacity-50"
                                            >
                                                Cancelar
                                            </button>
                                            <button
                                                type="button"
                                                onClick={handleConfirmPayment}
                                                disabled={submittingPayment || paymentReason.trim().length < MIN_REASON_LENGTH}
                                                className="flex-1 rounded bg-amber-600 py-2 text-xs font-bold text-white hover:bg-amber-700 disabled:cursor-not-allowed disabled:opacity-50"
                                            >
                                                {submittingPayment ? 'Confirmando...' : 'Confirmar pagamento'}
                                            </button>
                                        </div>
                                    </div>
                                )}
                            </div>

                            {/* Quick Action Status Change */}
                            <div className="space-y-2 pt-2">
                                <label className="text-xs text-gray-400 font-semibold block">ALTERAR STATUS</label>
                                <div className="flex gap-2">
                                    <button 
                                        onClick={() => handleUpdateStatus(selectedOrder.id, 'SHIPPED')}
                                        className="flex-1 py-2 bg-purple-600 hover:bg-purple-700 text-xs font-bold rounded text-white transition-colors"
                                    >
                                        Marcar como Enviado
                                    </button>
                                    <button 
                                        onClick={() => handleUpdateStatus(selectedOrder.id, 'DELIVERED')}
                                        className="flex-1 py-2 bg-green-600 hover:bg-green-700 text-xs font-bold rounded text-white transition-colors"
                                    >
                                        Marcar como Entregue
                                    </button>
                                </div>
                            </div>

                            {/* Auditoria de alterações administrativas */}
                            {auditEntries && auditEntries.length > 0 && (
                                <div className="space-y-2 border-t border-white/5 pt-4">
                                    <h3 className="flex items-center gap-2 text-sm font-bold text-gray-400">
                                        <History className="h-4 w-4" />
                                        Histórico administrativo
                                    </h3>
                                    <ul className="space-y-2">
                                        {auditEntries.map((entry) => (
                                            <li key={entry.id} className="rounded-lg bg-[#1A1B1D] p-3 text-xs text-gray-300 space-y-1">
                                                <div className="font-semibold text-white">
                                                    {statusLabel(entry.previousStatus)} → {statusLabel(entry.newStatus)}
                                                    <span className="ml-2 font-normal text-amber-400">(confirmação manual)</span>
                                                </div>
                                                <div className="text-gray-400">
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
                </div>
            </div>
        </div>
    );
}
