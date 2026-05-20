'use client';

import { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { 
    LayoutDashboard, 
    Package, 
    ShoppingCart, 
    Users, 
    Tags, 
    Menu, 
    X,
    LogOut
} from 'lucide-react';
import { cn } from '../components/ui/Button';

const adminLinks = [
    { name: 'Dashboard', href: '/admin', icon: LayoutDashboard },
    { name: 'Produtos', href: '/admin/produtos', icon: Package },
    { name: 'Pedidos', href: '/admin/pedidos', icon: ShoppingCart },
    { name: 'Categorias', href: '/admin/categorias', icon: Tags },
    { name: 'Usuários', href: '/admin/usuarios', icon: Users },
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
    const [isSidebarOpen, setIsSidebarOpen] = useState(false);
    const pathname = usePathname();

    return (
        <div className="min-h-screen bg-background text-foreground flex">
            {/* Mobile Sidebar Overlay */}
            {isSidebarOpen && (
                <div 
                    className="fixed inset-0 bg-black/50 z-40 lg:hidden"
                    onClick={() => setIsSidebarOpen(false)}
                />
            )}

            {/* Sidebar */}
            <aside 
                className={cn(
                    "fixed top-0 left-0 z-50 h-screen w-64 bg-[#1A1B1D] border-r border-white/5 transform transition-transform duration-300 lg:translate-x-0 lg:static flex flex-col",
                    isSidebarOpen ? "translate-x-0" : "-translate-x-full"
                )}
            >
                <div className="p-6 flex items-center justify-between border-b border-white/5">
                    <Link href="/admin" className="flex items-center gap-2">
                        <span className="text-2xl font-bold text-white">DN<span className="text-brand-secondary">Admin</span></span>
                    </Link>
                    <button 
                        className="lg:hidden text-gray-400 hover:text-white"
                        onClick={() => setIsSidebarOpen(false)}
                    >
                        <X className="w-6 h-6" />
                    </button>
                </div>

                <nav className="flex-1 p-4 space-y-2 overflow-y-auto">
                    {adminLinks.map((link) => {
                        const Icon = link.icon;
                        const isActive = pathname === link.href || pathname.startsWith(`${link.href}/`);
                        
                        return (
                            <Link 
                                key={link.href} 
                                href={link.href}
                                onClick={() => setIsSidebarOpen(false)}
                                className={cn(
                                    "flex items-center gap-3 px-4 py-3 rounded-lg transition-colors font-medium",
                                    isActive 
                                        ? "bg-brand-secondary/20 text-brand-secondary" 
                                        : "text-gray-400 hover:bg-[#2A2A2A] hover:text-white"
                                )}
                            >
                                <Icon className="w-5 h-5" />
                                {link.name}
                            </Link>
                        );
                    })}
                </nav>

                <div className="p-4 border-t border-white/5">
                    <Link 
                        href="/"
                        className="flex items-center gap-3 px-4 py-3 rounded-lg text-gray-400 hover:bg-[#2A2A2A] hover:text-white transition-colors font-medium"
                    >
                        <LogOut className="w-5 h-5" />
                        Sair (Voltar à Loja)
                    </Link>
                </div>
            </aside>

            {/* Main Content */}
            <div className="flex-1 flex flex-col min-h-screen overflow-hidden">
                {/* Topbar Mobile */}
                <header className="lg:hidden bg-[#1A1B1D] border-b border-white/5 p-4 flex items-center justify-between sticky top-0 z-30">
                    <div className="flex items-center gap-3">
                        <button 
                            className="text-gray-400 hover:text-white"
                            onClick={() => setIsSidebarOpen(true)}
                        >
                            <Menu className="w-6 h-6" />
                        </button>
                        <span className="text-lg font-bold text-white">DN<span className="text-brand-secondary">Admin</span></span>
                    </div>
                </header>

                {/* Page Content */}
                <main className="flex-1 overflow-x-hidden overflow-y-auto bg-background p-4 md:p-8">
                    {children}
                </main>
            </div>
        </div>
    );
}
