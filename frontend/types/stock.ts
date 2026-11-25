export type StockMovementType =
    | 'ENTRADA_NOTA'
    | 'SAIDA_VENDA'
    | 'DEVOLUCAO_PEDIDO'
    | 'AJUSTE'
    | 'EDICAO_CADASTRO'
    | 'SALDO_INICIAL';

export type StockReferenceType = 'STOCK_ENTRY' | 'ORDER';

export type StockAdjustmentReason = 'CORRECAO_INVENTARIO' | 'PERDA' | 'AVARIA' | 'DEVOLUCAO_CLIENTE';

export interface PageResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface Supplier {
    id: string;
    name: string;
    document: string | null;
    active: boolean;
    createdAt: string;
}

export interface UserRef {
    id: string;
    name: string | null;
    email: string | null;
}

export interface VariantRef {
    id: string;
    productId: string;
    productName: string;
    size: string | null;
    sku: string | null;
}

export interface StockVariant {
    id: string;
    productId: string;
    productName: string;
    size: string | null;
    sku: string | null;
    stock: number;
    active: boolean;
    productActive: boolean;
}

export interface StockReference {
    type: StockReferenceType;
    id: string;
    label: string;
}

export interface StockMovement {
    id: string;
    createdAt: string;
    type: StockMovementType;
    variant: VariantRef;
    quantityDelta: number;
    balanceBefore: number;
    balanceAfter: number;
    reference: StockReference | null;
    reasonCode: string | null;
    reasonText: string | null;
    actor: UserRef | null;
}

export interface StockEntrySummary {
    id: string;
    supplier: { id: string; name: string };
    invoiceNumber: string;
    issueDate: string | null;
    receivedAt: string;
    createdBy: UserRef | null;
    itemCount: number;
    totalUnits: number;
}

export interface StockEntryItem {
    variant: VariantRef;
    quantity: number;
    unitCost: number | null;
    balanceBefore: number | null;
    balanceAfter: number | null;
}

export interface StockEntry {
    id: string;
    supplier: { id: string; name: string };
    invoiceNumber: string;
    issueDate: string | null;
    receivedAt: string;
    notes: string | null;
    createdBy: UserRef | null;
    createdAt: string;
    totalUnits: number;
    totalCost: number | null;
    items: StockEntryItem[];
}

export interface StockEntryRequest {
    supplierId: string;
    invoiceNumber: string;
    issueDate?: string | null;
    notes?: string | null;
    idempotencyKey: string;
    items: { variantId: string; quantity: number; unitCost?: number | null }[];
}

export interface StockAdjustmentRequest {
    variantId: string;
    delta: number;
    expectedBalance: number;
    reasonCode: StockAdjustmentReason;
    reason: string;
    idempotencyKey: string;
}

export interface SaleOutflow {
    orderId: string;
    orderLabel: string;
    movedAt: string;
    status: string | null;
    customer: UserRef | null;
    lines: number;
    units: number;
    stockReleased: boolean;
}

export interface ReconciliationDivergence {
    variant: VariantRef;
    currentStock: number;
    ledgerBalance: number;
    difference: number;
}

export interface Reconciliation {
    checkedVariants: number;
    divergences: ReconciliationDivergence[];
}
