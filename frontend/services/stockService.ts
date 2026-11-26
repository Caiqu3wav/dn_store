import api from '@/lib/axios';
import type {
    AdjustPayload,
    AdoptResult,
    CreateEntryPayload,
    EntryDetail,
    StockMovement,
    Supplier,
} from '@/types/stock';

type Params = Record<string, string | number | boolean | string[] | null | undefined>;

/** Monta a query string ignorando vazios; arrays viram parâmetros repetidos (?type=A&type=B). */
export const qs = (params: Params): string => {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
        if (value === undefined || value === null || value === '') continue;
        if (Array.isArray(value)) value.forEach(v => search.append(key, v));
        else search.set(key, String(value));
    }
    const text = search.toString();
    return text ? `?${text}` : '';
};

export interface ApiFailure {
    status: number | null;
    message: string;
    errors: Record<string, string>;
}

/** Normaliza o erro do axios no formato ApiErrorResponse do backend. */
export const apiFailure = (error: unknown, fallback = 'Não foi possível concluir a operação.'): ApiFailure => {
    const response = (error as { response?: { status?: number; data?: { message?: string; errors?: Record<string, string> } } })?.response;
    return {
        status: response?.status ?? null,
        message: response?.data?.message || fallback,
        errors: response?.data?.errors ?? {},
    };
};

/** UUID v4; cai para Math.random só se crypto.randomUUID não existir (contexto sem HTTPS). */
export const newIdempotencyKey = (): string =>
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
              const r = (Math.random() * 16) | 0;
              return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
          });

export const stockService = {
    createSupplier: async (payload: { name: string; document: string | null; contact: string | null }): Promise<Supplier> =>
        (await api.post<Supplier>('/admin/stock/suppliers', payload)).data,

    createEntry: async (payload: CreateEntryPayload): Promise<EntryDetail> =>
        (await api.post<EntryDetail>('/admin/stock/entries', payload)).data,

    adjust: async (payload: AdjustPayload): Promise<StockMovement> =>
        (await api.put<StockMovement>('/admin/stock/adjust', payload)).data,

    adoptBalance: async (variantIds: string[]): Promise<AdoptResult> =>
        (await api.post<AdoptResult>('/admin/stock/reconciliation/adopt', { variantIds })).data,
};
