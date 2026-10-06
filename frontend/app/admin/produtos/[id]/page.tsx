'use client';

import { ChangeEvent, FormEvent, useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import useSWR from 'swr';
import { ArrowLeft, Image as ImageIcon, Loader2, Save, Trash2, Upload } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { fetcher, productService } from '@/services/productService';
import { Category, Product, ProductImage } from '@/types';
import { uploadToCloudinary } from '@/utils/cloudinary';
import { normalizeProductColor, productColorOptions } from '@/utils/productColor';

type ProductForm = {
    name: string;
    categoryId: string;
    color: string;
    description: string;
    price: string;
    promotionalPrice: string;
    stock: string;
    active: boolean;
    weight: string;
    width: string;
    height: string;
    depth: string;
};

const emptyForm: ProductForm = {
    name: '', categoryId: '', color: '', description: '', price: '',
    promotionalPrice: '', stock: '0', active: true,
    weight: '', width: '', height: '', depth: ''
};

const fieldClass = 'min-h-11 w-full rounded-lg border border-white/10 bg-[#1A1B1D] px-3 py-2 text-sm text-white outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary';

export default function EditProductPage() {
    const { id } = useParams<{ id: string }>();
    const router = useRouter();
    const { data: categories } = useSWR<Category[]>('/categories', fetcher);
    const [form, setForm] = useState<ProductForm>(emptyForm);
    const [images, setImages] = useState<ProductImage[]>([]);
    const [files, setFiles] = useState<File[]>([]);
    const [loadingData, setLoadingData] = useState(true);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        let cancelled = false;
        productService.getProductById(id)
            .then((product: Product) => {
                if (cancelled) return;
                const categoryId = typeof product.category === 'object' && product.category
                    ? product.category.id
                    : '';
                const physical = product as Product & { weight?: number; width?: number; height?: number; depth?: number };
                setForm({
                    name: product.name || '',
                    categoryId,
                    color: product.color || '',
                    description: product.description || '',
                    price: String(product.price ?? ''),
                    promotionalPrice: product.promotionalPrice == null ? '' : String(product.promotionalPrice),
                    stock: String(product.stock ?? 0),
                    active: product.active,
                    weight: physical.weight == null ? '' : String(physical.weight),
                    width: physical.width == null ? '' : String(physical.width),
                    height: physical.height == null ? '' : String(physical.height),
                    depth: physical.depth == null ? '' : String(physical.depth)
                });
                setImages(product.images || []);
            })
            .catch(error => {
                console.error('Erro ao carregar produto:', error);
                toast.error('Não foi possível carregar este produto.');
                router.replace('/admin/produtos');
            })
            .finally(() => {
                if (!cancelled) setLoadingData(false);
            });

        return () => { cancelled = true; };
    }, [id, router]);

    const updateField = <K extends keyof ProductForm>(field: K, value: ProductForm[K]) => {
        setForm(current => ({ ...current, [field]: value }));
    };

    const handleFiles = (event: ChangeEvent<HTMLInputElement>) => {
        const selected = Array.from(event.target.files || []).filter(file => file.type.startsWith('image/'));
        setFiles(current => [...current, ...selected]);
        event.target.value = '';
    };

    const removeImage = (imageIndex: number) => {
        setImages(current => current.filter((_, index) => index !== imageIndex));
    };

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const normalizedColor = normalizeProductColor(form.color);
        if (normalizedColor === null) {
            toast.error('Selecione uma cor válida da lista.');
            return;
        }
        setSaving(true);
        try {
            const uploadedUrls = await Promise.all(files.map(uploadToCloudinary));
            const allImages = [
                ...images,
                ...uploadedUrls.map(imageUrl => ({ imageUrl, main: false }))
            ].map((image, index) => ({ ...image, main: index === 0 }));

            await productService.updateProduct(id, {
                name: form.name.trim(),
                category: form.categoryId ? { id: form.categoryId } : null,
                color: normalizedColor,
                description: form.description,
                price: Number(form.price),
                promotionalPrice: form.promotionalPrice ? Number(form.promotionalPrice) : null,
                stock: Number(form.stock),
                active: form.active,
                weight: form.weight ? Number(form.weight) : 0,
                width: form.width ? Number(form.width) : 0,
                height: form.height ? Number(form.height) : 0,
                depth: form.depth ? Number(form.depth) : 0,
                images: allImages
            });
            toast.success('Produto atualizado com sucesso.');
            router.push('/admin/produtos');
        } catch (error) {
            console.error('Erro ao atualizar produto:', error);
            toast.error('Não foi possível salvar as alterações.');
        } finally {
            setSaving(false);
        }
    };

    if (loadingData) {
        return <div className="flex min-h-64 items-center justify-center text-gray-400"><Loader2 className="mr-2 h-5 w-5 animate-spin" /> Carregando produto...</div>;
    }

    return (
        <div className="mx-auto max-w-5xl space-y-5 pb-10 text-white">
            <header className="flex items-center gap-3 border-b border-white/10 pb-4">
                <Link href="/admin/produtos" aria-label="Voltar para produtos" className="grid h-10 w-10 shrink-0 place-items-center rounded-lg border border-white/10 bg-[#2A2A2A] text-gray-300 transition hover:bg-white/10 hover:text-white">
                    <ArrowLeft className="h-5 w-5" />
                </Link>
                <div className="min-w-0">
                    <p className="text-xs font-semibold uppercase tracking-wider text-brand-secondary">Catálogo</p>
                    <h1 className="truncate text-2xl font-bold">Editar produto</h1>
                </div>
            </header>

            <form onSubmit={handleSubmit} className="space-y-4">
                <section className="space-y-4 rounded-lg border border-white/10 bg-[#222325] p-4 sm:p-5">
                    <h2 className="border-b border-white/10 pb-3 text-sm font-semibold uppercase tracking-wide text-gray-300">Informações do produto</h2>
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                        <label className="space-y-1.5 text-xs text-gray-400">Nome *
                            <input required value={form.name} onChange={event => updateField('name', event.target.value)} className={fieldClass} />
                        </label>
                        <label className="space-y-1.5 text-xs text-gray-400">Categoria *
                            <select required value={form.categoryId} onChange={event => updateField('categoryId', event.target.value)} className={fieldClass}>
                                <option value="">Selecione uma categoria</option>
                                {categories?.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}
                            </select>
                        </label>
                        <label className="space-y-1.5 text-xs text-gray-400">Cor
                            <select value={form.color} onChange={event => updateField('color', event.target.value)} className={fieldClass}>
                                <option value="">Sem cor definida</option>
                                {productColorOptions.map(option => <option key={option} value={option}>{option}</option>)}
                            </select>
                        </label>
                        <label className="space-y-1.5 text-xs text-gray-400">Preço base (R$) *
                            <input required min="0" step="0.01" type="number" value={form.price} onChange={event => updateField('price', event.target.value)} className={fieldClass} />
                        </label>
                        <label className="space-y-1.5 text-xs text-gray-400">Preço promocional (R$)
                            <input min="0" step="0.01" type="number" value={form.promotionalPrice} onChange={event => updateField('promotionalPrice', event.target.value)} className={fieldClass} />
                        </label>
                        <label className="space-y-1.5 text-xs text-gray-400">Estoque *
                            <input required min="0" step="1" type="number" value={form.stock} onChange={event => updateField('stock', event.target.value)} className={fieldClass} />
                        </label>
                    </div>
                    <label className="block space-y-1.5 text-xs text-gray-400">Descrição
                        <textarea rows={3} value={form.description} onChange={event => updateField('description', event.target.value)} className={`${fieldClass} resize-y`} />
                    </label>
                    <label className="flex min-h-10 items-center gap-2 text-sm text-gray-200">
                        <input type="checkbox" checked={form.active} onChange={event => updateField('active', event.target.checked)} className="h-4 w-4 accent-red-600" />
                        Produto ativo na loja
                    </label>
                </section>

                <section className="space-y-4 rounded-lg border border-white/10 bg-[#222325] p-4 sm:p-5">
                    <h2 className="border-b border-white/10 pb-3 text-sm font-semibold uppercase tracking-wide text-gray-300">Dimensões para frete</h2>
                    <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                        {(['weight', 'width', 'height', 'depth'] as const).map((field, index) => (
                            <label key={field} className="space-y-1.5 text-xs text-gray-400">{['Peso (kg)', 'Largura (cm)', 'Altura (cm)', 'Profundidade (cm)'][index]}
                                <input min="0" step="0.1" type="number" value={form[field]} onChange={event => updateField(field, event.target.value)} className={fieldClass} />
                            </label>
                        ))}
                    </div>
                </section>

                <section className="space-y-4 rounded-lg border border-white/10 bg-[#222325] p-4 sm:p-5">
                    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-white/10 pb-3">
                        <div><h2 className="text-sm font-semibold uppercase tracking-wide text-gray-300">Imagens</h2><p className="mt-1 text-xs text-gray-500">A primeira imagem será a principal.</p></div>
                        <label className="inline-flex min-h-10 cursor-pointer items-center gap-2 rounded-md border border-white/10 px-3 text-sm text-gray-200 transition hover:bg-white/5">
                            <Upload className="h-4 w-4" /> Adicionar imagens
                            <input type="file" accept="image/*" multiple onChange={handleFiles} className="sr-only" />
                        </label>
                    </div>
                    {images.length === 0 && files.length === 0 ? <div className="flex min-h-24 items-center justify-center gap-2 text-sm text-gray-500"><ImageIcon className="h-4 w-4" /> Nenhuma imagem adicionada</div> : <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6">
                        {images.map((image, index) => <div key={image.id || image.imageUrl} className="group relative aspect-square overflow-hidden rounded-md border border-white/10 bg-[#1A1B1D]">
                            <img src={image.imageUrl} alt={`Imagem ${index + 1} do produto`} className="h-full w-full object-cover" />
                            <span className="absolute bottom-1 left-1 rounded bg-black/70 px-1.5 py-1 text-[10px] text-white">{index === 0 ? 'Principal' : `Imagem ${index + 1}`}</span>
                            <button type="button" onClick={() => removeImage(index)} aria-label={`Remover imagem ${index + 1}`} className="absolute right-1 top-1 grid h-8 w-8 place-items-center rounded bg-black/70 text-white transition hover:bg-red-600"><Trash2 className="h-4 w-4" /></button>
                        </div>)}
                        {files.map((file, index) => <div key={`${file.name}-${index}`} className="flex aspect-square flex-col items-center justify-center gap-2 rounded-md border border-dashed border-brand-secondary/60 bg-[#1A1B1D] p-3 text-center">
                            <ImageIcon className="h-5 w-5 text-brand-secondary" /><span className="w-full truncate text-xs text-gray-300">{file.name}</span>
                            <button type="button" onClick={() => setFiles(current => current.filter((_, fileIndex) => fileIndex !== index))} className="text-xs text-red-400 hover:text-red-300">Remover</button>
                        </div>)}
                    </div>}
                </section>

                <div className="sticky bottom-0 -mx-4 flex justify-end gap-2 border-t border-white/10 bg-[#1A1B1D]/95 px-4 py-3 backdrop-blur sm:static sm:mx-0 sm:border-0 sm:bg-transparent sm:p-0">
                    <Link href="/admin/produtos" className="inline-flex min-h-11 items-center rounded-lg border border-white/10 px-4 text-sm text-gray-300 hover:bg-white/5">Cancelar</Link>
                    <button type="submit" disabled={saving} className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-brand-secondary px-5 text-sm font-semibold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-60">
                        {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}{saving ? 'Salvando...' : 'Salvar alterações'}
                    </button>
                </div>
            </form>
        </div>
    );
}