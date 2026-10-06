'use client';

import { useState, useRef } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ArrowLeft, Save, Upload, FileSpreadsheet, Image as ImageIcon, Loader2, X, Plus, ChevronDown, ChevronUp } from 'lucide-react';
import useSWR from 'swr';
import { fetcher } from '@/services/productService';
import { Category } from '@/types';
import { toast } from 'react-hot-toast';
import { uploadToCloudinary } from '@/utils/cloudinary';
import Papa from 'papaparse';
import api from '@/lib/axios';
import { normalizeProductColor } from '@/utils/productColor';

interface DraftProduct {
    id: string;
    name: string;
    categoryId: string;
    price: string;
    promotionalPrice: string;
    stock: string;
    description: string;
    color: string;
    weight: string;
    width: string;
    height: string;
    depth: string;
    active: boolean;
    files: File[];
    previews: string[];
    isExpanded?: boolean;
}

type CsvRow = Record<string, string | undefined>;

export default function BatchUpload() {
    const router = useRouter();
    const { data: categories } = useSWR<Category[]>('/categories', fetcher);
    
    const [mode, setMode] = useState<'csv' | 'photos' | null>(null);
    const [drafts, setDrafts] = useState<DraftProduct[]>([]);
    const [loading, setLoading] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const csvInputRef = useRef<HTMLInputElement>(null);

    const generateId = () => Math.random().toString(36).substring(2, 9);

    const addDraft = () => {
        setDrafts(prev => [...prev, {
            id: generateId(), name: '', categoryId: '', price: '', promotionalPrice: '', stock: '', description: '', color: '',
            weight: '', width: '', height: '', depth: '', active: true, files: [], previews: [], isExpanded: true
        }]);
    };

    const handleCsvUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;

        Papa.parse(file, {
            header: true,
            skipEmptyLines: true,
            complete: (results) => {
                const newDrafts: DraftProduct[] = (results.data as CsvRow[]).map((row) => {
                    // Tentar achar categoria pelo nome se vier no CSV
                    const catName = row.category || row.categoria;
                    let foundCatId = '';
                    if (catName && categories) {
                        const found = categories.find(c => c.name.toLowerCase() === catName.toLowerCase());
                        if (found) foundCatId = found.id;
                    }

                    return {
                        id: generateId(),
                        name: row.name || row.nome || '',
                        price: row.price || row.preco || row.preço || '',
                        promotionalPrice: row.promotionalPrice || row.precoPromocional || row.preçoPromocional || '',
                        stock: row.stock || row.estoque || row.quantidade || '',
                        description: row.description || row.descricao || row.descrição || '',
                        color: row.color || row.cor || '',
                        weight: row.weight || row.peso || '',
                        width: row.width || row.largura || '',
                        height: row.height || row.altura || '',
                        depth: row.depth || row.profundidade || '',
                        active: row.active === undefined ? true : String(row.active).toLowerCase() !== 'false',
                        categoryId: row.categoryId || foundCatId,
                        files: [],
                        previews: [],
                        isExpanded: false
                    };
                });
                
                if (mode === 'photos' && drafts.length > 0) {
                    // Try to merge CSV data into existing photo drafts sequentially
                    const merged = [...drafts];
                    newDrafts.forEach((nd, i) => {
                        if (merged[i]) {
                            merged[i] = { ...merged[i], ...nd, files: merged[i].files, previews: merged[i].previews, isExpanded: false };
                        } else {
                            merged.push(nd);
                        }
                    });
                    setDrafts(merged);
                } else {
                    setDrafts(prev => [...prev, ...newDrafts]);
                    setMode('csv');
                }
                toast.success(`${results.data.length} produtos importados do CSV`);
            },
            error: (err) => {
                toast.error('Erro ao ler CSV');
                console.error(err);
            }
        });
        e.target.value = '';
    };

    const handlePhotosUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
        const files = Array.from(e.target.files || []).filter(f => f.type.startsWith('image/'));
        if (files.length === 0) return;

        const newDrafts = files.map(file => ({
            id: generateId(),
            name: file.name.split('.')[0], // Use filename as default name
            categoryId: '', price: '', promotionalPrice: '', stock: '', description: '', color: '',
            weight: '', width: '', height: '', depth: '',
            active: true,
            files: [file],
            previews: [URL.createObjectURL(file)],
            isExpanded: true
        }));

        setDrafts(prev => [...prev, ...newDrafts]);
        if (!mode) setMode('photos');
        e.target.value = '';
    };

    const addImagesToDraft = (draftId: string, newFiles: File[]) => {
        const imageFiles = newFiles.filter(f => f.type.startsWith('image/'));
        if (imageFiles.length === 0) return;

        setDrafts(prev => prev.map(draft => {
            if (draft.id === draftId) {
                return {
                    ...draft,
                    files: [...draft.files, ...imageFiles],
                    previews: [...draft.previews, ...imageFiles.map(f => URL.createObjectURL(f))]
                };
            }
            return draft;
        }));
    };

    const removeImage = (draftId: string, imgIndex: number) => {
        setDrafts(prev => prev.map(draft => {
            if (draft.id === draftId) {
                return {
                    ...draft,
                    files: draft.files.filter((_, i) => i !== imgIndex),
                    previews: draft.previews.filter((_, i) => i !== imgIndex)
                };
            }
            return draft;
        }));
    };

    const updateDraft = (id: string, field: keyof DraftProduct, value: string | boolean) => {
        setDrafts(prev => prev.map(d => d.id === id ? { ...d, [field]: value } : d));
    };

    const removeDraft = (id: string) => {
        setDrafts(prev => prev.filter(d => d.id !== id));
    };

    const toggleExpand = (id: string) => {
        setDrafts(prev => prev.map(d => d.id === id ? { ...d, isExpanded: !d.isExpanded } : d));
    };

    const handleSubmit = async () => {
        if (drafts.length === 0) return toast.error('Nenhum produto para salvar');
        
        // Validate
        const invalid = drafts.find(d => !d.name || !d.price || !d.categoryId || !d.stock);
        if (invalid) {
            toast.error(`Produto "${invalid.name || 'Sem nome'}" possui campos obrigatórios vazios.`);
            return;
        }

        const invalidColor = drafts.find(draft => normalizeProductColor(draft.color) === null);
        if (invalidColor) {
            toast.error(`Cor inválida no produto "${invalidColor.name || 'Sem nome'}".`);
            return;
        }

        setLoading(true);
        try {
            const payload = [];

            for (const draft of drafts) {
                // Upload images
                const imageUrls = await Promise.all(draft.files.map(f => uploadToCloudinary(f)));
                const imagesPayload = imageUrls.map((url, i) => ({ imageUrl: url, main: i === 0 }));

                payload.push({
                    name: draft.name,
                    category: { id: draft.categoryId },
                    price: parseFloat(draft.price),
                    promotionalPrice: draft.promotionalPrice ? parseFloat(draft.promotionalPrice) : null,
                    stock: parseInt(draft.stock),
                    description: draft.description,
                    color: normalizeProductColor(draft.color),
                    active: draft.active,
                    weight: draft.weight ? parseFloat(draft.weight) : null,
                    width: draft.width ? parseFloat(draft.width) : null,
                    height: draft.height ? parseFloat(draft.height) : null,
                    depth: draft.depth ? parseFloat(draft.depth) : null,
                    images: imagesPayload
                });
            }

            // Send batch to backend
            await api.post('/products/batch', payload);

            toast.success(`${drafts.length} produtos salvos com sucesso!`);
            router.push('/admin/produtos');

        } catch (error) {
            console.error(error);
            toast.error('Erro ao salvar lote de produtos.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="space-y-6 text-white max-w-6xl mx-auto pb-12">
            <div className="flex items-center gap-4">
                <Link href="/admin/produtos" className="p-2 bg-[#2A2A2A] border border-white/10 rounded-lg hover:bg-white/10">
                    <ArrowLeft className="w-5 h-5" />
                </Link>
                <div>
                    <h1 className="text-3xl font-bold">Upload em Lote</h1>
                    <p className="text-gray-400 mt-1">Crie múltiplos produtos de uma vez usando Fotos ou Planilha.</p>
                </div>
            </div>

            {/* Ações Globais */}
            <div className="bg-[#2A2A2A] border border-white/5 p-6 rounded-xl shadow-sm flex flex-wrap gap-4">
                <button onClick={() => csvInputRef.current?.click()} className="px-4 py-2 rounded-lg border border-brand-secondary text-brand-secondary hover:bg-brand-secondary/10 flex items-center gap-2">
                    <FileSpreadsheet className="w-5 h-5" /> Importar CSV
                </button>
                <input type="file" accept=".csv" ref={csvInputRef} onChange={handleCsvUpload} className="hidden" />

                <button onClick={() => fileInputRef.current?.click()} className="px-4 py-2 rounded-lg border border-brand-secondary text-brand-secondary hover:bg-brand-secondary/10 flex items-center gap-2">
                    <Upload className="w-5 h-5" /> Importar Fotos
                </button>
                <input type="file" multiple accept="image/*" ref={fileInputRef} onChange={handlePhotosUpload} className="hidden" />
                
                <button onClick={addDraft} className="px-4 py-2 rounded-lg border border-white/10 hover:bg-white/10 flex items-center gap-2 ml-auto">
                    <Plus className="w-5 h-5" /> Adicionar Produto Manual
                </button>
            </div>

            {/* Lista de Drafts */}
            <div className="space-y-4">
                {drafts.map((draft, index) => (
                    <div key={draft.id} className="bg-[#2A2A2A] border border-white/10 rounded-xl overflow-hidden">
                        {/* Header do Card (Colapsável) */}
                        <div className="flex items-center justify-between p-4 bg-[#1A1B1D] cursor-pointer" onClick={() => toggleExpand(draft.id)}>
                            <div className="flex items-center gap-4">
                                <span className="text-brand-secondary font-bold">#{index + 1}</span>
                                {draft.previews[0] ? (
                                    <img src={draft.previews[0]} className="w-10 h-10 object-cover rounded" />
                                ) : (
                                    <div className="w-10 h-10 bg-black/30 rounded flex items-center justify-center"><ImageIcon className="w-5 h-5 text-gray-500" /></div>
                                )}
                                <div>
                                    <p className="font-medium">{draft.name || "Sem nome"}</p>
                                    <p className="text-xs text-gray-400">
                                        R$ {draft.price || "0.00"} • {draft.files.length} fotos
                                    </p>
                                </div>
                            </div>
                            <div className="flex items-center gap-4">
                                <button onClick={(e) => { e.stopPropagation(); removeDraft(draft.id); }} className="p-2 text-red-500 hover:bg-red-500/10 rounded-lg">
                                    <X className="w-5 h-5" />
                                </button>
                                {draft.isExpanded ? <ChevronUp className="w-5 h-5 text-gray-400" /> : <ChevronDown className="w-5 h-5 text-gray-400" />}
                            </div>
                        </div>

                        {/* Corpo Expandido */}
                        {draft.isExpanded && (
                            <div className="p-6 grid grid-cols-1 md:grid-cols-12 gap-6">
                                {/* Formulário */}
                                <div className="md:col-span-8 space-y-4">
                                    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3">
                                        <div className="space-y-1">
                                            <label className="text-xs text-gray-400">Nome *</label>
                                            <input value={draft.name} onChange={e => updateDraft(draft.id, 'name', e.target.value)} className="w-full bg-[#1A1B1D] border border-white/10 rounded px-3 py-2 text-sm" />
                                        </div>
                                        <div className="space-y-1">
                                            <label className="text-xs text-gray-400">Categoria *</label>
                                            <select value={draft.categoryId} onChange={e => updateDraft(draft.id, 'categoryId', e.target.value)} className="w-full bg-[#1A1B1D] border border-white/10 rounded px-3 py-2 text-sm">
                                                <option value="">Selecione...</option>
                                                {categories?.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                                            </select>
                                        </div>
                                        <div className="space-y-1">
                                            <label className="text-xs text-gray-400">Preço (R$) *</label>
                                            <input type="number" step="0.01" value={draft.price} onChange={e => updateDraft(draft.id, 'price', e.target.value)} className="w-full bg-[#1A1B1D] border border-white/10 rounded px-3 py-2 text-sm" />
                                        </div>
                                        <div className="space-y-1">
                                            <label className="text-xs text-gray-400">Estoque *</label>
                                            <input type="number" value={draft.stock} onChange={e => updateDraft(draft.id, 'stock', e.target.value)} className="w-full bg-[#1A1B1D] border border-white/10 rounded px-3 py-2 text-sm" />
                                        </div>
                                    </div>
                                    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3 mt-3">
                                        <div className="space-y-1">
                                            <label className="text-xs text-gray-400">Preço promocional</label>
                                            <input type="number" step="0.01" value={draft.promotionalPrice} onChange={e => updateDraft(draft.id, 'promotionalPrice', e.target.value)} className="w-full bg-[#1A1B1D] border border-white/10 rounded px-3 py-2 text-sm" />
                                        </div>
                                        <div className="space-y-1">
                                            <label className="text-xs text-gray-400">Cor</label>
                                            <input value={draft.color} onChange={e => updateDraft(draft.id, 'color', e.target.value)} className="w-full bg-[#1A1B1D] border border-white/10 rounded px-3 py-2 text-sm" />
                                        </div>
                                        {[['weight', 'Peso (kg)'], ['width', 'Largura (cm)'], ['height', 'Altura (cm)'], ['depth', 'Profundidade (cm)']].map(([field, label]) => (
                                            <div key={field} className="space-y-1">
                                                <label className="text-xs text-gray-400">{label}</label>
                                                <input type="number" step="0.01" value={draft[field as keyof DraftProduct] as string} onChange={e => updateDraft(draft.id, field as keyof DraftProduct, e.target.value)} className="w-full bg-[#1A1B1D] border border-white/10 rounded px-3 py-2 text-sm" />
                                            </div>
                                        ))}
                                        <label className="flex items-center gap-2 text-xs text-gray-400 self-end pb-2">
                                            <input type="checkbox" checked={draft.active} onChange={e => updateDraft(draft.id, 'active', e.target.checked)} />
                                            Produto ativo
                                        </label>
                                    </div>
                                    <div className="space-y-1 mt-3">
                                        <label className="text-xs text-gray-400">Descrição</label>
                                        <textarea rows={2} value={draft.description} onChange={e => updateDraft(draft.id, 'description', e.target.value)} className="w-full bg-[#1A1B1D] border border-white/10 rounded px-3 py-2 text-sm" />
                                    </div>
                                </div>
                                {/* Fotos */}
                                <div className="md:col-span-4 bg-[#1A1B1D] p-4 rounded-lg border border-white/5">
                                    <div className="flex items-center justify-between mb-3">
                                        <h4 className="text-sm font-medium">Fotos</h4>
                                        <label className="cursor-pointer text-xs text-brand-secondary hover:underline flex items-center">
                                            <input type="file" multiple accept="image/*" className="hidden" onChange={(e) => {
                                                if(e.target.files) addImagesToDraft(draft.id, Array.from(e.target.files));
                                            }} />
                                            <Plus className="w-3 h-3 mr-1" /> Adicionar
                                        </label>
                                    </div>
                                    <div className="grid grid-cols-3 gap-2">
                                        {draft.previews.map((prev, i) => (
                                            <div key={i} className="relative aspect-square rounded border border-white/10 group">
                                                <img src={prev} className="w-full h-full object-cover rounded" />
                                                <button onClick={() => removeImage(draft.id, i)} className="absolute -top-1 -right-1 p-1 bg-red-500 rounded-full text-white opacity-0 group-hover:opacity-100 transition-opacity shadow-lg">
                                                    <X className="w-3 h-3" />
                                                </button>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                ))}

                {drafts.length === 0 && (
                    <div className="text-center py-20 text-gray-500">
                        <FileSpreadsheet className="w-12 h-12 mx-auto mb-4 opacity-50" />
                        <p>Nenhum produto na lista de upload.</p>
                        <p className="text-sm mt-1">Importe via CSV, Fotos ou Adicione Manualmente.</p>
                    </div>
                )}
            </div>

            {/* Salvar Tudos */}
            {drafts.length > 0 && (
                <div className="fixed bottom-0 left-0 right-0 bg-[#1A1B1D]/90 backdrop-blur border-t border-white/10 p-4 z-50">
                    <div className="max-w-6xl mx-auto flex justify-end gap-4">
                        <button disabled={loading} onClick={handleSubmit} className="px-8 py-3 rounded-xl bg-brand-secondary text-white font-bold flex items-center gap-2 hover:bg-red-700 transition-colors">
                            {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : <Save className="w-5 h-5" />}
                            Salvar {drafts.length} Produtos
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}
