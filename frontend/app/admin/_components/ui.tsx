'use client';

import Link from 'next/link';
import type { ComponentProps, ElementType, ReactNode } from 'react';
import { AlertTriangle, Loader2 } from 'lucide-react';
import { cn } from '../../components/ui/Button';

/* ------------------------------------------------------------------ Button */

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'warning' | 'caution';
export type ButtonSize = 'sm' | 'md' | 'lg';

const buttonBase =
    'inline-flex items-center justify-center gap-2 rounded-adm-control font-medium whitespace-nowrap transition-colors ' +
    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-adm-accent-ink ' +
    'disabled:cursor-not-allowed disabled:opacity-50';

const buttonVariants: Record<ButtonVariant, string> = {
    primary: 'bg-adm-accent text-white hover:bg-adm-accent-hover',
    secondary: 'border border-adm-line-strong bg-adm-raised text-adm-ink hover:bg-adm-hover',
    ghost: 'text-adm-ink-muted hover:bg-adm-hover hover:text-adm-ink',
    danger: 'border border-adm-danger/40 text-adm-danger hover:bg-adm-danger/10',
    warning: 'border border-adm-warning/40 text-adm-warning hover:bg-adm-warning/10',
    caution: 'bg-adm-warning text-black hover:bg-adm-warning/90',
};

const buttonSizes: Record<ButtonSize, string> = {
    sm: 'min-h-9 px-3 text-xs',
    md: 'min-h-10 px-4 text-sm',
    lg: 'min-h-11 px-5 text-sm',
};

/** Classes de botão para elementos que não são <button> (ex.: <Link>). */
export const buttonClass = (variant: ButtonVariant = 'secondary', size: ButtonSize = 'md', className?: string) =>
    cn(buttonBase, buttonVariants[variant], buttonSizes[size], className);

type ButtonProps = ComponentProps<'button'> & {
    variant?: ButtonVariant;
    size?: ButtonSize;
    loading?: boolean;
};

