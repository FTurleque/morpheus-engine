package com.morpheus.application.quality;

import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.List;
import java.util.Objects;

/** Snapshot-scoped traceability coverage over CURRENT requirements. */
public record RequirementTraceabilityCoverage(
        KnowledgeSnapshotMetadata snapshot,
        int totalRequirements,
        int linkedRequirements,
        int orphanRequirements,
        double coverageRatio,
        List<QualityFinding> findings) {

    public RequirementTraceabilityCoverage {
        Objects.requireNonNull(snapshot, "snapshot");
        findings = Objects.requireNonNull(findings, "findings").stream()
                .sorted()
                .toList();

        if (totalRequirements < 0 || linkedRequirements < 0 || orphanRequirements < 0) {
            throw new IllegalArgumentException("coverage counts must be >= 0");
        }
        if (linkedRequirements + orphanRequirements != totalRequirements) {
            throw new IllegalArgumentException("linked + orphan must equal total requirements");
        }
        if (!Double.isFinite(coverageRatio) || coverageRatio < 0.0 || coverageRatio > 1.0) {
            throw new IllegalArgumentException("coverageRatio must be finite and between 0.0 and 1.0");
        }
        double expected = totalRequirements == 0 ? 1.0 : (double) linkedRequirements / totalRequirements;
        if (Double.compare(coverageRatio, expected) != 0) {
            throw new IllegalArgumentException("coverageRatio is inconsistent with counts");
        }
        if (findings.size() != orphanRequirements
                || findings.stream().anyMatch(finding -> finding.code() != QualityFindingCode.ORPHAN_REQUIREMENT)) {
            throw new IllegalArgumentException("S1 findings must contain exactly one ORPHAN_REQUIREMENT per orphan");
        }
    }
}
