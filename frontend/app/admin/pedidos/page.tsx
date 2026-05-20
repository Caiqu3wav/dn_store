'use client';

import { Search, Filter, Eye } from 'lucide-react';
import { useState } from 'react';
import Link from 'next/link';

const mockPedidos = [
    { id: '#1024', customer: 'João Silva', date: 'Hoje, 14:30', status: 'Processando', total: 'R$ 259,90', items: 2 },
    { id: '#1023', customer: 'Maria Oliveira', date: 'Hoje, 11:15', status: 'Enviado', total: 'R$ 129,90', items: 1 },
    { id: '#1022', customer: 'Carlos Santos', date: 'Ontem, 16:45', status: 'Entregue', total: 'R$ 389,70', items: 3 },
    { id: '#1021', customer: 'Ana Paula', date: 'Ontem, 09:20', status: 'Cancelado', total: 'R$ 89,90', items: 1 },
    { id: '#1020', customer: 'Lucas Mendes', date: '12 Maio, 18:10', status: 'Entregue', total: 'R$ 199,90', items: 1 },
];

function StatusBadge({ status }: { status: string }) {
    const colors: Record<string, string> = {
        'Processando': 'bg-yellow-500/10 text-yellow-500 border-yellow-500/20',
        'Enviado': 'bg-blue-500/10 text-blue-500 border-blue-500/20',
        'Entregue': 'bg-green-500/10 text-green-500 border-green-500/20',
        'Cancelado': 'bg-red-500/10 text-red-500 border-red-500/20',
    };
    const colorClass = colors[status] || 'bg-gray-500/10 text-gray-400 border-gray-500/20';
    return (
        <span className={`px-2 py-1 text-xs font-medium rounded-full border ${colorClass}`}>
            {status}
        </span>
    );
}

export default function AdminPedidosPage() {
    const [searchTerm, setSearchTerm] = useState('');

    const filteredPedidos = mockPedidos.filter(p => 
        p.id.toLowerCase().includes(searchTerm.toLowerCase()) || 
        p.customer.toLowerCase().includes(searchTerm.toLowerCase())
    );

    return (
        <div className="space-y-6 text-white">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                    <h1 className="text-3xl font-bold">Pedidos</h1>
                    <p className="text-gray-400 mt-1">Gerencie as vendas da sua loja.</p>
                </div>
            </div>

            {/* Filters & Search */}
            <div className="flex flex-col md:flex-row gap-4">
                <div className="relative flex-1">
                    <input 
                        type="text" 
                        placeholder="Buscar por ID do pedido ou cliente..." 
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        className="w-full bg-[#2A2A2A] border border-white/10 rounded-lg py-2.5 pl-10 pr-4 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary"
                    />
                    <Search className="w-4 h-4 text-gray-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
                </div>
                <button className="inline-flex items-center gap-2 px-4 py-2.5 bg-[#2A2A2A] border border-white/10 text-gray-300 rounded-lg hover:bg-[#333] transition-colors">
                    <Filter className="w-4 h-4" />
                    Status
                </button>
            </div>

            {/* Orders Table */}
            <div className="bg-[#2A2A2A] border border-white/5 rounded-xl shadow-sm overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse">
                        <thead>
                            <tr className="bg-[#1A1B1D] text-gray-400 text-sm">
                                <th className="p-4 font-medium">Pedido</th>
                                <th className="p-4 font-medium">Data</th>
                                <th className="p-4 font-medium">Cliente</th>
                                <th className="p-4 font-medium">Itens</th>
                                <th className="p-4 font-medium">Status</th>
                                <th className="p-4 font-medium">Total</th>
                                <th className="p-4 font-medium text-right">Ações</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-white/5">
                            {filteredPedidos.map((pedido) => (
                                <tr key={pedido.id} className="hover:bg-white/[0.02] transition-colors group">
                                    <td className="p-4 font-bold text-white">{pedido.id}</td>
                                    <td className="p-4 text-gray-400 text-sm">{pedido.date}</td>
                                    <td className="p-4 text-gray-300">{pedido.customer}</td>
                                    <td className="p-4 text-gray-400">{pedido.items} itens</td>
                                    <td className="p-4">
                                        <StatusBadge status={pedido.status} />
                                    </td>
                                    <td className="p-4 text-white font-medium">{pedido.total}</td>
                                    <td className="p-4 text-right">
                                        <div className="flex items-center justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                            <button className="p-2 text-gray-400 hover:text-white hover:bg-white/10 rounded-lg transition-colors" title="Ver Detalhes">
                                                <Eye className="w-4 h-4" />
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                            {filteredPedidos.length === 0 && (
                                <tr>
                                    <td colSpan={7} className="p-8 text-center text-gray-500">
                                        Nenhum pedido encontrado.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
