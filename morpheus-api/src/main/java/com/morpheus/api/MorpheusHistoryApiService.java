package com.morpheus.api;

import com.morpheus.application.history.HistoricalRequirementQueryService;
import com.morpheus.application.history.PublishedSnapshotHistoryService;
import com.morpheus.application.history.RequirementSnapshotComparisonService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns published snapshot history queries and version-history API views. */
final class MorpheusHistoryApiService {
    private final Path databasePath;

    MorpheusHistoryApiService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    Object versions(String projectIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireProject(runtime, projectId);
            PublishedSnapshotHistoryService history = new PublishedSnapshotHistoryService(runtime.snapshots);
            List<KnowledgeSnapshotMetadata> lineage = history.lineage(projectId);
            return map(
                    "projectId", projectId.toString(),
                    "retentionPolicy", history.retentionPolicy().name(),
                    "items", lineage.stream().map(snapshot -> version(runtime, snapshot)).toList());
        }
    }

    Object historicalRequirements(
            String projectIdValue,
            String snapshotIdValue,
            PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.parse(snapshotIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireSnapshotProject(runtime, projectId, snapshotId);
            List<RequirementVersionRecord> records = new HistoricalRequirementQueryService(runtime.snapshots, runtime.requirements)
                    .requirements(snapshotId);
            int total = records.size();
            int from = Math.min(pageRequest.offset(), total);
            int to = (int) Math.min((long) from + pageRequest.limit(), total);
            return map(
                    "snapshotId", snapshotId.toString(),
                    "offset", pageRequest.offset(),
                    "limit", pageRequest.limit(),
                    "totalMatches", total,
                    "hasMore", to < total,
                    "items", records.subList(from, to).stream().map(this::requirementRecord).toList());
        }
    }

    Object compareVersions(String projectIdValue, String sourceIdValue, String targetIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        KnowledgeSnapshotId sourceId = KnowledgeSnapshotId.parse(sourceIdValue);
        KnowledgeSnapshotId targetId = KnowledgeSnapshotId.parse(targetIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireSnapshotProject(runtime, projectId, sourceId);
            requireSnapshotProject(runtime, projectId, targetId);
            var comparison = new RequirementSnapshotComparisonService(runtime.snapshots, runtime.requirements)
                    .compare(sourceId, targetId);
            return map(
                    "projectId", projectId.toString(),
                    "sourceSnapshotId", comparison.sourceSnapshot().id().toString(),
                    "targetSnapshotId", comparison.targetSnapshot().id().toString(),
                    "differences", comparison.differences().stream().map(difference -> map(
                            "requirementId", difference.entityIdentity().toString(),
                            "kind", difference.kind().name(),
                            "source", difference.source().map(this::requirementRecord).orElse(null),
                            "target", difference.target().map(this::requirementRecord).orElse(null))).toList());
        }
    }

    private ProjectStoreEntry requireProject(ApiRuntime runtime, ProjectSpecificationId projectId) {
        return runtime.snapshots.findProject(projectId)
                .orElseThrow(() -> ApiFailure.notFound("project not found: " + projectId));
    }

    private void requireSnapshotProject(
            ApiRuntime runtime,
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId) {
        requireProject(runtime, projectId);
        KnowledgeSnapshotMetadata snapshot = runtime.snapshots.findSnapshot(snapshotId)
                .orElseThrow(() -> ApiFailure.notFound("snapshot not found: " + snapshotId));
        if (!snapshot.projectId().equals(projectId)) {
            throw ApiFailure.notFound("snapshot not found in project: " + snapshotId);
        }
    }

    private Object version(ApiRuntime runtime, KnowledgeSnapshotMetadata snapshot) {
        var binding = runtime.requirements.findSnapshotVersion(snapshot.id())
                .orElseThrow(() -> state("published snapshot has no specification version binding: " + snapshot.id()));
        var specificationVersion = runtime.requirements.findSpecificationVersion(binding.specificationVersionId())
                .orElseThrow(() -> state("specification version not found: " + binding.specificationVersionId()));
        return map(
                "snapshotId", snapshot.id().toString(),
                "snapshotState", snapshot.state().name(),
                "predecessorSnapshotId", snapshot.predecessorId().map(Object::toString).orElse("none"),
                "sourceRevision", snapshot.sourceRevision().orElse("unknown"),
                "snapshotCreatedAt", snapshot.createdAt().toString(),
                "specificationVersionId", specificationVersion.id().toString(),
                "sequence", specificationVersion.sequence().map(Object::toString).orElse("unknown"),
                "providerVersion", specificationVersion.providerVersion().orElse("unknown"),
                "versionCreatedAt", specificationVersion.createdAt().toString(),
                "predecessorSpecificationVersionId", specificationVersion.predecessor().map(Object::toString).orElse("none"));
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

    private KnowledgeStoreException state(String message) {
        return new KnowledgeStoreException(message);
    }

    /** LinkedHashMap keeps construction stable while preserving JSON null in historical diffs. */
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
