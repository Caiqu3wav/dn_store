'use client';

import { useState } from 'react';
import Link from 'next/link';
import { ArrowLeft, Save, Plus, Trash2, Image as ImageIcon } from 'lucide-react';

export default function NovoProdutoPage() {
    const [variants, setVariants] = useState([{ id: 1, size: '', color: '', stock: 0, sku: '' }]);

    const addVariant = () => {
        setVariants([...variants, { id: Date.now(), size: '', color: '', stock: 0, sku: '' }]);
    };

    const removeVariant = (id: number) => {
        setVariants(variants.filter(v => v.id !== id));
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

            <form className="space-y-8" onSubmit={(e) => e.preventDefault()}>
                {/* Informações Básicas */}
                <div className="bg-[#2A2A2A] border border-white/5 p-6 rounded-xl shadow-sm space-y-6">
                    <h2 className="text-xl font-bold border-b border-white/5 pb-4">Informações Básicas</h2>
                    
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-gray-300">Nome do Produto *</label>
                            <input 
                                type="text" 
                                className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2.5 px-4 text-white focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary"
                                placeholder="Ex: Camiseta Classic"
                            />
                        </div>
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-gray-300">Categoria *</label>
                            <select className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2.5 px-4 text-white focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary appearance-none">
                                <option value="">Selecione uma categoria</option>
                                <option value="vestuario">Vestuário</option>
                                <option value="acessorios">Acessórios</option>
                                <option value="equipamentos">Equipamentos</option>
                            </select>
                        </div>
                    </div>

                    <div className="space-y-2">
                        <label className="text-sm font-medium text-gray-300">Descrição</label>
                        <textarea 
                            rows={4}
                            className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2.5 px-4 text-white focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary"
                            placeholder="Descreva os detalhes e diferenciais do produto..."
                        />
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-gray-300">Preço (R$) *</label>
                            <input 
                                type="number" 
                                step="0.01"
                                className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg py-2.5 px-4 text-white focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary"
                                placeholder="0,00"
                            />
                        </div>
                        <div className="space-y-2 flex flex-col justify-center pt-6">
                            <label className="flex items-center gap-3 cursor-pointer">
                                <input type="checkbox" className="w-5 h-5 rounded border-gray-300 text-brand-secondary focus:ring-brand-secondary" defaultChecked />
                                <span className="text-sm font-medium text-white">Produto Ativo na Loja</span>
                            </label>
                        </div>
                    </div>
                </div>

                {/* Imagens */}
                <div className="bg-[#2A2A2A] border border-white/5 p-6 rounded-xl shadow-sm space-y-6">
                    <h2 className="text-xl font-bold border-b border-white/5 pb-4">Imagens do Produto</h2>
                    
                    <div className="border-2 border-dashed border-white/20 rounded-xl p-8 text-center hover:bg-white/[0.02] transition-colors cursor-pointer flex flex-col items-center justify-center gap-2">
                        <div className="p-4 bg-[#1A1B1D] rounded-full text-brand-secondary mb-2">
                            <ImageIcon className="w-8 h-8" />
                        </div>
                        <p className="font-medium text-white">Clique para fazer upload ou arraste as imagens</p>
                        <p className="text-sm text-gray-500">PNG, JPG, WEBP até 5MB</p>
                    </div>
                </div>

                {/* Variantes */}
                <div className="bg-[#2A2A2A] border border-white/5 p-6 rounded-xl shadow-sm space-y-6">
                    <div className="flex items-center justify-between border-b border-white/5 pb-4">
                        <h2 className="text-xl font-bold">Variantes (Estoque e Tamanhos)</h2>
                        <button 
                            type="button"
                            onClick={addVariant}
                            className="text-sm flex items-center gap-2 text-brand-secondary hover:text-red-400 font-medium transition-colors"
                        >
                            <Plus className="w-4 h-4" />
                            Adicionar Variante
                        </button>
                    </div>

                    <div className="space-y-4">
                        {variants.map((variant, index) => (
                            <div key={variant.id} className="grid grid-cols-1 md:grid-cols-5 gap-4 items-end bg-[#1A1B1D] p-4 rounded-lg border border-white/5">
                                <div className="space-y-1">
                                    <label className="text-xs text-gray-400">Tamanho</label>
                                    <input type="text" placeholder="Ex: M" className="w-full bg-[#2A2A2A] border border-white/10 rounded-md py-2 px-3 text-sm text-white focus:outline-none focus:border-brand-secondary" />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs text-gray-400">Cor</label>
                                    <input type="text" placeholder="Ex: Preto" className="w-full bg-[#2A2A2A] border border-white/10 rounded-md py-2 px-3 text-sm text-white focus:outline-none focus:border-brand-secondary" />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs text-gray-400">SKU</label>
                                    <input type="text" placeholder="DN-TS-BLK-M" className="w-full bg-[#2A2A2A] border border-white/10 rounded-md py-2 px-3 text-sm text-white focus:outline-none focus:border-brand-secondary" />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs text-gray-400">Estoque</label>
                                    <input type="number" placeholder="0" className="w-full bg-[#2A2A2A] border border-white/10 rounded-md py-2 px-3 text-sm text-white focus:outline-none focus:border-brand-secondary" />
                                </div>
                                <div className="flex justify-end pb-1">
                                    <button 
                                        type="button"
                                        onClick={() => removeVariant(variant.id)}
                                        disabled={variants.length === 1}
                                        className="p-2 text-gray-500 hover:text-red-500 disabled:opacity-50 transition-colors"
                                    >
                                        <Trash2 className="w-5 h-5" />
                                    </button>
                                </div>
                            </div>
                        ))}
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
                        className="px-6 py-2.5 rounded-lg bg-brand-secondary text-white font-medium flex items-center gap-2 hover:bg-red-700 transition-colors"
                    >
                        <Save className="w-5 h-5" />
                        Salvar Produto
                    </button>
                </div>
            </form>
        </div>
    );
}
