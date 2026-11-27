package com.dnstore.backend.exception;

import java.util.Map;

/** Conflito de estoque (saldo desatualizado, nota duplicada...) com detalhes que o cliente pode usar. */
public class StockConflictException extends ConflictException {

    private final Map<String, String> details;

    public StockConflictException(String message, Map<String, String> details) {
        super(message);
        this.details = Map.copyOf(details);
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
