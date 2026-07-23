package com.morpheus.application.quality;

import com.morpheus.domain.diagnostic.DiagnosticSeverity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Stable aggregate metrics over one published quality report. */
public record QualityReportMetrics(
        int totalFindings,
        int totalRequirements,
        int linkedRequirements,
        int orphanRequirements,
        double requirementCoverageRatio,
        int totalTasks,
        int coveredTasks,
        int uncoveredTasks,
        double taskCoverageRatio,
        AcceptanceCoverageStatus acceptanceCoverageStatus,
        int totalChanges,
        int totalDesignDecisions,
        int totalExternalReferences,
        Map<QualityFindingCode, Integer> findingsByCode,
        Map<DiagnosticSeverity, Integer> findingsBySeverity,
        Map<QualityEvidenceKind, Integer> findingsByEvidenceKind) {

    public QualityReportMetrics {
        requireNonNegative(totalFindings, "totalFindings");
        requireNonNegative(totalRequirements, "totalRequirements");
        requireNonNegative(linkedRequirements, "linkedRequirements");
        requireNonNegative(orphanRequirements, "orphanRequirements");
        requireNonNegative(totalTasks, "totalTasks");
        requireNonNegative(coveredTasks, "coveredTasks");
        requireNonNegative(uncoveredTasks, "uncoveredTasks");
        requireNonNegative(totalChanges, "totalChanges");
        requireNonNegative(totalDesignDecisions, "totalDesignDecisions");
        requireNonNegative(totalExternalReferences, "totalExternalReferences");
        requireRatio(requirementCoverageRatio, "requirementCoverageRatio");
        requireRatio(taskCoverageRatio, "taskCoverageRatio");
        Objects.requireNonNull(acceptanceCoverageStatus, "acceptanceCoverageStatus");

        if (linkedRequirements + orphanRequirements != totalRequirements) {
            throw new IllegalArgumentException("linked + orphan must equal total requirements");
        }
        if (coveredTasks + uncoveredTasks != totalTasks) {
            throw new IllegalArgumentException("covered + uncovered must equal total tasks");
        }
        double expectedRequirementRatio = totalRequirements == 0
                ? 1.0
                : (double) linkedRequirements / totalRequirements;
        if (Double.compare(expectedRequirementRatio, requirementCoverageRatio) != 0) {
            throw new IllegalArgumentException("requirementCoverageRatio does not match requirement counts");
        }
        double expectedTaskRatio = totalTasks == 0 ? 1.0 : (double) coveredTasks / totalTasks;
        if (Double.compare(expectedTaskRatio, taskCoverageRatio) != 0) {
            throw new IllegalArgumentException("taskCoverageRatio does not match task counts");
        }

        findingsByCode = immutableCodeCounts(findingsByCode);
        findingsBySeverity = immutableSeverityCounts(findingsBySeverity);
        findingsByEvidenceKind = immutableEvidenceKindCounts(findingsByEvidenceKind);

        if (sum(findingsByCode) != totalFindings
                || sum(findingsBySeverity) != totalFindings
                || sum(findingsByEvidenceKind) != totalFindings) {
            throw new IllegalArgumentException("finding count maps must each sum to totalFindings");
        }
    }

    private static Map<QualityFindingCode, Integer> immutableCodeCounts(
            Map<QualityFindingCode, Integer> values) {
        Objects.requireNonNull(values, "findingsByCode");
        EnumMap<QualityFindingCode, Integer> copy = new EnumMap<>(QualityFindingCode.class);
        values.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "findingsByCode key"),
                requirePositive(value, "findingsByCode value")));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<DiagnosticSeverity, Integer> immutableSeverityCounts(
            Map<DiagnosticSeverity, Integer> values) {
        Objects.requireNonNull(values, "findingsBySeverity");
        EnumMap<DiagnosticSeverity, Integer> copy = new EnumMap<>(DiagnosticSeverity.class);
        values.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "findingsBySeverity key"),
                requirePositive(value, "findingsBySeverity value")));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<QualityEvidenceKind, Integer> immutableEvidenceKindCounts(
            Map<QualityEvidenceKind, Integer> values) {
        Objects.requireNonNull(values, "findingsByEvidenceKind");
        EnumMap<QualityEvidenceKind, Integer> copy = new EnumMap<>(QualityEvidenceKind.class);
        values.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "findingsByEvidenceKind key"),
                requirePositive(value, "findingsByEvidenceKind value")));
        return Collections.unmodifiableMap(copy);
    }

    private static int sum(Map<?, Integer> values) {
        return values.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static int requirePositive(Integer value, String name) {
        Objects.requireNonNull(value, name);
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
    }

    private static void requireRatio(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and between 0.0 and 1.0");
        }
    }
}
