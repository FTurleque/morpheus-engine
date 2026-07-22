package com.morpheus.application.read;

import com.morpheus.domain.diagnostic.DiagnosticCode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Outcome of one explicitly requested content category. */
public record ReadCategoryReport(
        ReadCategory category,
        ReadCategoryStatus status,
        int itemCount,
        List<DiagnosticCode> diagnosticCodes,
        Optional<String> detail) {

    public ReadCategoryReport {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(status, "status");
        if (itemCount < 0) {
            throw new IllegalArgumentException("itemCount must be >= 0");
        }
        diagnosticCodes = List.copyOf(Objects.requireNonNull(diagnosticCodes, "diagnosticCodes"));
        detail = Objects.requireNonNull(detail, "detail").map(String::trim).filter(value -> !value.isEmpty());
    }

    public static ReadCategoryReport of(ReadCategory category, ReadCategoryStatus status, int itemCount) {
        return new ReadCategoryReport(category, status, itemCount, List.of(), Optional.empty());
    }
}
