'use client';

import Link from 'next/link';
import { DollarSign, Package, Users } from 'lucide-react';
import { useEffect, useState } from 'react';
import useSWR from 'swr';
import { fetcher } from '@/services/productService';
import { adminService, AdminDashboardData, AdminOrder, MelhorEnvioStatus } from '@/services/adminService';
import { UNPAID_ORDER_STATUSES, isUnpaidOrder, orderStatusLabel, orderStatusStyle } from '@/utils/orderStatus';
import { toast } from 'react-hot-toast';

const RECENT_ORDERS_LIMIT = 5;

// Só os pedidos recentes e pagos: o backend aplica o LIMIT. O filtro/ordenação abaixo continua no cliente
// como proteção caso a API ignore os parâmetros (nesse caso ela devolve a lista completa, como antes).
const RECENT_ORDERS_KEY = `/orders/all?${[
    `limit=${RECENT_ORDERS_LIMIT}`,
    ...UNPAID_ORDER_STATUSES.map((status) => `excludeStatus=${status}`),
].join('&')}`;

// Horários do backend vêm em UTC sem fuso; sem o "Z" o navegador os leria como horário local.
const formatUtcDateTime = (value?: string | null) =>
    value
        ? new Date(`${value}Z`).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
        : null;

const MELHOR_ENVIO_STATE_UI: Record<MelhorEnvioStatus['state'], { label: string; style: string; hint: string }> = {
    CONNECTED: {
        label: 'Conectado',
        style: 'bg-green-500/15 text-green-400',
        hint: 'O token é renovado automaticamente antes de vencer.',
    },
    RENEWAL_FAILING: {
        label: 'Renovação falhando',
        style: 'bg-yellow-500/15 text-yellow-400',
        hint: 'A última renovação automática falhou. O sistema tenta de novo sozinho; se persistir, reconecte.',
    },
    REAUTH_REQUIRED: {
        label: 'Reconexão necessária',
        style: 'bg-red-500/15 text-red-400',
        hint: 'O Melhor Envio recusou a renovação. Clique em Conectar Melhor Envio; sem isso o frete fica indisponível.',
    },
    NOT_CONNECTED: {
        label: 'Não conectado',
        style: 'bg-red-500/15 text-red-400',
        hint: 'Conecte a conta para calcular fretes reais.',
    },
    DISABLED: {
        label: 'Integração desativada',
        style: 'bg-gray-500/15 text-gray-300',
        hint: 'O backend não está configurado para usar o Melhor Envio.',
    },
};

const formatCurrency = (value?: number) =>
    new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value ?? 0);

