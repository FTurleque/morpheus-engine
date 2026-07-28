package com.morpheus.application.query.export;

/** Explicit export budget failure; exports are never silently truncated. */
public final class QueryExportBudgetException extends IllegalStateException {
    public QueryExportBudgetException(String message) {
        super(message);
    }
}
