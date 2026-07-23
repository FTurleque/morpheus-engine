package com.morpheus.application.quality;

import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.List;
import java.util.Objects;

/** Snapshot-scoped statement about whether acceptance-criterion coverage can be evaluated. */
public record AcceptanceCoverageAssessment(
        KnowledgeSnapshotMetadata snapshot,
        AcceptanceCoverageStatus status,
        List<QualityFinding> findings) {

    public AcceptanceCoverageAssessment {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(status, "status");
        findings = Objects.requireNonNull(findings, "findings").stream()
                .peek(item -> Objects.requireNonNull(item, "findings item"))
                .sorted()
                .toList();
    }
}
