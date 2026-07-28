package com.morpheus.application.query.dsl;

import java.util.List;
import java.util.Objects;

/** Application error preserving all structured query diagnostics. */
public final class QueryValidationException extends IllegalArgumentException {
    private final List<QueryDiagnostic> diagnostics;

    public QueryValidationException(List<QueryDiagnostic> diagnostics, String message) {
        super(message);
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (diagnostics.isEmpty()) {
            throw new IllegalArgumentException("diagnostics must not be empty");
        }
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<QueryDiagnostic> diagnostics() {
        return diagnostics;
    }
}
