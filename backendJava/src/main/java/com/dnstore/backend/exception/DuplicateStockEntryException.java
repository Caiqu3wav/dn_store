package com.dnstore.backend.exception;

import java.util.UUID;

/** Já existe uma nota com o mesmo número para o fornecedor. Carrega o id da nota existente. */
public class DuplicateStockEntryException extends ConflictException {

    public static final String CODE = "STOCK_ENTRY_DUPLICATE";

    private final UUID existingEntryId;

    public DuplicateStockEntryException(String message, UUID existingEntryId) {
        super(message);
        this.existingEntryId = existingEntryId;
    }

    public UUID getExistingEntryId() {
        return existingEntryId;
    }
}
