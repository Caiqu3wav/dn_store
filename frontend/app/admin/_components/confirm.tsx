'use client';

import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { Modal } from './Modal';
import { Button } from './ui';

export type ConfirmOptions = {
    title: string;
    message: ReactNode;
    confirmLabel?: string;
    cancelLabel?: string;
};

type Confirm = (options: ConfirmOptions) => Promise<boolean>;

const ConfirmContext = createContext<Confirm | null>(null);

type Pending = { options: ConfirmOptions; resolve: (confirmed: boolean) => void };

/**
 * Substitui window.confirm: `if (!(await confirm({...}))) return;` mantém a semântica (a ação só
 * acontece depois de confirmar). Esc, fechar e "Cancelar" resolvem `false`; o foco inicial é
 * "Cancelar", então Enter não confirma uma exclusão por engano.
 */
export function ConfirmProvider({ children }: { children: ReactNode }) {
    const [pending, setPending] = useState<Pending | null>(null);

    const confirm = useCallback<Confirm>(
        (options) => new Promise<boolean>((resolve) => setPending({ options, resolve })),
        [],
    );

    const settle = (confirmed: boolean) => {
        pending?.resolve(confirmed);
        setPending(null);
    };

    return (
        <ConfirmContext.Provider value={confirm}>
            {children}
            <Modal
                open={pending !== null}
                onClose={() => settle(false)}
                title={pending?.options.title ?? ''}
                size="sm"
                showClose={false}
                dismissOnOverlay
                footer={
                    <>
                        <Button onClick={() => settle(false)}>{pending?.options.cancelLabel ?? 'Cancelar'}</Button>
                        <Button variant="primary" onClick={() => settle(true)}>{pending?.options.confirmLabel ?? 'Confirmar'}</Button>
                    </>
                }
            >
                <div className="text-sm leading-relaxed text-adm-ink-muted">{pending?.options.message}</div>
            </Modal>
        </ConfirmContext.Provider>
    );
}

export function useConfirm(): Confirm {
    const confirm = useContext(ConfirmContext);
    if (!confirm) throw new Error('useConfirm precisa estar dentro de <ConfirmProvider> (app/admin/layout.tsx).');
    return confirm;
}
