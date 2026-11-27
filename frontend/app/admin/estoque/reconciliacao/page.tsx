'use client';

import { useState } from 'react';
import useSWR from 'swr';
import { CheckCircle2 } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { fetcher } from '@/services/productService';
import { apiFailure, qs, stockService } from '@/services/stockService';
import type { PageView, ReconciliationDetail, ReconciliationRow, ReconciliationStatus } from '@/types/stock';
import { Checkbox } from '../../_components/form';
import { useConfirm } from '../../_components/confirm';
import { Modal } from '../../_components/Modal';
import { Table, TableWrap, Tbody, Td, Th, Thead, rowClass } from '../../_components/table';
import { Badge, Button, Card, CardHeader, EmptyState, ErrorState, TableSkeletonRows, type BadgeTone } from '../../_components/ui';
import { Notice, Pager } from '../_components/shared';

const STATUS: Record<ReconciliationStatus, { label: string; tone: BadgeTone }> = {
    OK: { label: 'Confere', tone: 'success' },
    DIVERGENT: { label: 'Divergente', tone: 'danger' },
    NO_LEDGER: { label: 'Sem livro', tone: 'warning' },
};

export default function StockReconciliationPage() {
    const confirm = useConfirm();
    const [onlyIssues, setOnlyIssues] = useState(true);
    const [page, setPage] = useState(0);
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [adopting, setAdopting] = useState(false);
    const [detailId, setDetailId] = useState<string | null>(null);

    const { data, error, isLoading, mutate } = useSWR<PageView<ReconciliationRow>>(
        `/admin/stock/reconciliation${qs({ onlyIssues, page, size: 50 })}`,
        fetcher,
        { keepPreviousData: true },
    );
    const { data: detail, error: detailError, isLoading: detailLoading } = useSWR<ReconciliationDetail>(
        detailId ? `/admin/stock/reconciliation/variants/${detailId}` : null,
        fetcher,
    );

    const toggle = (id: string) => setSelected(current => {
        const next = new Set(current);
        if (next.has(id)) next.delete(id); else next.add(id);
        return next;
    });

    const adopt = async () => {
        if (adopting || selected.size === 0) return;
        const ok = await confirm({
            title: 'Adotar saldo atual no livro?',
            message: `Para ${selected.size} ${selected.size === 1 ? 'variação' : 'variações'} será gravado um marcador de abertura com o saldo de hoje (delta 0). Nenhuma entrada é inventada; o livro só passa a ter um ponto de partida.`,
            confirmLabel: 'Adotar saldo',
        });
        if (!ok) return;
        setAdopting(true);
        try {
            const result = await stockService.adoptBalance([...selected]);
            toast.success(`${result.adopted} ${result.adopted === 1 ? 'variação adotada' : 'variações adotadas'}${result.skippedVariantIds.length ? `, ${result.skippedVariantIds.length} já tinham livro` : ''}.`);
            setSelected(new Set());
            await mutate();
        } catch (e) {
            toast.error(apiFailure(e, 'Não foi possível adotar o saldo.').message);
        } finally {
            setAdopting(false);
        }
    };

    const adoptable = data?.items.filter(row => row.status === 'NO_LEDGER') ?? [];

    return (
        <div className="space-y-6">
            <Notice tone="info">
                A reconciliação compara o saldo de cada variação com o último movimento do livro. <strong>Divergente</strong> indica uma alteração de saldo
                que não passou pelo livro (ou perdeu a corrida de um deploy). <strong>Sem livro</strong> são variações que já tinham estoque antes do log existir.
            </Notice>

            <Card as="section" aria-label="Reconciliação" className="overflow-hidden">
                <CardHeader
                    title="Saldo × livro"
                    actions={
                        <>
                            <Checkbox checked={onlyIssues} onChange={event => { setOnlyIssues(event.target.checked); setPage(0); }}>Só com problema</Checkbox>
                            <Button variant="primary" size="sm" loading={adopting} disabled={selected.size === 0} onClick={adopt}>Adotar saldo ({selected.size})</Button>
                        </>
                    }
                />
                {error ? (
                    <ErrorState description="Não foi possível carregar a reconciliação." onRetry={() => mutate()} />
                ) : (
                    <TableWrap>
                        <Table>
                            <Thead>
                                <tr>
                                    <Th className="w-10"><span className="sr-only">Selecionar</span></Th>
                                    <Th>Produto / variação</Th><Th className="text-right">Saldo</Th><Th className="text-right">Livro</Th><Th className="text-right">Diferença</Th><Th>Situação</Th><Th><span className="sr-only">Detalhes</span></Th>
                                </tr>
                            </Thead>
                            <Tbody>
                                {isLoading && !data && <TableSkeletonRows columns={7} />}
                                {data?.items.map(row => (
                                    <tr key={row.variantId} className={rowClass(selected.has(row.variantId))}>
                                        <Td>
                                            {row.status === 'NO_LEDGER' && (
                                                <input type="checkbox" className="h-4 w-4 accent-adm-accent" aria-label={`Selecionar ${row.productName} ${row.size}`} checked={selected.has(row.variantId)} onChange={() => toggle(row.variantId)} />
                                            )}
                                        </Td>
                                        <Td>{row.productName} <span className="text-adm-ink-muted">· {row.size}{row.color ? ` · ${row.color}` : ''}{row.active ? '' : ' (inativo)'}</span></Td>
                                        <Td className="text-right tabular-nums">{row.currentStock}</Td>
                                        <Td className="text-right tabular-nums">{row.ledgerBalance ?? '—'}</Td>
                                        <Td className="text-right tabular-nums">{row.difference ?? '—'}</Td>
                                        <Td><Badge tone={STATUS[row.status].tone} dot>{STATUS[row.status].label}</Badge></Td>
                                        <Td className="text-right"><Button size="sm" variant="ghost" onClick={() => setDetailId(row.variantId)}>Detalhes</Button></Td>
                                    </tr>
                                ))}
                            </Tbody>
                        </Table>
                        {data && data.items.length === 0 && (
                            <EmptyState icon={CheckCircle2} title={onlyIssues ? 'Tudo confere' : 'Nenhuma variação cadastrada'} description={onlyIssues ? 'Nenhuma variação diverge do livro nem está sem livro.' : undefined} />
                        )}
                    </TableWrap>
                )}
                {data && adoptable.length > 0 && (
                    <div className="border-t border-adm-line px-5 py-3">
                        <Checkbox
                            checked={adoptable.every(row => selected.has(row.variantId))}
                            onChange={event => setSelected(current => {
                                const next = new Set(current);
                                adoptable.forEach(row => (event.target.checked ? next.add(row.variantId) : next.delete(row.variantId)));
                                return next;
                            })}
                        >
                            Selecionar todas as variações sem livro desta página
                        </Checkbox>
                    </div>
                )}
                {data && <Pager page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />}
            </Card>

            <Modal open={detailId !== null} onClose={() => setDetailId(null)} title="Detalhes da reconciliação" size="md">
                {detailLoading && <p className="text-sm text-adm-ink-muted" aria-busy="true">Carregando…</p>}
                {detailError && <ErrorState description="Não foi possível carregar o detalhe." />}
                {detail && (
                    <div className="space-y-4 text-sm">
                        <p className="text-adm-ink">{detail.row.productName} · {detail.row.size}{detail.row.color ? ` · ${detail.row.color}` : ''}</p>
                        <dl className="grid grid-cols-3 gap-3">
                            <div><dt className="text-xs text-adm-ink-muted">Saldo</dt><dd className="text-lg font-semibold tabular-nums">{detail.row.currentStock}</dd></div>
                            <div><dt className="text-xs text-adm-ink-muted">Livro</dt><dd className="text-lg font-semibold tabular-nums">{detail.row.ledgerBalance ?? '—'}</dd></div>
                            <div><dt className="text-xs text-adm-ink-muted">Situação</dt><dd><Badge tone={STATUS[detail.row.status].tone}>{STATUS[detail.row.status].label}</Badge></dd></div>
                        </dl>
                        {detail.chainBreaks.length === 0 ? (
                            <p className="text-adm-ink-muted">Nenhuma quebra de cadeia: em cada movimento novo, o saldo antes é igual ao saldo depois do anterior.</p>
                        ) : (
                            <div>
                                <Notice tone="warning">Há {detail.chainBreaks.length} {detail.chainBreaks.length === 1 ? 'ponto' : 'pontos'} em que o saldo mudou fora do livro.</Notice>
                                <ul className="mt-3 space-y-1.5">
                                    {detail.chainBreaks.map(b => (
                                        <li key={b.seq} className="rounded-adm-control border border-adm-line px-3 py-2 tabular-nums">
                                            Movimento #{b.seq}: esperado {b.previousStockAfter}, registrou {b.stockBefore} (diferença {b.stockBefore - b.previousStockAfter})
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        )}
                    </div>
                )}
            </Modal>
        </div>
    );
}
