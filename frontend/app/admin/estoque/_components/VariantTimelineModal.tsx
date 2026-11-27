'use client';

import { useState } from 'react';
import useSWR from 'swr';
import Link from 'next/link';
import { fetcher } from '@/services/productService';
import { qs } from '@/services/stockService';
import type { PageView, StockMovement } from '@/types/stock';
import { Modal } from '../../_components/Modal';
import { ErrorState, EmptyState, Skeleton } from '../../_components/ui';
import { formatDateTime, invoiceLabel } from '../_lib/format';
import { Delta, MovementBadge, Pager } from './shared';

export type TimelineTarget = { variantId: string; title: string };

/** Linha do tempo de uma variação: do movimento mais recente ao mais antigo, com saldo antes → depois. */
export function VariantTimelineModal({ target, onClose }: { target: TimelineTarget | null; onClose: () => void }) {
    const [page, setPage] = useState(0);
    const { data, error, isLoading, mutate } = useSWR<PageView<StockMovement>>(
        target ? `/admin/stock/variants/${target.variantId}/timeline${qs({ page, size: 10 })}` : null,
        fetcher,
        { keepPreviousData: true },
    );

    const close = () => { setPage(0); onClose(); };

    return (
        <Modal open={target !== null} onClose={close} size="lg" title={target?.title ?? ''} description="Histórico completo desta variação, do mais recente ao mais antigo.">
            {isLoading && !data && (
                <div className="space-y-3" aria-hidden="true">{Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-12 w-full" />)}</div>
            )}
            {error && <ErrorState onRetry={() => mutate()} />}
            {data && data.items.length === 0 && <EmptyState title="Sem movimentações" description="Esta variação ainda não tem registros no livro." />}
            {data && data.items.length > 0 && (
                <>
                    <ol className="space-y-3">
                        {data.items.map(m => (
                            <li key={m.id} className="relative rounded-adm-control border border-adm-line bg-adm-sunken px-4 py-3">
                                <div className="flex flex-wrap items-center justify-between gap-2">
                                    <div className="flex items-center gap-2"><MovementBadge type={m.type} /><span className="text-xs text-adm-ink-subtle">{formatDateTime(m.createdAt)}</span></div>
                                    <div className="text-sm tabular-nums">
                                        <span className="text-adm-ink-muted">{m.stockBefore ?? '?'} → </span>
                                        <span className="font-semibold text-adm-ink">{m.stockAfter}</span>
                                        <span className="ml-2"><Delta value={m.delta} /></span>
                                    </div>
                                </div>
                                <p className="mt-1.5 text-xs text-adm-ink-muted">
                                    {m.entryId && <Link className="text-adm-accent-ink underline" href={`/admin/estoque/notas/${m.entryId}`}>NF {invoiceLabel('', m.invoiceNumber ?? '')} · {m.supplierName}</Link>}
                                    {m.orderId && <Link className="text-adm-accent-ink underline" href={`/admin/estoque?orderId=${m.orderId}`}>Pedido {m.orderId.slice(0, 8)}</Link>}
                                    {m.reason && <span>{m.entryId || m.orderId ? ' · ' : ''}{m.reason}</span>}
                                    {m.performedBy && <span> · {m.performedBy}</span>}
                                </p>
                            </li>
                        ))}
                    </ol>
                    <div className="-mx-6 -mb-4 mt-4"><Pager page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} /></div>
                </>
            )}
        </Modal>
    );
}
