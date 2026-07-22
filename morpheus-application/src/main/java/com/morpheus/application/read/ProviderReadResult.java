package com.morpheus.application.read;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.provider.ProviderId;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Explicit normalized read result, including one report for every requested category. */
public record ProviderReadResult(
        ProviderId providerId,
        Optional<NormalizedProjectContent> content,
        List<ReadCategoryReport> categoryReports,
        List<Diagnostic> diagnostics) {

    public ProviderReadResult {
        Objects.requireNonNull(providerId, "providerId");
        content = Objects.requireNonNull(content, "content");
        categoryReports = List.copyOf(Objects.requireNonNull(categoryReports, "categoryReports"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));

        var seen = EnumSet.noneOf(ReadCategory.class);
        for (ReadCategoryReport report : categoryReports) {
            if (!seen.add(report.category())) {
                throw new IllegalArgumentException("duplicate read category report: " + report.category());
            }
        }
    }

    public Optional<ReadCategoryReport> report(ReadCategory category) {
        Objects.requireNonNull(category, "category");
        return categoryReports.stream().filter(report -> report.category() == category).findFirst();
    }
}
