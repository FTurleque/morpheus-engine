package com.morpheus.application.quality;

import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.List;
import java.util.Objects;

/** Deterministic coverage of implementation tasks by published CURRENT requirements. */
public record TaskRequirementCoverage(
        KnowledgeSnapshotMetadata snapshot,
        int totalTasks,
        int coveredTasks,
        int uncoveredTasks,
        double coverageRatio,
        List<QualityFinding> findings) {

    public TaskRequirementCoverage {
        Objects.requireNonNull(snapshot, "snapshot");
        if (totalTasks < 0 || coveredTasks < 0 || uncoveredTasks < 0) {
            throw new IllegalArgumentException("task coverage counts must be non-negative");
        }
        if (coveredTasks + uncoveredTasks != totalTasks) {
            throw new IllegalArgumentException("covered + uncovered must equal total tasks");
        }
        if (!Double.isFinite(coverageRatio) || coverageRatio < 0.0 || coverageRatio > 1.0) {
            throw new IllegalArgumentException("coverageRatio must be finite and between 0.0 and 1.0");
        }
        double expected = totalTasks == 0 ? 1.0 : (double) coveredTasks / totalTasks;
        if (Double.compare(expected, coverageRatio) != 0) {
            throw new IllegalArgumentException("coverageRatio does not match task counts");
        }
        findings = Objects.requireNonNull(findings, "findings").stream()
                .peek(item -> Objects.requireNonNull(item, "findings item"))
                .sorted()
                .toList();
    }
}
