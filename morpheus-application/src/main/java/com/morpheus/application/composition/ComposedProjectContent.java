package com.morpheus.application.composition;

import com.morpheus.application.ingestion.NormalizedProjectContent;

import java.util.Objects;

/** Provider-neutral result of a multi-provider composition pass. */
public record ComposedProjectContent(
        NormalizedProjectContent content,
        ProviderCompositionReport report) {

    public ComposedProjectContent {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(report, "report");
    }
}
