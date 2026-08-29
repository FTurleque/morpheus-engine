package com.morpheus.api;

import com.morpheus.application.product.ProductMetadata;
import com.morpheus.application.query.BusinessContentQueryService;
import com.morpheus.application.query.ChangeContextQueryService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.query.compact.CompactQueryViewService;
import com.morpheus.application.store.ProjectStoreEntry;
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
    private final MorpheusHistoryApiService historyService;
    private final MorpheusRequirementQueryApiService requirementQueryService;
    private final MorpheusSpecificationQueryApiService specificationQueryService;

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
        this.historyService = new MorpheusHistoryApiService(this.databasePath);
        this.requirementQueryService = new MorpheusRequirementQueryApiService(this.databasePath);
        this.specificationQueryService = new MorpheusSpecificationQueryApiService(this.databasePath);
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

    MorpheusHistoryApiService historyService() {
        return historyService;
    }

    MorpheusRequirementQueryApiService requirementQueryService() {
        return requirementQueryService;
    }

    MorpheusSpecificationQueryApiService specificationQueryService() {
        return specificationQueryService;
    }

    public Object sync(String projectIdValue, Optional<String> revision) {
        return projectSyncService.sync(projectIdValue, revision);
    }

    public Object syncStatus(String projectIdValue, long maxAgeMinutes) {
        return projectSyncService.syncStatus(projectIdValue, maxAgeMinutes);
    }

    public Object listSpecifications(String projectIdValue, PageRequest pageRequest) {
        return specificationQueryService.listSpecifications(projectIdValue, pageRequest);
    }

    public Object specification(String projectIdValue, String specificationIdValue) {
        return specificationQueryService.specification(projectIdValue, specificationIdValue);
    }

    public Object specificationContext(
            String projectIdValue,
            String specificationIdValue,
            PageRequest pageRequest) {
        return specificationQueryService.specificationContext(projectIdValue, specificationIdValue, pageRequest);
    }

    public Object requirements(String projectIdValue, String query, PageRequest pageRequest) {
        return requirementQueryService.requirements(projectIdValue, query, pageRequest);
    }

    public Object requirement(String projectIdValue, String requirementIdValue) {
        return requirementQueryService.requirement(projectIdValue, requirementIdValue);
    }

    public Object traceRequirement(String projectIdValue, String requirementIdValue, int depth) {
        return requirementQueryService.traceRequirement(projectIdValue, requirementIdValue, depth);
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
        return historyService.versions(projectIdValue);
    }

    public Object historicalRequirements(
            String projectIdValue,
            String snapshotIdValue,
            PageRequest pageRequest) {
        return historyService.historicalRequirements(projectIdValue, snapshotIdValue, pageRequest);
    }

    public Object compareVersions(String projectIdValue, String sourceIdValue, String targetIdValue) {
        return historyService.compareVersions(projectIdValue, sourceIdValue, targetIdValue);
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

    private Object page(SnapshotPage<?> page, List<?> items) {
        return map(
                "snapshotId", page.snapshot().id().toString(),
                "offset", page.pageRequest().offset(),
                "limit", page.pageRequest().limit(),
                "totalMatches", page.totalMatches(),
                "hasMore", page.hasMore(),
                "items", items);
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

    public record RegistrationResult(Object project, boolean created) {
        public RegistrationResult {
            Objects.requireNonNull(project, "project");
        }
    }
}
