'use client';

import { useState } from 'react';
import useSWR from 'swr';
import { ShoppingBag, Eye, Edit3, Loader2, Calendar, User, MapPin } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { adminService } from '@/services/adminService';
import { toast } from 'react-hot-toast';

export default function AdminOrdersPage() {
    const { data: orders, mutate } = useSWR<any[]>('/orders/all', fetcher);
    const [selectedOrder, setSelectedOrder] = useState<any | null>(null);

    const handleUpdateStatus = async (orderId: string, newStatus: string) => {
        try {
            await adminService.updateOrderStatus(orderId, newStatus);
            toast.success('Status do pedido atualizado!');
            mutate();
            if (selectedOrder && selectedOrder.id === orderId) {
                setSelectedOrder((prev: any) => ({ ...prev, status: newStatus }));
            }
        } catch (error: any) {
            toast.error('Erro ao atualizar status do pedido.');
        }
    };

    const getStatusStyle = (status: string) => {
        const cleanStatus = status.toLowerCase();
        if (cleanStatus === 'pending' || cleanStatus === 'pendente') {
            return 'bg-yellow-500/10 text-yellow-500 border-yellow-500/20';
        }
        if (cleanStatus === 'confirmado' || cleanStatus === 'confirmed' || cleanStatus === 'pago') {
            return 'bg-blue-500/10 text-blue-400 border-blue-500/20';
        }
        if (cleanStatus === 'enviado' || cleanStatus === 'shipped') {
            return 'bg-purple-500/10 text-purple-400 border-purple-500/20';
        }
        if (cleanStatus === 'entregue' || cleanStatus === 'delivered') {
            return 'bg-green-500/10 text-green-500 border-green-500/20';
        }
        return 'bg-red-500/10 text-red-500 border-red-500/20';
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
                                            onClick={() => setSelectedOrder(order)}
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
                                                    {order.status}
                                                </span>
                                            </td>
                                            <td className="p-4 text-right" onClick={(e) => e.stopPropagation()}>
                                                <div className="flex justify-end gap-2">
                                                    <button
                                                        onClick={() => setSelectedOrder(order)}
                                                        className="p-1.5 bg-[#3A3A3A] hover:bg-white/10 rounded text-gray-300 hover:text-white transition-colors"
                                                        title="Visualizar Detalhes"
                                                    >
                                                        <Eye className="w-4 h-4" />
                                                    </button>
                                                    <select
                                                        value={order.status}
                                                        onChange={(e) => handleUpdateStatus(order.id, e.target.value)}
                                                        className="bg-[#3A3A3A] border border-white/5 rounded px-2 py-1 text-white text-xs font-semibold focus:outline-none focus:border-brand-secondary"
                                                    >
                                                        <option value="pending">Pendente</option>
                                                        <option value="CONFIRMADO">Confirmado</option>
                                                        <option value="enviado">Enviado</option>
                                                        <option value="entregue">Entregue</option>
                                                        <option value="cancelado">Cancelado</option>
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
                                    {selectedOrder.items?.map((item: any) => (
                                        <div key={item.id} className="flex justify-between items-center bg-[#1A1B1D] p-3 rounded-lg text-sm">
                                            <div className="space-y-0.5">
                                                <div className="font-semibold text-white">
                                                    {item.productVariant?.product?.name || 'Produto'}
                                                </div>
                                                <div className="text-xs text-gray-400">
                                                    Qtd: {item.quantity} x R$ {item.productVariant?.product?.price?.toFixed(2)}
                                                </div>
                                            </div>
                                            <div className="font-bold text-white">
                                                R$ {item.subtotal?.toFixed(2).replace('.', ',')}
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

                            {/* Quick Action Status Change */}
                            <div className="space-y-2 pt-2">
                                <label className="text-xs text-gray-400 font-semibold block">ALTERAR STATUS</label>
                                <div className="flex gap-2">
                                    <button 
                                        onClick={() => handleUpdateStatus(selectedOrder.id, 'enviado')}
                                        className="flex-1 py-2 bg-purple-600 hover:bg-purple-700 text-xs font-bold rounded text-white transition-colors"
                                    >
                                        Marcar como Enviado
                                    </button>
                                    <button 
                                        onClick={() => handleUpdateStatus(selectedOrder.id, 'entregue')}
                                        className="flex-1 py-2 bg-green-600 hover:bg-green-700 text-xs font-bold rounded text-white transition-colors"
                                    >
                                        Marcar como Entregue
                                    </button>
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
