'use client';

import { useState } from 'react';
import useSWR from 'swr';
import { ShoppingBag, Eye, Loader2, Calendar, User, MapPin, History, Package } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { adminService, AdminOrder } from '@/services/adminService';
import { toast } from 'react-hot-toast';
import api from '@/lib/axios';

interface HistoryEntry {
    id: string;
    previousStatus: string | null;
    newStatus: string | null;
    eventType: string | null;
    result: string;
    reason: string | null;
    processedAt: string;
}

const ORDER_STATUSES: { value: string; label: string }[] = [
    { value: 'PENDING_PAYMENT', label: 'Aguardando Pagamento' },
    { value: 'PAID',            label: 'Pago' },
    { value: 'PROCESSING',      label: 'Em Processamento' },
    { value: 'SHIPPED',         label: 'Enviado' },
    { value: 'DELIVERED',       label: 'Entregue' },
    { value: 'CANCELLED',       label: 'Cancelado' },
    { value: 'PAYMENT_FAILED',  label: 'Pagamento Falhou' },
    { value: 'PAYMENT_REVIEW',  label: 'Em Revisão' },
    { value: 'EXPIRED',         label: 'Expirado' },
];

const STATUS_STYLE: Record<string, string> = {
    PENDING_PAYMENT: 'bg-yellow-500/10 text-yellow-400 border-yellow-500/20',
    PAID:            'bg-blue-500/10 text-blue-400 border-blue-500/20',
    PROCESSING:      'bg-indigo-500/10 text-indigo-400 border-indigo-500/20',
    SHIPPED:         'bg-purple-500/10 text-purple-400 border-purple-500/20',
    DELIVERED:       'bg-green-500/10 text-green-400 border-green-500/20',
    CANCELLED:       'bg-red-500/10 text-red-400 border-red-500/20',
    PAYMENT_FAILED:  'bg-red-500/10 text-red-400 border-red-500/20',
    PAYMENT_REVIEW:  'bg-orange-500/10 text-orange-400 border-orange-500/20',
    EXPIRED:         'bg-gray-500/10 text-gray-400 border-gray-500/20',
};

const RESULT_STYLE: Record<string, string> = {
    APPLIED:              'text-green-400',
    IGNORED_OUT_OF_ORDER: 'text-yellow-400',
    REQUIRES_REVIEW:      'text-orange-400',
};

function statusLabel(value: string) {
    return ORDER_STATUSES.find(s => s.value === value)?.label ?? value;
}

