'use client';

import { useState } from 'react';
import useSWR from 'swr';
import { Plus, Edit2, Trash2, Save, X, Loader2 } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { categoryService } from '@/services/categoryService';
import { Category } from '@/types';
import { toast } from 'react-hot-toast';

export default function AdminCategoriesPage() {
    const { data: categories, mutate } = useSWR<Category[]>('/categories', fetcher);
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
        } catch (error: any) {
            toast.error(error.response?.data || 'Erro ao criar categoria.');
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
        } catch (error: any) {
            toast.error(error.response?.data || 'Erro ao atualizar categoria.');
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async (id: string) => {
        if (!confirm('Deseja excluir esta categoria? Isso pode afetar produtos vinculados.')) return;

        try {
            await categoryService.deleteCategory(id);
            toast.success('Categoria excluída!');
            mutate();
        } catch (error: any) {
            toast.error('Erro ao excluir categoria.');
        }
    };

    return (
        <div className="space-y-8 text-white max-w-6xl mx-auto">
            <div>
                <h1 className="text-3xl font-bold">Categorias</h1>
                <p className="text-gray-400 mt-1">Gerencie as categorias de produtos da loja.</p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                {/* Form Criação */}
                <div className="bg-[#2A2A2A] border border-white/5 p-6 rounded-xl h-fit space-y-4">
                    <h2 className="text-xl font-bold border-b border-white/5 pb-2">Nova Categoria</h2>
                    <form onSubmit={handleCreate} className="space-y-4">
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-gray-300">Nome *</label>
                            <input 
                                required
                                type="text"
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2 px-3 text-white focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary"
                                placeholder="Ex: Acessórios"
                            />
                        </div>
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-gray-300">Descrição</label>
                            <textarea 
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                rows={3}
                                className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2 px-3 text-white focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary"
                                placeholder="Ex: Itens e wearables adicionais..."
                            />
                        </div>
                        <button
                            type="submit"
                            disabled={isSaving}
                            className="w-full bg-brand-secondary hover:bg-red-700 text-white font-bold py-2 px-4 rounded-lg flex items-center justify-center gap-2 transition-colors disabled:opacity-50"
                        >
                            {isSaving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                            Criar Categoria
                        </button>
                    </form>
                </div>

                {/* Listagem */}
                <div className="lg:col-span-2 bg-[#2A2A2A] border border-white/5 rounded-xl overflow-hidden">
                    <div className="p-6 border-b border-white/5">
                        <h2 className="text-xl font-bold">Categorias Cadastradas</h2>
                    </div>
                    
                    <div className="overflow-x-auto">
                        <table className="w-full text-left border-collapse">
                            <thead>
                                <tr className="border-b border-white/5 text-gray-400 text-sm">
                                    <th className="p-4 font-semibold">Nome</th>
                                    <th className="p-4 font-semibold">Descrição</th>
                                    <th className="p-4 font-semibold text-right">Ações</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-white/5">
                                {!categories ? (
                                    <tr>
                                        <td colSpan={3} className="p-8 text-center text-gray-500">
                                            <Loader2 className="w-6 h-6 animate-spin mx-auto mb-2" />
                                            Carregando categorias...
                                        </td>
                                    </tr>
                                ) : categories.length === 0 ? (
                                    <tr>
                                        <td colSpan={3} className="p-8 text-center text-gray-500">
                                            Nenhuma categoria cadastrada.
                                        </td>
                                    </tr>
                                ) : (
                                    categories.map((category) => (
                                        <tr key={category.id} className="hover:bg-white/[0.01] transition-colors">
                                            {editingId === category.id ? (
                                                <>
                                                    <td className="p-4">
                                                        <input 
                                                            required
                                                            type="text"
                                                            value={editName}
                                                            onChange={(e) => setEditName(e.target.value)}
                                                            className="bg-[#1A1B1D] border border-brand-secondary rounded px-2 py-1 text-white text-sm w-full"
                                                        />
                                                    </td>
                                                    <td className="p-4">
                                                        <input 
                                                            type="text"
                                                            value={editDescription}
                                                            onChange={(e) => setEditDescription(e.target.value)}
                                                            className="bg-[#1A1B1D] border border-white/10 rounded px-2 py-1 text-white text-sm w-full"
                                                        />
                                                    </td>
                                                    <td className="p-4 text-right">
                                                        <div className="flex justify-end gap-2">
                                                            <button 
                                                                onClick={() => handleUpdate(category.id)}
                                                                className="p-1.5 bg-green-600 hover:bg-green-700 rounded text-white transition-colors"
                                                                title="Salvar"
                                                            >
                                                                <Save className="w-4 h-4" />
                                                            </button>
                                                            <button 
                                                                onClick={handleEditCancel}
                                                                className="p-1.5 bg-[#3A3A3A] hover:bg-[#4A4A4A] rounded text-gray-300 transition-colors"
                                                                title="Cancelar"
                                                            >
                                                                <X className="w-4 h-4" />
                                                            </button>
                                                        </div>
                                                    </td>
                                                </>
                                            ) : (
                                                <>
                                                    <td className="p-4 font-semibold text-white">{category.name}</td>
                                                    <td className="p-4 text-gray-400 text-sm max-w-xs truncate">{category.description || '-'}</td>
                                                    <td className="p-4 text-right">
                                                        <div className="flex justify-end gap-2">
                                                            <button 
                                                                onClick={() => handleEditStart(category)}
                                                                className="p-1.5 bg-[#3A3A3A] hover:bg-white/10 rounded text-gray-300 hover:text-white transition-colors"
                                                            >
                                                                <Edit2 className="w-4 h-4" />
                                                            </button>
                                                            <button 
                                                                onClick={() => handleDelete(category.id)}
                                                                className="p-1.5 bg-[#3A3A3A] hover:bg-red-500/20 rounded text-gray-400 hover:text-red-500 transition-colors"
                                                            >
                                                                <Trash2 className="w-4 h-4" />
                                                            </button>
                                                        </div>
                                                    </td>
                                                </>
                                            )}
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    );
}