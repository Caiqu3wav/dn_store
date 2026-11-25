'use client';

import { ChangeEvent, FormEvent, useEffect, useState } from 'react';
import Image from 'next/image';
import { useParams, useRouter } from 'next/navigation';
import useSWR from 'swr';
import { ArrowLeft, Image as ImageIcon, Save, Trash2, Upload } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { fetcher, productService } from '@/services/productService';
import { Category, Product, ProductImage } from '@/types';
import { uploadToCloudinary } from '@/utils/cloudinary';
import { normalizeProductColor, productColorOptions } from '@/utils/productColor';
import { VariantDraft, VariantStockEditor } from '../VariantStockEditor';
import { Button, Card, IconLink, LinkButton, PageHeader, Skeleton } from '../../_components/ui';
import { Checkbox, Field, Input, Select, Textarea } from '../../_components/form';

type ProductForm = {
    name: string;
    categoryId: string;
    color: string;
    description: string;
    price: string;
    promotionalPrice: string;
    active: boolean;
    weight: string;
    width: string;
    height: string;
    depth: string;
};

const emptyForm: ProductForm = {
    name: '', categoryId: '', color: '', description: '', price: '',
    promotionalPrice: '', active: true,
    weight: '', width: '', height: '', depth: ''
};

export default function EditProductPage() {
    const { id } = useParams<{ id: string }>();
    const router = useRouter();
    const { data: categories } = useSWR<Category[]>('/categories', fetcher);
    const [form, setForm] = useState<ProductForm>(emptyForm);
    const [images, setImages] = useState<ProductImage[]>([]);
    const [variants, setVariants] = useState<VariantDraft[]>([]);
    const [files, setFiles] = useState<File[]>([]);
    const [loadingData, setLoadingData] = useState(true);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        let cancelled = false;
        productService.getAdminProductById(id)
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
                    active: product.active,
                    weight: physical.weight == null ? '' : String(physical.weight),
                    width: physical.width == null ? '' : String(physical.width),
                    height: physical.height == null ? '' : String(physical.height),
                    depth: physical.depth == null ? '' : String(physical.depth)
                });
                setImages(product.images || []);
                setVariants((product.variants ?? []).map(variant => ({
                    id: variant.id,
                    size: variant.size,
                    stock: variant.stock,
                    active: variant.active !== false
                })));
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
        if (variants.length === 0) {
            toast.error('Adicione ao menos um tamanho para o produto.');
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
                variants,
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
        return (
            <div className="mx-auto max-w-5xl space-y-5" role="status" aria-label="Carregando produto">
                <Skeleton className="h-12 w-72" />
                <Skeleton className="h-64 w-full" />
                <Skeleton className="h-40 w-full" />
            </div>
        );
    }

    const sectionClass = 'space-y-4 p-4 sm:p-5';
    const sectionTitleClass = 'border-b border-adm-line pb-3 text-sm font-semibold uppercase tracking-wide text-adm-ink-muted';

    return (
        <div className="mx-auto max-w-5xl space-y-5 pb-10">
            <PageHeader
                eyebrow="Catálogo"
                title="Editar produto"
                leading={
                    <IconLink href="/admin/produtos" label="Voltar para produtos" className="h-10 w-10 border border-adm-line bg-adm-raised">
                        <ArrowLeft className="h-5 w-5" aria-hidden="true" />
                    </IconLink>
                }
            />

            <form onSubmit={handleSubmit} className="space-y-4">
                <Card as="section" className={sectionClass}>
                    <h2 className={sectionTitleClass}>Informações do produto</h2>
                    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                        <Field label="Nome *">
                            <Input required value={form.name} onChange={event => updateField('name', event.target.value)} />
                        </Field>
                        <Field label="Categoria *">
                            <Select required value={form.categoryId} onChange={event => updateField('categoryId', event.target.value)}>
                                <option value="">Selecione uma categoria</option>
                                {categories?.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}
                            </Select>
                        </Field>
                        <Field label="Cor">
                            <Select value={form.color} onChange={event => updateField('color', event.target.value)}>
                                <option value="">Sem cor definida</option>
                                {productColorOptions.map(option => <option key={option} value={option}>{option}</option>)}
                            </Select>
                        </Field>
                        <Field label="Preço base (R$) *">
                            <Input required min="0" step="0.01" type="number" value={form.price} onChange={event => updateField('price', event.target.value)} />
                        </Field>
                        <Field label="Preço promocional (R$)">
                            <Input min="0" step="0.01" type="number" value={form.promotionalPrice} onChange={event => updateField('promotionalPrice', event.target.value)} />
                        </Field>
                    </div>
                    <Field label="Descrição">
                        <Textarea rows={3} value={form.description} onChange={event => updateField('description', event.target.value)} />
                    </Field>
                    <Checkbox checked={form.active} onChange={event => updateField('active', event.target.checked)}>
                        Produto ativo na loja
                    </Checkbox>
                </Card>

                <VariantStockEditor variants={variants} onChange={setVariants} />

                <Card as="section" className={sectionClass}>
                    <h2 className={sectionTitleClass}>Dimensões para frete</h2>
                    <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                        {(['weight', 'width', 'height', 'depth'] as const).map((field, index) => (
                            <Field key={field} label={['Peso (kg)', 'Largura (cm)', 'Altura (cm)', 'Profundidade (cm)'][index]}>
                                <Input min="0" step="0.1" type="number" value={form[field]} onChange={event => updateField(field, event.target.value)} />
                            </Field>
                        ))}
                    </div>
                </Card>

                <Card as="section" className={sectionClass}>
                    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-adm-line pb-3">
                        <div><h2 className="text-sm font-semibold uppercase tracking-wide text-adm-ink-muted">Imagens</h2><p className="mt-1 text-xs text-adm-ink-subtle">A primeira imagem será a principal.</p></div>
                        <label className="inline-flex min-h-10 cursor-pointer items-center gap-2 rounded-adm-control border border-adm-line-strong bg-adm-raised px-3 text-sm font-medium text-adm-ink transition-colors hover:bg-adm-hover focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-adm-accent-ink">
                            <Upload className="h-4 w-4" aria-hidden="true" /> Adicionar imagens
                            <input type="file" accept="image/*" multiple onChange={handleFiles} className="sr-only" />
                        </label>
                    </div>
                    {images.length === 0 && files.length === 0 ? <div className="flex min-h-24 items-center justify-center gap-2 text-sm text-adm-ink-subtle"><ImageIcon className="h-4 w-4" aria-hidden="true" /> Nenhuma imagem adicionada</div> : <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6">
                        {images.map((image, index) => <div key={image.id || image.imageUrl} className="group relative aspect-square overflow-hidden rounded-adm-control border border-adm-line bg-adm-sunken">
                            <Image src={image.imageUrl} alt={`Imagem ${index + 1} do produto`} fill sizes="(max-width: 640px) 50vw, 128px" unoptimized className="object-cover" />
                            <span className="absolute bottom-1 left-1 rounded bg-black/75 px-1.5 py-1 text-[10px] text-white">{index === 0 ? 'Principal' : `Imagem ${index + 1}`}</span>
                            <button type="button" onClick={() => removeImage(index)} aria-label={`Remover imagem ${index + 1}`} className="absolute right-1 top-1 grid h-8 w-8 place-items-center rounded bg-black/75 text-white transition-colors hover:bg-adm-accent focus-visible:outline-2 focus-visible:outline-adm-accent-ink"><Trash2 className="h-4 w-4" aria-hidden="true" /></button>
                        </div>)}
                        {files.map((file, index) => <div key={`${file.name}-${index}`} className="flex aspect-square flex-col items-center justify-center gap-2 rounded-adm-control border border-dashed border-adm-accent-ink/60 bg-adm-sunken p-3 text-center">
                            <ImageIcon className="h-5 w-5 text-adm-accent-ink" aria-hidden="true" /><span className="w-full truncate text-xs text-adm-ink-muted">{file.name}</span>
                            <button type="button" onClick={() => setFiles(current => current.filter((_, fileIndex) => fileIndex !== index))} className="rounded text-xs text-adm-danger hover:underline focus-visible:outline-2 focus-visible:outline-adm-accent-ink">Remover</button>
                        </div>)}
                    </div>}
                </Card>

                <div className="sticky bottom-0 z-10 -mx-4 flex justify-end gap-2 border-t border-adm-line bg-adm-canvas/95 px-4 py-3 backdrop-blur sm:static sm:mx-0 sm:border-0 sm:bg-transparent sm:p-0 sm:backdrop-blur-none">
                    <LinkButton href="/admin/produtos" size="lg">Cancelar</LinkButton>
                    <Button type="submit" variant="primary" size="lg" loading={saving}>
                        {!saving && <Save className="h-4 w-4" aria-hidden="true" />}{saving ? 'Salvando...' : 'Salvar alterações'}
                    </Button>
                </div>
            </form>
        </div>
    );
}
