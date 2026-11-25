'use client';

import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { cn } from '../../components/ui/Button';
import { IconButton } from './ui';

const FOCUSABLE =
    'a[href],button:not([disabled]),textarea:not([disabled]),input:not([disabled]):not([type="hidden"]),select:not([disabled]),[tabindex]:not([tabindex="-1"])';

const sizes = {
    sm: 'sm:max-w-md',
    md: 'sm:max-w-lg',
    lg: 'sm:max-w-2xl',
    xl: 'sm:max-w-4xl',
} as const;

// Modais abertos, do mais antigo para o mais novo. Só o do topo reage a Esc e Tab (ex.: confirmação
// aberta por cima do modal de padrões).
const openModals: symbol[] = [];

type ModalProps = {
    open: boolean;
    onClose: () => void;
    title: ReactNode;
    description?: ReactNode;
    children: ReactNode;
    footer?: ReactNode;
    size?: keyof typeof sizes;
    /** Fechar ao clicar no fundo. Desligado por padrão para não perder um formulário preenchido. */
    dismissOnOverlay?: boolean;
    showClose?: boolean;
};

export function Modal({ open, onClose, title, description, children, footer, size = 'md', dismissOnOverlay = false, showClose = true }: ModalProps) {
    const titleId = useId();
    const descriptionId = useId();
    const dialogRef = useRef<HTMLDivElement>(null);
    const onCloseRef = useRef(onClose);

    useEffect(() => {
        onCloseRef.current = onClose;
    }, [onClose]);

    useEffect(() => {
        if (!open) return;

        const self = Symbol('modal');
        openModals.push(self);
        const dialog = dialogRef.current;
        const previouslyFocused = document.activeElement as HTMLElement | null;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';

        const initial = dialog?.querySelector<HTMLElement>('[data-autofocus]') ?? dialog?.querySelector<HTMLElement>(FOCUSABLE) ?? dialog;
        initial?.focus();

        const onKeyDown = (event: KeyboardEvent) => {
            if (openModals[openModals.length - 1] !== self || !dialog) return;
            if (event.key === 'Escape') {
                event.preventDefault();
                onCloseRef.current();
                return;
            }
            if (event.key !== 'Tab') return;
            const items = Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE));
            if (items.length === 0) {
                event.preventDefault();
                return;
            }
            const first = items[0];
            const last = items[items.length - 1];
            if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog)) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault();
                first.focus();
            }
        };
        document.addEventListener('keydown', onKeyDown);

        return () => {
            document.removeEventListener('keydown', onKeyDown);
            openModals.splice(openModals.indexOf(self), 1);
            document.body.style.overflow = previousOverflow;
            previouslyFocused?.focus?.();
        };
    }, [open]);

    if (!open) return null;

    return createPortal(
        <div
            className="fixed inset-0 z-[60] flex items-end justify-center bg-black/70 backdrop-blur-[2px] sm:items-center sm:p-6"
            onMouseDown={(event) => {
                if (dismissOnOverlay && event.target === event.currentTarget) onClose();
            }}
        >
            <div
                ref={dialogRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby={titleId}
                aria-describedby={description ? descriptionId : undefined}
                tabIndex={-1}
                className={cn(
                    'flex max-h-[92dvh] w-full flex-col rounded-t-adm-card border border-adm-line bg-adm-surface text-adm-ink shadow-adm-overlay outline-none sm:rounded-adm-card',
                    sizes[size],
                )}
            >
                <header className="flex items-start justify-between gap-4 border-b border-adm-line px-5 py-4">
                    <div className="min-w-0">
                        <h2 id={titleId} className="text-lg font-semibold">{title}</h2>
                        {description && <p id={descriptionId} className="mt-1 text-sm text-adm-ink-muted">{description}</p>}
                    </div>
                    {showClose && (
                        <IconButton label="Fechar" onClick={onClose} className="-mr-2 -mt-1">
                            <X className="h-5 w-5" aria-hidden="true" />
                        </IconButton>
                    )}
                </header>
                <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">{children}</div>
                {footer && <footer className="flex flex-col-reverse gap-2 border-t border-adm-line px-5 py-4 sm:flex-row sm:justify-end">{footer}</footer>}
            </div>
        </div>,
        document.body,
    );
}
