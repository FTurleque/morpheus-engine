package com.morpheus.api;

import com.morpheus.application.query.BusinessContentQueryService;
import com.morpheus.application.query.ConstraintEvaluationQueryService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.ConstraintEvaluation;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Additive read-only HTTP adapter for explicit M16 constraint-policy evaluations. */
final class MorpheusConstraintSemanticsApiService {
    private final Path databasePath;

    MorpheusConstraintSemanticsApiService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    Object evaluate(
            String projectIdValue,
            String changeIdValue,
            String targetStateValue,
            PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        ChangeLifecycleState targetState = lifecycle(targetStateValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            if (runtime.snapshots.findProject(projectId).isEmpty()) {
                throw ApiFailure.notFound("project not found: " + projectId);
            }
            var change = new BusinessContentQueryService(runtime.snapshots, runtime.content)
                    .activeChange(projectId, changeId)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            if (change.item().isEmpty()) {
                throw ApiFailure.notFound("change not found: " + changeId);
            }
            var result = new ConstraintEvaluationQueryService(runtime.snapshots, runtime.content)
                    .activeEvaluations(projectId, changeId, targetState, pageRequest)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return new EvaluationPageView(
                    result.snapshot().id().toString(),
                    result.pageRequest().offset(),
                    result.pageRequest().limit(),
                    result.totalMatches(),
                    result.hasMore(),
                    result.items().stream().map(this::view).toList());
        }
    }

    private EvaluationView view(ConstraintEvaluation evaluation) {
        return new EvaluationView(
                evaluation.constraintId().toString(),
                evaluation.changeId().toString(),
                evaluation.targetState().name(),
                evaluation.state().name(),
                evaluation.applicability().name(),
                evaluation.severity().name(),
                evaluation.satisfaction().name(),
                evaluation.blockingPolicy().mode().name(),
                evaluation.blockingPolicy().targetStates().stream().map(Enum::name).toList(),
                evaluation.reason(),
                evaluation.supportingEvidenceIds().stream().map(Object::toString).toList(),
                evaluation.sourceEvidenceId().toString());
    }

    private ChangeLifecycleState lifecycle(String value) {
        if (value == null || value.isBlank()) {
            throw ApiFailure.badRequest("query parameter is required: targetState");
        }
        try {
            return ChangeLifecycleState.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw ApiFailure.badRequest("targetState is not a valid MORPHEUS lifecycle state: " + value);
        }
    }

    private record EvaluationPageView(
            String snapshotId,
            int offset,
            int limit,
            int totalMatches,
            boolean hasMore,
            List<EvaluationView> items) {
    }

    private record EvaluationView(
            String constraintId,
            String changeId,
            String targetState,
            String state,
            String applicability,
            String severity,
            String satisfaction,
            String blockingMode,
            List<String> blockingTargets,
            String reason,
            List<String> supportingEvidenceIds,
            String sourceEvidenceId) {
    }
}
