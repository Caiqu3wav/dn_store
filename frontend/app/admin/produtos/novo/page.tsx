'use client';

import { useState, useRef } from 'react';
import Image from 'next/image';
import { useRouter } from 'next/navigation';
import { ArrowLeft, Save, Image as ImageIcon, X } from 'lucide-react';
import useSWR from 'swr';
import { fetcher, productService } from '@/services/productService';
import { Category } from '@/types';
import { toast } from 'react-hot-toast';
import { uploadToCloudinary } from '@/utils/cloudinary';
import { normalizeProductColor, productColorOptions } from '@/utils/productColor';
import { VariantDraft, VariantStockEditor } from '../VariantStockEditor';
import { Button, Card, IconLink, LinkButton, PageHeader } from '../../_components/ui';
import { Checkbox, Field, Input, Select, Textarea } from '../../_components/form';

export default function NovoProdutoPage() {
    const router = useRouter();
    const [loading, setLoading] = useState(false);
    
    // Fetch categories dynamically
    const { data: categories } = useSWR<Category[]>('/categories', fetcher);
    
    // Form fields
    const [name, setName] = useState('');
    const [categoryId, setCategoryId] = useState('');
    const [color, setColor] = useState('');
    const [description, setDescription] = useState('');
    const [price, setPrice] = useState('');
    const [promotionalPrice, setPromotionalPrice] = useState('');
    const [variants, setVariants] = useState<VariantDraft[]>([]);
    const [active, setActive] = useState(true);

    const [weight, setWeight] = useState('');
    const [width, setWidth] = useState('');
    const [height, setHeight] = useState('');
    const [depth, setDepth] = useState('');

    // Images
    const [files, setFiles] = useState<File[]>([]);
    const [imagePreviews, setImagePreviews] = useState<string[]>([]);
    const [isDragging, setIsDragging] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const addImages = (selectedFiles: File[]) => {
        const imageFiles = selectedFiles.filter(file => file.type.startsWith('image/'));

        if (imageFiles.length === 0) return;

        setFiles(prev => [...prev, ...imageFiles]);
        const newPreviews = imageFiles.map(file => URL.createObjectURL(file));
        setImagePreviews(prev => [...prev, ...newPreviews]);
    };

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files) {
            addImages(Array.from(e.target.files));
            e.target.value = '';
        }
    };

    const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
        e.preventDefault();
        setIsDragging(false);
        addImages(Array.from(e.dataTransfer.files));
    };

    const removeImage = (index: number) => {
        setFiles(prev => prev.filter((_, i) => i !== index));
        setImagePreviews(prev => prev.filter((_, i) => i !== index));
    };



    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        const normalizedColor = normalizeProductColor(color);
        if (normalizedColor === null) {
            toast.error('Selecione uma cor válida da lista.');
            return;
        }
        if (variants.length === 0) {
            toast.error('Adicione ao menos um tamanho para o produto.');
            return;
        }
        setLoading(true);

        try {
            // 1. Upload Images
            const imageUrls = await Promise.all(files.map(file => uploadToCloudinary(file)));
            const imagesPayload = imageUrls.map((url, index) => ({
                imageUrl: url,
                main: index === 0 // A primeira é a principal
            }));

            // 2. Prepare Payload
            const payload = {
                name,
                category: categoryId ? { id: categoryId } : null,
                color: normalizedColor,
                description,
                price: parseFloat(price),
                promotionalPrice: promotionalPrice ? parseFloat(promotionalPrice) : null,
                variants,
                active,
                weight: weight ? parseFloat(weight) : null,
                width: width ? parseFloat(width) : null,
                height: height ? parseFloat(height) : null,
                depth: depth ? parseFloat(depth) : null,
                images: imagesPayload
            };

            // 3. API Call
            await productService.createProduct(payload);
            toast.success('Produto criado com sucesso!');
            router.push('/admin/produtos');

        } catch (error: unknown) {
            console.error("Erro ao salvar produto:", error);
            toast.error(error instanceof Error ? error.message : 'Erro ao salvar produto. Verifique os dados e o console.');
        } finally {
            setLoading(false);
        }
    };

    const sectionClass = 'space-y-4 p-4 sm:p-5';
    const sectionTitleClass = 'border-b border-adm-line pb-3 text-sm font-semibold uppercase tracking-wide text-adm-ink-muted';

    return (
        <div className="mx-auto max-w-5xl space-y-6 pb-12">
            <PageHeader
                eyebrow="Catálogo"
                title="Novo Produto"
                description="Adicione um novo produto ao catálogo."
                leading={
                    <IconLink href="/admin/produtos" label="Voltar para produtos" className="h-10 w-10 border border-adm-line bg-adm-raised">
                        <ArrowLeft className="h-5 w-5" aria-hidden="true" />
                    </IconLink>
                }
            />

            <form className="space-y-5" onSubmit={handleSubmit}>
                {/* Informações Básicas */}
                <Card as="section" className={sectionClass}>
                    <h2 className={sectionTitleClass}>Informações Básicas</h2>

                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                        <Field label="Nome do Produto *">
                            <Input
                                required
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                type="text"
                                placeholder="Ex: Camiseta Classic"
                            />
                        </Field>
                        <Field label="Categoria *">
                            <Select
                                required
                                value={categoryId}
                                onChange={(e) => setCategoryId(e.target.value)}
                            >
                                <option value="">Selecione uma categoria</option>
                                {categories?.map((cat) => (
                                    <option key={cat.id} value={cat.id}>{cat.name}</option>
                                ))}
                            </Select>
                        </Field>
                        <Field label="Cor">
                            <Select value={color} onChange={(e) => setColor(e.target.value)}>
                                <option value="">Selecione uma cor</option>
                                {productColorOptions.map(option => <option key={option} value={option}>{option}</option>)}
                            </Select>
                        </Field>
                    </div>

                    <Field label="Descrição">
                        <Textarea
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            rows={4}
                            placeholder="Descreva os detalhes e diferenciais do produto..."
                        />
                    </Field>

                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                        <Field label="Preço Base (R$) *">
                            <Input
                                required
                                value={price}
                                onChange={(e) => setPrice(e.target.value)}
                                type="number"
                                step="0.01"
                                placeholder="0.00"
                            />
                        </Field>
                        <Field label="Preço Promocional (R$)">
                            <Input
                                value={promotionalPrice}
                                onChange={(e) => setPromotionalPrice(e.target.value)}
                                type="number"
                                step="0.01"
                                placeholder="0.00"
                            />
                        </Field>
                    </div>

                    <Checkbox checked={active} onChange={(e) => setActive(e.target.checked)}>
                        Produto Ativo na Loja
                    </Checkbox>
                </Card>

                <VariantStockEditor variants={variants} onChange={setVariants} />

                {/* Dimensões (Opcional) */}
                <Card as="section" className={sectionClass}>
                    <h2 className={sectionTitleClass}>Dimensões para Frete (Opcional)</h2>
                    <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                        <Field label="Peso (kg)">
                            <Input type="number" step="0.01" value={weight} onChange={(e) => setWeight(e.target.value)} />
                        </Field>
                        <Field label="Largura (cm)">
                            <Input type="number" step="0.1" value={width} onChange={(e) => setWidth(e.target.value)} />
                        </Field>
                        <Field label="Altura (cm)">
                            <Input type="number" step="0.1" value={height} onChange={(e) => setHeight(e.target.value)} />
                        </Field>
                        <Field label="Profundidade (cm)">
                            <Input type="number" step="0.1" value={depth} onChange={(e) => setDepth(e.target.value)} />
                        </Field>
                    </div>
                </Card>

                {/* Imagens */}
                <Card as="section" className={sectionClass}>
                    <h2 className={sectionTitleClass}>Imagens do Produto</h2>

                    {imagePreviews.length > 0 && (
                        <div className="flex gap-4 overflow-x-auto pb-2">
                            {imagePreviews.map((preview, index) => (
                                <div key={preview} className="relative h-32 w-32 shrink-0 overflow-hidden rounded-adm-control border border-adm-line">
                                    <Image src={preview} alt={`Prévia da imagem ${index + 1} do produto`} width={128} height={128} unoptimized className="h-full w-full object-cover" />
                                    <button
                                        type="button"
                                        onClick={() => removeImage(index)}
                                        aria-label={`Remover imagem ${index + 1}`}
                                        className="absolute right-1 top-1 grid h-8 w-8 place-items-center rounded-full bg-black/70 text-white transition-colors hover:bg-adm-accent focus-visible:outline-2 focus-visible:outline-adm-accent-ink"
                                    >
                                        <X className="h-4 w-4" aria-hidden="true" />
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}

                    <div
                        role="button"
                        tabIndex={0}
                        aria-label="Fazer upload de imagens do produto"
                        onClick={() => fileInputRef.current?.click()}
                        onKeyDown={(e) => {
                            if (e.key === 'Enter' || e.key === ' ') {
                                e.preventDefault();
                                fileInputRef.current?.click();
                            }
                        }}
                        onDragOver={(e) => e.preventDefault()}
                        onDragEnter={(e) => {
                            e.preventDefault();
                            setIsDragging(true);
                        }}
                        onDragLeave={(e) => {
                            if (e.currentTarget === e.target) setIsDragging(false);
                        }}
                        onDrop={handleDrop}
                        className={`flex cursor-pointer flex-col items-center justify-center gap-2 rounded-adm-card border-2 border-dashed p-8 text-center transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-adm-accent-ink ${
                            isDragging
                                ? 'border-adm-accent-ink bg-adm-accent/10'
                                : 'border-adm-line-strong hover:bg-adm-hover/50'
                        }`}
                    >
                        <div className="mb-2 rounded-full bg-adm-sunken p-4 text-adm-accent-ink">
                            <ImageIcon className="h-8 w-8" aria-hidden="true" />
                        </div>
                        <p className="font-medium">Clique para fazer upload de imagens</p>
                        <p className="text-sm text-adm-ink-subtle">A primeira imagem será a principal</p>
                        <input
                            type="file"
                            multiple
                            accept="image/*"
                            className="hidden"
                            ref={fileInputRef}
                            onChange={handleFileChange}
                        />
                    </div>
                </Card>

                {/* Submit Actions */}
                <div className="flex items-center justify-end gap-3 pt-2">
                    <LinkButton href="/admin/produtos" size="lg">Cancelar</LinkButton>
                    <Button type="submit" variant="primary" size="lg" loading={loading}>
                        {!loading && <Save className="h-5 w-5" aria-hidden="true" />}
                        {loading ? 'Salvando...' : 'Salvar Produto'}
                    </Button>
                </div>
            </form>
        </div>
    );
}
