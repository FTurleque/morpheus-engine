package com.morpheus.api;

import com.morpheus.api.transport.ApiFailure;
import com.morpheus.api.transport.PagedEnvelope;
import com.morpheus.application.foundation.PayloadMap;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.payload.PublicPayloads;
import com.morpheus.application.query.BusinessContentQueryService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.query.SpecificationContextQueryService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Owns active specification list, detail and contextual API views. */
public final class MorpheusSpecificationQueryApiService {
    private final Path databasePath;
    private final OperationalRecorder recorder;

    MorpheusSpecificationQueryApiService(Path databasePath, OperationalRecorder recorder) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public Object listSpecifications(String projectIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath, recorder)) {
            KnowledgeSnapshotMetadata snapshot = activeSnapshot(runtime, projectId);
            var content = runtime.content().findSnapshotContent(snapshot.id())
                    .orElseThrow(() -> state("published snapshot has no business-content projection: " + snapshot.id()));
            List<Specification> specifications = content.specifications().stream()
                    .sorted(Comparator.comparing(Specification::id))
                    .toList();
            return PagedEnvelope.following(
                    PayloadMap.of("snapshotId", snapshot.id().toString()),
                    PagedEnvelope.slice(pageRequest.offset(), pageRequest.limit(), specifications, PublicPayloads::specification));
        }
    }

    public Object specification(String projectIdValue, String specificationIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        SpecificationId specificationId = SpecificationId.parse(specificationIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath, recorder)) {
            requireProject(runtime, projectId);
            var result = business(runtime).activeSpecification(projectId, specificationId)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            Specification item = result.item()
                    .orElseThrow(() -> ApiFailure.notFound("specification not found: " + specificationId));
            return PayloadMap.of("snapshotId", result.snapshot().id().toString(), "specification", PublicPayloads.specification(item));
        }
    }

    public Object specificationContext(
            String projectIdValue,
            String specificationIdValue,
            PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        SpecificationId specificationId = SpecificationId.parse(specificationIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath, recorder)) {
            requireProject(runtime, projectId);
            var specificationResult = business(runtime).activeSpecification(projectId, specificationId)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            if (specificationResult.item().isEmpty()) {
                throw ApiFailure.notFound("specification not found: " + specificationId);
            }
            var result = new SpecificationContextQueryService(
                    runtime.snapshots(), runtime.content(), runtime.requirements(), runtime.traceability())
                    .active(projectId, specificationId, pageRequest)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return PayloadMap.of(
                    "snapshotId", result.snapshot().id().toString(),
                    "specification", PublicPayloads.specification(result.specification()),
                    "requirements", page(result.requirements(), result.requirements().items().stream()
                            .map(PublicPayloads::requirement).toList()),
                    "scenarios", result.scenarios().stream().map(PublicPayloads::scenario).toList(),
                    "changes", result.changes().stream().map(PublicPayloads::change).toList());
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

    private Object page(SnapshotPage<?> page, List<?> items) {
        return PagedEnvelope.snapshotPage(page, items);
    }

    private KnowledgeStoreException state(String message) {
        return new KnowledgeStoreException(message);
    }
}
