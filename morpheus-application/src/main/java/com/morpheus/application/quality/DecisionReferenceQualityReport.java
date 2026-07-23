package com.morpheus.application.quality;

import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.List;
import java.util.Objects;

/** Snapshot-scoped aggregate for decision and external-reference quality. */
public record DecisionReferenceQualityReport(
        KnowledgeSnapshotMetadata snapshot,
        List<DesignDecisionQualityAssessment> decisions,
        List<ExternalReferenceQualityAssessment> externalReferences,
        List<QualityFinding> findings) {

    public DecisionReferenceQualityReport {
        Objects.requireNonNull(snapshot, "snapshot");
        decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
        externalReferences = List.copyOf(Objects.requireNonNull(externalReferences, "externalReferences"));
        findings = Objects.requireNonNull(findings, "findings").stream()
                .peek(item -> Objects.requireNonNull(item, "findings item"))
                .sorted()
                .toList();
    }
}
