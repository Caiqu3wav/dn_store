'use client';

import { useState } from 'react';
import useSWR from 'swr';
import { Plus } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { fetcher } from '@/services/productService';
import { apiFailure, qs, stockService } from '@/services/stockService';
import type { PageView, Supplier } from '@/types/stock';
import { Field, Input, Select } from '../../_components/form';
import { Modal } from '../../_components/Modal';
import { Button } from '../../_components/ui';
import { Notice } from './shared';

type Props = {
    value: string;
    onChange: (supplierId: string) => void;
    invalid?: boolean;
    disabled?: boolean;
    /** Inclui a opção "Todos" (filtros) em vez de exigir um fornecedor. */
    allowAll?: boolean;
};

export function SupplierSelect({ value, onChange, invalid, disabled, allowAll }: Props) {
    const { data, error, isLoading, mutate } = useSWR<PageView<Supplier>>(
        `/admin/stock/suppliers${qs({ size: 100 })}`,
        fetcher,
    );
    const [modalOpen, setModalOpen] = useState(false);

    return (
        <div className="flex items-start gap-2">
            <Field label="Fornecedor" className="min-w-0 flex-1">
                <Select
                    value={value}
                    disabled={disabled || isLoading}
                    aria-invalid={invalid || undefined}
                    className={invalid ? 'border-adm-danger' : undefined}
                    onChange={event => onChange(event.target.value)}
                >
                    <option value="">{isLoading ? 'Carregando…' : allowAll ? 'Todos os fornecedores' : 'Selecione o fornecedor'}</option>
                    {data?.items.map(supplier => (
                        <option key={supplier.id} value={supplier.id}>{supplier.name}</option>
                    ))}
                </Select>
                {error && (
                    <span role="alert" className="block text-xs text-adm-danger">
                        Não foi possível carregar os fornecedores.{' '}
                        <button type="button" className="underline" onClick={() => mutate()}>Tentar de novo</button>
                    </span>
                )}
            </Field>
            {!allowAll && (
                <Button className="mt-[1.65rem]" onClick={() => setModalOpen(true)} disabled={disabled}>
                    <Plus className="h-4 w-4" aria-hidden="true" /> Novo
                </Button>
            )}
            <NewSupplierModal
                open={modalOpen}
                onClose={() => setModalOpen(false)}
                onCreated={async supplier => {
                    await mutate();
                    onChange(supplier.id);
                    setModalOpen(false);
                    toast.success('Fornecedor cadastrado.');
                }}
            />
        </div>
    );
}

function NewSupplierModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (supplier: Supplier) => void | Promise<void> }) {
    const [name, setName] = useState('');
    const [document, setDocument] = useState('');
    const [contact, setContact] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const close = () => {
        if (saving) return;
        setError(null);
        onClose();
    };

    const submit = async (event: React.FormEvent) => {
        event.preventDefault();
        if (saving) return;
        setSaving(true);
        setError(null);
        try {
            const supplier = await stockService.createSupplier({
                name: name.trim(),
                document: document.trim() || null,
                contact: contact.trim() || null,
            });
            setName('');
            setDocument('');
            setContact('');
            await onCreated(supplier);
        } catch (e) {
            setError(apiFailure(e, 'Não foi possível cadastrar o fornecedor.').message);
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            open={open}
            onClose={close}
            title="Novo fornecedor"
            description="Fabricante ou distribuidor de quem você recebe as notas."
            footer={
                <>
                    <Button onClick={close} disabled={saving}>Cancelar</Button>
                    <Button variant="primary" type="submit" form="new-supplier-form" loading={saving} disabled={!name.trim()}>Cadastrar</Button>
                </>
            }
        >
            <form id="new-supplier-form" onSubmit={submit} className="space-y-4">
                <Field label="Nome *">
                    <Input value={name} maxLength={150} autoFocus required onChange={event => setName(event.target.value)} />
                </Field>
                <Field label="CNPJ" hint="Opcional. 14 dígitos; pontuação é ignorada.">
                    <Input value={document} inputMode="numeric" maxLength={18} onChange={event => setDocument(event.target.value)} />
                </Field>
                <Field label="Contato">
                    <Input value={contact} maxLength={255} placeholder="Telefone, e-mail ou vendedor" onChange={event => setContact(event.target.value)} />
                </Field>
                {error && <Notice tone="danger">{error}</Notice>}
            </form>
        </Modal>
    );
}
