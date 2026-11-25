'use client';

import { useState } from 'react';
import useSWR from 'swr';
import { Plus, Minus, Loader2, ArrowDownToLine, SlidersHorizontal, History } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { toast } from 'react-hot-toast';
import api from '@/lib/axios';
import { Product } from '@/types';

interface Variant {
    id: string;
    size: string;
    color: string;
    stock: number;
    sku: string | null;
}

interface StockMovement {
    id: string;
    variantSize: string;
    productName: string;
    delta: number;
    stockAfter: number;
    type: string;
    reason: string | null;
    performedBy: string | null;
    createdAt: string;
}

interface EntryRow {
    variantId: string;
    quantity: number;
    reason: string;
}

const TYPE_LABEL: Record<string, string> = {
    SALE: 'Venda', CANCELLATION: 'Cancelamento', ENTRY: 'Entrada', ADJUSTMENT: 'Ajuste',
};
const TYPE_COLOR: Record<string, string> = {
    SALE: 'text-red-400', CANCELLATION: 'text-yellow-400', ENTRY: 'text-green-400', ADJUSTMENT: 'text-blue-400',
};

type ActiveTab = 'entry' | 'history';

export default function AdminStockPage() {
    const { data: products } = useSWR<Product[]>('/products/admin', fetcher);
    const [selectedProductId, setSelectedProductId] = useState('');
    const [variants, setVariants] = useState<Variant[]>([]);
    const [loadingVariants, setLoadingVariants] = useState(false);
    const [activeTab, setActiveTab] = useState<ActiveTab>('entry');

    const [entryRows, setEntryRows] = useState<EntryRow[]>([]);
    const [submittingEntry, setSubmittingEntry] = useState(false);

    const [adjustVariantId, setAdjustVariantId] = useState('');
    const [adjustDelta, setAdjustDelta] = useState(0);
    const [adjustReason, setAdjustReason] = useState('');
    const [submittingAdjust, setSubmittingAdjust] = useState(false);

    const [history, setHistory] = useState<StockMovement[]>([]);
    const [loadingHistory, setLoadingHistory] = useState(false);

    const loadVariants = async (productId: string) => {
        setSelectedProductId(productId);
        setVariants([]);
        setEntryRows([]);
        setHistory([]);
        setActiveTab('entry');
        if (!productId) return;
        setLoadingVariants(true);
        try {
            const { data } = await api.get<Variant[]>(`/products/${productId}/variants`);
            setVariants(data);
            setEntryRows(data.map(v => ({ variantId: v.id, quantity: 0, reason: '' })));
        } catch {
            toast.error('Erro ao carregar variações.');
        } finally {
            setLoadingVariants(false);
        }
    };

    const loadHistory = async () => {
        if (!selectedProductId) return;
        setActiveTab('history');
        setLoadingHistory(true);
        try {
            const { data } = await api.get<StockMovement[]>(`/admin/stock/history/product/${selectedProductId}`);
            setHistory(data);
        } catch {
            toast.error('Erro ao carregar histórico.');
        } finally {
            setLoadingHistory(false);
        }
    };

    const handleEntry = async () => {
        const items = entryRows.filter(r => r.quantity > 0);
        if (items.length === 0) { toast.error('Informe ao menos uma quantidade.'); return; }
        setSubmittingEntry(true);
        try {
            await api.post('/admin/stock/entry', { items });
            toast.success('Entrada registrada!');
            await loadVariants(selectedProductId);
        } catch (e: unknown) {
            const msg = (e as { response?: { data?: { message?: string } } }).response?.data?.message;
            toast.error(msg ?? 'Erro ao registrar entrada.');
        } finally {
            setSubmittingEntry(false);
        }
    };

    const handleAdjust = async () => {
        if (!adjustVariantId || adjustDelta === 0 || !adjustReason.trim()) {
            toast.error('Preencha variação, delta e motivo.');
            return;
        }
        setSubmittingAdjust(true);
        try {
            await api.put('/admin/stock/adjust', { variantId: adjustVariantId, delta: adjustDelta, reason: adjustReason });
            toast.success('Ajuste aplicado!');
            setAdjustDelta(0);
            setAdjustReason('');
            await loadVariants(selectedProductId);
        } catch (e: unknown) {
            const msg = (e as { response?: { data?: { message?: string } } }).response?.data?.message;
            toast.error(msg ?? 'Erro ao ajustar estoque.');
        } finally {
            setSubmittingAdjust(false);
        }
    };

    return (
        <div className="space-y-6 text-white max-w-5xl mx-auto pb-12">
            <div>
                <h1 className="text-3xl font-bold">Estoque</h1>
                <p className="text-gray-400 mt-1">Entrada de mercadoria, ajustes manuais e histórico de movimentações.</p>
            </div>

            <div className="bg-[#2A2A2A] border border-white/5 rounded-xl p-6 space-y-3">
                <label className="text-sm font-semibold text-gray-300">Selecionar Produto</label>
                <select
                    value={selectedProductId}
                    onChange={e => loadVariants(e.target.value)}
                    className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg px-4 py-3 text-sm text-white focus:outline-none focus:border-brand-secondary"
                >
                    <option value="">— escolha um produto —</option>
                    {products?.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
                </select>
            </div>

            {loadingVariants && (
                <div className="flex justify-center py-8"><Loader2 className="w-6 h-6 animate-spin text-gray-400" /></div>
            )}

            {variants.length > 0 && (
                <>
                    <div className="flex gap-0 border-b border-white/10">
                        {([
                            { id: 'entry' as ActiveTab, label: 'Entrada & Ajuste', icon: <ArrowDownToLine className="w-4 h-4" /> },
                            { id: 'history' as ActiveTab, label: 'Histórico', icon: <History className="w-4 h-4" /> },
                        ]).map(t => (
                            <button
                                key={t.id}
                                onClick={() => t.id === 'history' ? loadHistory() : setActiveTab('entry')}
                                className={`flex items-center gap-2 px-5 py-3 text-sm font-semibold border-b-2 transition-colors ${activeTab === t.id ? 'border-brand-secondary text-white' : 'border-transparent text-gray-400 hover:text-white'}`}
                            >
                                {t.icon} {t.label}
                            </button>
                        ))}
                    </div>

                    {activeTab === 'entry' && (
                        <div className="space-y-6">
                            {/* Entry */}
                            <div className="bg-[#2A2A2A] border border-white/5 rounded-xl p-6 space-y-4">
                                <h2 className="text-lg font-bold flex items-center gap-2">
                                    <ArrowDownToLine className="w-5 h-5 text-green-400" /> Entrada de Estoque
                                </h2>
                                <p className="text-xs text-gray-400">Informe a quantidade recebida por variação. Deixe 0 para não alterar.</p>
                                <div className="space-y-2">
                                    {variants.map((v, i) => (
                                        <div key={v.id} className="grid grid-cols-12 gap-3 items-center bg-[#1A1B1D] p-3 rounded-lg">
                                            <div className="col-span-3 text-sm">
                                                <div className="font-semibold">{v.size || '—'}</div>
                                                <div className="text-xs text-gray-400">{v.color || '—'}</div>
                                            </div>
                                            <div className="col-span-2 text-center text-sm">
                                                <div className="text-gray-400 text-xs">Atual</div>
                                                <div className="font-bold">{v.stock}</div>
                                            </div>
                                            <div className="col-span-3">
                                                <label className="text-xs text-gray-400 block mb-1">Qtd entrada</label>
                                                <input
                                                    type="number" min={0}
                                                    value={entryRows[i]?.quantity ?? 0}
                                                    onChange={e => setEntryRows(rows => rows.map((r, j) =>
                                                        j === i ? { ...r, quantity: Math.max(0, parseInt(e.target.value) || 0) } : r))}
                                                    className="w-full bg-[#2A2A2A] border border-white/10 rounded px-2 py-1.5 text-sm text-white focus:outline-none focus:border-brand-secondary"
                                                />
                                            </div>
                                            <div className="col-span-4">
                                                <label className="text-xs text-gray-400 block mb-1">Motivo</label>
                                                <input
                                                    type="text" placeholder="ex: NF 1234"
                                                    value={entryRows[i]?.reason ?? ''}
                                                    onChange={e => setEntryRows(rows => rows.map((r, j) =>
                                                        j === i ? { ...r, reason: e.target.value } : r))}
                                                    className="w-full bg-[#2A2A2A] border border-white/10 rounded px-2 py-1.5 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-brand-secondary"
                                                />
                                            </div>
                                        </div>
                                    ))}
                                </div>
                                <button
                                    onClick={handleEntry} disabled={submittingEntry}
                                    className="flex items-center gap-2 bg-green-600 hover:bg-green-700 disabled:opacity-40 text-white font-bold px-6 py-3 rounded-lg transition-colors"
                                >
                                    {submittingEntry ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                                    Confirmar Entrada
                                </button>
                            </div>

                            {/* Adjust */}
                            <div className="bg-[#2A2A2A] border border-white/5 rounded-xl p-6 space-y-4">
                                <h2 className="text-lg font-bold flex items-center gap-2">
                                    <SlidersHorizontal className="w-5 h-5 text-blue-400" /> Ajuste Manual
                                </h2>
                                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                                    <div>
                                        <label className="text-xs text-gray-400 block mb-1">Variação</label>
                                        <select
                                            value={adjustVariantId}
                                            onChange={e => setAdjustVariantId(e.target.value)}
                                            className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white focus:outline-none focus:border-brand-secondary"
                                        >
                                            <option value="">— selecione —</option>
                                            {variants.map(v => (
                                                <option key={v.id} value={v.id}>{v.size} / {v.color} (atual: {v.stock})</option>
                                            ))}
                                        </select>
                                    </div>
                                    <div>
                                        <label className="text-xs text-gray-400 block mb-1">Delta (+ entrada / − saída)</label>
                                        <div className="flex items-center gap-2">
                                            <button onClick={() => setAdjustDelta(d => d - 1)}
                                                className="p-2 bg-[#1A1B1D] border border-white/10 rounded hover:bg-white/10 transition-colors">
                                                <Minus className="w-4 h-4" />
                                            </button>
                                            <input
                                                type="number" value={adjustDelta}
                                                onChange={e => setAdjustDelta(parseInt(e.target.value) || 0)}
                                                className="flex-1 bg-[#1A1B1D] border border-white/10 rounded px-3 py-2 text-sm text-white text-center focus:outline-none focus:border-brand-secondary"
                                            />
                                            <button onClick={() => setAdjustDelta(d => d + 1)}
                                                className="p-2 bg-[#1A1B1D] border border-white/10 rounded hover:bg-white/10 transition-colors">
                                                <Plus className="w-4 h-4" />
                                            </button>
                                        </div>
                                    </div>
                                    <div>
                                        <label className="text-xs text-gray-400 block mb-1">Motivo *</label>
                                        <input
                                            type="text" placeholder="ex: Avaria, inventário..."
                                            value={adjustReason} onChange={e => setAdjustReason(e.target.value)}
                                            className="w-full bg-[#1A1B1D] border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-brand-secondary"
                                        />
                                    </div>
                                </div>
                                <button
                                    onClick={handleAdjust} disabled={submittingAdjust}
                                    className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-40 text-white font-bold px-6 py-3 rounded-lg transition-colors"
                                >
                                    {submittingAdjust ? <Loader2 className="w-4 h-4 animate-spin" /> : <SlidersHorizontal className="w-4 h-4" />}
                                    Aplicar Ajuste
                                </button>
                            </div>
                        </div>
                    )}

                    {activeTab === 'history' && (
                        <div className="bg-[#2A2A2A] border border-white/5 rounded-xl p-6 space-y-4">
                            <h2 className="text-lg font-bold flex items-center gap-2">
                                <History className="w-5 h-5 text-brand-secondary" /> Histórico de Movimentações
                            </h2>
                            {loadingHistory ? (
                                <div className="flex justify-center py-8"><Loader2 className="w-5 h-5 animate-spin text-gray-400" /></div>
                            ) : history.length === 0 ? (
                                <p className="text-sm text-gray-500 py-6 text-center">Nenhuma movimentação registrada.</p>
                            ) : (
                                <div className="overflow-x-auto">
                                    <table className="w-full text-sm text-left border-collapse">
                                        <thead>
                                            <tr className="text-xs text-gray-400 border-b border-white/10">
                                                <th className="py-2 pr-4">Data</th>
                                                <th className="py-2 pr-4">Variação</th>
                                                <th className="py-2 pr-4">Tipo</th>
                                                <th className="py-2 pr-4 text-right">Delta</th>
                                                <th className="py-2 pr-4 text-right">Estoque após</th>
                                                <th className="py-2">Motivo / Responsável</th>
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-white/5">
                                            {history.map(m => (
                                                <tr key={m.id} className="hover:bg-white/[0.02]">
                                                    <td className="py-2.5 pr-4 text-gray-400 text-xs whitespace-nowrap">
                                                        {new Date(m.createdAt).toLocaleString('pt-BR')}
                                                    </td>
                                                    <td className="py-2.5 pr-4 font-medium">{m.variantSize}</td>
                                                    <td className={`py-2.5 pr-4 font-semibold text-xs ${TYPE_COLOR[m.type] ?? 'text-gray-300'}`}>
                                                        {TYPE_LABEL[m.type] ?? m.type}
                                                    </td>
                                                    <td className={`py-2.5 pr-4 text-right font-bold tabular-nums ${m.delta > 0 ? 'text-green-400' : 'text-red-400'}`}>
                                                        {m.delta > 0 ? `+${m.delta}` : m.delta}
                                                    </td>
                                                    <td className="py-2.5 pr-4 text-right tabular-nums text-gray-300">{m.stockAfter}</td>
                                                    <td className="py-2.5 text-xs text-gray-400">
                                                        {m.reason ?? '—'}
                                                        {m.performedBy && <span className="ml-1 text-gray-600">({m.performedBy})</span>}
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </div>
                    )}
                </>
            )}
        </div>
    );
}
