package com.morpheus.api;

import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.RequirementQueryService;
import com.morpheus.application.query.RequirementSearchQuery;
import com.morpheus.application.query.TraceRequirementQueryService;
import com.morpheus.application.query.compact.CompactQueryViewService;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Owns active requirement search, detail and trace API views. */
final class MorpheusRequirementQueryApiService {
    private final Path databasePath;

    MorpheusRequirementQueryApiService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    Object requirements(String projectIdValue, String query, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        String queryText = query == null ? "" : query.trim();
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireProject(runtime, projectId);
            var result = new RequirementQueryService(runtime.snapshots, runtime.requirements)
                    .findActive(projectId, new RequirementSearchQuery(queryText), pageRequest)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return map(
                    "snapshotId", result.snapshot().id().toString(),
                    "query", queryText,
                    "offset", result.pageRequest().offset(),
                    "limit", result.pageRequest().limit(),
                    "totalMatches", result.totalMatches(),
                    "hasMore", result.hasMore(),
                    "items", result.items().stream().map(this::requirementRecord).toList());
        }
    }

    Object requirement(String projectIdValue, String requirementIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        RequirementId requirementId = RequirementId.parse(requirementIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            KnowledgeSnapshotMetadata snapshot = activeSnapshot(runtime, projectId);
            RequirementVersionRecord record = runtime.requirements.currentRequirement(snapshot.id(), requirementId.value())
                    .orElseThrow(() -> ApiFailure.notFound("requirement not found: " + requirementId));
            return map("snapshotId", snapshot.id().toString(), "requirement", requirementRecord(record));
        }
    }

    Object traceRequirement(String projectIdValue, String requirementIdValue, int depth) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        RequirementId requirementId = RequirementId.parse(requirementIdValue);
        requireRange("depth", depth, 1, MorpheusApiService.MAX_DEPTH);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            activeSnapshot(runtime, projectId);
            var result = new TraceRequirementQueryService(
                    runtime.snapshots, runtime.requirements, runtime.traceability, runtime.externalReferences)
                    .active(projectId, requirementId, depth, Set.of())
                    .orElseThrow(() -> ApiFailure.notFound("requirement not found: " + requirementId));
            return new CompactQueryViewService(runtime.content).traceRequirement(result);
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

    private Object requirementRecord(RequirementVersionRecord record) {
        var version = record.entityVersion();
        return map(
                "entityVersionId", version.id().toString(),
                "specificationVersionId", version.specificationVersionId().toString(),
                "temporalState", version.temporalState().name(),
                "requirement", requirement(version.content()));
    }

    private Object requirement(Requirement item) {
        return map(
                "id", item.id().toString(),
                "specificationId", item.specificationId().toString(),
                "key", item.key().orElse(""),
                "title", item.title(),
                "statement", item.statement());
    }

    private void requireRange(String name, long value, long minimum, long maximum) {
        if (value < minimum || value > maximum) {
            throw ApiFailure.badRequest(name + " must be between " + minimum + " and " + maximum);
        }
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
}
