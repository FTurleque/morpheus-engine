package com.morpheus.api;

import com.morpheus.application.quality.AcceptanceQualityService;
import com.morpheus.application.quality.ChangeCompletenessAssessment;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.DecisionReferenceQualityService;
import com.morpheus.application.quality.QualityFinding;
import com.morpheus.application.quality.QualityReport;
import com.morpheus.application.quality.QualityReportService;
import com.morpheus.application.quality.RequirementQualityService;
import com.morpheus.application.quality.TaskQualityService;
import com.morpheus.application.quality.compact.CompactQualityReportService;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns project quality diagnostics and change completeness API views. */
final class MorpheusDiagnosticsApiService {
    private final Path databasePath;

    MorpheusDiagnosticsApiService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    Object changeStatus(String projectIdValue, String changeIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            ChangeCompletenessAssessment assessment = completeness(runtime, projectId, changeId);
            return map(
                    "snapshotId", activeSnapshot(runtime, projectId).id().toString(),
                    "changeId", changeId.toString(),
                    "status", "UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT",
                    "lifecycleState", "UNAVAILABLE",
                    "observableFacts", lifecycleFacts(assessment),
                    "reason", "Published snapshot content does not persist an explicit ChangeLifecycle state; MORPHEUS does not infer it.");
        }
    }

    Object blockingConditions(String projectIdValue, String changeIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            ChangeCompletenessAssessment assessment = completeness(runtime, projectId, changeId);
            return map(
                    "snapshotId", activeSnapshot(runtime, projectId).id().toString(),
                    "changeId", changeId.toString(),
                    "currentRequirementCount", assessment.currentRequirementCount(),
                    "constraintCount", assessment.constraintCount(),
                    "designDecisionCount", assessment.designDecisionCount(),
                    "implementationTaskCount", assessment.implementationTaskCount(),
                    "observableFacts", lifecycleFacts(assessment),
                    "unavailableFacts", assessment.lifecycleFacts().unavailableFacts(),
                    "findings", assessment.findings().stream().map(this::finding).toList());
        }
    }

    Object diagnostics(String projectIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            activeSnapshot(runtime, projectId);
            QualityReportService service = new QualityReportService(
                    runtime.snapshots,
                    new RequirementQualityService(runtime.snapshots, runtime.requirements, runtime.traceability),
                    new TaskQualityService(runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability),
                    new AcceptanceQualityService(runtime.snapshots, runtime.content),
                    new ChangeCompletenessService(runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability),
                    new DecisionReferenceQualityService(
                            runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability, runtime.externalReferences));
            QualityReport report = service.assessActive(projectId)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return new CompactQualityReportService().view(report);
        }
    }

    private ProjectStoreEntry requireProject(ApiRuntime runtime, ProjectSpecificationId projectId) {
        return runtime.snapshots.findProject(projectId)
                .orElseThrow(() -> ApiFailure.notFound("project not found: " + projectId));
    }

    private KnowledgeSnapshotMetadata activeSnapshot(ApiRuntime runtime, ProjectSpecificationId projectId) {
        requireProject(runtime, projectId);
        return runtime.snapshots.activeSnapshot(projectId)
                .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
    }

    private ChangeCompletenessAssessment completeness(
            ApiRuntime runtime,
            ProjectSpecificationId projectId,
            ChangeId changeId) {
        activeSnapshot(runtime, projectId);
        var report = new ChangeCompletenessService(
                runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability)
                .assessActive(projectId)
                .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
        return report.changes().stream()
                .filter(item -> item.change().id().equals(changeId))
                .findFirst()
                .orElseThrow(() -> ApiFailure.notFound("change not found: " + changeId));
    }

    private Object lifecycleFacts(ChangeCompletenessAssessment assessment) {
        var facts = assessment.lifecycleFacts();
        return map(
                "requirementsIdentified", facts.requirementsIdentified().name(),
                "criticalConstraintsKnown", facts.criticalConstraintsKnown().name(),
                "acceptanceCriteriaDefined", facts.acceptanceCriteriaDefined().name(),
                "designRequired", facts.designRequired().name(),
                "designDecisionsAvailable", facts.designDecisionsAvailable().name(),
                "planPresent", facts.planPresent().name(),
                "knownBlocker", facts.knownBlocker().name(),
                "blockingAcceptanceCriterionFailed", facts.blockingAcceptanceCriterionFailed().name(),
                "blockingAcceptanceCriterionUnverified", facts.blockingAcceptanceCriterionUnverified().name());
    }

    private Object finding(QualityFinding finding) {
        return map(
                "code", finding.code().name(),
                "severity", finding.severity().name(),
                "evidenceKind", finding.evidenceKind().name(),
                "subjectKind", finding.subject().kind().name(),
                "subjectId", finding.subject().identity().toString(),
                "message", finding.message(),
                "details", finding.details(),
                "confidence", finding.confidence().map(Object::toString).orElse(""),
                "evidenceIds", finding.evidenceIds().stream().map(Object::toString).toList());
    }

    /** LinkedHashMap keeps construction stable while the canonical serializer sorts JSON keys. */
    private Map<String, Object> map(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("map entries must be key/value pairs");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return Collections.unmodifiableMap(result);
    }
}
