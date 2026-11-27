import type { AdjustmentCategory, MovementType } from '@/types/stock';
import type { BadgeTone } from '../../_components/ui';

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const dateOnly = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeZone: 'UTC' });

export const formatMoney = (value: number | null | undefined) => (value == null ? '—' : money.format(value));

export const formatDateTime = (iso: string | null | undefined) => (iso ? dateTime.format(new Date(iso)) : '—');

/** Datas sem hora (LocalDate) chegam como YYYY-MM-DD; interpretadas em UTC para não recuar um dia. */
export const formatDate = (iso: string | null | undefined) => (iso ? dateOnly.format(new Date(`${iso}T00:00:00Z`)) : '—');

/** Data de hoje no fuso local, no formato aceito por <input type="date">. */
export const todayLocal = () => new Date().toLocaleDateString('en-CA');

export const shortId = (id: string) => id.slice(0, 8);

export const invoiceLabel = (series: string, number: string) => (series ? `${series}-${number}` : number);

export const MOVEMENT_LABEL: Record<MovementType, string> = {
    SALE: 'Venda',
    CANCELLATION: 'Cancelamento',
    ENTRY: 'Entrada (nota)',
    ADJUSTMENT: 'Ajuste',
    INITIAL: 'Saldo inicial',
    REACTIVATION: 'Reativação',
    OPENING: 'Abertura do livro',
};

export const MOVEMENT_TONE: Record<MovementType, BadgeTone> = {
    SALE: 'danger',
    CANCELLATION: 'warning',
    ENTRY: 'success',
    ADJUSTMENT: 'info',
    INITIAL: 'violet',
    REACTIVATION: 'danger',
    OPENING: 'neutral',
};

export const CATEGORY_LABEL: Record<AdjustmentCategory, string> = {
    COUNT: 'Contagem de estoque',
    LOSS: 'Perda ou extravio',
    DAMAGE: 'Avaria',
    CORRECTION: 'Correção de lançamento',
    PRODUCT_EDIT: 'Edição do produto',
    OTHER: 'Outro',
};

/** Categorias oferecidas no ajuste manual (PRODUCT_EDIT é gerada pelo sistema). */
export const MANUAL_CATEGORIES: AdjustmentCategory[] = ['COUNT', 'LOSS', 'DAMAGE', 'CORRECTION', 'OTHER'];

export const signed = (n: number) => (n > 0 ? `+${n}` : String(n));
