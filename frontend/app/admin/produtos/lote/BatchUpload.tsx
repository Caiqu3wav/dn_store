'use client';

import { useState, useRef } from 'react';
import Image from 'next/image';
import { useRouter } from 'next/navigation';
import { ArrowLeft, Save, Upload, FileSpreadsheet, Image as ImageIcon, X, Plus, ChevronDown, ChevronUp } from 'lucide-react';
import useSWR from 'swr';
import { fetcher } from '@/services/productService';
import { Category, PRODUCT_VARIANT_SIZES } from '@/types';
import { toast } from 'react-hot-toast';
import { uploadToCloudinary } from '@/utils/cloudinary';
import api from '@/lib/axios';
import { normalizeProductColor } from '@/utils/productColor';
import { Button, Card, EmptyState, IconButton, IconLink, PageHeader } from '../../_components/ui';
import { Checkbox, Field, Input, Select, Textarea } from '../../_components/form';

interface DraftProduct {
    id: string;
    name: string;
    categoryId: string;
    price: string;
    promotionalPrice: string;
    variants: string;
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
            id: generateId(), name: '', categoryId: '', price: '', promotionalPrice: '', variants: '', description: '', color: '',
            weight: '', width: '', height: '', depth: '', active: true, files: [], previews: [], isExpanded: true
        }]);
    };

    const handleCsvUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        e.target.value = '';

        // papaparse só é necessário ao importar um CSV; carregá-lo sob demanda tira ele do bundle da rota.
        const { default: Papa } = await import('papaparse');
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
                        variants: row.variants || row.variations || row.variacoes || row['variações'] || '',
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
    };

    const handlePhotosUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
        const files = Array.from(e.target.files || []).filter(f => f.type.startsWith('image/'));
        if (files.length === 0) return;

        const newDrafts = files.map(file => ({
            id: generateId(),
            name: file.name.split('.')[0], // Use filename as default name
            categoryId: '', price: '', promotionalPrice: '', variants: '', description: '', color: '',
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

        const parsedVariants = drafts.map(draft => {
            const parts = draft.variants.split(';').map(part => part.trim()).filter(Boolean);
            const variants = parts.map(part => {
                const separator = part.lastIndexOf(':');
                const size = part.slice(0, separator).trim();
                const stockText = part.slice(separator + 1).trim();
                return {
                    size,
                    stock: /^\d+$/.test(stockText) ? Number(stockText) : Number.NaN,
                    active: true
                };
            });
            const uniqueSizes = new Set(variants.map(variant => variant.size));
            const valid = variants.length > 0
                && variants.every(variant =>
                    (PRODUCT_VARIANT_SIZES as readonly string[]).includes(variant.size)
                    && Number.isSafeInteger(variant.stock)
                    && variant.stock >= 0)
                && uniqueSizes.size === variants.length;
            return valid ? variants : null;
        });

        const invalid = drafts.findIndex(d => !d.name || !d.price || !d.categoryId);
        if (invalid !== -1) {
            toast.error(`Produto "${drafts[invalid].name || 'Sem nome'}" possui campos obrigatórios vazios.`);
            return;
        }

        const invalidVariants = parsedVariants.findIndex(variants => variants === null);
        if (invalidVariants !== -1) {
            toast.error(`Informe tamanhos e estoques válidos para "${drafts[invalidVariants].name || 'Sem nome'}" (ex.: P:5; M:2).`);
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

            for (const [index, draft] of drafts.entries()) {
                const variants = parsedVariants[index]!;
                // Upload images
                const imageUrls = await Promise.all(draft.files.map(f => uploadToCloudinary(f)));
                const imagesPayload = imageUrls.map((url, i) => ({ imageUrl: url, main: i === 0 }));

                payload.push({
                    name: draft.name,
                    category: { id: draft.categoryId },
                    price: parseFloat(draft.price),
                    promotionalPrice: draft.promotionalPrice ? parseFloat(draft.promotionalPrice) : null,
                    stock: variants.reduce((total, variant) => total + variant.stock, 0),
                    variants,
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
        <div className={`mx-auto max-w-6xl space-y-6 ${drafts.length > 0 ? 'pb-28' : 'pb-12'}`}>
            <PageHeader
                eyebrow="Catálogo"
                title="Upload em Lote"
                description="Crie múltiplos produtos de uma vez usando Fotos ou Planilha."
                leading={
                    <IconLink href="/admin/produtos" label="Voltar para produtos" className="h-10 w-10 border border-adm-line bg-adm-raised">
                        <ArrowLeft className="h-5 w-5" aria-hidden="true" />
                    </IconLink>
                }
            />

            {/* Ações Globais */}
            <Card as="section" aria-label="Ações do lote" className="flex flex-wrap gap-3 p-5">
                <Button variant="warning" onClick={() => csvInputRef.current?.click()} className="border-adm-accent-ink/50 text-adm-accent-ink hover:bg-adm-accent/10">
                    <FileSpreadsheet className="h-5 w-5" aria-hidden="true" /> Importar CSV
                </Button>
                <input type="file" accept=".csv" ref={csvInputRef} onChange={handleCsvUpload} className="hidden" aria-label="Arquivo CSV" />

                <Button variant="warning" onClick={() => fileInputRef.current?.click()} className="border-adm-accent-ink/50 text-adm-accent-ink hover:bg-adm-accent/10">
                    <Upload className="h-5 w-5" aria-hidden="true" /> Importar Fotos
                </Button>
                <input type="file" multiple accept="image/*" ref={fileInputRef} onChange={handlePhotosUpload} className="hidden" aria-label="Fotos dos produtos" />

                <Button onClick={addDraft} className="sm:ml-auto">
                    <Plus className="h-5 w-5" aria-hidden="true" /> Adicionar Produto Manual
                </Button>
            </Card>

            {/* Lista de Drafts */}
            <div className="space-y-4">
                {drafts.map((draft, index) => (
                    <Card key={draft.id} className="overflow-hidden">
                        {/* Header do Card (Colapsável) */}
                        <div className="flex cursor-pointer items-center justify-between gap-3 bg-adm-sunken p-4" onClick={() => toggleExpand(draft.id)}>
                            <button type="button" aria-expanded={draft.isExpanded} className="flex min-w-0 flex-1 items-center gap-4 rounded text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-adm-accent-ink">
                                <span className="font-bold text-adm-accent-ink">#{index + 1}</span>
                                {draft.previews[0] ? (
                                    <Image src={draft.previews[0]} alt={`Prévia do produto ${draft.name || 'sem nome'}`} width={40} height={40} unoptimized className="h-10 w-10 shrink-0 rounded object-cover" />
                                ) : (
                                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded bg-adm-raised"><ImageIcon className="h-5 w-5 text-adm-ink-subtle" aria-hidden="true" /></div>
                                )}
                                <div className="min-w-0">
                                    <p className="truncate font-medium">{draft.name || "Sem nome"}</p>
                                    <p className="text-xs text-adm-ink-muted">
                                        R$ {draft.price || "0.00"} • {draft.files.length} fotos
                                    </p>
                                </div>
                            </button>
                            <div className="flex items-center gap-2">
                                <IconButton tone="danger" label={`Remover produto ${draft.name || index + 1} do lote`} onClick={(e) => { e.stopPropagation(); removeDraft(draft.id); }}>
                                    <X className="h-5 w-5" aria-hidden="true" />
                                </IconButton>
                                {draft.isExpanded ? <ChevronUp className="h-5 w-5 text-adm-ink-muted" aria-hidden="true" /> : <ChevronDown className="h-5 w-5 text-adm-ink-muted" aria-hidden="true" />}
                            </div>
                        </div>

                        {/* Corpo Expandido */}
                        {draft.isExpanded && (
                            <div className="grid grid-cols-1 gap-6 p-5 md:grid-cols-12">
                                {/* Formulário */}
                                <div className="space-y-4 md:col-span-8">
                                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
                                        <Field label="Nome *">
                                            <Input value={draft.name} onChange={e => updateDraft(draft.id, 'name', e.target.value)} />
                                        </Field>
                                        <Field label="Categoria *">
                                            <Select value={draft.categoryId} onChange={e => updateDraft(draft.id, 'categoryId', e.target.value)}>
                                                <option value="">Selecione...</option>
                                                {categories?.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                                            </Select>
                                        </Field>
                                        <Field label="Preço (R$) *">
                                            <Input type="number" step="0.01" value={draft.price} onChange={e => updateDraft(draft.id, 'price', e.target.value)} />
                                        </Field>
                                        <Field label="Tamanhos e estoques *">
                                            <Input value={draft.variants} onChange={e => updateDraft(draft.id, 'variants', e.target.value)} placeholder="P:5; M:2; BL P:1" />
                                        </Field>
                                    </div>
                                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
                                        <Field label="Preço promocional">
                                            <Input type="number" step="0.01" value={draft.promotionalPrice} onChange={e => updateDraft(draft.id, 'promotionalPrice', e.target.value)} />
                                        </Field>
                                        <Field label="Cor">
                                            <Input value={draft.color} onChange={e => updateDraft(draft.id, 'color', e.target.value)} />
                                        </Field>
                                        {[['weight', 'Peso (kg)'], ['width', 'Largura (cm)'], ['height', 'Altura (cm)'], ['depth', 'Profundidade (cm)']].map(([field, label]) => (
                                            <Field key={field} label={label}>
                                                <Input type="number" step="0.01" value={draft[field as keyof DraftProduct] as string} onChange={e => updateDraft(draft.id, field as keyof DraftProduct, e.target.value)} />
                                            </Field>
                                        ))}
                                        <Checkbox className="self-end pb-2" checked={draft.active} onChange={e => updateDraft(draft.id, 'active', e.target.checked)}>
                                            Produto ativo
                                        </Checkbox>
                                    </div>
                                    <Field label="Descrição">
                                        <Textarea rows={2} value={draft.description} onChange={e => updateDraft(draft.id, 'description', e.target.value)} />
                                    </Field>
                                </div>
                                {/* Fotos */}
                                <div className="rounded-adm-card border border-adm-line bg-adm-sunken p-4 md:col-span-4">
                                    <div className="mb-3 flex items-center justify-between">
                                        <h4 className="text-sm font-medium">Fotos</h4>
                                        <label className="flex cursor-pointer items-center rounded text-xs font-medium text-adm-accent-ink hover:underline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-adm-accent-ink">
                                            <input type="file" multiple accept="image/*" className="sr-only" onChange={(e) => {
                                                if(e.target.files) addImagesToDraft(draft.id, Array.from(e.target.files));
                                            }} />
                                            <Plus className="mr-1 h-3 w-3" aria-hidden="true" /> Adicionar
                                        </label>
                                    </div>
                                    <div className="grid grid-cols-3 gap-2">
                                        {draft.previews.map((prev, i) => (
                                            <div key={i} className="group relative aspect-square overflow-hidden rounded border border-adm-line">
                                                <Image src={prev} alt={`Prévia ${i + 1} do produto ${draft.name || 'sem nome'}`} fill sizes="(max-width: 640px) 33vw, 160px" unoptimized className="rounded object-cover" />
                                                <button type="button" onClick={() => removeImage(draft.id, i)} aria-label={`Remover foto ${i + 1}`} className="absolute -right-1 -top-1 rounded-full bg-adm-accent p-1 text-white shadow-lg transition-opacity focus-visible:opacity-100 group-hover:opacity-100 sm:opacity-0">
                                                    <X className="h-3 w-3" aria-hidden="true" />
                                                </button>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        )}
                    </Card>
                ))}

                {drafts.length === 0 && (
                    <EmptyState
                        icon={FileSpreadsheet}
                        className="py-20"
                        title="Nenhum produto na lista de upload."
                        description="Importe via CSV, Fotos ou Adicione Manualmente."
                    />
                )}
            </div>

            {/* Salvar Tudos */}
            {drafts.length > 0 && (
                <div className="fixed bottom-0 left-0 right-0 z-30 border-t border-adm-line bg-adm-sunken/90 p-4 backdrop-blur lg:left-64">
                    <div className="mx-auto flex max-w-6xl justify-end gap-4">
                        <Button variant="primary" size="lg" loading={loading} onClick={handleSubmit}>
                            {!loading && <Save className="h-5 w-5" aria-hidden="true" />}
                            Salvar {drafts.length} Produtos
                        </Button>
                    </div>
                </div>
            )}
        </div>
    );
}
