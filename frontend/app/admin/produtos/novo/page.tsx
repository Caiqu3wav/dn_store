'use client';

import { useState, useRef } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ArrowLeft, Save, Image as ImageIcon, Loader2, X } from 'lucide-react';
import useSWR from 'swr';
import { fetcher, productService } from '@/services/productService';
import { Category } from '@/types';
import { toast } from 'react-hot-toast';

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
    const [stock, setStock] = useState('');
    const [active, setActive] = useState(true);

    const [weight, setWeight] = useState('');
    const [width, setWidth] = useState('');
    const [height, setHeight] = useState('');
    const [depth, setDepth] = useState('');

    // Images
    const [files, setFiles] = useState<File[]>([]);
    const [imagePreviews, setImagePreviews] = useState<string[]>([]);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files.length > 0) {
            const newFiles = Array.from(e.target.files);
            setFiles(prev => [...prev, ...newFiles]);
            
            const newPreviews = newFiles.map(file => URL.createObjectURL(file));
            setImagePreviews(prev => [...prev, ...newPreviews]);
        }
    };

    const removeImage = (index: number) => {
        setFiles(prev => prev.filter((_, i) => i !== index));
        setImagePreviews(prev => prev.filter((_, i) => i !== index));
    };

    const uploadToCloudinary = async (file: File) => {
        const cloudName = process.env.NEXT_PUBLIC_CLOUDINARY_CLOUD_NAME || 'dn_store';
        const uploadPreset = process.env.NEXT_PUBLIC_CLOUDINARY_UPLOAD_PRESET || 'dn_store_preset';
        
        console.log(`Iniciando upload para o Cloudinary (Cloud: ${cloudName}, Preset: ${uploadPreset})`);
        
        const formData = new FormData();
        formData.append('file', file);
        formData.append('upload_preset', uploadPreset);

        try {
            const res = await fetch(`https://api.cloudinary.com/v1_1/${cloudName}/image/upload`, {
                method: 'POST',
                body: formData
            });

            if (!res.ok) {
                const errData = await res.json();
                console.error("Erro do Cloudinary:", errData);
                throw new Error(errData.error?.message || 'Falha no upload da imagem');
            }
            
            const data = await res.json();
            return data.secure_url;
        } catch (error) {
            console.error("Fetch error no Cloudinary:", error);
            throw error;
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
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
                color,
                description,
                price: parseFloat(price),
                promotionalPrice: promotionalPrice ? parseFloat(promotionalPrice) : null,
                stock: parseInt(stock),
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

        } catch (error: any) {
            console.error("Erro ao salvar produto:", error);
            toast.error(error.message || 'Erro ao salvar produto. Verifique os dados e o console.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="space-y-6 text-white max-w-4xl mx-auto pb-12">
            <div className="flex items-center gap-4">
                <Link 
                    href="/admin/produtos"
                    className="p-2 bg-[#2A2A2A] border border-white/10 rounded-lg hover:bg-white/10 transition-colors"
                >
                    <ArrowLeft className="w-5 h-5" />
                </Link>
                <div>
                    <h1 className="text-3xl font-bold">Novo Produto</h1>
                    <p className="text-gray-400 mt-1">Adicione um novo produto ao catálogo.</p>
                </div>
            </div>

            <form className="space-y-8" onSubmit={handleSubmit}>
                {/* Informações Básicas */}
                <div className="bg-[#2A2A2A] border border-white/5 p-6 rounded-xl shadow-sm space-y-6">
                    <h2 className="text-xl font-bold border-b border-white/5 pb-4">Informações Básicas</h2>
                    
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-gray-300">Nome do Produto *</label>
                            <input 
                                required
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                type="text" 
                                className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2.5 px-4 text-white focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary"
                                placeholder="Ex: Camiseta Classic"
                            />
                        </div>
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-gray-300">Categoria *</label>
                            <select 
                                required
                                value={categoryId}
                                onChange={(e) => setCategoryId(e.target.value)}
                                className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2.5 px-4 text-white focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary"
                            >
                                <option value="">Selecione uma categoria</option>
                                {categories?.map((cat) => (
                                    <option key={cat.id} value={cat.id}>{cat.name}</option>
                                ))}
                            </select>
                        </div>
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-gray-300">Cor</label>
                            <input 
                                value={color}
                                onChange={(e) => setColor(e.target.value)}
                                type="text" 
                                className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2.5 px-4 text-white focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary"
                                placeholder="Ex: Azul, Preto, Vermelho"
                            />
                        </div>
                    </div>

                    <div className="space-y-2">
                        <label className="text-sm font-medium text-gray-300">Descrição</label>
                        <textarea 
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            rows={4}
                            className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2.5 px-4 text-white focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary"
                            placeholder="Descreva os detalhes e diferenciais do produto..."
                        />
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-gray-300">Preço Base (R$) *</label>
                            <input 
                                required
                                value={price}
                                onChange={(e) => setPrice(e.target.value)}
                                type="number" 
                                step="0.01"
                                className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2.5 px-4 text-white focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary"
                                placeholder="0.00"
                            />
                        </div>
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-gray-300">Preço Promocional (R$)</label>
                            <input 
                                value={promotionalPrice}
                                onChange={(e) => setPromotionalPrice(e.target.value)}
                                type="number" 
                                step="0.01"
                                className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2.5 px-4 text-white focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary"
                                placeholder="0.00"
                            />
                        </div>
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-gray-300">Estoque Un. *</label>
                            <input 
                                required
                                value={stock}
                                onChange={(e) => setStock(e.target.value)}
                                type="number" 
                                className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2.5 px-4 text-white focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary"
                                placeholder="0"
                            />
                        </div>
                    </div>
                    
                    <div className="flex items-center gap-3 pt-2">
                        <input 
                            checked={active}
                            onChange={(e) => setActive(e.target.checked)}
                            type="checkbox" 
                            className="w-5 h-5 rounded border-gray-300 text-brand-secondary focus:ring-brand-secondary" 
                        />
                        <span className="text-sm font-medium text-white">Produto Ativo na Loja</span>
                    </div>
                </div>

                {/* Dimensões (Opcional) */}
                <div className="bg-[#2A2A2A] border border-white/5 p-6 rounded-xl shadow-sm space-y-6">
                    <h2 className="text-xl font-bold border-b border-white/5 pb-4">Dimensões para Frete (Opcional)</h2>
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
                        <div className="space-y-2">
                            <label className="text-xs text-gray-400">Peso (kg)</label>
                            <input type="number" step="0.01" value={weight} onChange={(e) => setWeight(e.target.value)} className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2 px-3 text-white" />
                        </div>
                        <div className="space-y-2">
                            <label className="text-xs text-gray-400">Largura (cm)</label>
                            <input type="number" step="0.1" value={width} onChange={(e) => setWidth(e.target.value)} className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2 px-3 text-white" />
                        </div>
                        <div className="space-y-2">
                            <label className="text-xs text-gray-400">Altura (cm)</label>
                            <input type="number" step="0.1" value={height} onChange={(e) => setHeight(e.target.value)} className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2 px-3 text-white" />
                        </div>
                        <div className="space-y-2">
                            <label className="text-xs text-gray-400">Profundidade (cm)</label>
                            <input type="number" step="0.1" value={depth} onChange={(e) => setDepth(e.target.value)} className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2 px-3 text-white" />
                        </div>
                    </div>
                </div>

                {/* Imagens */}
                <div className="bg-[#2A2A2A] border border-white/5 p-6 rounded-xl shadow-sm space-y-6">
                    <h2 className="text-xl font-bold border-b border-white/5 pb-4">Imagens do Produto</h2>
                    
                    {imagePreviews.length > 0 && (
                        <div className="flex gap-4 overflow-x-auto pb-4">
                            {imagePreviews.map((preview, index) => (
                                <div key={index} className="relative w-32 h-32 rounded-lg overflow-hidden shrink-0 border border-white/10">
                                    <img src={preview} alt="Preview" className="w-full h-full object-cover" />
                                    <button 
                                        type="button"
                                        onClick={() => removeImage(index)}
                                        className="absolute top-1 right-1 p-1 bg-black/60 rounded-full text-white hover:bg-red-500 transition-colors"
                                    >
                                        <X className="w-4 h-4" />
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}

                    <div 
                        onClick={() => fileInputRef.current?.click()}
                        className="border-2 border-dashed border-white/20 rounded-xl p-8 text-center hover:bg-white/[0.02] transition-colors cursor-pointer flex flex-col items-center justify-center gap-2"
                    >
                        <div className="p-4 bg-[#1A1B1D] rounded-full text-brand-secondary mb-2">
                            <ImageIcon className="w-8 h-8" />
                        </div>
                        <p className="font-medium text-white">Clique para fazer upload de imagens</p>
                        <p className="text-sm text-gray-500">A primeira imagem será a principal</p>
                        <input 
                            type="file" 
                            multiple 
                            accept="image/*"
                            className="hidden" 
                            ref={fileInputRef}
                            onChange={handleFileChange}
                        />
                    </div>
                </div>

                {/* Submit Actions */}
                <div className="flex items-center justify-end gap-4 pt-4">
                    <Link 
                        href="/admin/produtos"
                        className="px-6 py-2.5 rounded-lg border border-white/10 text-white font-medium hover:bg-white/10 transition-colors"
                    >
                        Cancelar
                    </Link>
                    <button 
                        type="submit"
                        disabled={loading}
                        className="px-6 py-2.5 rounded-lg bg-brand-secondary text-white font-medium flex items-center gap-2 hover:bg-red-700 transition-colors disabled:opacity-50"
                    >
                        {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : <Save className="w-5 h-5" />}
                        {loading ? 'Salvando...' : 'Salvar Produto'}
                    </button>
                </div>
            </form>
        </div>
    );
}
