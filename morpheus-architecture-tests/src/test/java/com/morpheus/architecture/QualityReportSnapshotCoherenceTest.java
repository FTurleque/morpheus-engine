package com.morpheus.architecture;

import com.morpheus.application.quality.AcceptanceCoverageAssessment;
import com.morpheus.application.quality.AcceptanceCoverageStatus;
import com.morpheus.application.quality.ChangeCompletenessReport;
import com.morpheus.application.quality.DecisionReferenceQualityReport;
import com.morpheus.application.quality.LifecycleQualityAggregationStatus;
import com.morpheus.application.quality.QualityReport;
import com.morpheus.application.quality.QualityReportMetrics;
import com.morpheus.application.quality.RequirementTraceabilityCoverage;
import com.morpheus.application.quality.TaskRequirementCoverage;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

class QualityReportSnapshotCoherenceTest {

    @Test
    void reportRejectsAComponentFromAnotherPublishedSnapshot() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotMetadata first = snapshot(projectId, KnowledgeSnapshotId.generate());
        KnowledgeSnapshotMetadata second = snapshot(projectId, KnowledgeSnapshotId.generate());

        RequirementTraceabilityCoverage wrongRequirements =
                new RequirementTraceabilityCoverage(second, 0, 0, 0, 1.0, List.of());
        TaskRequirementCoverage tasks = new TaskRequirementCoverage(first, 0, 0, 0, 1.0, List.of());
        AcceptanceCoverageAssessment acceptance = new AcceptanceCoverageAssessment(
                first,
                AcceptanceCoverageStatus.NO_CRITERIA,
                0, 0, 0, 0, 0, 0,
                1.0,
                List.of());
        ChangeCompletenessReport changes = new ChangeCompletenessReport(first, List.of());
        DecisionReferenceQualityReport decisions = new DecisionReferenceQualityReport(
                first, List.of(), List.of(), List.of());
        QualityReportMetrics metrics = new QualityReportMetrics(
                0,
                0, 0, 0, 1.0,
                0, 0, 0, 1.0,
                AcceptanceCoverageStatus.NO_CRITERIA,
                0, 0, 0,
                Map.of(), Map.of(), Map.of());

        assertThrows(IllegalArgumentException.class, () -> new QualityReport(
                first,
                wrongRequirements,
                tasks,
                acceptance,
                changes,
                decisions,
                LifecycleQualityAggregationStatus.REQUIRES_EXPLICIT_LIFECYCLE_INPUT,
                metrics,
                List.of()));
    }

    private KnowledgeSnapshotMetadata snapshot(
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId) {
        return new KnowledgeSnapshotMetadata(
                snapshotId,
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.ACTIVE,
                Optional.of("revision"),
                Instant.parse("2026-07-23T21:10:00Z"));
    }
}
