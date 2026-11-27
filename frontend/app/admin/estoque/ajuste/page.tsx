'use client';

import { useState } from 'react';
import useSWR from 'swr';
import { toast } from 'react-hot-toast';
import { fetcher } from '@/services/productService';
import { apiFailure, newIdempotencyKey, qs, stockService } from '@/services/stockService';
import type { AdjustmentCategory, PageView, StockMovement } from '@/types/stock';
import { cn } from '../../../components/ui/Button';
import { Field, Input, Select, Textarea } from '../../_components/form';
import { Modal } from '../../_components/Modal';
import { Table, TableWrap, Tbody, Td, Th, Thead, rowClass } from '../../_components/table';
import { Button, Card, CardHeader, EmptyState } from '../../_components/ui';
import { Delta, MovementBadge, Notice } from '../_components/shared';
import { VariantPicker, describePicker, type PickerValue } from '../_components/VariantPicker';
import { CATEGORY_LABEL, MANUAL_CATEGORIES, formatDateTime } from '../_lib/format';

type Mode = 'delta' | 'count';

export default function StockAdjustPage() {
    const [picked, setPicked] = useState<PickerValue | null>(null);
    // Saldo que o admin viu ao escolher a variação; vai como expectedBalance para detectar mudança no meio do caminho.
    const [seen, setSeen] = useState({ balance: 0, at: '' });
    const seenBalance = seen.balance;
    const markSeen = (balance: number) =>
        setSeen({ balance, at: new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) });
    const [mode, setMode] = useState<Mode>('delta');
    const [amount, setAmount] = useState('');
    const [category, setCategory] = useState<AdjustmentCategory>('COUNT');
    const [reason, setReason] = useState('');
    const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey);
    const [attempted, setAttempted] = useState(false);
    const [confirming, setConfirming] = useState(false);
    const [sending, setSending] = useState(false);
    const [failure, setFailure] = useState<{ message: string; currentBalance?: number } | null>(null);

    const variant = picked?.kind === 'variant' ? picked.variant : null;
    const { data: recent, mutate: refreshRecent } = useSWR<PageView<StockMovement>>(
        variant ? `/admin/stock/variants/${variant.id}/timeline${qs({ size: 5 })}` : null,
        fetcher,
    );

    const trimmed = amount.trim();
    const parsed = /^-?\d+$/.test(trimmed) ? Number(trimmed) : null;
    const delta = parsed === null ? null : mode === 'delta' ? parsed : parsed - seenBalance;
    const after = delta === null ? null : seenBalance + delta;

    let amountError: string | null = null;
    if (parsed === null) amountError = mode === 'delta' ? 'Informe um inteiro (use - para saída).' : 'Informe a quantidade contada.';
    else if (mode === 'count' && parsed < 0) amountError = 'A contagem não pode ser negativa.';
    else if (delta === 0) amountError = 'O ajuste não altera o saldo.';
    else if (after !== null && after < 0) amountError = `Resultaria em saldo negativo (${after}).`;
    const reasonError = reason.trim() ? null : 'O motivo é obrigatório.';
    const valid = !!variant && !amountError && !reasonError;

    const pick = (value: PickerValue | null) => {
        setPicked(value);
        setFailure(null);
        setAttempted(false);
        if (value?.kind === 'variant') markSeen(value.variant.stock);
    };

    const review = () => {
        setAttempted(true);
        setFailure(null);
        if (valid) setConfirming(true);
    };

    const submit = async () => {
        if (sending || !variant || delta === null) return;
        setSending(true);
        try {
            const movement = await stockService.adjust({
                variantId: variant.id,
                ...(mode === 'count' ? { countedQuantity: parsed as number } : { delta: parsed as number }),
                expectedBalance: seenBalance,
                category,
                reason: reason.trim(),
                idempotencyKey,
            });
            toast.success(`Saldo ajustado: ${movement.stockBefore ?? seenBalance} → ${movement.stockAfter}.`);
            markSeen(movement.stockAfter);
            setAmount('');
            setReason('');
            setAttempted(false);
            setIdempotencyKey(newIdempotencyKey());
            setConfirming(false);
            await refreshRecent();
        } catch (error) {
            const failed = apiFailure(error, 'Não foi possível ajustar o saldo.');
            setConfirming(false);
            const current = failed.status === 409 ? Number(failed.errors.currentBalance) : NaN;
            setFailure({
                message: failed.status === null
                    ? 'Sem resposta do servidor. Pode tentar de novo: a mesma chave é reutilizada, então o ajuste não será aplicado duas vezes.'
                    : failed.message,
                currentBalance: Number.isFinite(current) ? current : undefined,
            });
        } finally {
            setSending(false);
        }
    };

    return (
        <div className="space-y-6">
            <Card as="section" aria-label="Ajuste manual">
                <CardHeader title="Ajuste manual de saldo" description="Para contagem, perda, avaria ou correção. Todo ajuste fica no log com o motivo e quem fez." />
                <div className="space-y-5 p-5">
                    {/* Não usar <Field> (um <label>) aqui: o clique numa opção seria redirecionado ao input e reabriria a lista. */}
                    <div className="space-y-1.5">
                        <span className="block text-xs font-medium text-adm-ink-muted">Variação</span>
                        <VariantPicker label="Variação" value={picked} onChange={pick} />
                    </div>

                    {variant && (
                        <>
                            <div className="flex flex-wrap items-center gap-x-6 gap-y-1 rounded-adm-control border border-adm-line bg-adm-sunken px-4 py-3 text-sm">
                                <span className="text-adm-ink-muted">Saldo atual</span>
                                <span className="text-2xl font-semibold tabular-nums text-adm-ink" aria-live="polite">{seenBalance}</span>
                                <span className="text-xs text-adm-ink-subtle">saldo visto às {seen.at}; se mudar antes de confirmar, avisamos</span>
                            </div>

                            {failure && (
                                <Notice tone="danger">
                                    {failure.message}
                                    {failure.currentBalance !== undefined && (
                                        <div className="mt-2">
                                            <Button size="sm" onClick={() => { markSeen(failure.currentBalance as number); setFailure(null); }}>
                                                Usar o saldo atual ({failure.currentBalance}) e revisar
                                            </Button>
                                        </div>
                                    )}
                                </Notice>
                            )}

                            <fieldset>
                                <legend className="mb-1.5 text-xs font-medium text-adm-ink-muted">Como informar</legend>
                                <div className="inline-flex rounded-adm-control border border-adm-line-strong p-0.5" role="radiogroup">
                                    {([['delta', 'Entrada/saída (+/−)'], ['count', 'Contei N unidades']] as const).map(([value, label]) => (
                                        <label key={value} className={cn('inline-flex min-h-9 cursor-pointer items-center rounded px-3 text-sm', mode === value ? 'bg-adm-accent text-white' : 'text-adm-ink-muted hover:text-adm-ink')}>
                                            <input type="radio" name="mode" className="sr-only" checked={mode === value} onChange={() => { setMode(value); setAmount(''); }} />
                                            {label}
                                        </label>
                                    ))}
                                </div>
                            </fieldset>

                            <div className="grid gap-4 md:grid-cols-3">
                                <Field label={mode === 'delta' ? 'Quantidade (+ entra, − sai)' : 'Quantidade contada'}>
                                    <Input inputMode="numeric" value={amount} aria-invalid={attempted && !!amountError} className={attempted && amountError ? 'border-adm-danger' : undefined} placeholder={mode === 'delta' ? 'ex.: -2' : 'ex.: 14'} onChange={event => setAmount(event.target.value)} />
                                    {attempted && amountError && <span role="alert" className="block text-xs text-adm-danger">{amountError}</span>}
                                </Field>
                                <Field label="Categoria">
                                    <Select value={category} onChange={event => setCategory(event.target.value as AdjustmentCategory)}>
                                        {MANUAL_CATEGORIES.map(c => <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>)}
                                    </Select>
                                </Field>
                                <div className="flex items-end">
                                    <p className="rounded-adm-control border border-adm-line px-4 py-2 text-sm tabular-nums" aria-live="polite">
                                        <span className="text-adm-ink-muted">{seenBalance} → </span>
                                        <span className={cn('font-semibold', after !== null && after < 0 ? 'text-adm-danger' : 'text-adm-ink')}>{after ?? '—'}</span>
                                        {delta !== null && delta !== 0 && <span className="ml-2"><Delta value={delta} /></span>}
                                    </p>
                                </div>
                            </div>

                            <Field label="Motivo *" hint="Obrigatório. Ex.: “Contagem física de 26/11”, “Caixa molhada na entrega”.">
                                <Textarea rows={2} maxLength={255} value={reason} aria-invalid={attempted && !!reasonError} className={attempted && reasonError ? 'border-adm-danger' : undefined} onChange={event => setReason(event.target.value)} />
                                {attempted && reasonError && <span role="alert" className="block text-xs text-adm-danger">{reasonError}</span>}
                            </Field>

                            <div className="flex justify-end"><Button variant="primary" size="lg" onClick={review}>Revisar ajuste</Button></div>
                        </>
                    )}
                </div>
            </Card>

            {variant && (
                <Card as="section" aria-label="Últimos movimentos da variação" className="overflow-hidden">
                    <CardHeader title="Últimos movimentos desta variação" />
                    {recent && recent.items.length === 0 ? (
                        <EmptyState title="Sem movimentações" description="Esta variação ainda não tem registros no livro." />
                    ) : (
                        <TableWrap>
                            <Table>
                                <Thead><tr><Th>Quando</Th><Th>Tipo</Th><Th className="text-right">Mov.</Th><Th className="text-right">Saldo</Th><Th>Motivo</Th></tr></Thead>
                                <Tbody>
                                    {recent?.items.map(m => (
                                        <tr key={m.id} className={rowClass()}>
                                            <Td className="whitespace-nowrap text-adm-ink-muted">{formatDateTime(m.createdAt)}</Td>
                                            <Td><MovementBadge type={m.type} /></Td>
                                            <Td className="text-right"><Delta value={m.delta} /></Td>
                                            <Td className="whitespace-nowrap text-right tabular-nums"><span className="text-adm-ink-muted">{m.stockBefore ?? '—'} → </span><span className="font-semibold">{m.stockAfter}</span></Td>
                                            <Td className="max-w-64 truncate text-adm-ink-muted">{m.reason ?? '—'}</Td>
                                        </tr>
                                    ))}
                                </Tbody>
                            </Table>
                        </TableWrap>
                    )}
                </Card>
            )}

            <Modal
                open={confirming}
                onClose={() => { if (!sending) setConfirming(false); }}
                title="Confirmar ajuste"
                footer={
                    <>
                        <Button onClick={() => setConfirming(false)} disabled={sending}>Voltar</Button>
                        <Button variant="primary" loading={sending} disabled={sending} onClick={submit}>Confirmar ajuste</Button>
                    </>
                }
            >
                {picked && delta !== null && after !== null && (
                    <div className="space-y-3 text-sm">
                        <p className="text-adm-ink">{describePicker(picked)}</p>
                        <p className="rounded-adm-control border border-adm-line bg-adm-sunken px-4 py-3 text-lg tabular-nums">
                            <span className="text-adm-ink-muted">{seenBalance} → </span><strong>{after}</strong> <Delta value={delta} />
                        </p>
                        <p className="text-adm-ink-muted">{CATEGORY_LABEL[category]} · {reason.trim()}</p>
                        <p className="text-xs text-adm-ink-subtle">Se o saldo mudar antes de confirmar, o ajuste é recusado e você revê os números.</p>
                    </div>
                )}
            </Modal>
        </div>
    );
}
