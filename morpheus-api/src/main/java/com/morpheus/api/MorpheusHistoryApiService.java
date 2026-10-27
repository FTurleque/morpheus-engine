package com.morpheus.api;

import com.morpheus.api.transport.ApiFailure;
import com.morpheus.api.transport.PagedEnvelope;
import com.morpheus.application.foundation.PayloadMap;
import com.morpheus.application.history.HistoricalRequirementQueryService;
import com.morpheus.application.history.PublishedSnapshotHistoryService;
import com.morpheus.application.history.RequirementSnapshotComparisonService;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Owns published snapshot history queries and version-history API views. */
public final class MorpheusHistoryApiService {
    private final Path databasePath;
    private final OperationalRecorder recorder;

    MorpheusHistoryApiService(Path databasePath, OperationalRecorder recorder) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    /**
     * One page of the published lineage, oldest first. The lineage never shrinks ({@code KEEP_ALL_PUBLISHED}), so it is
     * walked whole to know {@code totalMatches}, but only the retained page pays the two store reads of {@link #version}.
     */
    public Object versions(String projectIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath, recorder)) {
            requireProject(runtime, projectId);
            PublishedSnapshotHistoryService history = new PublishedSnapshotHistoryService(runtime.snapshots());
            List<KnowledgeSnapshotMetadata> lineage = history.lineage(projectId);
            return PagedEnvelope.following(
                    PayloadMap.of("projectId", projectId.toString(), "retentionPolicy", history.retentionPolicy().name()),
                    PagedEnvelope.slice(
                            pageRequest.offset(), pageRequest.limit(), lineage, snapshot -> version(runtime, snapshot)));
        }
    }

    public Object historicalRequirements(
            String projectIdValue,
            String snapshotIdValue,
            PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.parse(snapshotIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath, recorder)) {
            requireSnapshotProject(runtime, projectId, snapshotId);
            List<RequirementVersionRecord> records = new HistoricalRequirementQueryService(runtime.snapshots(), runtime.requirements())
                    .requirements(snapshotId);
            return PagedEnvelope.following(
                    PayloadMap.of("snapshotId", snapshotId.toString()),
                    PagedEnvelope.slice(pageRequest.offset(), pageRequest.limit(), records, this::requirementRecord));
        }
    }

    public Object compareVersions(String projectIdValue, String sourceIdValue, String targetIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        KnowledgeSnapshotId sourceId = KnowledgeSnapshotId.parse(sourceIdValue);
        KnowledgeSnapshotId targetId = KnowledgeSnapshotId.parse(targetIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath, recorder)) {
            requireSnapshotProject(runtime, projectId, sourceId);
            requireSnapshotProject(runtime, projectId, targetId);
            var comparison = new RequirementSnapshotComparisonService(runtime.snapshots(), runtime.requirements())
                    .compare(sourceId, targetId);
            return PayloadMap.of(
                    "projectId", projectId.toString(),
                    "sourceSnapshotId", comparison.sourceSnapshot().id().toString(),
                    "targetSnapshotId", comparison.targetSnapshot().id().toString(),
                    "differences", comparison.differences().stream().map(difference -> PayloadMap.admittingAbsentValues(
                            "requirementId", difference.entityIdentity().toString(),
                            "kind", difference.kind().name(),
                            "source", difference.source().map(this::requirementRecord).orElse(null),
                            "target", difference.target().map(this::requirementRecord).orElse(null))).toList());
        }
    }

    private ProjectStoreEntry requireProject(ApiRuntime runtime, ProjectSpecificationId projectId) {
        return runtime.snapshots().findProject(projectId)
                .orElseThrow(() -> ApiFailure.notFound("project not found: " + projectId));
    }

    private void requireSnapshotProject(
            ApiRuntime runtime,
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId) {
        requireProject(runtime, projectId);
        KnowledgeSnapshotMetadata snapshot = runtime.snapshots().findSnapshot(snapshotId)
                .orElseThrow(() -> ApiFailure.notFound("snapshot not found: " + snapshotId));
        if (!snapshot.projectId().equals(projectId)) {
            throw ApiFailure.notFound("snapshot not found in project: " + snapshotId);
        }
    }

    private Object version(ApiRuntime runtime, KnowledgeSnapshotMetadata snapshot) {
        var binding = runtime.requirements().findSnapshotVersion(snapshot.id())
                .orElseThrow(() -> state("published snapshot has no specification version binding: " + snapshot.id()));
        var specificationVersion = runtime.requirements().findSpecificationVersion(binding.specificationVersionId())
                .orElseThrow(() -> state("specification version not found: " + binding.specificationVersionId()));
        return PayloadMap.of(
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
        return PayloadMap.of(
                "entityVersionId", version.id().toString(),
                "specificationVersionId", version.specificationVersionId().toString(),
                "temporalState", version.temporalState().name(),
                "requirement", requirement(version.content()));
    }

    private Object requirement(Requirement item) {
        return PayloadMap.of(
                "id", item.id().toString(),
                "specificationId", item.specificationId().toString(),
                "key", item.key().orElse(""),
                "title", item.title(),
                "statement", item.statement());
    }

    private KnowledgeStoreException state(String message) {
        return new KnowledgeStoreException(message);
    }
}
