'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ArrowLeft, Plus, Trash2 } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { apiFailure, newIdempotencyKey, stockService } from '@/services/stockService';
import type { CreateEntryPayload } from '@/types/stock';
import { Field, Input, Textarea } from '../../../_components/form';
import { Modal } from '../../../_components/Modal';
import { Table, TableWrap, Tbody, Td, Th, Thead } from '../../../_components/table';
import { Button, Card, CardHeader, IconButton, LinkButton, PageHeader } from '../../../_components/ui';
import { Notice } from '../../_components/shared';
import { SupplierSelect } from '../../_components/SupplierSelect';
import { VariantPicker, describePicker, type PickerValue } from '../../_components/VariantPicker';
import { formatMoney, todayLocal } from '../../_lib/format';

type Line = { key: string; picked: PickerValue | null; quantity: string; unitCost: string };

const emptyLine = (): Line => ({ key: newIdempotencyKey(), picked: null, quantity: '', unitCost: '' });

const parseQuantity = (text: string): number | null => (/^\d+$/.test(text.trim()) && Number(text) > 0 ? Number(text) : null);

/** Aceita "10", "10,5" e "10.50"; vazio = sem custo. Retorna `undefined` quando inválido. */
const parseCost = (text: string): number | null | undefined => {
    const value = text.trim();
    if (value === '') return null;
    return /^\d+([.,]\d{1,2})?$/.test(value) ? Number(value.replace(',', '.')) : undefined;
};

type LineErrors = { variant?: string; quantity?: string; cost?: string };

