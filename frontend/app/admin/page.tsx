'use client';

import Link from 'next/link';
import { DollarSign, Package, Users, ShoppingBag } from 'lucide-react';
import { useEffect, useState } from 'react';
import useSWR from 'swr';
import { fetcher } from '@/services/productService';
import { adminService, AdminDashboardData, AdminOrder, MelhorEnvioStatus } from '@/services/adminService';
import { UNPAID_ORDER_STATUSES, isUnpaidOrder } from '@/utils/orderStatus';
import { toast } from 'react-hot-toast';
import { Badge, Button, Card, CardHeader, EmptyState, ErrorState, PageHeader, Skeleton, TableSkeletonRows, type BadgeTone } from './_components/ui';
import { OrderStatusBadge } from './_components/OrderStatusBadge';
import { Table, TableWrap, Tbody, Td, Th, Thead, rowClass } from './_components/table';

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

const MELHOR_ENVIO_STATE_UI: Record<MelhorEnvioStatus['state'], { label: string; tone: BadgeTone; hint: string }> = {
    CONNECTED: {
        label: 'Conectado',
        tone: 'success',
        hint: 'O token é renovado automaticamente antes de vencer.',
    },
    RENEWAL_FAILING: {
        label: 'Renovação falhando',
        tone: 'warning',
        hint: 'A última renovação automática falhou. O sistema tenta de novo sozinho; se persistir, reconecte.',
    },
    REAUTH_REQUIRED: {
        label: 'Reconexão necessária',
        tone: 'danger',
        hint: 'O Melhor Envio recusou a renovação. Clique em Conectar Melhor Envio; sem isso o frete fica indisponível.',
    },
    NOT_CONNECTED: {
        label: 'Não conectado',
        tone: 'danger',
        hint: 'Conecte a conta para calcular fretes reais.',
    },
    DISABLED: {
        label: 'Integração desativada',
        tone: 'neutral',
        hint: 'O backend não está configurado para usar o Melhor Envio.',
    },
};

const formatCurrency = (value?: number) =>
    new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value ?? 0);

