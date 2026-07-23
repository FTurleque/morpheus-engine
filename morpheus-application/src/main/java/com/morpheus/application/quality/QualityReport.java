package com.morpheus.application.quality;

import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable aggregate of the snapshot-scoped M6 quality analyses. */
public record QualityReport(
        KnowledgeSnapshotMetadata snapshot,
        RequirementTraceabilityCoverage requirements,
        TaskRequirementCoverage tasks,
        AcceptanceCoverageAssessment acceptance,
        ChangeCompletenessReport changes,
        DecisionReferenceQualityReport decisionsAndReferences,
        LifecycleQualityAggregationStatus lifecycleAggregationStatus,
        QualityReportMetrics metrics,
        List<QualityFinding> findings) {

    public QualityReport {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(acceptance, "acceptance");
        Objects.requireNonNull(changes, "changes");
        Objects.requireNonNull(decisionsAndReferences, "decisionsAndReferences");
        Objects.requireNonNull(lifecycleAggregationStatus, "lifecycleAggregationStatus");
        Objects.requireNonNull(metrics, "metrics");

        requireSameSnapshot(snapshot, requirements.snapshot(), "requirements");
        requireSameSnapshot(snapshot, tasks.snapshot(), "tasks");
        requireSameSnapshot(snapshot, acceptance.snapshot(), "acceptance");
        requireSameSnapshot(snapshot, changes.snapshot(), "changes");
        requireSameSnapshot(snapshot, decisionsAndReferences.snapshot(), "decisionsAndReferences");

        findings = Objects.requireNonNull(findings, "findings").stream()
                .peek(item -> Objects.requireNonNull(item, "findings item"))
                .distinct()
                .sorted()
                .toList();

        List<QualityFinding> expected = expectedFindings(
                requirements,
                tasks,
                acceptance,
                changes,
                decisionsAndReferences);
        if (!expected.equals(findings)) {
            throw new IllegalArgumentException("aggregate findings must equal the distinct ordered component findings");
        }
        if (metrics.totalFindings() != findings.size()) {
            throw new IllegalArgumentException("metrics.totalFindings must match aggregate findings");
        }
        if (metrics.totalRequirements() != requirements.totalRequirements()
                || metrics.linkedRequirements() != requirements.linkedRequirements()
                || metrics.orphanRequirements() != requirements.orphanRequirements()
                || Double.compare(metrics.requirementCoverageRatio(), requirements.coverageRatio()) != 0) {
            throw new IllegalArgumentException("requirement metrics must match RequirementTraceabilityCoverage");
        }
        if (metrics.totalTasks() != tasks.totalTasks()
                || metrics.coveredTasks() != tasks.coveredTasks()
                || metrics.uncoveredTasks() != tasks.uncoveredTasks()
                || Double.compare(metrics.taskCoverageRatio(), tasks.coverageRatio()) != 0) {
            throw new IllegalArgumentException("task metrics must match TaskRequirementCoverage");
        }
        if (metrics.acceptanceCoverageStatus() != acceptance.status()) {
            throw new IllegalArgumentException("acceptance metric must match AcceptanceCoverageAssessment");
        }
        if (metrics.totalChanges() != changes.changes().size()) {
            throw new IllegalArgumentException("totalChanges must match ChangeCompletenessReport");
        }
        if (metrics.totalDesignDecisions() != decisionsAndReferences.decisions().size()) {
            throw new IllegalArgumentException("totalDesignDecisions must match DecisionReferenceQualityReport");
        }
        if (metrics.totalExternalReferences() != decisionsAndReferences.externalReferences().size()) {
            throw new IllegalArgumentException("totalExternalReferences must match DecisionReferenceQualityReport");
        }
    }

    static List<QualityFinding> expectedFindings(
            RequirementTraceabilityCoverage requirements,
            TaskRequirementCoverage tasks,
            AcceptanceCoverageAssessment acceptance,
            ChangeCompletenessReport changes,
            DecisionReferenceQualityReport decisionsAndReferences) {
        List<QualityFinding> result = new ArrayList<>();
        result.addAll(requirements.findings());
        result.addAll(tasks.findings());
        result.addAll(acceptance.findings());
        changes.changes().forEach(change -> result.addAll(change.findings()));
        result.addAll(decisionsAndReferences.findings());
        return result.stream().distinct().sorted().toList();
    }

    private static void requireSameSnapshot(
            KnowledgeSnapshotMetadata expected,
            KnowledgeSnapshotMetadata actual,
            String component) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(component + " belongs to another snapshot");
        }
    }
}
