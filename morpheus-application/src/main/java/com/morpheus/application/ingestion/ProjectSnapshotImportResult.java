package com.morpheus.application.ingestion;

import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.version.SpecificationVersion;

import java.util.List;
import java.util.Objects;

/** Immutable receipt for one full normalized-content publication into a new knowledge snapshot. */
public record ProjectSnapshotImportResult(
        KnowledgeSnapshotMetadata snapshot,
        SpecificationVersion specificationVersion,
        int requirementCount,
        int traceabilityLinkCount,
        List<Diagnostic> diagnostics) {

    public ProjectSnapshotImportResult {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(specificationVersion, "specificationVersion");
        if (requirementCount < 0 || traceabilityLinkCount < 0) {
            throw new IllegalArgumentException("import counts must be non-negative");
        }
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}