export default function AdminDashboard() {
    const [dashboardData, setDashboardData] = useState<AdminDashboardData | null>(null);
    const [loading, setLoading] = useState(true);
    const [dashboardError, setDashboardError] = useState(false);
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
                setDashboardError(true);
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
        { title: 'Receita (pedidos pagos)', value: `R$ ${dashboardData.totalRevenue.toFixed(2).replace('.', ',')}`, icon: DollarSign },
        { title: 'Total de Produtos', value: dashboardData.totalProducts.toString(), icon: Package },
        { title: 'Total Usuários', value: dashboardData.totalUsers.toString(), icon: Users },
    ] : [
        { title: 'Receita (pedidos pagos)', value: 'R$ 0,00', icon: DollarSign },
        { title: 'Total de Produtos', value: '0', icon: Package },
        { title: 'Total Usuários', value: '0', icon: Users },
    ];

    const recentOrders = (orders ?? [])
        .filter((order) => !isUnpaidOrder(order.status))
        .sort((a, b) => new Date(b.createdAt ?? 0).getTime() - new Date(a.createdAt ?? 0).getTime())
        .slice(0, RECENT_ORDERS_LIMIT);

    const melhorEnvioUi = melhorEnvioStatus ? MELHOR_ENVIO_STATE_UI[melhorEnvioStatus.state] : null;

    return (
        <div className="mx-auto max-w-6xl space-y-6">
            <PageHeader title="Dashboard" description="Bem-vindo ao painel de controle da DN Store." />

            {/* Stat Cards */}
            <section aria-label="Indicadores" className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                {stats.map((stat) => {
                    const Icon = stat.icon;
                    return (
                        <Card key={stat.title} className="p-5">
                            <div className="flex items-center gap-4">
                                <div className="grid h-11 w-11 shrink-0 place-items-center rounded-adm-control bg-adm-accent/15 text-adm-accent-ink">
                                    <Icon className="h-5 w-5" aria-hidden="true" />
                                </div>
                                <div className="min-w-0">
                                    <h2 className="text-sm font-medium text-adm-ink-muted">{stat.title}</h2>
                                    {loading ? (
                                        <Skeleton className="mt-2 h-7 w-28" />
                                    ) : (
                                        <p className="mt-0.5 truncate text-2xl font-semibold tabular-nums text-adm-ink">
                                            {dashboardError ? '—' : stat.value}
                                        </p>
                                    )}
                                </div>
                            </div>
                        </Card>
                    );
                })}
            </section>

            <Card as="section" aria-label="Integrações">
                <CardHeader
                    title="Integrações"
                    description="Conecte a conta administrativa do Melhor Envio para calcular fretes reais."
                    actions={
                        <Button variant="primary" onClick={connectMelhorEnvio} loading={connectingMelhorEnvio}>
                            {connectingMelhorEnvio ? 'Iniciando conexão...' : 'Conectar Melhor Envio'}
                        </Button>
                    }
                />
                <div className="space-y-2 px-5 py-4 text-sm" role="status">
                    {melhorEnvioStatus && melhorEnvioUi ? (
                        <>
                            <Badge tone={melhorEnvioUi.tone} dot>
                                Melhor Envio: {melhorEnvioUi.label}
                                {melhorEnvioStatus.environment ? ` (${melhorEnvioStatus.environment})` : ''}
                            </Badge>
                            <p className="text-adm-ink-muted">{melhorEnvioUi.hint}</p>
                            {melhorEnvioStatus.accessTokenExpiresAt && (
                                <p className="text-adm-ink-subtle">
                                    Token atual válido até {formatUtcDateTime(melhorEnvioStatus.accessTokenExpiresAt)}.
                                </p>
                            )}
                            {melhorEnvioStatus.persistencePending && (
                                <p className="text-adm-warning">
                                    O Melhor Envio emitiu um token novo que ainda não foi gravado no banco. O sistema continua tentando; se persistir, verifique o banco de dados.
                                </p>
                            )}
                            {melhorEnvioStatus.lastRefreshFailure && (
                                <p className="text-adm-ink-subtle">
                                    Última falha de renovação: {formatUtcDateTime(melhorEnvioStatus.lastRefreshFailure.at)}
                                    {melhorEnvioStatus.lastRefreshFailure.httpStatus ? ` (HTTP ${melhorEnvioStatus.lastRefreshFailure.httpStatus})` : ''}.
                                </p>
                            )}
                        </>
                    ) : melhorEnvioStatusError ? (
                        <p className="text-adm-warning">Não foi possível consultar o estado da conexão.</p>
                    ) : (
                        <>
                            <Skeleton className="h-6 w-56" />
                            <p className="text-adm-ink-subtle">Consultando estado da conexão...</p>
                        </>
                    )}
                </div>
            </Card>

            {/* Recent Orders Table */}
            <Card as="section" aria-label="Pedidos recentes" className="overflow-hidden">
                <CardHeader
                    title="Pedidos Recentes"
                    actions={
                        <Link href="/admin/pedidos" className="rounded text-sm font-medium text-adm-accent-ink transition-colors hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-adm-accent-ink">
                            Ver todos
                        </Link>
                    }
                />
                <TableWrap>
                    <Table>
                        <Thead>
                            <tr>
                                <Th>Pedido</Th>
                                <Th>Cliente</Th>
                                <Th>Data</Th>
                                <Th>Status</Th>
                                <Th className="text-right">Total</Th>
                            </tr>
                        </Thead>
                        <Tbody>
                            {!orders && !ordersError ? (
                                <TableSkeletonRows rows={RECENT_ORDERS_LIMIT} columns={5} />
                            ) : ordersError ? (
                                <tr>
                                    <td colSpan={5}><ErrorState title="Não foi possível carregar os pedidos." /></td>
                                </tr>
                            ) : recentOrders.length === 0 ? (
                                <tr>
                                    <td colSpan={5}><EmptyState icon={ShoppingBag} title="Nenhum pedido realizado ainda." /></td>
                                </tr>
                            ) : (
                                recentOrders.map((order) => (
                                    <tr key={order.id} className={rowClass()}>
                                        <Td className="font-mono text-xs text-adm-ink-muted">{order.id.slice(0, 8)}...</Td>
                                        <Td className="text-adm-ink-muted">{order.user?.name || order.user?.email || 'Cliente'}</Td>
                                        <Td className="whitespace-nowrap text-adm-ink-muted">
                                            {order.createdAt ? new Date(order.createdAt).toLocaleDateString('pt-BR') : '-'}
                                        </Td>
                                        <Td><OrderStatusBadge status={order.status} /></Td>
                                        <Td className="whitespace-nowrap text-right font-medium tabular-nums">{formatCurrency(order.total)}</Td>
                                    </tr>
                                ))
                            )}
                        </Tbody>
                    </Table>
                </TableWrap>
            </Card>
        </div>
    );
}
