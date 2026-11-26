export type MovementType = 'SALE' | 'CANCELLATION' | 'ENTRY' | 'ADJUSTMENT' | 'INITIAL' | 'REACTIVATION' | 'OPENING';

export type AdjustmentCategory = 'COUNT' | 'LOSS' | 'DAMAGE' | 'CORRECTION' | 'PRODUCT_EDIT' | 'OTHER';

export interface PageView<T> {
    items: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface Supplier {
    id: string;
    name: string;
    document: string | null;
    contact: string | null;
    active: boolean;
    createdAt: string;
}

export interface StockVariant {
    id: string;
    productId: string;
    productName: string;
    productActive: boolean;
    size: string;
    color: string | null;
    sku: string | null;
    stock: number;
    active: boolean;
}

export interface StockMovement {
    id: string;
    seq: number | null;
    variantId: string;
    productId: string | null;
    productName: string | null;
    variantSize: string | null;
    variantColor: string | null;
    delta: number;
    stockBefore: number | null;
    stockAfter: number;
    type: MovementType;
    category: AdjustmentCategory | null;
    orderId: string | null;
    entryId: string | null;
    invoiceNumber: string | null;
    supplierName: string | null;
    reason: string | null;
    performedBy: string | null;
    createdAt: string;
}

export interface EntrySummary {
    id: string;
    supplierId: string;
    supplierName: string;
    invoiceNumber: string;
    invoiceSeries: string;
    invoiceDate: string;
    itemCount: number;
    totalQuantity: number;
    totalCost: number | null;
    createdBy: string;
    createdAt: string;
}

export interface EntryItem {
    variantId: string;
    productId: string;
    productName: string;
    size: string;
    color: string | null;
    quantity: number;
    unitCost: number | null;
    lineTotal: number | null;
}

export interface EntryDetail {
    summary: EntrySummary;
    notes: string | null;
    items: EntryItem[];
    movements: StockMovement[];
    replayed: boolean;
}

export interface OrderStock {
    orderId: string;
    status: string;
    stockReleased: boolean;
    total: number;
    orderCreatedAt: string | null;
    reservedQuantity: number;
    releasedQuantity: number;
    movementCount: number;
    lastMovementAt: string;
}

export type ReconciliationStatus = 'OK' | 'DIVERGENT' | 'NO_LEDGER';

export interface ReconciliationRow {
    variantId: string;
    productId: string;
    productName: string;
    size: string;
    color: string | null;
    active: boolean;
    currentStock: number;
    ledgerBalance: number | null;
    difference: number | null;
    status: ReconciliationStatus;
}

export interface ChainBreak {
    seq: number;
    stockBefore: number;
    previousStockAfter: number;
}

export interface ReconciliationDetail {
    row: ReconciliationRow;
    chainBreaks: ChainBreak[];
}

export interface AdoptResult {
    adopted: number;
    skippedVariantIds: string[];
}

export interface CreateEntryPayload {
    supplierId: string;
    invoiceNumber: string;
    invoiceSeries: string;
    invoiceDate: string;
    idempotencyKey: string;
    notes: string | null;
    lines: { variantId: string; quantity: number; unitCost: number | null }[];
}

export interface AdjustPayload {
    variantId: string;
    delta?: number;
    countedQuantity?: number;
    expectedBalance: number;
    category: AdjustmentCategory;
    reason: string;
    idempotencyKey: string;
}