export default function AdminDashboard() {
    const [dashboardData, setDashboardData] = useState<AdminDashboardData | null>(null);
    const [loading, setLoading] = useState(true);
    const [connectingMelhorEnvio, setConnectingMelhorEnvio] = useState(false);
    const { data: orders, error: ordersError } = useSWR<AdminOrder[]>(RECENT_ORDERS_KEY, fetcher);
    const { data: melhorEnvioStatus, error: melhorEnvioStatusError, mutate: refreshMelhorEnvioStatus } =
        useSWR<MelhorEnvioStatus>('/integrations/melhor-envio/status', fetcher);

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
            refreshMelhorEnvioStatus();
            toast.success(message);
        } else {
            toast.error(message);
        }
        window.history.replaceState({}, '', '/admin');
    }, [refreshMelhorEnvioStatus]);

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
        { title: 'Receita (pedidos pagos)', value: `R$ ${dashboardData.totalRevenue.toFixed(2).replace('.', ',')}`, icon: DollarSign, trend: '', isPositive: true },
        { title: 'Total de Produtos', value: dashboardData.totalProducts.toString(), icon: Package, trend: '', isPositive: true },
        { title: 'Total Usuários', value: dashboardData.totalUsers.toString(), icon: Users, trend: '', isPositive: true },
    ] : [
        { title: 'Receita (pedidos pagos)', value: 'R$ 0,00', icon: DollarSign, trend: '', isPositive: true },
        { title: 'Total de Produtos', value: '0', icon: Package, trend: '', isPositive: true },
        { title: 'Total Usuários', value: '0', icon: Users, trend: '', isPositive: true },
    ];

    const recentOrders = (orders ?? [])
        .filter((order) => !isUnpaidOrder(order.status))
        .sort((a, b) => new Date(b.createdAt ?? 0).getTime() - new Date(a.createdAt ?? 0).getTime())
        .slice(0, RECENT_ORDERS_LIMIT);

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
                        <div className="mt-3 space-y-1 text-sm" role="status">
                            {melhorEnvioStatus ? (
                                <>
                                    <span className={`inline-block rounded-full px-3 py-1 text-xs font-bold ${MELHOR_ENVIO_STATE_UI[melhorEnvioStatus.state].style}`}>
                                        Melhor Envio: {MELHOR_ENVIO_STATE_UI[melhorEnvioStatus.state].label}
                                        {melhorEnvioStatus.environment ? ` (${melhorEnvioStatus.environment})` : ''}
                                    </span>
                                    <p className="text-gray-400">{MELHOR_ENVIO_STATE_UI[melhorEnvioStatus.state].hint}</p>
                                    {melhorEnvioStatus.accessTokenExpiresAt && (
                                        <p className="text-gray-500">
                                            Token atual válido até {formatUtcDateTime(melhorEnvioStatus.accessTokenExpiresAt)}.
                                        </p>
                                    )}
                                    {melhorEnvioStatus.persistencePending && (
                                        <p className="text-yellow-400">
                                            O Melhor Envio emitiu um token novo que ainda não foi gravado no banco. O sistema continua tentando; se persistir, verifique o banco de dados.
                                        </p>
                                    )}
                                    {melhorEnvioStatus.lastRefreshFailure && (
                                        <p className="text-gray-500">
                                            Última falha de renovação: {formatUtcDateTime(melhorEnvioStatus.lastRefreshFailure.at)}
                                            {melhorEnvioStatus.lastRefreshFailure.httpStatus ? ` (HTTP ${melhorEnvioStatus.lastRefreshFailure.httpStatus})` : ''}.
                                        </p>
                                    )}
                                </>
                            ) : melhorEnvioStatusError ? (
                                <p className="text-yellow-400">Não foi possível consultar o estado da conexão.</p>
                            ) : (
                                <p className="text-gray-500">Consultando estado da conexão...</p>
                            )}
                        </div>
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
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
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
                    <Link href="/admin/pedidos" className="text-sm text-brand-secondary hover:text-red-400 font-medium transition-colors">
                        Ver todos
                    </Link>
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
                            {!orders && !ordersError ? (
                                <tr>
                                    <td colSpan={5} className="p-6 text-center text-gray-500">Carregando pedidos...</td>
                                </tr>
                            ) : ordersError ? (
                                <tr>
                                    <td colSpan={5} className="p-6 text-center text-gray-500">Não foi possível carregar os pedidos.</td>
                                </tr>
                            ) : recentOrders.length === 0 ? (
                                <tr>
                                    <td colSpan={5} className="p-6 text-center text-gray-500">Nenhum pedido realizado ainda.</td>
                                </tr>
                            ) : (
                                recentOrders.map((order) => (
                                    <tr key={order.id} className="hover:bg-white/[0.02] transition-colors">
                                        <td className="p-4 font-mono text-xs text-gray-400">{order.id.slice(0, 8)}...</td>
                                        <td className="p-4 text-gray-300">{order.user?.name || order.user?.email || 'Cliente'}</td>
                                        <td className="p-4 text-gray-400 text-sm whitespace-nowrap">
                                            {order.createdAt ? new Date(order.createdAt).toLocaleDateString('pt-BR') : '-'}
                                        </td>
                                        <td className="p-4">
                                            <span className={`px-2 py-1 text-xs font-semibold rounded-full border whitespace-nowrap ${orderStatusStyle(order.status)}`}>
                                                {orderStatusLabel(order.status)}
                                            </span>
                                        </td>
                                        <td className="p-4 font-medium text-white whitespace-nowrap">{formatCurrency(order.total)}</td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
