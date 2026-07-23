package com.morpheus.application.quality.compact;

import com.morpheus.application.query.compact.CompactQueryTypes.QueryMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.SnapshotMetadata;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Stable compact exposure DTO for one aggregate M6 quality report. */
public record CompactQualityReportView(
        QueryMetadata query,
        SnapshotMetadata snapshot,
        MetricsView metrics,
        List<FindingView> findings) {

    public CompactQualityReportView {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(metrics, "metrics");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }

    public record MetricsView(
            int totalFindings,
            int totalRequirements,
            int linkedRequirements,
            int orphanRequirements,
            double requirementCoverageRatio,
            int totalTasks,
            int coveredTasks,
            int uncoveredTasks,
            double taskCoverageRatio,
            String acceptanceCoverageStatus,
            String lifecycleAggregationStatus,
            int totalChanges,
            int totalDesignDecisions,
            int totalExternalReferences,
            Map<String, Integer> findingsByCode,
            Map<String, Integer> findingsBySeverity,
            Map<String, Integer> findingsByEvidenceKind) {

        public MetricsView {
            acceptanceCoverageStatus = requireNonBlank(acceptanceCoverageStatus, "acceptanceCoverageStatus");
            lifecycleAggregationStatus = requireNonBlank(lifecycleAggregationStatus, "lifecycleAggregationStatus");
            findingsByCode = sortedCounts(findingsByCode, "findingsByCode");
            findingsBySeverity = sortedCounts(findingsBySeverity, "findingsBySeverity");
            findingsByEvidenceKind = sortedCounts(findingsByEvidenceKind, "findingsByEvidenceKind");
        }
    }

    public record FindingView(
            String code,
            String severity,
            String evidenceKind,
            String subjectKind,
            String subjectIdentity,
            String message,
            Map<String, String> details,
            Optional<Double> confidence,
            List<String> evidenceIds) {

        public FindingView {
            code = requireNonBlank(code, "code");
            severity = requireNonBlank(severity, "severity");
            evidenceKind = requireNonBlank(evidenceKind, "evidenceKind");
            subjectKind = requireNonBlank(subjectKind, "subjectKind");
            subjectIdentity = requireNonBlank(subjectIdentity, "subjectIdentity");
            message = requireNonBlank(message, "message");
            details = sortedStrings(details, "details");
            confidence = Objects.requireNonNull(confidence, "confidence");
            evidenceIds = Objects.requireNonNull(evidenceIds, "evidenceIds").stream()
                    .map(value -> requireNonBlank(value, "evidenceIds item"))
                    .distinct()
                    .sorted()
                    .toList();
        }
    }

    private static Map<String, Integer> sortedCounts(Map<String, Integer> values, String name) {
        Objects.requireNonNull(values, name);
        TreeMap<String, Integer> sorted = new TreeMap<>();
        values.forEach((key, value) -> {
            String normalizedKey = requireNonBlank(key, name + " key");
            Objects.requireNonNull(value, name + " value");
            if (value <= 0) {
                throw new IllegalArgumentException(name + " values must be > 0");
            }
            sorted.put(normalizedKey, value);
        });
        return Collections.unmodifiableMap(sorted);
    }

    private static Map<String, String> sortedStrings(Map<String, String> values, String name) {
        Objects.requireNonNull(values, name);
        TreeMap<String, String> sorted = new TreeMap<>();
        values.forEach((key, value) -> sorted.put(
                requireNonBlank(key, name + " key"),
                Objects.requireNonNull(value, name + " value")));
        return Collections.unmodifiableMap(sorted);
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
