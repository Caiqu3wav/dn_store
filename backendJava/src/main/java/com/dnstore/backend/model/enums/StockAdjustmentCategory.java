package com.dnstore.backend.model.enums;

/** Por que o saldo foi ajustado à mão. Gravado em stock_movements.category. */
public enum StockAdjustmentCategory {
    COUNT,
    LOSS,
    DAMAGE,
    CORRECTION,
    PRODUCT_EDIT,
    OTHER
}
