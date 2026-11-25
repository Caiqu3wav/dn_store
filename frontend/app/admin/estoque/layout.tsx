'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';
import { cn } from '../../components/ui/Button';

const tabs = [
    { name: 'Movimentações', href: '/admin/estoque' },
    { name: 'Notas de entrada', href: '/admin/estoque/entradas' },
    { name: 'Pedidos que baixaram estoque', href: '/admin/estoque/pedidos' },
];

export default function StockLayout({ children }: { children: ReactNode }) {
    const pathname = usePathname();
    return (
        <div className="space-y-6">
            <nav aria-label="Seções de estoque" className="-mx-1 flex gap-1 overflow-x-auto border-b border-adm-line px-1">
                {tabs.map(tab => {
                    const active = tab.href === '/admin/estoque' ? pathname === tab.href : pathname.startsWith(tab.href);
                    return (
                        <Link
                            key={tab.href}
                            href={tab.href}
                            aria-current={active ? 'page' : undefined}
                            className={cn(
                                '-mb-px whitespace-nowrap border-b-2 px-3 py-2.5 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-adm-accent-ink',
                                active ? 'border-adm-accent-ink text-adm-ink' : 'border-transparent text-adm-ink-muted hover:text-adm-ink',
                            )}
                        >
                            {tab.name}
                        </Link>
                    );
                })}
            </nav>
            {children}
        </div>
    );
}
