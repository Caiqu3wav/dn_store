'use client';

import { DollarSign, ShoppingBag, Package, Users } from 'lucide-react';
import { useEffect, useState } from 'react';
import { adminService, AdminDashboardData } from '@/services/adminService';
import { toast } from 'react-hot-toast';

export default function AdminDashboard() {
    const [dashboardData, setDashboardData] = useState<AdminDashboardData | null>(null);
    const [loading, setLoading] = useState(true);
    const [connectingMelhorEnvio, setConnectingMelhorEnvio] = useState(false);

    useEffect(() => {
        const searchParams = new URLSearchParams(window.location.search);
        const integration = searchParams.get('integration');
        const status = searchParams.get('status');
        if (integration !== 'melhor-envio' || !status) {
            return;
        }

        const message = status === 'connected'
            ? 'Melhor Envio conectado com sucesso.'
            : status === 'denied'
                ? 'A autorização do Melhor Envio foi cancelada.'
                : 'Não foi possível conectar o Melhor Envio.';
        if (status === 'connected') {
            toast.success(message);
        } else {
            toast.error(message);
        }
        window.history.replaceState({}, '', '/admin');
    }, []);

    useEffect(() => {
        const loadDashboard = async () => {
            try {
                const data = await adminService.getDashboardData();
                setDashboardData(data);
            } catch (error) {
                console.error("Erro ao carregar dashboard:", error);
                toast.error("Erro ao carregar dados do dashboard.");
            } finally {
                setLoading(false);
            }
        };
        loadDashboard();
    }, []);

    const connectMelhorEnvio = async () => {
        if (connectingMelhorEnvio) {
            return;
        }
        setConnectingMelhorEnvio(true);
        try {
            const authorizationUrl = await adminService.getMelhorEnvioAuthorizationUrl();
            window.location.assign(authorizationUrl);
        } catch {
            toast.error('Não foi possível iniciar a conexão com o Melhor Envio.');
            setConnectingMelhorEnvio(false);
        }
    };

    const stats = dashboardData ? [
        { title: 'Receita Total', value: `R$ ${dashboardData.totalRevenue.toFixed(2).replace('.', ',')}`, icon: DollarSign, trend: '', isPositive: true },
        { title: 'Total de Pedidos', value: dashboardData.totalOrders.toString(), icon: ShoppingBag, trend: '', isPositive: true },
        { title: 'Total de Produtos', value: dashboardData.totalProducts.toString(), icon: Package, trend: '', isPositive: true },
        { title: 'Total Usuários', value: dashboardData.totalUsers.toString(), icon: Users, trend: '', isPositive: true },
    ] : [
        { title: 'Receita Total', value: 'R$ 0,00', icon: DollarSign, trend: '', isPositive: true },
        { title: 'Total de Pedidos', value: '0', icon: ShoppingBag, trend: '', isPositive: true },
        { title: 'Total de Produtos', value: '0', icon: Package, trend: '', isPositive: true },
        { title: 'Total Usuários', value: '0', icon: Users, trend: '', isPositive: true },
    ];

    const recentOrders: { id: string; customer: string; date: string; status: string; total: string }[] = [];

    return (
        <div className="space-y-8 text-white">
            <div>
                <h1 className="text-3xl font-bold">Dashboard</h1>
                <p className="text-gray-400 mt-1">Bem-vindo ao painel de controle da DN Store.</p>
            </div>

            <section className="rounded-xl border border-white/10 bg-[#2A2A2A] p-6">
                <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                    <div>
                        <h2 className="text-lg font-bold">Integrações</h2>
                        <p className="mt-1 text-sm text-gray-400">
                            Conecte a conta administrativa do Melhor Envio para calcular fretes reais.
                        </p>
                    </div>
                    <button
                        type="button"
                        onClick={connectMelhorEnvio}
                        disabled={connectingMelhorEnvio}
                        className="rounded-lg bg-brand-secondary px-5 py-3 font-bold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                        {connectingMelhorEnvio ? 'Iniciando conexão...' : 'Conectar Melhor Envio'}
                    </button>
                </div>
            </section>

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
                            <p className="text-3xl font-bold mt-1 text-white">
                                {loading ? '...' : stat.value}
                            </p>
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
                                        <span className={`px-2 py-1 text-xs font-semibold rounded-full border ${
                                            order.status === 'delivered' ? 'bg-green-500/10 text-green-500 border-green-500/20' : 'bg-yellow-500/10 text-yellow-500 border-yellow-500/20'
                                        }`}>
                                            {order.status}
                                        </span>
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
