'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { PageHeader } from '../_components/ui';
import { cn } from '../../components/ui/Button';

const sections = [
    { href: '/admin/estoque', label: 'Log', exact: true },
    { href: '/admin/estoque/notas', label: 'Notas de entrada' },
    { href: '/admin/estoque/ajuste', label: 'Ajuste manual' },
    { href: '/admin/estoque/pedidos', label: 'Pedidos' },
    { href: '/admin/estoque/reconciliacao', label: 'Reconciliação' },
];

export default function StockLayout({ children }: { children: React.ReactNode }) {
    const pathname = usePathname();

    return (
        <div className="mx-auto max-w-6xl space-y-6">
            <PageHeader
                eyebrow="Operação"
                title="Estoque"
                description="Entradas por nota de fornecedor, vendas, ajustes e o histórico completo de cada variação."
            />
            <nav aria-label="Seções do estoque" className="-mx-1 overflow-x-auto px-1">
                <ul className="flex min-w-max gap-1 border-b border-adm-line">
                    {sections.map(section => {
                        const active = section.exact ? pathname === section.href : pathname.startsWith(section.href);
                        return (
                            <li key={section.href}>
                                <Link
                                    href={section.href}
                                    aria-current={active ? 'page' : undefined}
                                    className={cn(
                                        '-mb-px inline-flex min-h-11 items-center border-b-2 px-4 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-adm-accent-ink',
                                        active
                                            ? 'border-adm-accent-ink text-adm-ink'
                                            : 'border-transparent text-adm-ink-muted hover:text-adm-ink',
                                    )}
                                >
                                    {section.label}
                                </Link>
                            </li>
                        );
                    })}
                </ul>
            </nav>
            {children}
        </div>
    );
}
