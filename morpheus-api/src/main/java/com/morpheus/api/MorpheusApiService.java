package com.morpheus.api;

import com.morpheus.application.history.HistoricalRequirementQueryService;
import com.morpheus.application.history.PublishedSnapshotHistoryService;
import com.morpheus.application.history.RequirementSnapshotComparisonService;
import com.morpheus.application.product.ProductMetadata;
import com.morpheus.application.query.BusinessContentQueryService;
import com.morpheus.application.query.ChangeContextQueryService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.RequirementQueryService;
import com.morpheus.application.query.RequirementSearchQuery;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.query.SpecificationContextQueryService;
import com.morpheus.application.query.TraceRequirementQueryService;
import com.morpheus.application.query.compact.CompactQueryViewService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Headless M11 application adapter facade.
 *
 * <p>This class performs adapter orchestration and DTO mapping only. Business semantics remain in
 * MORPHEUS application/domain services.</p>
 */
public final class MorpheusApiService {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = PageRequest.MAX_LIMIT;
    public static final int DEFAULT_DEPTH = 2;
    public static final int MAX_DEPTH = 20;
    public static final long DEFAULT_MAX_AGE_MINUTES = 60L;
    public static final long MAX_MAX_AGE_MINUTES = 525_600L;

    private final Path databasePath;
    private final MorpheusProjectRegistryApiService projectRegistryService;
    private final MorpheusProjectSyncApiService projectSyncService;
    private final MorpheusDiagnosticsApiService diagnosticsService;

    public MorpheusApiService(Path databasePath) {
        this(databasePath, Optional.empty());
    }

    MorpheusApiService(Path databasePath, AllowedWorkspaceRoots allowedWorkspaceRoots) {
        this(databasePath, Optional.of(Objects.requireNonNull(allowedWorkspaceRoots, "allowedWorkspaceRoots")));
    }

