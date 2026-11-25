'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import {
    LayoutDashboard,
    Package,
    ShoppingCart,
    Users,
    Tags,
    CalendarDays,
    Menu, 
    X,
    LogOut,
    Warehouse
} from 'lucide-react';
import { cn } from '../components/ui/Button';
import { useAuth } from '../context/AuthContext';
import { toast } from 'react-hot-toast';
import { SWRConfig } from 'swr';
import { ConfirmProvider } from './_components/confirm';

// Padrões de cache do admin. Sem isto o SWR refaz toda listagem (inclusive /orders/all, que é pesada)
// a cada foco de janela. Telas que precisam de dado fresco (pedidos) sobrescrevem revalidateOnFocus.
const adminSwrConfig = {
    revalidateOnFocus: false,
    dedupingInterval: 10_000,
};

const adminLinks = [
    { name: 'Dashboard', href: '/admin', icon: LayoutDashboard },
    { name: 'Produtos', href: '/admin/produtos', icon: Package },
    { name: 'Estoque', href: '/admin/estoque', icon: Warehouse },
    { name: 'Pedidos', href: '/admin/pedidos', icon: ShoppingCart },
    { name: 'Categorias', href: '/admin/categorias', icon: Tags },
    { name: 'Eventos', href: '/admin/eventos', icon: CalendarDays },
    { name: 'Usuários', href: '/admin/usuarios', icon: Users },
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
    const [isSidebarOpen, setIsSidebarOpen] = useState(false);
    const pathname = usePathname();
    const router = useRouter();
    const { user, loading } = useAuth();

    useEffect(() => {
        if (!loading) {
            if (!user) {
                toast.error("Você precisa estar logado para acessar esta página.");
                router.push(`/auth?next=${encodeURIComponent(pathname)}`);
            } else if (user.role !== 'ADMIN') {
                toast.error("Acesso negado. Apenas administradores podem acessar esta área.");
                router.push('/');
            }
        }
    }, [user, loading, router, pathname]);

    useEffect(() => {
        if (!isSidebarOpen) return;
        const closeOnEscape = (event: KeyboardEvent) => {
            if (event.key === 'Escape') setIsSidebarOpen(false);
        };
        document.addEventListener('keydown', closeOnEscape);
        return () => document.removeEventListener('keydown', closeOnEscape);
    }, [isSidebarOpen]);

    if (loading || !user || user.role !== 'ADMIN') {
        return (
            <div className="min-h-screen bg-adm-canvas flex items-center justify-center" role="status" aria-label="Carregando painel">
                <div className="w-8 h-8 border-4 border-adm-accent-ink border-t-transparent rounded-full animate-spin"></div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-adm-canvas text-adm-ink flex [color-scheme:dark]">
            <a
                href="#admin-content"
                className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[70] focus:rounded-adm-control focus:bg-adm-accent focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-white"
            >
                Pular para o conteúdo
            </a>

            {/* Mobile Sidebar Overlay */}
            {isSidebarOpen && (
                <div
                    className="fixed inset-0 bg-black/60 z-40 lg:hidden"
                    onClick={() => setIsSidebarOpen(false)}
                    aria-hidden="true"
                />
            )}

            {/* Sidebar */}
            <aside
                aria-label="Navegação do painel"
                className={cn(
                    "fixed top-0 left-0 z-50 h-dvh w-64 shrink-0 bg-adm-sunken border-r border-adm-line flex flex-col transition-[transform,visibility] duration-300 lg:visible lg:translate-x-0 lg:sticky lg:self-start",
                    isSidebarOpen ? "visible translate-x-0" : "invisible -translate-x-full"
                )}
            >
                <div className="flex h-16 items-center justify-between border-b border-adm-line px-5">
                    <Link href="/admin" className="flex items-center gap-2 rounded-adm-control focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-adm-accent-ink">
                        <span style={{ fontFamily: 'DN'  }} className="text-3xl text-outline text-white">DN<span style={{ fontFamily: 'DN'  }} className="text-brand-secondary text-outline"> Admin</span></span>
                    </Link>
                    <button
                        type="button"
                        aria-label="Fechar menu"
                        className="grid h-9 w-9 place-items-center rounded-adm-control text-adm-ink-muted hover:bg-adm-hover hover:text-adm-ink lg:hidden"
                        onClick={() => setIsSidebarOpen(false)}
                    >
                        <X className="w-5 h-5" aria-hidden="true" />
                    </button>
                </div>

                <nav className="flex-1 space-y-1 overflow-y-auto p-3">
                    {adminLinks.map((link) => {
                        const Icon = link.icon;
                        const isActive = link.href === '/admin'
                            ? pathname === link.href
                            : pathname === link.href || pathname.startsWith(`${link.href}/`);

                        return (
                            <Link
                                key={link.href}
                                href={link.href}
                                onClick={() => setIsSidebarOpen(false)}
                                aria-current={isActive ? 'page' : undefined}
                                className={cn(
                                    "flex items-center gap-3 rounded-adm-control px-3 py-2.5 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-adm-accent-ink",
                                    isActive
                                        ? "bg-adm-accent/15 text-adm-accent-ink"
                                        : "text-adm-ink-muted hover:bg-adm-hover hover:text-adm-ink"
                                )}
                            >
                                <Icon className="w-5 h-5" aria-hidden="true" />
                                {link.name}
                            </Link>
                        );
                    })}
                </nav>

                <div className="border-t border-adm-line p-3">
                    <Link
                        href="/"
                        className="flex items-center gap-3 rounded-adm-control px-3 py-2.5 text-sm font-medium text-adm-ink-muted transition-colors hover:bg-adm-hover hover:text-adm-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-adm-accent-ink"
                    >
                        <LogOut className="w-5 h-5" aria-hidden="true" />
                        Sair (Voltar à Loja)
                    </Link>
                </div>
            </aside>

            {/* Main Content */}
            <div className="flex-1 min-w-0 flex flex-col min-h-screen">
                {/* Topbar Mobile */}
                <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-adm-line bg-adm-sunken px-4 lg:hidden">
                    <button
                        type="button"
                        aria-label="Abrir menu"
                        aria-expanded={isSidebarOpen}
                        className="grid h-9 w-9 place-items-center rounded-adm-control text-adm-ink-muted hover:bg-adm-hover hover:text-adm-ink"
                        onClick={() => setIsSidebarOpen(true)}
                    >
                        <Menu className="w-6 h-6" aria-hidden="true" />
                    </button>
                    <span className="text-lg font-bold text-white">DN<span className="text-adm-accent-ink">Admin</span></span>
                </header>

                {/* Page Content */}
                <div id="admin-content" tabIndex={-1} className="flex-1 overflow-x-hidden overflow-y-auto bg-adm-canvas p-4 outline-none md:p-8">
                    <SWRConfig value={adminSwrConfig}>
                        <ConfirmProvider>{children}</ConfirmProvider>
                    </SWRConfig>
                </div>
            </div>
        </div>
    );
}
