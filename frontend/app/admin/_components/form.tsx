'use client';

import { forwardRef, type ComponentProps, type ReactNode } from 'react';
import { cn } from '../../components/ui/Button';

/** Classes compartilhadas por input, select e textarea do admin. */
export const controlClass =
    'min-h-10 w-full rounded-adm-control border border-adm-line-strong bg-adm-sunken px-3 py-2 text-sm text-adm-ink ' +
    'placeholder:text-adm-ink-subtle transition-colors focus:border-adm-accent-ink focus:outline-none ' +
    'focus:ring-2 focus:ring-adm-accent-ink/30 disabled:cursor-not-allowed disabled:opacity-50';

export const Input = forwardRef<HTMLInputElement, ComponentProps<'input'>>(function Input({ className, ...rest }, ref) {
    return <input ref={ref} className={cn(controlClass, className)} {...rest} />;
});

export const Select = forwardRef<HTMLSelectElement, ComponentProps<'select'>>(function Select({ className, ...rest }, ref) {
    return <select ref={ref} className={cn(controlClass, className)} {...rest} />;
});

export const Textarea = forwardRef<HTMLTextAreaElement, ComponentProps<'textarea'>>(function Textarea({ className, ...rest }, ref) {
    return <textarea ref={ref} className={cn(controlClass, 'resize-y', className)} {...rest} />;
});

/**
 * Rótulo + controle. O controle fica dentro do <label>, então a associação é implícita
 * (clicar no texto foca o campo e leitores de tela anunciam o rótulo).
 */
export function Field({ label, hint, className, children }: { label: ReactNode; hint?: ReactNode; className?: string; children: ReactNode }) {
    return (
        <label className={cn('block space-y-1.5', className)}>
            <span className="block text-xs font-medium text-adm-ink-muted">{label}</span>
            {children}
            {hint && <span className="block text-xs text-adm-ink-subtle">{hint}</span>}
        </label>
    );
}

export function Checkbox({ children, className, ...rest }: Omit<ComponentProps<'input'>, 'type'> & { children: ReactNode }) {
    return (
        <label className={cn('inline-flex min-h-8 cursor-pointer items-center gap-2 text-sm text-adm-ink', className)}>
            <input type="checkbox" className="h-4 w-4 accent-adm-accent" {...rest} />
            {children}
        </label>
    );
}
