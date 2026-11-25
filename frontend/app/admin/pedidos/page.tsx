'use client';

import { useState } from 'react';
import useSWR from 'swr';
import { Calendar, Eye, History, Loader2, MapPin, Package, ShoppingBag, User } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { fetcher } from '@/services/productService';
import { adminService, AdminOrder, OrderStatusAuditEntry } from '@/services/adminService';
import api from '@/lib/axios';
import { isUnpaidOrder, orderStatusLabel, orderStatusStyle } from '@/utils/orderStatus';

type AdminTab = 'details' | 'history';

const fmt = (value?: number) =>
    new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value ?? 0);

export default function AdminOrdersPage() {
    const { data: allOrders, mutate } = useSWR<AdminOrder[]>('/orders/all', fetcher);
    const [showUnpaid, setShowUnpaid] = useState(false);
    const [selectedOrder, setSelectedOrder] = useState<AdminOrder | null>(null);
    const [tab, setTab] = useState<AdminTab>('details');
    const [history, setHistory] = useState<OrderStatusAuditEntry[]>([]);
    const [isLoadingHistory, setIsLoadingHistory] = useState(false);

    const unpaidCount = allOrders?.filter((order) => isUnpaidOrder(order.status)).length ?? 0;
    const orders = showUnpaid ? allOrders : allOrders?.filter((order) => !isUnpaidOrder(order.status));

    const handleUpdateStatus = async (orderId: string, newStatus: string) => {
        try {
            await adminService.updateOrderStatus(orderId, newStatus);
            toast.success('Status atualizado!');
            await mutate();
            setSelectedOrder((previous) =>
                previous?.id === orderId ? { ...previous, status: newStatus } : previous
            );
        } catch {
            toast.error('Erro ao atualizar status do pedido.');
        }
    };

    const handleLoadHistory = async (orderId: string) => {
        setTab('history');
        setIsLoadingHistory(true);
        try {
            const { data } = await api.get<OrderStatusAuditEntry[]>(`/orders/${orderId}/status-audit`);
            setHistory(data);
        } catch {
            setHistory([]);
            toast.error('Erro ao carregar a auditoria do pedido.');
        } finally {
            setIsLoadingHistory(false);
        }
    };

    return (
        <div className="space-y-6 text-white max-w-7xl mx-auto pb-12">
            <div>
                <h1 className="text-3xl font-bold">Pedidos</h1>
                <p className="text-gray-400 mt-1">Gerencie pedidos e acompanhe o histórico de auditoria.</p>
            </div>

            <div className="grid grid-cols-1 xl:grid-cols-3 gap-8">
                <div className="xl:col-span-2 bg-[#2A2A2A] border border-white/5 rounded-xl overflow-hidden h-fit">
                    <div className="p-6 border-b border-white/5 flex items-center justify-between">
                        <h2 className="text-xl font-bold">Histórico de Pedidos</h2>
                        {unpaidCount > 0 && (
                            <button
                                type="button"
                                onClick={() => setShowUnpaid((value) => !value)}
                                className="text-sm text-gray-400 hover:text-white"
                            >
                                {showUnpaid ? 'Ocultar não pagos' : `Mostrar não pagos (${unpaidCount})`}
                            </button>
                        )}
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
                                ) : orders.map((order) => (
                                    <tr
                                        key={order.id}
                                        onClick={() => {
                                            setSelectedOrder(order);
                                            setTab('details');
                                        }}
                                        className={`cursor-pointer hover:bg-white/[0.01] ${selectedOrder?.id === order.id ? 'bg-white/[0.03]' : ''}`}
                                    >
                                        <td className="p-4 font-mono text-xs text-gray-400">{order.id.slice(0, 8)}...</td>
                                        <td className="p-4">
                                            <div className="font-semibold">{order.user?.name || 'Cliente'}</div>
                                            <div className="text-xs text-gray-400">{order.user?.email}</div>
                                        </td>
                                        <td className="p-4 text-gray-300">
                                            {order.createdAt ? new Date(order.createdAt).toLocaleDateString('pt-BR') : '-'}
                                        </td>
                                        <td className="p-4 font-semibold">{fmt(order.total)}</td>
                                        <td className="p-4">
                                            <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold border ${orderStatusStyle(order.status)}`}>
                                                {orderStatusLabel(order.status)}
                                            </span>
                                        </td>
                                        <td className="p-4 text-right" onClick={(event) => event.stopPropagation()}>
                                            <button
                                                type="button"
                                                onClick={() => {
                                                    setSelectedOrder(order);
                                                    setTab('details');
                                                }}
                                                className="p-1.5 bg-[#3A3A3A] hover:bg-white/10 rounded text-gray-300 hover:text-white"
                                                title="Visualizar detalhes"
                                            >
                                                <Eye className="w-4 h-4" />
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>

                <div className="bg-[#2A2A2A] border border-white/5 rounded-xl h-fit">
                    <div className="flex border-b border-white/5">
                        <button
                            type="button"
                            onClick={() => setTab('details')}
                            className={`flex-1 py-3 text-sm font-semibold flex items-center justify-center gap-2 ${tab === 'details' ? 'text-white border-b-2 border-brand-secondary' : 'text-gray-400 hover:text-white'}`}
                        >
                            <ShoppingBag className="w-4 h-4" /> Detalhes
                        </button>
                        <button
                            type="button"
                            onClick={() => selectedOrder && handleLoadHistory(selectedOrder.id)}
                            className={`flex-1 py-3 text-sm font-semibold flex items-center justify-center gap-2 ${tab === 'history' ? 'text-white border-b-2 border-brand-secondary' : 'text-gray-400 hover:text-white'}`}
                        >
                            <History className="w-4 h-4" /> Auditoria
                        </button>
                    </div>

                    <div className="p-6">
                        {!selectedOrder ? (
                            <div className="text-center py-12 text-gray-500">Selecione um pedido para ver os detalhes.</div>
                        ) : tab === 'details' ? (
                            <div className="space-y-6">
                                <div className="grid grid-cols-2 gap-4 bg-[#1A1B1D] p-4 rounded-lg text-sm">
                                    <div>
                                        <div className="text-gray-500 text-xs font-semibold">CÓDIGO</div>
                                        <div className="font-mono text-xs break-all">{selectedOrder.id}</div>
                                    </div>
                                    <div>
                                        <div className="text-gray-500 text-xs font-semibold">DATA</div>
                                        <div className="flex items-center gap-1.5 text-xs text-gray-300">
                                            <Calendar className="w-3.5 h-3.5" />
                                            {selectedOrder.createdAt ? new Date(selectedOrder.createdAt).toLocaleString('pt-BR') : '-'}
                                        </div>
                                    </div>
                                </div>

                                <div className="space-y-2">
                                    <h3 className="text-sm font-bold text-gray-400 flex items-center gap-2"><User className="w-4 h-4" /> Cliente</h3>
                                    <div className="bg-[#1A1B1D] p-4 rounded-lg space-y-1 text-sm">
                                        <div className="font-semibold">{selectedOrder.user?.name}</div>
                                        <div className="text-gray-400">{selectedOrder.user?.email}</div>
                                        <div className="text-gray-400">{selectedOrder.user?.phone ?? '-'}</div>
                                    </div>
                                </div>

                                {selectedOrder.address && (
                                    <div className="space-y-2">
                                        <h3 className="text-sm font-bold text-gray-400 flex items-center gap-2"><MapPin className="w-4 h-4" /> Endereço</h3>
                                        <div className="bg-[#1A1B1D] p-4 rounded-lg text-sm text-gray-300 space-y-1">
                                            <div>{selectedOrder.address.street}, {selectedOrder.address.number}</div>
                                            <div>{selectedOrder.address.neighborhood} — {selectedOrder.address.city}/{selectedOrder.address.state}</div>
                                            <div className="text-xs text-gray-500">CEP: {selectedOrder.address.zipCode}</div>
                                        </div>
                                    </div>
                                )}

                                {selectedOrder.items && selectedOrder.items.length > 0 && (
                                    <div className="space-y-2">
                                        <h3 className="text-sm font-bold text-gray-400 flex items-center gap-2"><Package className="w-4 h-4" /> Itens</h3>
                                        <div className="space-y-2 max-h-52 overflow-y-auto pr-1">
                                            {selectedOrder.items.map((item) => (
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
                                )}

                                <div className="border-t border-white/5 pt-4 space-y-2 text-sm">
                                    <div className="flex justify-between text-gray-400">
                                        <span>Desconto Cupom</span>
                                        <span>- {fmt(selectedOrder.discountAmount)}</span>
                                    </div>
                                    <div className="flex justify-between text-lg font-bold border-t border-white/5 pt-2">
                                        <span>Valor Total</span>
                                        <span className="text-green-500">{fmt(selectedOrder.total)}</span>
                                    </div>
                                </div>

                                <div className="flex gap-2">
                                    <button
                                        type="button"
                                        onClick={() => handleUpdateStatus(selectedOrder.id, 'SHIPPED')}
                                        className="flex-1 py-2 bg-purple-600 hover:bg-purple-700 text-xs font-bold rounded"
                                    >
                                        Marcar como Enviado
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => handleUpdateStatus(selectedOrder.id, 'DELIVERED')}
                                        className="flex-1 py-2 bg-green-600 hover:bg-green-700 text-xs font-bold rounded"
                                    >
                                        Marcar como Entregue
                                    </button>
                                </div>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {isLoadingHistory ? (
                                    <Loader2 className="w-5 h-5 animate-spin mx-auto text-gray-400" />
                                ) : history.length === 0 ? (
                                    <div className="text-center py-8 text-gray-500">Nenhuma alteração registrada.</div>
                                ) : history.map((entry) => (
                                    <div key={entry.id} className="bg-[#1A1B1D] p-3 rounded-lg text-xs space-y-1">
                                        <div className="font-semibold text-gray-200">
                                            {orderStatusLabel(entry.previousStatus)} → {orderStatusLabel(entry.newStatus)}
                                        </div>
                                        <div className="text-gray-400">{entry.reason}</div>
                                        <div className="text-gray-500">
                                            {entry.actorName} · {new Date(entry.createdAt).toLocaleString('pt-BR')}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
