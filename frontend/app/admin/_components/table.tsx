import type { ComponentProps } from 'react';
import { cn } from '../../components/ui/Button';

/** Tabela do admin: wrapper com rolagem horizontal + peças com a mesma densidade em todas as telas. */
export function TableWrap({ className, ...rest }: ComponentProps<'div'>) {
    return <div className={cn('overflow-x-auto', className)} {...rest} />;
}

export function Table({ className, ...rest }: ComponentProps<'table'>) {
    return <table className={cn('w-full border-collapse text-left text-sm', className)} {...rest} />;
}

export function Thead({ className, ...rest }: ComponentProps<'thead'>) {
    return <thead className={cn('bg-adm-sunken text-xs uppercase tracking-wide text-adm-ink-subtle', className)} {...rest} />;
}

export function Tbody({ className, ...rest }: ComponentProps<'tbody'>) {
    return <tbody className={cn('divide-y divide-adm-line', className)} {...rest} />;
}

export function Th({ className, ...rest }: ComponentProps<'th'>) {
    return <th scope="col" className={cn('whitespace-nowrap px-4 py-3 font-medium', className)} {...rest} />;
}

export function Td({ className, ...rest }: ComponentProps<'td'>) {
    return <td className={cn('px-4 py-3 align-middle text-adm-ink', className)} {...rest} />;
}

/** Linha de dados com hover; `selected` marca a linha ativa. */
export const rowClass = (selected = false) =>
    cn('transition-colors hover:bg-adm-hover/60', selected && 'bg-adm-hover');
