package com.morpheus.application.quality;

import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.List;
import java.util.Objects;

/** Snapshot-scoped acceptance verification coverage and findings. */
public record AcceptanceCoverageAssessment(
        KnowledgeSnapshotMetadata snapshot,
        AcceptanceCoverageStatus status,
        int totalCriteria,
        int verifiedCriteria,
        int partiallyVerifiedCriteria,
        int failedCriteria,
        int notVerifiedCriteria,
        int unknownCriteria,
        double verifiedCoverageRatio,
        List<QualityFinding> findings) {

    public AcceptanceCoverageAssessment {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(status, "status");
        requireNonNegative(totalCriteria, "totalCriteria");
        requireNonNegative(verifiedCriteria, "verifiedCriteria");
        requireNonNegative(partiallyVerifiedCriteria, "partiallyVerifiedCriteria");
        requireNonNegative(failedCriteria, "failedCriteria");
        requireNonNegative(notVerifiedCriteria, "notVerifiedCriteria");
        requireNonNegative(unknownCriteria, "unknownCriteria");

        int classified = verifiedCriteria
                + partiallyVerifiedCriteria
                + failedCriteria
                + notVerifiedCriteria
                + unknownCriteria;
        if (classified != totalCriteria) {
            throw new IllegalArgumentException("acceptance verification counts must sum to totalCriteria");
        }

        double expectedRatio = totalCriteria == 0 ? 1.0 : (double) verifiedCriteria / totalCriteria;
        if (Double.compare(expectedRatio, verifiedCoverageRatio) != 0) {
            throw new IllegalArgumentException("verifiedCoverageRatio does not match acceptance counts");
        }
        if (status == AcceptanceCoverageStatus.NO_CRITERIA && totalCriteria != 0) {
            throw new IllegalArgumentException("NO_CRITERIA requires zero acceptance criteria");
        }
        if (status == AcceptanceCoverageStatus.EVALUATED && totalCriteria == 0) {
            throw new IllegalArgumentException("EVALUATED requires at least one acceptance criterion");
        }
        if (status == AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL && totalCriteria != 0) {
            throw new IllegalArgumentException("unavailable acceptance coverage cannot expose criteria");
        }

        findings = Objects.requireNonNull(findings, "findings").stream()
                .peek(item -> Objects.requireNonNull(item, "findings item"))
                .distinct()
                .sorted()
                .toList();
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
    }
}