    private MorpheusApiService(Path databasePath, Optional<AllowedWorkspaceRoots> allowedWorkspaceRoots) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        Optional<AllowedWorkspaceRoots> workspaceRoots = Objects.requireNonNull(allowedWorkspaceRoots, "allowedWorkspaceRoots");
        this.projectRegistryService = new MorpheusProjectRegistryApiService(this.databasePath, workspaceRoots);
        this.projectSyncService = new MorpheusProjectSyncApiService(this.databasePath, workspaceRoots);
        this.diagnosticsService = new MorpheusDiagnosticsApiService(this.databasePath);
    }

    public Object health() {
        return map("status", "UP", "service", "morpheus", "apiVersion", ProductMetadata.API_VERSION);
    }

    public Object version() {
        return map("version", ProductMetadata.version());
    }

    public Object listProjects() {
        return projectRegistryService.listProjects();
    }

    public RegistrationResult registerProject(String workspace) {
        MorpheusProjectRegistryApiService.RegistrationResult result = projectRegistryService.registerProject(workspace);
        return new RegistrationResult(result.project(), result.created());
    }

    public Object project(String projectIdValue) {
        return projectRegistryService.project(projectIdValue);
    }

    MorpheusProjectRegistryApiService projectRegistryService() {
        return projectRegistryService;
    }

    MorpheusProjectSyncApiService projectSyncService() {
        return projectSyncService;
    }

    MorpheusDiagnosticsApiService diagnosticsService() {
        return diagnosticsService;
    }

    public Object sync(String projectIdValue, Optional<String> revision) {
        return projectSyncService.sync(projectIdValue, revision);
    }

    public Object syncStatus(String projectIdValue, long maxAgeMinutes) {
        return projectSyncService.syncStatus(projectIdValue, maxAgeMinutes);
    }

    public Object listSpecifications(String projectIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
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

    public Object specification(String projectIdValue, String specificationIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        SpecificationId specificationId = SpecificationId.parse(specificationIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireProject(runtime, projectId);
            var result = business(runtime).activeSpecification(projectId, specificationId)
                    .orElseThrow(() -> conflict("project has no ACTIVE snapshot: " + projectId));
            Specification item = result.item().orElseThrow(() -> notFound("specification not found: " + specificationId));
            return map("snapshotId", result.snapshot().id().toString(), "specification", specification(item));
        }
    }

    public Object specificationContext(
            String projectIdValue,
            String specificationIdValue,
            PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        SpecificationId specificationId = SpecificationId.parse(specificationIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireProject(runtime, projectId);
            var specificationResult = business(runtime).activeSpecification(projectId, specificationId)
                    .orElseThrow(() -> conflict("project has no ACTIVE snapshot: " + projectId));
            if (specificationResult.item().isEmpty()) {
                throw notFound("specification not found: " + specificationId);
            }
            var result = new SpecificationContextQueryService(
                    runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability)
                    .active(projectId, specificationId, pageRequest)
                    .orElseThrow(() -> conflict("project has no ACTIVE snapshot: " + projectId));
            return map(
                    "snapshotId", result.snapshot().id().toString(),
                    "specification", specification(result.specification()),
                    "requirements", page(result.requirements(), result.requirements().items().stream()
                            .map(this::requirement).toList()),
                    "scenarios", result.scenarios().stream().map(this::scenario).toList(),
                    "changes", result.changes().stream().map(this::change).toList());
        }
    }

    public Object requirements(String projectIdValue, String query, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        String queryText = query == null ? "" : query.trim();
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireProject(runtime, projectId);
            var result = new RequirementQueryService(runtime.snapshots, runtime.requirements)
                    .findActive(projectId, new RequirementSearchQuery(queryText), pageRequest)
                    .orElseThrow(() -> conflict("project has no ACTIVE snapshot: " + projectId));
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

    public Object requirement(String projectIdValue, String requirementIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        RequirementId requirementId = RequirementId.parse(requirementIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            KnowledgeSnapshotMetadata snapshot = activeSnapshot(runtime, projectId);
            RequirementVersionRecord record = runtime.requirements.currentRequirement(snapshot.id(), requirementId.value())
                    .orElseThrow(() -> notFound("requirement not found: " + requirementId));
            return map("snapshotId", snapshot.id().toString(), "requirement", requirementRecord(record));
        }
    }

    public Object traceRequirement(String projectIdValue, String requirementIdValue, int depth) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        RequirementId requirementId = RequirementId.parse(requirementIdValue);
        requireRange("depth", depth, 1, MAX_DEPTH);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            activeSnapshot(runtime, projectId);
            var result = new TraceRequirementQueryService(
                    runtime.snapshots, runtime.requirements, runtime.traceability, runtime.externalReferences)
                    .active(projectId, requirementId, depth, Set.of())
                    .orElseThrow(() -> notFound("requirement not found: " + requirementId));
            return new CompactQueryViewService(runtime.content).traceRequirement(result);
        }
    }

    public Object listChanges(String projectIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireProject(runtime, projectId);
            SnapshotPage<ChangeProposal> result = business(runtime).listActiveChanges(projectId, pageRequest)
                    .orElseThrow(() -> conflict("project has no ACTIVE snapshot: " + projectId));
            return page(result, result.items().stream().map(this::change).toList());
        }
    }

    public Object change(String projectIdValue, String changeIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            var result = requireChange(runtime, projectId, changeId);
            return map("snapshotId", result.snapshot().id().toString(), "change", change(result.item().orElseThrow()));
        }
    }

    public Object constraints(String projectIdValue, String changeIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireChange(runtime, projectId, changeId);
            SnapshotPage<Constraint> result = business(runtime).activeConstraints(projectId, changeId, pageRequest)
                    .orElseThrow(() -> conflict("project has no ACTIVE snapshot: " + projectId));
            return page(result, result.items().stream().map(this::constraint).toList());
        }
    }

    public Object acceptanceCriteria(String projectIdValue, String changeIdValue) {
        return acceptanceCriteria(projectIdValue, changeIdValue, PageRequest.first(MAX_LIMIT));
    }

    public Object acceptanceCriteria(
            String projectIdValue,
            String changeIdValue,
            PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        Objects.requireNonNull(pageRequest, "pageRequest");
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireChange(runtime, projectId, changeId);
            SnapshotPage<AcceptanceCriterion> result = business(runtime)
                    .activeAcceptanceCriteriaForChange(projectId, changeId, pageRequest)
                    .orElseThrow(() -> conflict("project has no ACTIVE snapshot: " + projectId));
            return page(result, result.items().stream().map(this::acceptanceCriterion).toList());
        }
    }

    public Object designDecisions(String projectIdValue, String changeIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireChange(runtime, projectId, changeId);
            SnapshotPage<DesignDecision> result = business(runtime).activeDesignDecisions(projectId, changeId, pageRequest)
                    .orElseThrow(() -> conflict("project has no ACTIVE snapshot: " + projectId));
            return page(result, result.items().stream().map(this::decision).toList());
        }
    }

    public Object implementationTasks(String projectIdValue, String changeIdValue, PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireChange(runtime, projectId, changeId);
            SnapshotPage<ImplementationTask> result = business(runtime).activeImplementationTasks(projectId, changeId, pageRequest)
                    .orElseThrow(() -> conflict("project has no ACTIVE snapshot: " + projectId));
            return page(result, result.items().stream().map(this::task).toList());
        }
    }

    public Object changeContext(String projectIdValue, String changeIdValue, int depth) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        requireRange("depth", depth, 1, MAX_DEPTH);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            activeSnapshot(runtime, projectId);
            var result = new ChangeContextQueryService(
                    runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability, runtime.externalReferences)
                    .active(projectId, changeId, depth, Set.of())
                    .orElseThrow(() -> conflict("project has no ACTIVE snapshot: " + projectId));
            if (result.change().isEmpty()) {
                throw notFound("change not found: " + changeId);
            }
            return new CompactQueryViewService(runtime.content).changeContext(result);
        }
    }

    public Object changeStatus(String projectIdValue, String changeIdValue) {
        return diagnosticsService.changeStatus(projectIdValue, changeIdValue);
    }

    public Object blockingConditions(String projectIdValue, String changeIdValue) {
        return diagnosticsService.blockingConditions(projectIdValue, changeIdValue);
    }

    public Object versions(String projectIdValue) {
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

    public Object historicalRequirements(
            String projectIdValue,
            String snapshotIdValue,
            PageRequest pageRequest) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.parse(snapshotIdValue);
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

    public Object compareVersions(String projectIdValue, String sourceIdValue, String targetIdValue) {
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

    public Object diagnostics(String projectIdValue) {
        return diagnosticsService.diagnostics(projectIdValue);
    }

    private ProjectStoreEntry requireProject(ApiRuntime runtime, ProjectSpecificationId projectId) {
        return runtime.snapshots.findProject(projectId)
                .orElseThrow(() -> notFound("project not found: " + projectId));
    }

    private KnowledgeSnapshotMetadata activeSnapshot(ApiRuntime runtime, ProjectSpecificationId projectId) {
        requireProject(runtime, projectId);
        return runtime.snapshots.activeSnapshot(projectId)
                .orElseThrow(() -> conflict("project has no ACTIVE snapshot: " + projectId));
    }

    private void requireSnapshotProject(ApiRuntime runtime, ProjectSpecificationId projectId, KnowledgeSnapshotId snapshotId) {
        requireProject(runtime, projectId);
        KnowledgeSnapshotMetadata snapshot = runtime.snapshots.findSnapshot(snapshotId)
                .orElseThrow(() -> notFound("snapshot not found: " + snapshotId));
        if (!snapshot.projectId().equals(projectId)) {
            throw notFound("snapshot not found in project: " + snapshotId);
        }
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
                .orElseThrow(() -> conflict("project has no ACTIVE snapshot: " + projectId));
        if (result.item().isEmpty()) {
            throw notFound("change not found: " + changeId);
        }
        return result;
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

    private Object specification(Specification item) {
        return map(
                "id", item.id().toString(),
                "projectId", item.projectId().toString(),
                "key", item.key(),
                "title", item.title(),
                "description", item.description().orElse(""));
    }

    private Object change(ChangeProposal item) {
        return map(
                "id", item.id().toString(),
                "projectId", item.projectId().toString(),
                "key", item.key().orElse(""),
                "title", item.title(),
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
                "title", item.title(),
                "condition", item.condition(),
                "verificationStatus", item.verificationStatus().name(),
                "verificationEvidenceIds", item.verificationEvidenceIds().stream().map(Object::toString).toList(),
                "sourceEvidenceId", item.provenance().evidenceId().toString());
    }

    private Object decision(DesignDecision item) {
        return map(
                "id", item.id().toString(),
                "changeId", item.changeId().toString(),
                "title", item.title(),
                "decision", item.decision());
    }

    private Object task(ImplementationTask item) {
        return map(
                "id", item.id().toString(),
                "changeId", item.changeId().toString(),
                "key", item.key().orElse(""),
                "title", item.title(),
                "completed", item.completed());
    }

    private Object scenario(Scenario item) {
        return map(
                "id", item.id().toString(),
                "requirementId", item.requirementId().map(Object::toString).orElse(""),
                "title", item.title(),
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

    /** LinkedHashMap keeps construction stable while the canonical serializer sorts JSON keys. */
    private Map<String, Object> map(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("map entries must be key/value pairs");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        // Unlike Map.copyOf, this deliberately preserves null values used to represent an absent
        // source/target side in ADDED/REMOVED historical diffs. CanonicalJsonSerializer emits JSON null.
        return Collections.unmodifiableMap(result);
    }

    private void requireRange(String name, long value, long minimum, long maximum) {
        if (value < minimum || value > maximum) {
            throw ApiFailure.badRequest(name + " must be between " + minimum + " and " + maximum);
        }
    }

    private ApiFailure notFound(String message) {
        return ApiFailure.notFound(message);
    }

    private ApiFailure conflict(String message) {
        return ApiFailure.conflict(message);
    }

    private KnowledgeStoreException state(String message) {
        return new KnowledgeStoreException(message);
    }

    public record RegistrationResult(Object project, boolean created) {
        public RegistrationResult {
            Objects.requireNonNull(project, "project");
        }
    }
}
