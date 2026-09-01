package com.morpheus.api;

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Owns active change list, detail, subresource and contextual API views. */
final class MorpheusChangeQueryApiService {
    private static final String FIELD_TITLE = "title";

    private final Path databasePath;

    MorpheusChangeQueryApiService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    Object listChanges(String projectIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireProject(runtime, projectId);
            SnapshotPage<ChangeProposal> result = business(runtime).listActiveChanges(projectId, pageRequest)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return page(result, result.items().stream().map(this::change).toList());
        }
    }

    Object change(String projectIdValue, String changeIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            var result = requireChange(runtime, projectId, changeId);
            return map("snapshotId", result.snapshot().id().toString(), "change", change(result.item().orElseThrow()));
        }
    }

    Object constraints(String projectIdValue, String changeIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireChange(runtime, projectId, changeId);
            SnapshotPage<Constraint> result = business(runtime).activeConstraints(projectId, changeId, pageRequest)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return page(result, result.items().stream().map(this::constraint).toList());
        }
    }

    Object acceptanceCriteria(String projectIdValue, String changeIdValue) {
        return acceptanceCriteria(projectIdValue, changeIdValue, PageRequest.first(MorpheusApiService.MAX_LIMIT));
    }

    Object acceptanceCriteria(String projectIdValue, String changeIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireChange(runtime, projectId, changeId);
            SnapshotPage<AcceptanceCriterion> result = business(runtime)
                    .activeAcceptanceCriteriaForChange(projectId, changeId, pageRequest)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return page(result, result.items().stream().map(this::acceptanceCriterion).toList());
        }
    }

    Object designDecisions(String projectIdValue, String changeIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireChange(runtime, projectId, changeId);
            SnapshotPage<DesignDecision> result = business(runtime).activeDesignDecisions(projectId, changeId, pageRequest)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return page(result, result.items().stream().map(this::decision).toList());
        }
    }

    Object implementationTasks(String projectIdValue, String changeIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireChange(runtime, projectId, changeId);
            SnapshotPage<ImplementationTask> result = business(runtime).activeImplementationTasks(projectId, changeId, pageRequest)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return page(result, result.items().stream().map(this::task).toList());
        }
    }

    Object changeContext(String projectIdValue, String changeIdValue, int depth) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        requireRange("depth", depth, 1, MorpheusApiService.MAX_DEPTH);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            activeSnapshot(runtime, projectId);
            var result = new ChangeContextQueryService(
                    runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability, runtime.externalReferences)
                    .active(projectId, changeId, depth, Set.of())
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            if (result.change().isEmpty()) {
                throw ApiFailure.notFound("change not found: " + changeId);
            }
            return new CompactQueryViewService(runtime.content).changeContext(result);
        }
    }

    private void requireProject(ApiRuntime runtime, ProjectSpecificationId projectId) {
        if (runtime.snapshots.findProject(projectId).isEmpty()) {
            throw ApiFailure.notFound("project not found: " + projectId);
        }
    }

    private KnowledgeSnapshotMetadata activeSnapshot(ApiRuntime runtime, ProjectSpecificationId projectId) {
        requireProject(runtime, projectId);
        return runtime.snapshots.activeSnapshot(projectId)
                .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
    }

    private BusinessContentQueryService business(ApiRuntime runtime) {
        return new BusinessContentQueryService(runtime.snapshots, runtime.content);
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

    private Object change(ChangeProposal item) {
        return map(
                "id", item.id().toString(),
                "projectId", item.projectId().toString(),
                "key", item.key().orElse(""),
                FIELD_TITLE, item.title(),
                "intent", item.intent(),
                "scope", item.scope(),
                "outOfScope", item.outOfScope(),
                "risks", item.risks());
    }

    private Object constraint(Constraint item) {
        return map("id", item.id().toString(), "changeId", item.changeId().toString(), "statement", item.statement());
    }

    private Object acceptanceCriterion(AcceptanceCriterion item) {
        return map(
                "id", item.id().toString(),
                "requirementId", item.requirementId().map(Object::toString).orElse(""),
                "changeId", item.changeId().map(Object::toString).orElse(""),
                FIELD_TITLE, item.title(),
                "condition", item.condition(),
                "verificationStatus", item.verificationStatus().name(),
                "verificationEvidenceIds", item.verificationEvidenceIds().stream().map(Object::toString).toList(),
                "sourceEvidenceId", item.provenance().evidenceId().toString());
    }

    private Object decision(DesignDecision item) {
        return map(
                "id", item.id().toString(),
                "changeId", item.changeId().toString(),
                FIELD_TITLE, item.title(),
                "decision", item.decision());
    }

    private Object task(ImplementationTask item) {
        return map(
                "id", item.id().toString(),
                "changeId", item.changeId().toString(),
                "key", item.key().orElse(""),
                FIELD_TITLE, item.title(),
                "completed", item.completed());
    }

    private Object page(SnapshotPage<?> page, List<?> items) {
        return map(
                "snapshotId", page.snapshot().id().toString(),
                "offset", page.pageRequest().offset(),
                "limit", page.pageRequest().limit(),
                "totalMatches", page.totalMatches(),
                "hasMore", page.hasMore(),
                "items", items);
    }

    /** LinkedHashMap preserves stable construction order before canonical JSON serialization. */
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

    private void requireRange(String name, long value, long minimum, long maximum) {
        if (value < minimum || value > maximum) {
            throw ApiFailure.badRequest(name + " must be between " + minimum + " and " + maximum);
        }
    }
}
