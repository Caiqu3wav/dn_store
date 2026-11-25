'use client';

import { useState } from 'react';
import useSWR from 'swr';
import { Plus, Edit2, Trash2, Save, X, Tags } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { categoryService } from '@/services/categoryService';
import { Category } from '@/types';
import { toast } from 'react-hot-toast';
import axios from 'axios';
import { Button, Card, CardHeader, EmptyState, ErrorState, IconButton, PageHeader, TableSkeletonRows } from '../_components/ui';
import { Field, Input, Textarea } from '../_components/form';
import { Table, TableWrap, Tbody, Td, Th, Thead, rowClass } from '../_components/table';
import { useConfirm } from '../_components/confirm';

function getErrorMessage(error: unknown, fallback: string) {
    if (!axios.isAxiosError(error)) return fallback;

    const responseData: unknown = error.response?.data;
    if (typeof responseData === 'string') return responseData;
    if (
        typeof responseData === 'object'
        && responseData !== null
        && 'message' in responseData
        && typeof responseData.message === 'string'
    ) {
        return responseData.message;
    }

    return error.message || fallback;
}

export default function AdminCategoriesPage() {
    const confirm = useConfirm();
    const { data: categories, error: categoriesError, mutate } = useSWR<Category[]>('/categories', fetcher);
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [isSaving, setIsSaving] = useState(false);

    // Edit mode states
    const [editingId, setEditingId] = useState<string | null>(null);
    const [editName, setEditName] = useState('');
    const [editDescription, setEditDescription] = useState('');

    const handleCreate = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!name.trim()) return;

        setIsSaving(true);
        try {
            await categoryService.createCategory({ name, description });
            toast.success('Categoria criada com sucesso!');
            setName('');
            setDescription('');
            mutate();
        } catch (error: unknown) {
            toast.error(getErrorMessage(error, 'Erro ao criar categoria.'));
        } finally {
            setIsSaving(false);
        }
    };

    const handleEditStart = (category: Category) => {
        setEditingId(category.id);
        setEditName(category.name);
        setEditDescription(category.description || '');
    };

    const handleEditCancel = () => {
        setEditingId(null);
    };

    const handleUpdate = async (id: string) => {
        if (!editName.trim()) return;

        setIsSaving(true);
        try {
            await categoryService.updateCategory(id, { name: editName, description: editDescription });
            toast.success('Categoria atualizada!');
            setEditingId(null);
            mutate();
        } catch (error: unknown) {
            toast.error(getErrorMessage(error, 'Erro ao atualizar categoria.'));
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async (id: string) => {
        const confirmed = await confirm({
            title: 'Excluir categoria',
            message: 'Deseja excluir esta categoria? Isso pode afetar produtos vinculados.',
            confirmLabel: 'Excluir',
        });
        if (!confirmed) return;

        try {
            await categoryService.deleteCategory(id);
            toast.success('Categoria excluída!');
            mutate();
        } catch {
            toast.error('Erro ao excluir categoria.');
        }
    };

    return (
        <div className="mx-auto max-w-6xl space-y-6">
            <PageHeader title="Categorias" description="Gerencie as categorias de produtos da loja." />

            <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-3">
                {/* Form Criação */}
                <Card as="section" aria-label="Nova categoria">
                    <CardHeader title="Nova Categoria" />
                    <form onSubmit={handleCreate} className="space-y-4 p-5">
                        <Field label="Nome *">
                            <Input
                                required
                                type="text"
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                placeholder="Ex: Acessórios"
                            />
                        </Field>
                        <Field label="Descrição">
                            <Textarea
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                rows={3}
                                placeholder="Ex: Itens e wearables adicionais..."
                            />
                        </Field>
                        <Button type="submit" variant="primary" className="w-full" loading={isSaving}>
                            {!isSaving && <Plus className="h-4 w-4" aria-hidden="true" />}
                            Criar Categoria
                        </Button>
                    </form>
                </Card>

                {/* Listagem */}
                <Card as="section" aria-label="Categorias cadastradas" className="overflow-hidden lg:col-span-2">
                    <CardHeader title="Categorias Cadastradas" />

                    <TableWrap>
                        <Table>
                            <Thead>
                                <tr>
                                    <Th>Nome</Th>
                                    <Th>Descrição</Th>
                                    <Th className="text-right">Ações</Th>
                                </tr>
                            </Thead>
                            <Tbody>
                                {categoriesError && !categories ? (
                                    <tr>
                                        <td colSpan={3}><ErrorState title="Não foi possível carregar as categorias." onRetry={() => { void mutate(); }} /></td>
                                    </tr>
                                ) : !categories ? (
                                    <TableSkeletonRows rows={5} columns={3} />
                                ) : categories.length === 0 ? (
                                    <tr>
                                        <td colSpan={3}><EmptyState icon={Tags} title="Nenhuma categoria cadastrada." description="Use o formulário ao lado para criar a primeira." /></td>
                                    </tr>
                                ) : (
                                    categories.map((category) => (
                                        <tr key={category.id} className={rowClass()}>
                                            {editingId === category.id ? (
                                                <>
                                                    <Td>
                                                        <Input
                                                            required
                                                            type="text"
                                                            aria-label="Nome da categoria"
                                                            value={editName}
                                                            onChange={(e) => setEditName(e.target.value)}
                                                            className="min-h-9 border-adm-accent-ink py-1"
                                                        />
                                                    </Td>
                                                    <Td>
                                                        <Input
                                                            type="text"
                                                            aria-label="Descrição da categoria"
                                                            value={editDescription}
                                                            onChange={(e) => setEditDescription(e.target.value)}
                                                            className="min-h-9 py-1"
                                                        />
                                                    </Td>
                                                    <Td className="text-right">
                                                        <div className="flex justify-end gap-1">
                                                            <IconButton
                                                                label="Salvar"
                                                                onClick={() => handleUpdate(category.id)}
                                                                disabled={isSaving}
                                                                className="bg-adm-success/15 text-adm-success hover:bg-adm-success/25 hover:text-adm-success"
                                                            >
                                                                <Save className="h-4 w-4" aria-hidden="true" />
                                                            </IconButton>
                                                            <IconButton label="Cancelar" onClick={handleEditCancel}>
                                                                <X className="h-4 w-4" aria-hidden="true" />
                                                            </IconButton>
                                                        </div>
                                                    </Td>
                                                </>
                                            ) : (
                                                <>
                                                    <Td className="font-medium">{category.name}</Td>
                                                    <Td className="max-w-xs truncate text-adm-ink-muted">{category.description || '-'}</Td>
                                                    <Td className="text-right">
                                                        <div className="flex justify-end gap-1">
                                                            <IconButton label={`Editar ${category.name}`} onClick={() => handleEditStart(category)}>
                                                                <Edit2 className="h-4 w-4" aria-hidden="true" />
                                                            </IconButton>
                                                            <IconButton tone="danger" label={`Excluir ${category.name}`} onClick={() => handleDelete(category.id)}>
                                                                <Trash2 className="h-4 w-4" aria-hidden="true" />
                                                            </IconButton>
                                                        </div>
                                                    </Td>
                                                </>
                                            )}
                                        </tr>
                                    ))
                                )}
                            </Tbody>
                        </Table>
                    </TableWrap>
                </Card>
            </div>
        </div>
    );
}
