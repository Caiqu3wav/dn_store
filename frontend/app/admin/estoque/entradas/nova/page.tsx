'use client';

import { useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import axios from 'axios';
import useSWR from 'swr';
import { ArrowLeft, Plus, Trash2 } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { fetcher } from '@/services/productService';
import { stockService } from '@/services/stockService';
import { getApiErrorCode, getApiErrorMessage } from '@/utils/apiError';
import type { PageResponse, StockVariant, Supplier } from '@/types/stock';
import { Button, Card, CardHeader, IconButton, LinkButton, PageHeader } from '../../../_components/ui';
import { Field, Input, Select, Textarea } from '../../../_components/form';
import { Modal } from '../../../_components/Modal';
import { useConfirm } from '../../../_components/confirm';
import { VariantPicker, newIdempotencyKey, variantLabel } from '../../_components';

type Line = { key: number; variant: StockVariant | null; quantity: string; unitCost: string };

const MAX_LINES = 200;

/** id da nota já existente, enviado pelo backend em errors.existingEntryId quando o número é duplicado. */
function existingEntryId(error: unknown): string | undefined {
    if (!axios.isAxiosError(error)) return undefined;
    const data = error.response?.data as { errors?: { existingEntryId?: unknown } } | undefined;
    const id = data?.errors?.existingEntryId;
    return typeof id === 'string' ? id : undefined;
}

export default function NewStockEntryPage() {
    const router = useRouter();
    const confirm = useConfirm();
    // Uma chave por abertura do formulário, reutilizada nas retentativas (o backend não aplica duas vezes).
    const idempotencyKey = useRef(newIdempotencyKey());
    const nextLineKey = useRef(2);

    const { data: suppliers, mutate: reloadSuppliers } = useSWR<PageResponse<Supplier>>('/admin/suppliers?size=100', fetcher);
    const [supplierId, setSupplierId] = useState('');
    const [invoiceNumber, setInvoiceNumber] = useState('');
    const [issueDate, setIssueDate] = useState('');
    const [notes, setNotes] = useState('');
    const [lines, setLines] = useState<Line[]>([{ key: 1, variant: null, quantity: '', unitCost: '' }]);
    const [attempted, setAttempted] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [duplicateId, setDuplicateId] = useState<string | null>(null);
    const [supplierModalOpen, setSupplierModalOpen] = useState(false);

    const activeSuppliers = (suppliers?.content ?? []).filter(supplier => supplier.active);

    const updateLine = (key: number, patch: Partial<Line>) =>
        setLines(current => current.map(line => (line.key === key ? { ...line, ...patch } : line)));

    // ---- validação
    const usedVariantIds = lines.flatMap(line => (line.variant ? [line.variant.id] : []));
    const lineError = (line: Line): string | null => {
        if (!line.variant) return 'Escolha o produto.';
        if (usedVariantIds.filter(id => id === line.variant?.id).length > 1) return 'Esta variação já está em outra linha; some as quantidades.';
        const quantity = Number(line.quantity);
        if (line.quantity.trim() === '' || !Number.isInteger(quantity) || quantity < 1) return 'Informe uma quantidade inteira maior que zero.';
        if (quantity > 1_000_000) return 'Quantidade acima do limite.';
        if (line.unitCost.trim() !== '') {
            const cost = Number(line.unitCost.replace(',', '.'));
            if (!Number.isFinite(cost) || cost < 0) return 'Custo unitário inválido.';
        }
        return null;
    };
    const supplierError = supplierId ? null : 'Selecione o fornecedor.';
    const invoiceError = invoiceNumber.trim() ? null : 'Informe o número da nota.';
    const hasErrors = !!supplierError || !!invoiceError || lines.some(line => lineError(line) !== null);
    const totalUnits = lines.reduce((sum, line) => sum + (Number.isInteger(Number(line.quantity)) ? Number(line.quantity) : 0), 0);

    const submit = async () => {
        setAttempted(true);
        setDuplicateId(null);
        if (hasErrors || submitting) return;
        const supplier = activeSuppliers.find(item => item.id === supplierId);
        const confirmed = await confirm({
            title: 'Registrar nota de entrada?',
            message: (
                <>
                    Nota <strong>{invoiceNumber.trim().toUpperCase()}</strong> de <strong>{supplier?.name}</strong>: {lines.length} {lines.length === 1 ? 'linha' : 'linhas'}, {totalUnits} unidades.
                    <br />O estoque será aumentado agora e a nota <strong>não poderá ser editada nem apagada</strong>. Correções são feitas por ajuste de estoque.
                </>
            ),
            confirmLabel: 'Registrar entrada',
        });
        if (!confirmed) return;

        setSubmitting(true);
        try {
            const entry = await stockService.createEntry({
                supplierId,
                invoiceNumber: invoiceNumber.trim(),
                issueDate: issueDate || null,
                notes: notes.trim() || null,
                idempotencyKey: idempotencyKey.current,
                items: lines.map(line => ({
                    variantId: line.variant!.id,
                    quantity: Number(line.quantity),
                    unitCost: line.unitCost.trim() === '' ? null : Number(line.unitCost.replace(',', '.')),
                })),
            });
            toast.success('Entrada registrada e estoque atualizado.');
            router.push(`/admin/estoque/entradas/${entry.id}`);
        } catch (error) {
            if (getApiErrorCode(error) === 'STOCK_ENTRY_DUPLICATE') {
                setDuplicateId(existingEntryId(error) ?? null);
            }
            toast.error(getApiErrorMessage(error, 'Não foi possível registrar a entrada.'));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="space-y-6">
            <LinkButton href="/admin/estoque/entradas" variant="ghost" size="sm"><ArrowLeft className="h-4 w-4" aria-hidden="true" /> Notas de entrada</LinkButton>
            <PageHeader eyebrow="Estoque" title="Nova entrada de nota" description="Registre o recebimento de mercadoria de um fornecedor. Uma nota pode ter várias linhas." />

            <form noValidate className="space-y-6" onSubmit={event => { event.preventDefault(); void submit(); }}>
                {duplicateId && (
                    <div role="alert" className="rounded-adm-card border border-adm-warning/40 bg-adm-warning/10 p-4 text-sm text-adm-ink">
                        Já existe uma nota com este número para o fornecedor.{' '}
                        <Link href={`/admin/estoque/entradas/${duplicateId}`} className="font-medium text-adm-accent-ink underline underline-offset-2">Abrir a nota existente</Link>
                    </div>
                )}

                <Card>
                    <CardHeader title="Dados da nota" />
                    <div className="grid gap-4 p-5 sm:grid-cols-2">
                        <div className="space-y-1.5">
                            <Field label="Fornecedor / fabricante">
                                <Select value={supplierId} onChange={event => setSupplierId(event.target.value)} aria-invalid={attempted && !!supplierError}>
                                    <option value="">Selecione…</option>
                                    {activeSuppliers.map(supplier => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}
                                </Select>
                            </Field>
                            <Button size="sm" variant="ghost" onClick={() => setSupplierModalOpen(true)}><Plus className="h-4 w-4" aria-hidden="true" /> Novo fornecedor</Button>
                            {attempted && supplierError && <p role="alert" className="text-xs text-adm-danger">{supplierError}</p>}
                        </div>
                        <div className="space-y-1.5">
                            <Field label="Número da nota">
                                <Input value={invoiceNumber} maxLength={40} onChange={event => { setInvoiceNumber(event.target.value); setDuplicateId(null); }} aria-invalid={attempted && !!invoiceError} placeholder="Ex.: 000123" />
                            </Field>
                            {attempted && invoiceError && <p role="alert" className="text-xs text-adm-danger">{invoiceError}</p>}
                        </div>
                        <Field label="Data de emissão (opcional)"><Input type="date" value={issueDate} onChange={event => setIssueDate(event.target.value)} /></Field>
                        <Field label="Observação (opcional)"><Textarea rows={2} maxLength={500} value={notes} onChange={event => setNotes(event.target.value)} /></Field>
                    </div>
                </Card>

                <Card>
                    <CardHeader
                        title="Itens da nota"
                        description="Cada variação (tamanho) aparece uma única vez; some as quantidades."
                        actions={<Button size="sm" disabled={lines.length >= MAX_LINES} onClick={() => setLines(current => [...current, { key: nextLineKey.current++, variant: null, quantity: '', unitCost: '' }])}><Plus className="h-4 w-4" aria-hidden="true" /> Adicionar item</Button>}
                    />
                    <ul className="divide-y divide-adm-line">
                        {lines.map((line, index) => {
                            const error = attempted ? lineError(line) : null;
                            return (
                                <li key={line.key} className="grid gap-3 p-4 sm:grid-cols-[minmax(0,1fr)_8rem_9rem_auto] sm:items-start sm:px-5">
                                    <div>
                                        <VariantPicker value={line.variant} onChange={variant => updateLine(line.key, { variant })} label={`Item ${index + 1}`} invalid={!!error && !line.variant} />
                                        {error && <p role="alert" className="mt-1 text-xs text-adm-danger">{error}</p>}
                                    </div>
                                    <Field label="Quantidade"><Input type="number" min={1} step={1} inputMode="numeric" value={line.quantity} onChange={event => updateLine(line.key, { quantity: event.target.value })} aria-label={`Quantidade do item ${index + 1}`} /></Field>
                                    <Field label="Custo unit. (R$)"><Input inputMode="decimal" value={line.unitCost} onChange={event => updateLine(line.key, { unitCost: event.target.value })} placeholder="Opcional" aria-label={`Custo unitário do item ${index + 1}`} /></Field>
                                    <div className="sm:pt-6">
                                        <IconButton label={`Remover item ${index + 1}${line.variant ? ` (${variantLabel(line.variant)})` : ''}`} tone="danger" disabled={lines.length === 1} onClick={() => setLines(current => current.filter(item => item.key !== line.key))}>
                                            <Trash2 className="h-4 w-4" aria-hidden="true" />
                                        </IconButton>
                                    </div>
                                </li>
                            );
                        })}
                    </ul>
                    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-adm-line px-5 py-4">
                        <p className="text-sm text-adm-ink-muted">{lines.length} {lines.length === 1 ? 'linha' : 'linhas'} · <strong className="text-adm-ink">{totalUnits}</strong> unidades</p>
                        <div className="flex gap-2">
                            <LinkButton href="/admin/estoque/entradas">Cancelar</LinkButton>
                            <Button type="submit" variant="primary" loading={submitting} disabled={submitting}>Registrar entrada</Button>
                        </div>
                    </div>
                </Card>
            </form>

            <SupplierModal
                open={supplierModalOpen}
                onClose={() => setSupplierModalOpen(false)}
                onCreated={async supplier => { await reloadSuppliers(); setSupplierId(supplier.id); }}
            />
        </div>
    );
}

function SupplierModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (supplier: Supplier) => Promise<void> }) {
    return (
        <Modal open={open} onClose={onClose} title="Novo fornecedor" description="Fabricante ou fornecedor de quem você recebe mercadoria." size="sm">
            {open && <SupplierForm onClose={onClose} onCreated={onCreated} />}
        </Modal>
    );
}

function SupplierForm({ onClose, onCreated }: { onClose: () => void; onCreated: (supplier: Supplier) => Promise<void> }) {
    const [name, setName] = useState('');
    const [document, setDocument] = useState('');
    const [saving, setSaving] = useState(false);

    const save = async () => {
        if (!name.trim() || saving) return;
        setSaving(true);
        try {
            const supplier = await stockService.createSupplier({ name: name.trim(), document: document.trim() || null });
            await onCreated(supplier);
            toast.success('Fornecedor cadastrado.');
            onClose();
        } catch (error) {
            toast.error(getApiErrorMessage(error, 'Não foi possível cadastrar o fornecedor.'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <form className="space-y-4" onSubmit={event => { event.preventDefault(); void save(); }}>
            <Field label="Nome"><Input value={name} maxLength={160} onChange={event => setName(event.target.value)} autoFocus /></Field>
            <Field label="CNPJ ou CPF (opcional)" hint="Apenas números ou com pontuação."><Input value={document} maxLength={20} onChange={event => setDocument(event.target.value)} inputMode="numeric" /></Field>
            <div className="flex justify-end gap-2 border-t border-adm-line pt-4">
                <Button onClick={onClose} disabled={saving}>Cancelar</Button>
                <Button type="submit" variant="primary" loading={saving} disabled={!name.trim()}>Cadastrar</Button>
            </div>
        </form>
    );
}
