package com.morpheus.api;

import com.morpheus.application.query.BusinessContentQueryService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.query.SpecificationContextQueryService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns active specification list, detail and contextual API views. */
final class MorpheusSpecificationQueryApiService {
    private static final String FIELD_TITLE = "title";

    private final Path databasePath;

    MorpheusSpecificationQueryApiService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    Object listSpecifications(String projectIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            KnowledgeSnapshotMetadata snapshot = activeSnapshot(runtime, projectId);
            var content = runtime.content.findSnapshotContent(snapshot.id())
                    .orElseThrow(() -> state("published snapshot has no business-content projection: " + snapshot.id()));
            List<Specification> specifications = content.specifications().stream()
                    .sorted(Comparator.comparing(Specification::id))
                    .toList();
            return page(snapshot, specifications, pageRequest, specifications.stream().map(this::specification).toList());
        }
    }

    Object specification(String projectIdValue, String specificationIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        SpecificationId specificationId = SpecificationId.parse(specificationIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireProject(runtime, projectId);
            var result = business(runtime).activeSpecification(projectId, specificationId)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            Specification item = result.item()
                    .orElseThrow(() -> ApiFailure.notFound("specification not found: " + specificationId));
            return map("snapshotId", result.snapshot().id().toString(), "specification", specification(item));
        }
    }

    Object specificationContext(
            String projectIdValue,
            String specificationIdValue,
            PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        SpecificationId specificationId = SpecificationId.parse(specificationIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireProject(runtime, projectId);
            var specificationResult = business(runtime).activeSpecification(projectId, specificationId)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            if (specificationResult.item().isEmpty()) {
                throw ApiFailure.notFound("specification not found: " + specificationId);
            }
            var result = new SpecificationContextQueryService(
                    runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability)
                    .active(projectId, specificationId, pageRequest)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return map(
                    "snapshotId", result.snapshot().id().toString(),
                    "specification", specification(result.specification()),
                    "requirements", page(result.requirements(), result.requirements().items().stream()
                            .map(this::requirement).toList()),
                    "scenarios", result.scenarios().stream().map(this::scenario).toList(),
                    "changes", result.changes().stream().map(this::change).toList());
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

    private Object requirement(Requirement item) {
        return map(
                "id", item.id().toString(),
                "specificationId", item.specificationId().toString(),
                "key", item.key().orElse(""),
                FIELD_TITLE, item.title(),
                "statement", item.statement());
    }

    private Object specification(Specification item) {
        return map(
                "id", item.id().toString(),
                "projectId", item.projectId().toString(),
                "key", item.key(),
                FIELD_TITLE, item.title(),
                "description", item.description().orElse(""));
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

    private Object scenario(Scenario item) {
        return map(
                "id", item.id().toString(),
                "requirementId", item.requirementId().map(Object::toString).orElse(""),
                FIELD_TITLE, item.title(),
                "preconditions", item.preconditions(),
                "action", item.action(),
                "expectedOutcome", item.expectedOutcome());
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

    private Object page(
            KnowledgeSnapshotMetadata snapshot,
            List<?> source,
            PageRequest pageRequest,
            List<?> mapped) {
        int total = source.size();
        int from = Math.min(pageRequest.offset(), total);
        int to = (int) Math.min((long) from + pageRequest.limit(), total);
        return map(
                "snapshotId", snapshot.id().toString(),
                "offset", pageRequest.offset(),
                "limit", pageRequest.limit(),
                "totalMatches", total,
                "hasMore", to < total,
                "items", mapped.subList(from, to));
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

    private KnowledgeStoreException state(String message) {
        return new KnowledgeStoreException(message);
    }
}