export default function AdminOrdersPage() {
    const { data: orders, mutate } = useSWR<AdminOrder[]>('/orders/all', fetcher);
    const [selectedOrder, setSelectedOrder] = useState<AdminOrder | null>(null);
    const [tab, setTab] = useState<'details' | 'history'>('details');
    const [history, setHistory] = useState<HistoryEntry[]>([]);
    const [loadingHistory, setLoadingHistory] = useState(false);

    const handleUpdateStatus = async (orderId: string, newStatus: string) => {
        try {
            await adminService.updateOrderStatus(orderId, newStatus);
            toast.success('Status atualizado!');
            mutate();
            setSelectedOrder(prev => prev?.id === orderId ? { ...prev, status: newStatus } : prev);
        } catch {
            toast.error('Erro ao atualizar status.');
        }
    };

    const handleSelectOrder = async (order: AdminOrder) => {
        setSelectedOrder(order);
        setTab('details');
    };

    const handleLoadHistory = async (orderId: string) => {
        setTab('history');
        setLoadingHistory(true);
        try {
            const { data } = await api.get<HistoryEntry[]>(`/admin/orders/${orderId}/history`);
            setHistory(data);
        } catch {
            setHistory([]);
        } finally {
            setLoadingHistory(false);
        }
    };

    const fmt = (v: number) => `R$ ${v.toFixed(2).replace('.', ',')}`;

    return (
        <div className="space-y-6 text-white max-w-7xl mx-auto pb-12">
            <div>
                <h1 className="text-3xl font-bold">Pedidos</h1>
                <p className="text-gray-400 mt-1">Gerencie pedidos e acompanhe o histórico de auditoria.</p>
            </div>

            <div className="grid grid-cols-1 xl:grid-cols-3 gap-8">
                {/* Order list */}
                <div className="xl:col-span-2 bg-[#2A2A2A] border border-white/5 rounded-xl overflow-hidden h-fit">
                    <div className="p-6 border-b border-white/5 flex items-center justify-between">
                        <h2 className="text-xl font-bold">Histórico de Pedidos</h2>
                        <span className="text-sm text-gray-400">Total: {orders?.length ?? 0}</span>
                    </div>
                    <div className="overflow-x-auto">
                        <table className="w-full text-left border-collapse">
                            <thead>
                                <tr className="border-b border-white/5 text-gray-400 text-sm">
                                    <th className="p-4 font-semibold">Cód</th>
                                    <th className="p-4 font-semibold">Cliente</th>
                                    <th className="p-4 font-semibold">Data</th>
                                    <th className="p-4 font-semibold">Total</th>
                                    <th className="p-4 font-semibold">Status</th>
                                    <th className="p-4 font-semibold text-right">Ações</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-white/5 text-sm">
                                {!orders ? (
                                    <tr><td colSpan={6} className="p-8 text-center text-gray-500">
                                        <Loader2 className="w-6 h-6 animate-spin mx-auto mb-2" />
                                        Carregando pedidos...
                                    </td></tr>
                                ) : orders.length === 0 ? (
                                    <tr><td colSpan={6} className="p-8 text-center text-gray-500">Nenhum pedido ainda.</td></tr>
                                ) : orders.map(order => (
                                    <tr
                                        key={order.id}
                                        className={`hover:bg-white/[0.01] transition-colors cursor-pointer ${selectedOrder?.id === order.id ? 'bg-white/[0.03]' : ''}`}
                                        onClick={() => handleSelectOrder(order)}
                                    >
                                        <td className="p-4 font-mono text-xs text-gray-400">{order.id.slice(0, 8)}…</td>
                                        <td className="p-4">
                                            <div className="font-semibold">{order.user?.name ?? 'Cliente'}</div>
                                            <div className="text-xs text-gray-400">{order.user?.email}</div>
                                        </td>
                                        <td className="p-4 text-gray-300">
                                            {order.createdAt ? new Date(order.createdAt).toLocaleDateString('pt-BR') : '-'}
                                        </td>
                                        <td className="p-4 font-semibold">{fmt(order.total ?? 0)}</td>
                                        <td className="p-4">
                                            <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold border ${STATUS_STYLE[order.status] ?? 'bg-gray-500/10 text-gray-400 border-gray-500/20'}`}>
                                                {statusLabel(order.status)}
                                            </span>
                                        </td>
                                        <td className="p-4 text-right" onClick={e => e.stopPropagation()}>
                                            <div className="flex justify-end gap-2">
                                                <button
                                                    onClick={() => handleSelectOrder(order)}
                                                    className="p-1.5 bg-[#3A3A3A] hover:bg-white/10 rounded text-gray-300 hover:text-white transition-colors"
                                                    title="Detalhes"
                                                >
                                                    <Eye className="w-4 h-4" />
                                                </button>
                                                <button
                                                    onClick={() => handleLoadHistory(order.id)}
                                                    className="p-1.5 bg-[#3A3A3A] hover:bg-white/10 rounded text-gray-300 hover:text-white transition-colors"
                                                    title="Histórico"
                                                >
                                                    <History className="w-4 h-4" />
                                                </button>
                                                <select
                                                    value={order.status}
                                                    onChange={e => handleUpdateStatus(order.id, e.target.value)}
                                                    className="bg-[#3A3A3A] border border-white/5 rounded px-2 py-1 text-white text-xs font-semibold focus:outline-none focus:border-brand-secondary"
                                                >
                                                    {ORDER_STATUSES.map(s => (
                                                        <option key={s.value} value={s.value}>{s.label}</option>
                                                    ))}
                                                </select>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>

                {/* Detail / History panel */}
                <div className="bg-[#2A2A2A] border border-white/5 rounded-xl h-fit">
                    {/* Tabs */}
                    <div className="flex border-b border-white/5">
                        <button
                            onClick={() => setTab('details')}
                            className={`flex-1 py-3 text-sm font-semibold flex items-center justify-center gap-2 transition-colors ${tab === 'details' ? 'text-white border-b-2 border-brand-secondary' : 'text-gray-400 hover:text-white'}`}
                        >
                            <ShoppingBag className="w-4 h-4" /> Detalhes
                        </button>
                        <button
                            onClick={() => selectedOrder && handleLoadHistory(selectedOrder.id)}
                            className={`flex-1 py-3 text-sm font-semibold flex items-center justify-center gap-2 transition-colors ${tab === 'history' ? 'text-white border-b-2 border-brand-secondary' : 'text-gray-400 hover:text-white'}`}
                        >
                            <History className="w-4 h-4" /> Auditoria
                        </button>
                    </div>

                    <div className="p-6">
                        {!selectedOrder ? (
                            <div className="text-center py-12 text-gray-500">
                                Selecione um pedido para ver os detalhes.
                            </div>
                        ) : tab === 'details' ? (
                            <div className="space-y-6">
                                <div className="grid grid-cols-2 gap-4 bg-[#1A1B1D] p-4 rounded-lg text-sm">
                                    <div className="space-y-1">
                                        <div className="text-gray-500 text-xs font-semibold">CÓDIGO</div>
                                        <div className="font-mono text-xs break-all">{selectedOrder.id}</div>
                                    </div>
                                    <div className="space-y-1">
                                        <div className="text-gray-500 text-xs font-semibold">DATA</div>
                                        <div className="flex items-center gap-1.5 text-xs text-gray-300">
                                            <Calendar className="w-3.5 h-3.5" />
                                            {selectedOrder.createdAt ? new Date(selectedOrder.createdAt).toLocaleString('pt-BR') : '-'}
                                        </div>
                                    </div>
                                </div>

                                <div className="space-y-2">
                                    <h3 className="text-sm font-bold text-gray-400 flex items-center gap-2">
                                        <User className="w-4 h-4" /> Cliente
                                    </h3>
                                    <div className="bg-[#1A1B1D] p-4 rounded-lg space-y-1 text-sm">
                                        <div className="font-semibold">{selectedOrder.user?.name}</div>
                                        <div className="text-gray-400">{selectedOrder.user?.email}</div>
                                        <div className="text-gray-400">{selectedOrder.user?.phone ?? '-'}</div>
                                    </div>
                                </div>

                                <div className="space-y-2">
                                    <h3 className="text-sm font-bold text-gray-400 flex items-center gap-2">
                                        <MapPin className="w-4 h-4" /> Endereço
                                    </h3>
                                    <div className="bg-[#1A1B1D] p-4 rounded-lg text-sm text-gray-300 space-y-1">
                                        <div>{selectedOrder.address?.street}, {selectedOrder.address?.number}</div>
                                        <div>{selectedOrder.address?.neighborhood} — {selectedOrder.address?.city}/{selectedOrder.address?.state}</div>
                                        <div className="text-xs text-gray-500">CEP: {selectedOrder.address?.zipCode}</div>
                                    </div>
                                </div>

                                <div className="space-y-2">
                                    <h3 className="text-sm font-bold text-gray-400 flex items-center gap-2">
                                        <Package className="w-4 h-4" /> Itens
                                    </h3>
                                    <div className="space-y-2 max-h-52 overflow-y-auto pr-1">
                                        {selectedOrder.items?.map(item => (
                                            <div key={item.id} className="flex justify-between items-center bg-[#1A1B1D] p-3 rounded-lg text-sm">
                                                <div>
                                                    <div className="font-semibold">{item.productName ?? 'Produto'}</div>
                                                    <div className="text-xs text-gray-400">Qtd: {item.quantity} × {fmt(item.price)}</div>
                                                </div>
                                                <div className="font-bold">{fmt(item.totalPrice)}</div>
                                            </div>
                                        ))}
                                    </div>
                                </div>

                                <div className="border-t border-white/5 pt-4 space-y-2 text-sm">
                                    {(selectedOrder.shippingCost ?? 0) > 0 && (
                                        <div className="flex justify-between text-gray-400">
                                            <span>Frete ({selectedOrder.shippingType})</span>
                                            <span>{fmt(selectedOrder.shippingCost ?? 0)}</span>
                                        </div>
                                    )}
                                    {(selectedOrder.discountAmount ?? 0) > 0 && (
                                        <div className="flex justify-between text-gray-400">
                                            <span>Desconto</span>
                                            <span>- {fmt(selectedOrder.discountAmount ?? 0)}</span>
                                        </div>
                                    )}
                                    <div className="flex justify-between text-lg font-bold border-t border-white/5 pt-2">
                                        <span>Total</span>
                                        <span className="text-green-400">{fmt(selectedOrder.total ?? 0)}</span>
                                    </div>
                                </div>

                                <div className="space-y-2 pt-2">
                                    <label className="text-xs text-gray-400 font-semibold block">ALTERAR STATUS</label>
                                    <div className="grid grid-cols-2 gap-2">
                                        <button onClick={() => handleUpdateStatus(selectedOrder.id, 'PROCESSING')}
                                            className="py-2 bg-indigo-600 hover:bg-indigo-700 text-xs font-bold rounded text-white transition-colors">
                                            Em Processamento
                                        </button>
                                        <button onClick={() => handleUpdateStatus(selectedOrder.id, 'SHIPPED')}
                                            className="py-2 bg-purple-600 hover:bg-purple-700 text-xs font-bold rounded text-white transition-colors">
                                            Enviado
                                        </button>
                                        <button onClick={() => handleUpdateStatus(selectedOrder.id, 'DELIVERED')}
                                            className="py-2 bg-green-600 hover:bg-green-700 text-xs font-bold rounded text-white transition-colors">
                                            Entregue
                                        </button>
                                        <button onClick={() => handleUpdateStatus(selectedOrder.id, 'CANCELLED')}
                                            className="py-2 bg-red-700 hover:bg-red-800 text-xs font-bold rounded text-white transition-colors">
                                            Cancelar
                                        </button>
                                    </div>
                                </div>
                            </div>
                        ) : (
                            /* Audit history tab */
                            <div className="space-y-3">
                                <p className="text-xs text-gray-500 font-semibold uppercase tracking-wide">
                                    Histórico de Auditoria — {selectedOrder.id.slice(0, 8)}…
                                </p>
                                {loadingHistory ? (
                                    <div className="flex justify-center py-8">
                                        <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
                                    </div>
                                ) : history.length === 0 ? (
                                    <p className="text-sm text-gray-500 py-6 text-center">Nenhum evento registrado.</p>
                                ) : (
                                    <ol className="relative border-l border-white/10 space-y-4 pl-4">
                                        {history.map(entry => (
                                            <li key={entry.id} className="relative">
                                                <span className="absolute -left-[1.15rem] top-1 w-2.5 h-2.5 rounded-full bg-brand-secondary border-2 border-[#2A2A2A]" />
                                                <div className="bg-[#1A1B1D] rounded-lg p-3 space-y-1 text-xs">
                                                    <div className="flex items-center justify-between gap-2">
                                                        <span className={`font-bold ${RESULT_STYLE[entry.result] ?? 'text-gray-300'}`}>
                                                            {entry.previousStatus ?? '—'} → {entry.newStatus ?? '—'}
                                                        </span>
                                                        <span className="text-gray-500 shrink-0">
                                                            {new Date(entry.processedAt).toLocaleString('pt-BR')}
                                                        </span>
                                                    </div>
                                                    {entry.eventType && (
                                                        <div className="text-gray-400">Evento: <span className="text-gray-200">{entry.eventType}</span></div>
                                                    )}
                                                    {entry.reason && (
                                                        <div className="text-gray-400">{entry.reason}</div>
                                                    )}
                                                    <div className={`font-semibold ${RESULT_STYLE[entry.result] ?? 'text-gray-400'}`}>
                                                        {entry.result}
                                                    </div>
                                                </div>
                                            </li>
                                        ))}
                                    </ol>
                                )}
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
