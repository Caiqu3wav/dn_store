'use client';

import { DollarSign, ShoppingBag, Package, Users } from 'lucide-react';

const stats = [
    { title: 'Vendas do Mês', value: 'R$ 15.430', icon: DollarSign, trend: '+12%', isPositive: true },
    { title: 'Pedidos Pendentes', value: '18', icon: ShoppingBag, trend: '-2%', isPositive: false },
    { title: 'Produtos Ativos', value: '124', icon: Package, trend: '+5%', isPositive: true },
    { title: 'Total Clientes', value: '892', icon: Users, trend: '+18%', isPositive: true },
];

const recentOrders = [
    { id: '#1024', customer: 'João Silva', date: 'Hoje, 14:30', status: 'Processando', total: 'R$ 259,90' },
    { id: '#1023', customer: 'Maria Oliveira', date: 'Hoje, 11:15', status: 'Enviado', total: 'R$ 129,90' },
    { id: '#1022', customer: 'Carlos Santos', date: 'Ontem, 16:45', status: 'Entregue', total: 'R$ 389,70' },
    { id: '#1021', customer: 'Ana Paula', date: 'Ontem, 09:20', status: 'Cancelado', total: 'R$ 89,90' },
    { id: '#1020', customer: 'Lucas Mendes', date: '12 Maio, 18:10', status: 'Entregue', total: 'R$ 199,90' },
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

export default function AdminDashboard() {
    return (
        <div className="space-y-8 text-white">
            <div>
                <h1 className="text-3xl font-bold">Dashboard</h1>
                <p className="text-gray-400 mt-1">Bem-vindo ao painel de controle da DN Store.</p>
            </div>

            {/* Stat Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                {stats.map((stat, i) => {
                    const Icon = stat.icon;
                    return (
                        <div key={i} className="bg-[#2A2A2A] border border-white/5 p-6 rounded-xl shadow-sm">
                            <div className="flex items-center justify-between mb-4">
                                <div className="p-2 bg-[#1A1B1D] rounded-lg">
                                    <Icon className="w-6 h-6 text-brand-secondary" />
                                </div>
                                <span className={`text-sm font-medium ${stat.isPositive ? 'text-green-500' : 'text-red-500'}`}>
                                    {stat.trend}
                                </span>
                            </div>
                            <h3 className="text-gray-400 text-sm font-medium">{stat.title}</h3>
                            <p className="text-3xl font-bold mt-1 text-white">{stat.value}</p>
                        </div>
                    );
                })}
            </div>

            {/* Recent Orders Table */}
            <div className="bg-[#2A2A2A] border border-white/5 rounded-xl shadow-sm overflow-hidden">
                <div className="p-6 border-b border-white/5 flex items-center justify-between">
                    <h2 className="text-lg font-bold text-white">Pedidos Recentes</h2>
                    <button className="text-sm text-brand-secondary hover:text-red-400 font-medium transition-colors">
                        Ver todos
                    </button>
                </div>
                <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse">
                        <thead>
                            <tr className="bg-[#1A1B1D] text-gray-400 text-sm">
                                <th className="p-4 font-medium">Pedido</th>
                                <th className="p-4 font-medium">Cliente</th>
                                <th className="p-4 font-medium">Data</th>
                                <th className="p-4 font-medium">Status</th>
                                <th className="p-4 font-medium">Total</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-white/5">
                            {recentOrders.map((order, i) => (
                                <tr key={i} className="hover:bg-white/[0.02] transition-colors">
                                    <td className="p-4 font-medium text-white">{order.id}</td>
                                    <td className="p-4 text-gray-300">{order.customer}</td>
                                    <td className="p-4 text-gray-400 text-sm">{order.date}</td>
                                    <td className="p-4">
                                        <StatusBadge status={order.status} />
                                    </td>
                                    <td className="p-4 font-medium text-white">{order.total}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