export function Button({ variant = 'secondary', size = 'md', loading = false, className, children, disabled, type = 'button', ...rest }: ButtonProps) {
    return (
        <button type={type} disabled={disabled || loading} className={buttonClass(variant, size, className)} {...rest}>
            {loading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
            {children}
        </button>
    );
}

/** Link com aparência de botão. */
export function LinkButton({ variant = 'secondary', size = 'md', className, ...rest }: ComponentProps<typeof Link> & { variant?: ButtonVariant; size?: ButtonSize }) {
    return <Link className={buttonClass(variant, size, className)} {...rest} />;
}

const iconTones = {
    default: 'text-adm-ink-muted hover:bg-adm-hover hover:text-adm-ink',
    danger: 'text-adm-ink-muted hover:bg-adm-danger/10 hover:text-adm-danger',
} as const;

const iconBase =
    'grid h-9 w-9 shrink-0 place-items-center rounded-adm-control transition-colors ' +
    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-adm-accent-ink disabled:opacity-50';

/** Botão só com ícone. `label` vira aria-label e tooltip (obrigatório para acessibilidade). */
export function IconButton({ label, tone = 'default', className, type = 'button', ...rest }: ComponentProps<'button'> & { label: string; tone?: keyof typeof iconTones }) {
    return <button type={type} aria-label={label} title={label} className={cn(iconBase, iconTones[tone], className)} {...rest} />;
}

/** Link só com ícone (ex.: editar). */
export function IconLink({ label, className, ...rest }: ComponentProps<typeof Link> & { label: string }) {
    return <Link aria-label={label} title={label} className={cn(iconBase, iconTones.default, className)} {...rest} />;
}

/* ------------------------------------------------------------------- Cards */

export function Card({ as, className, ...rest }: ComponentProps<'div'> & { as?: ElementType }) {
    const Component = as ?? 'div';
    return <Component className={cn('rounded-adm-card border border-adm-line bg-adm-surface shadow-adm-card', className)} {...rest} />;
}

export function CardHeader({ title, description, actions, className }: { title: ReactNode; description?: ReactNode; actions?: ReactNode; className?: string }) {
    return (
        <div className={cn('flex flex-wrap items-center justify-between gap-3 border-b border-adm-line px-5 py-4', className)}>
            <div className="min-w-0">
                <h2 className="text-base font-semibold text-adm-ink">{title}</h2>
                {description && <p className="mt-0.5 text-sm text-adm-ink-muted">{description}</p>}
            </div>
            {actions && <div className="flex items-center gap-2">{actions}</div>}
        </div>
    );
}

export function PageHeader({ eyebrow, title, description, actions, leading }: { eyebrow?: string; title: ReactNode; description?: ReactNode; actions?: ReactNode; leading?: ReactNode }) {
    return (
        <header className="flex flex-col gap-4 border-b border-adm-line pb-5 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex min-w-0 items-center gap-3">
                {leading}
                <div className="min-w-0">
                    {eyebrow && <p className="mb-1 text-xs font-semibold uppercase tracking-wider text-adm-accent-ink">{eyebrow}</p>}
                    <h1 className="truncate text-2xl font-semibold text-adm-ink">{title}</h1>
                    {description && <p className="mt-1 text-sm text-adm-ink-muted">{description}</p>}
                </div>
            </div>
            {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
        </header>
    );
}

/* ------------------------------------------------------------------ Badges */

export type BadgeTone = 'neutral' | 'success' | 'warning' | 'danger' | 'info' | 'violet' | 'accent';

const badgeTones: Record<BadgeTone, string> = {
    neutral: 'border-adm-line-strong bg-adm-raised text-adm-ink-muted',
    success: 'border-adm-success/25 bg-adm-success/10 text-adm-success',
    warning: 'border-adm-warning/25 bg-adm-warning/10 text-adm-warning',
    danger: 'border-adm-danger/25 bg-adm-danger/10 text-adm-danger',
    info: 'border-adm-info/25 bg-adm-info/10 text-adm-info',
    violet: 'border-adm-violet/25 bg-adm-violet/10 text-adm-violet',
    accent: 'border-adm-accent-ink/25 bg-adm-accent/15 text-adm-accent-ink',
};

const dotTones: Record<BadgeTone, string> = {
    neutral: 'bg-adm-ink-subtle',
    success: 'bg-adm-success',
    warning: 'bg-adm-warning',
    danger: 'bg-adm-danger',
    info: 'bg-adm-info',
    violet: 'bg-adm-violet',
    accent: 'bg-adm-accent-ink',
};

export function Badge({ tone = 'neutral', dot = false, className, children }: { tone?: BadgeTone; dot?: boolean; className?: string; children: ReactNode }) {
    return (
        <span className={cn('inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs font-medium', badgeTones[tone], className)}>
            {dot && <span className={cn('h-1.5 w-1.5 rounded-full', dotTones[tone])} aria-hidden="true" />}
            {children}
        </span>
    );
}

/* ---------------------------------------------------------- Loading / empty */

export function Skeleton({ className }: { className?: string }) {
    return <div aria-hidden="true" className={cn('animate-pulse rounded bg-adm-raised', className)} />;
}

/** Linhas de esqueleto para dentro de um <tbody>. */
export function TableSkeletonRows({ rows = 6, columns }: { rows?: number; columns: number }) {
    return (
        <>
            {Array.from({ length: rows }, (_, row) => (
                <tr key={row} aria-hidden="true">
                    {Array.from({ length: columns }, (_, column) => (
                        <td key={column} className="px-4 py-3.5">
                            <Skeleton className={cn('h-4', column === 0 ? 'w-24' : 'w-full max-w-40')} />
                        </td>
                    ))}
                </tr>
            ))}
        </>
    );
}

export function EmptyState({ icon: Icon, title, description, action, className }: { icon?: ElementType; title: string; description?: ReactNode; action?: ReactNode; className?: string }) {
    return (
        <div className={cn('flex flex-col items-center justify-center px-4 py-12 text-center', className)}>
            {Icon && <Icon className="mb-3 h-8 w-8 text-adm-ink-subtle" aria-hidden="true" />}
            <p className="text-sm font-medium text-adm-ink">{title}</p>
            {description && <p className="mt-1 max-w-sm text-sm text-adm-ink-muted">{description}</p>}
            {action && <div className="mt-4">{action}</div>}
        </div>
    );
}

export function ErrorState({ title = 'Não foi possível carregar', description, onRetry, className }: { title?: string; description?: ReactNode; onRetry?: () => void; className?: string }) {
    return (
        <div role="alert" className={cn('flex flex-col items-center justify-center px-4 py-12 text-center', className)}>
            <AlertTriangle className="mb-3 h-8 w-8 text-adm-warning" aria-hidden="true" />
            <p className="text-sm font-medium text-adm-ink">{title}</p>
            {description && <p className="mt-1 max-w-sm text-sm text-adm-ink-muted">{description}</p>}
            {onRetry && <Button size="sm" className="mt-4" onClick={onRetry}>Tentar novamente</Button>}
        </div>
    );
}
