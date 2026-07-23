package com.morpheus.application.quality;

import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Deterministic snapshot-scoped completeness report for normalized changes. */
public record ChangeCompletenessReport(
        KnowledgeSnapshotMetadata snapshot,
        List<ChangeCompletenessAssessment> changes) {

    public ChangeCompletenessReport {
        Objects.requireNonNull(snapshot, "snapshot");
        changes = Objects.requireNonNull(changes, "changes").stream()
                .peek(item -> Objects.requireNonNull(item, "changes item"))
                .sorted(Comparator.comparing(item -> item.change().id().toString()))
                .toList();
    }
}
