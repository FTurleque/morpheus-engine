package com.morpheus.application.quality;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Aggregates validated M6 snapshot analyses without reimplementing their rules. */
public final class QualityReportService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final RequirementQualityService requirementQuality;
    private final TaskQualityService taskQuality;
    private final AcceptanceQualityService acceptanceQuality;
    private final ChangeCompletenessService changeCompleteness;
    private final DecisionReferenceQualityService decisionReferenceQuality;

    public QualityReportService(
            SpecificationKnowledgeStore snapshotStore,
            RequirementQualityService requirementQuality,
            TaskQualityService taskQuality,
            AcceptanceQualityService acceptanceQuality,
            ChangeCompletenessService changeCompleteness,
            DecisionReferenceQualityService decisionReferenceQuality) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.requirementQuality = Objects.requireNonNull(requirementQuality, "requirementQuality");
        this.taskQuality = Objects.requireNonNull(taskQuality, "taskQuality");
        this.acceptanceQuality = Objects.requireNonNull(acceptanceQuality, "acceptanceQuality");
        this.changeCompleteness = Objects.requireNonNull(changeCompleteness, "changeCompleteness");
        this.decisionReferenceQuality = Objects.requireNonNull(decisionReferenceQuality, "decisionReferenceQuality");
    }

    public Optional<QualityReport> assessActive(ProjectSpecificationId projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return snapshotStore.activeSnapshot(projectId).map(this::assessPublished);
    }

    public QualityReport assessSnapshot(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("unknown knowledge snapshot: " + snapshotId));
        requirePublished(snapshot);
        return assessPublished(snapshot);
    }

    private QualityReport assessPublished(KnowledgeSnapshotMetadata snapshot) {
        KnowledgeSnapshotId snapshotId = snapshot.id();
        RequirementTraceabilityCoverage requirements = requirementQuality.assessSnapshot(snapshotId);
        TaskRequirementCoverage tasks = taskQuality.assessSnapshot(snapshotId);
        AcceptanceCoverageAssessment acceptance = acceptanceQuality.assessSnapshot(snapshotId);
        ChangeCompletenessReport changes = changeCompleteness.assessSnapshot(snapshotId);
        DecisionReferenceQualityReport decisionsAndReferences = decisionReferenceQuality.assessSnapshot(snapshotId);

        List<QualityFinding> findings = QualityReport.expectedFindings(
                requirements,
                tasks,
                acceptance,
                changes,
                decisionsAndReferences);

        QualityReportMetrics metrics = new QualityReportMetrics(
                findings.size(),
                requirements.totalRequirements(),
                requirements.linkedRequirements(),
                requirements.orphanRequirements(),
                requirements.coverageRatio(),
                tasks.totalTasks(),
                tasks.coveredTasks(),
                tasks.uncoveredTasks(),
                tasks.coverageRatio(),
                acceptance.status(),
                changes.changes().size(),
                decisionsAndReferences.decisions().size(),
                decisionsAndReferences.externalReferences().size(),
                countByCode(findings),
                countBySeverity(findings),
                countByEvidenceKind(findings));

        return new QualityReport(
                snapshot,
                requirements,
                tasks,
                acceptance,
                changes,
                decisionsAndReferences,
                LifecycleQualityAggregationStatus.REQUIRES_EXPLICIT_LIFECYCLE_INPUT,
                metrics,
                findings);
    }

    private Map<QualityFindingCode, Integer> countByCode(List<QualityFinding> findings) {
        EnumMap<QualityFindingCode, Integer> counts = new EnumMap<>(QualityFindingCode.class);
        findings.forEach(finding -> counts.merge(finding.code(), 1, Integer::sum));
        return counts;
    }

    private Map<DiagnosticSeverity, Integer> countBySeverity(List<QualityFinding> findings) {
        EnumMap<DiagnosticSeverity, Integer> counts = new EnumMap<>(DiagnosticSeverity.class);
        findings.forEach(finding -> counts.merge(finding.severity(), 1, Integer::sum));
        return counts;
    }

    private Map<QualityEvidenceKind, Integer> countByEvidenceKind(List<QualityFinding> findings) {
        EnumMap<QualityEvidenceKind, Integer> counts = new EnumMap<>(QualityEvidenceKind.class);
        findings.forEach(finding -> counts.merge(finding.evidenceKind(), 1, Integer::sum));
        return counts;
    }

    private void requirePublished(KnowledgeSnapshotMetadata snapshot) {
        if (snapshot.state() != KnowledgeSnapshotState.ACTIVE
                && snapshot.state() != KnowledgeSnapshotState.RETIRED) {
            throw new KnowledgeStoreException(
                    "quality report requires an ACTIVE or RETIRED snapshot: "
                            + snapshot.id() + " is " + snapshot.state());
        }
    }
}