export default function NewStockEntryPage() {
    const router = useRouter();
    // A chave é gerada UMA vez por abertura do formulário e reaproveitada nas retentativas: se a resposta
    // se perder, reenviar não lança a nota duas vezes.
    const [idempotencyKey] = useState(newIdempotencyKey);
    const [supplierId, setSupplierId] = useState('');
    const [invoiceNumber, setInvoiceNumber] = useState('');
    const [invoiceSeries, setInvoiceSeries] = useState('');
    const [invoiceDate, setInvoiceDate] = useState(todayLocal);
    const [notes, setNotes] = useState('');
    const [lines, setLines] = useState<Line[]>(() => [emptyLine()]);
    const [attempted, setAttempted] = useState(false);
    const [confirming, setConfirming] = useState(false);
    const [sending, setSending] = useState(false);
    const [failure, setFailure] = useState<{ message: string; existingEntryId?: string } | null>(null);

    const costs = lines.map(line => parseCost(line.unitCost));
    const lineErrors: LineErrors[] = lines.map((line, index) => {
        const errors: LineErrors = {};
        if (!line.picked || line.picked.kind !== 'variant') errors.variant = 'Escolha a variação.';
        if (parseQuantity(line.quantity) === null) errors.quantity = 'Informe um inteiro maior que zero.';
        if (costs[index] === undefined) errors.cost = 'Use até 2 casas decimais.';
        return errors;
    });
    const headerErrors = {
        supplier: supplierId ? null : 'Selecione o fornecedor.',
        number: invoiceNumber.trim() ? null : 'Informe o número da nota.',
        date: invoiceDate ? null : 'Informe a data da nota.',
    };
    const valid = !headerErrors.supplier && !headerErrors.number && !headerErrors.date
        && lineErrors.every(e => !e.variant && !e.quantity && !e.cost);

    const totalQuantity = lines.reduce((sum, line) => sum + (parseQuantity(line.quantity) ?? 0), 0);
    const allCosted = lines.length > 0 && lines.every((line, i) => parseQuantity(line.quantity) !== null && typeof costs[i] === 'number');
    const totalCost = allCosted
        ? lines.reduce((sum, line, i) => sum + (parseQuantity(line.quantity) ?? 0) * (costs[i] as number), 0)
        : null;

    const takenIds = (except: string) => lines
        .filter(l => l.key !== except && l.picked?.kind === 'variant')
        .map(l => (l.picked as Extract<PickerValue, { kind: 'variant' }>).variant.id);

    const updateLine = (key: string, patch: Partial<Line>) =>
        setLines(current => current.map(line => (line.key === key ? { ...line, ...patch } : line)));

    const review = () => {
        setAttempted(true);
        setFailure(null);
        if (valid) setConfirming(true);
    };

    const submit = async () => {
        if (sending) return;
        setSending(true);
        const payload: CreateEntryPayload = {
            supplierId,
            invoiceNumber: invoiceNumber.trim(),
            invoiceSeries: invoiceSeries.trim(),
            invoiceDate,
            idempotencyKey,
            notes: notes.trim() || null,
            lines: lines.map((line, i) => ({
                variantId: (line.picked as Extract<PickerValue, { kind: 'variant' }>).variant.id,
                quantity: parseQuantity(line.quantity) as number,
                unitCost: (costs[i] as number | null) ?? null,
            })),
        };
        try {
            const detail = await stockService.createEntry(payload);
            toast.success(detail.replayed ? 'Esta nota já havia sido registrada.' : 'Nota registrada.');
            router.push(`/admin/estoque/notas/${detail.summary.id}`);
        } catch (error) {
            const failed = apiFailure(error, 'Não foi possível registrar a nota.');
            setConfirming(false);
            if (failed.status === null) {
                setFailure({ message: 'Sem resposta do servidor. Pode tentar de novo: a mesma chave é reutilizada, então a nota não será lançada em duplicidade.' });
            } else {
                setFailure({ message: failed.message, existingEntryId: failed.status === 409 ? failed.errors.existingEntryId : undefined });
            }
        } finally {
            setSending(false);
        }
    };

    return (
        <div className="space-y-6">
            <PageHeader
                leading={<LinkButton href="/admin/estoque/notas" size="sm" variant="ghost" aria-label="Voltar para as notas"><ArrowLeft className="h-4 w-4" aria-hidden="true" /></LinkButton>}
                eyebrow="Nota de entrada"
                title="Nova entrada"
                description="Registre o recebimento de mercadoria: uma nota pode ter vários produtos."
            />

            {failure && (
                <Notice tone="danger">
                    {failure.message}
                    {failure.existingEntryId && (
                        <> <Link className="font-medium text-adm-accent-ink underline" href={`/admin/estoque/notas/${failure.existingEntryId}`}>Abrir a nota já registrada</Link></>
                    )}
                </Notice>
            )}

            <Card as="section" aria-label="Dados da nota">
                <CardHeader title="Dados da nota" />
                <div className="grid gap-4 p-5 md:grid-cols-2">
                    <div className="md:col-span-2">
                        <SupplierSelect value={supplierId} onChange={setSupplierId} invalid={attempted && !!headerErrors.supplier} />
                        {attempted && headerErrors.supplier && <p role="alert" className="mt-1 text-xs text-adm-danger">{headerErrors.supplier}</p>}
                    </div>
                    <Field label="Número da nota *" hint={attempted && headerErrors.number ? undefined : 'A mesma nota não pode ser registrada duas vezes para o fornecedor.'}>
                        <Input value={invoiceNumber} maxLength={40} aria-invalid={attempted && !!headerErrors.number} className={attempted && headerErrors.number ? 'border-adm-danger' : undefined} onChange={event => setInvoiceNumber(event.target.value)} />
                        {attempted && headerErrors.number && <span role="alert" className="block text-xs text-adm-danger">{headerErrors.number}</span>}
                    </Field>
                    <div className="grid grid-cols-2 gap-4">
                        <Field label="Série" hint="Opcional"><Input value={invoiceSeries} maxLength={10} onChange={event => setInvoiceSeries(event.target.value)} /></Field>
                        <Field label="Data da nota *">
                            <Input type="date" value={invoiceDate} max={todayLocal()} aria-invalid={attempted && !!headerErrors.date} onChange={event => setInvoiceDate(event.target.value)} />
                        </Field>
                    </div>
                    <div className="md:col-span-2">
                        <Field label="Observações" hint="Opcional, até 255 caracteres."><Textarea rows={2} maxLength={255} value={notes} onChange={event => setNotes(event.target.value)} /></Field>
                    </div>
                </div>
            </Card>

            <Card as="section" aria-label="Itens da nota">
                <CardHeader title="Itens" description="Busque a variação (só ativas por padrão), informe a quantidade e, se quiser, o custo unitário." />
                <div className="space-y-3 p-5">
                    {lines.map((line, index) => {
                        const errors = lineErrors[index];
                        return (
                            <div key={line.key} className="grid gap-3 rounded-adm-control border border-adm-line bg-adm-sunken p-3 md:grid-cols-[minmax(0,1fr)_7rem_8rem_auto] md:items-start">
                                <div>
                                    <VariantPicker
                                        label={`Variação da linha ${index + 1}`}
                                        value={line.picked}
                                        invalid={attempted && !!errors.variant}
                                        takenVariantIds={takenIds(line.key)}
                                        onChange={picked => updateLine(line.key, { picked })}
                                    />
                                    {attempted && errors.variant && <p role="alert" className="mt-1 text-xs text-adm-danger">{errors.variant}</p>}
                                </div>
                                <div>
                                    <Input
                                        aria-label={`Quantidade da linha ${index + 1}`}
                                        placeholder="Qtd."
                                        inputMode="numeric"
                                        value={line.quantity}
                                        aria-invalid={attempted && !!errors.quantity}
                                        className={attempted && errors.quantity ? 'border-adm-danger' : undefined}
                                        onChange={event => updateLine(line.key, { quantity: event.target.value })}
                                    />
                                    {attempted && errors.quantity && <p role="alert" className="mt-1 text-xs text-adm-danger">{errors.quantity}</p>}
                                </div>
                                <div>
                                    <Input
                                        aria-label={`Custo unitário da linha ${index + 1} (opcional)`}
                                        placeholder="Custo un. (R$)"
                                        inputMode="decimal"
                                        value={line.unitCost}
                                        aria-invalid={attempted && !!errors.cost}
                                        className={attempted && errors.cost ? 'border-adm-danger' : undefined}
                                        onChange={event => updateLine(line.key, { unitCost: event.target.value })}
                                    />
                                    {attempted && errors.cost && <p role="alert" className="mt-1 text-xs text-adm-danger">{errors.cost}</p>}
                                </div>
                                <IconButton
                                    label={`Remover linha ${index + 1}`}
                                    tone="danger"
                                    disabled={lines.length === 1}
                                    onClick={() => setLines(current => current.filter(l => l.key !== line.key))}
                                >
                                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                                </IconButton>
                            </div>
                        );
                    })}
                    <Button onClick={() => setLines(current => [...current, emptyLine()])}><Plus className="h-4 w-4" aria-hidden="true" /> Adicionar linha</Button>
                </div>
                <div className="flex flex-wrap items-center justify-between gap-3 border-t border-adm-line px-5 py-4">
                    <p className="text-sm text-adm-ink-muted" aria-live="polite">
                        <span className="font-semibold text-adm-ink">{totalQuantity}</span> {totalQuantity === 1 ? 'unidade' : 'unidades'} em {lines.length} {lines.length === 1 ? 'linha' : 'linhas'}
                        {' · '}custo total <span className="font-semibold text-adm-ink">{totalCost == null ? '—' : formatMoney(totalCost)}</span>
                        {totalCost == null && <span className="text-adm-ink-subtle"> (só é calculado quando todas as linhas têm custo)</span>}
                    </p>
                    <Button variant="primary" size="lg" onClick={review}>Revisar e registrar</Button>
                </div>
            </Card>

            <Modal
                open={confirming}
                onClose={() => { if (!sending) setConfirming(false); }}
                size="lg"
                title="Confirmar entrada"
                description="Confira os dados. Depois de registrada, a nota é imutável."
                footer={
                    <>
                        <Button onClick={() => setConfirming(false)} disabled={sending}>Voltar e editar</Button>
                        <Button variant="primary" loading={sending} disabled={sending} onClick={submit}>Confirmar e registrar</Button>
                    </>
                }
            >
                <div className="space-y-4">
                    <Notice tone="warning">A nota <strong>não pode ser editada nem excluída</strong>. Erros se corrigem depois com um ajuste manual.</Notice>
                    <p className="text-sm text-adm-ink-muted">NF <span className="font-medium text-adm-ink">{invoiceSeries.trim() ? `${invoiceSeries.trim()}-` : ''}{invoiceNumber.trim()}</span> · {invoiceDate.split('-').reverse().join('/')}</p>
                    <TableWrap>
                        <Table>
                            <Thead><tr><Th>Variação</Th><Th className="text-right">Qtd.</Th><Th className="text-right">Custo un.</Th></tr></Thead>
                            <Tbody>
                                {lines.map(line => (
                                    <tr key={line.key}>
                                        <Td>{line.picked ? describePicker(line.picked) : '—'}</Td>
                                        <Td className="text-right tabular-nums">{parseQuantity(line.quantity)}</Td>
                                        <Td className="text-right tabular-nums">{formatMoney(parseCost(line.unitCost) ?? null)}</Td>
                                    </tr>
                                ))}
                            </Tbody>
                        </Table>
                    </TableWrap>
                    <p className="text-sm text-adm-ink">Total: <strong>{totalQuantity}</strong> {totalQuantity === 1 ? 'unidade' : 'unidades'}{totalCost != null && <> · <strong>{formatMoney(totalCost)}</strong></>}</p>
                </div>
            </Modal>
        </div>
    );
}
