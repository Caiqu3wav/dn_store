import api from '../lib/axios';
import type {
    PageResponse,
    StockAdjustmentRequest,
    StockEntry,
    StockEntryRequest,
    Supplier,
} from '@/types/stock';

/** Monta uma querystring ignorando valores vazios (filtros opcionais do admin). */
export function buildQuery(params: Record<string, string | number | undefined | null>): string {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') search.set(key, String(value));
    });
    const query = search.toString();
    return query ? `?${query}` : '';
}

export interface SupplierInput {
    name: string;
    document?: string | null;
    active?: boolean;
}

export const stockService = {
    /**
     * Cria a nota. A mesma idempotencyKey pode ser reenviada com segurança: o backend devolve a nota
     * original (200) em vez de aplicar o estoque duas vezes.
     */
    createEntry: async (request: StockEntryRequest): Promise<StockEntry> => {
        const response = await api.post('/admin/stock/entries', request);
        return response.data;
    },

    adjust: async (request: StockAdjustmentRequest): Promise<void> => {
        await api.post('/admin/stock/adjustments', request);
    },

    createSupplier: async (input: SupplierInput): Promise<Supplier> => {
        const response = await api.post('/admin/suppliers', input);
        return response.data;
    },

    searchSuppliers: async (q: string): Promise<PageResponse<Supplier>> => {
        const response = await api.get(`/admin/suppliers${buildQuery({ q, size: 100 })}`);
        return response.data;
    },
};
