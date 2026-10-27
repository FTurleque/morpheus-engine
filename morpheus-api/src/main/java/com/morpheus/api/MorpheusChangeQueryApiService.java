package com.morpheus.api;

import com.morpheus.api.transport.ApiFailure;
import com.morpheus.api.transport.PagedEnvelope;
import com.morpheus.application.foundation.PayloadMap;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.payload.PublicPayloads;
import com.morpheus.application.query.BusinessContentQueryService;
import com.morpheus.application.query.ChangeContextQueryService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.query.compact.CompactQueryViewService;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.task.ImplementationTask;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Owns active change list, detail, subresource and contextual API views. */
public final class MorpheusChangeQueryApiService {
    private final Path databasePath;
    private final OperationalRecorder recorder;

    MorpheusChangeQueryApiService(Path databasePath, OperationalRecorder recorder) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public Object listChanges(String projectIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath, recorder)) {
            requireProject(runtime, projectId);
            SnapshotPage<ChangeProposal> result = business(runtime).listActiveChanges(projectId, pageRequest)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return page(result, result.items().stream().map(PublicPayloads::change).toList());
        }
    }

    public Object change(String projectIdValue, String changeIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath, recorder)) {
            var result = requireChange(runtime, projectId, changeId);
            return PayloadMap.of("snapshotId", result.snapshot().id().toString(), "change", PublicPayloads.change(result.item().orElseThrow()));
        }
    }

    public Object constraints(String projectIdValue, String changeIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath, recorder)) {
            requireChange(runtime, projectId, changeId);
            SnapshotPage<Constraint> result = business(runtime).activeConstraints(projectId, changeId, pageRequest)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return page(result, result.items().stream().map(PublicPayloads::constraint).toList());
        }
    }

    Object acceptanceCriteria(String projectIdValue, String changeIdValue) {
        return acceptanceCriteria(projectIdValue, changeIdValue, PageRequest.first(MorpheusApiService.MAX_LIMIT));
    }

    public Object acceptanceCriteria(String projectIdValue, String changeIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath, recorder)) {
            requireChange(runtime, projectId, changeId);
            SnapshotPage<AcceptanceCriterion> result = business(runtime)
                    .activeAcceptanceCriteriaForChange(projectId, changeId, pageRequest)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return page(result, result.items().stream().map(PublicPayloads::acceptanceCriterion).toList());
        }
    }

    public Object designDecisions(String projectIdValue, String changeIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath, recorder)) {
            requireChange(runtime, projectId, changeId);
            SnapshotPage<DesignDecision> result = business(runtime).activeDesignDecisions(projectId, changeId, pageRequest)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return page(result, result.items().stream().map(PublicPayloads::decision).toList());
        }
    }

    public Object implementationTasks(String projectIdValue, String changeIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath, recorder)) {
            requireChange(runtime, projectId, changeId);
            SnapshotPage<ImplementationTask> result = business(runtime).activeImplementationTasks(projectId, changeId, pageRequest)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return page(result, result.items().stream().map(PublicPayloads::task).toList());
        }
    }

    public Object changeContext(String projectIdValue, String changeIdValue, int depth) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        requireRange("depth", depth, 1, MorpheusApiService.MAX_DEPTH);
        try (ApiRuntime runtime = new ApiRuntime(databasePath, recorder)) {
            activeSnapshot(runtime, projectId);
            var result = new ChangeContextQueryService(
                    runtime.snapshots(), runtime.content(), runtime.requirements(), runtime.traceability(), runtime.externalReferences())
                    .active(projectId, changeId, depth, Set.of())
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            if (result.change().isEmpty()) {
                throw ApiFailure.notFound("change not found: " + changeId);
            }
            return new CompactQueryViewService(runtime.content()).changeContext(result);
        }
    }

    private void requireProject(ApiRuntime runtime, ProjectSpecificationId projectId) {
        if (runtime.snapshots().findProject(projectId).isEmpty()) {
            throw ApiFailure.notFound("project not found: " + projectId);
        }
    }

    private KnowledgeSnapshotMetadata activeSnapshot(ApiRuntime runtime, ProjectSpecificationId projectId) {
        requireProject(runtime, projectId);
        return runtime.snapshots().activeSnapshot(projectId)
                .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
    }

    private BusinessContentQueryService business(ApiRuntime runtime) {
        return new BusinessContentQueryService(runtime.snapshots(), runtime.content());
    }

    private com.morpheus.application.query.SnapshotItemResult<ChangeProposal> requireChange(
            ApiRuntime runtime,
            ProjectSpecificationId projectId,
            ChangeId changeId) {
        requireProject(runtime, projectId);
        var result = business(runtime).activeChange(projectId, changeId)
                .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
        if (result.item().isEmpty()) {
            throw ApiFailure.notFound("change not found: " + changeId);
        }
        return result;
    }

    private Object page(SnapshotPage<?> page, List<?> items) {
        return PagedEnvelope.snapshotPage(page, items);
    }

    private void requireRange(String name, long value, long minimum, long maximum) {
        if (value < minimum || value > maximum) {
            throw ApiFailure.badRequest(name + " must be between " + minimum + " and " + maximum);
        }
    }
}
