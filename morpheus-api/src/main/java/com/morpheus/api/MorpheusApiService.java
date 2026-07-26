package com.morpheus.api;

import com.morpheus.application.history.HistoricalRequirementQueryService;
import com.morpheus.application.history.PublishedSnapshotHistoryService;
import com.morpheus.application.history.RequirementSnapshotComparisonService;
import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ProjectSnapshotImportResult;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.quality.AcceptanceQualityService;
import com.morpheus.application.quality.ChangeCompletenessAssessment;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.DecisionReferenceQualityService;
import com.morpheus.application.quality.QualityFinding;
import com.morpheus.application.quality.QualityReport;
import com.morpheus.application.quality.QualityReportService;
import com.morpheus.application.quality.RequirementQualityService;
import com.morpheus.application.quality.TaskQualityService;
import com.morpheus.application.quality.compact.CompactQualityReportService;
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
import com.morpheus.application.sync.IncrementalSyncService;
import com.morpheus.application.sync.LocalSourceInventoryScanner;
import com.morpheus.application.sync.SyncFreshness;
import com.morpheus.application.sync.SyncFreshnessService;
import com.morpheus.application.sync.SyncPlan;
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
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.provider.openspec.OpenSpecProjectContentReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
    public static final String FALLBACK_VERSION = "0.1.0-SNAPSHOT";

    private final Path databasePath;

    public MorpheusApiService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    public Object health() {
        return map("status", "UP", "service", "morpheus", "apiVersion", "v1");
    }

    public Object version() {
        String implementationVersion = MorpheusApiService.class.getPackage().getImplementationVersion();
        return map("version", implementationVersion == null || implementationVersion.isBlank()
                ? FALLBACK_VERSION : implementationVersion);
    }

    public Object listProjects() {
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            return runtime.snapshots.listProjects().stream()
                    .map(project -> project(runtime, project))
                    .toList();
        }
    }

    public RegistrationResult registerProject(String workspace) {
        Path path = existingDirectory(workspace);
        SourceLocator root = SourceLocator.file(path.toString());
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            Optional<ProjectStoreEntry> existing = runtime.snapshots.findProjectByRoot(root);
            ProjectStoreEntry entry = existing.orElseGet(() -> {
                ProjectStoreEntry created = new ProjectStoreEntry(ProjectSpecificationId.generate(), root);
                runtime.snapshots.putProject(created);
                return created;
            });
            return new RegistrationResult(project(runtime, entry), existing.isEmpty());
        }
    }

    public Object project(String projectIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            return project(runtime, requireProject(runtime, projectId));
        }
    }

    public Object sync(String projectIdValue, Optional<String> revision) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        Optional<String> normalizedRevision = Objects.requireNonNull(revision, "revision")
                .map(String::trim).filter(value -> !value.isEmpty());
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            Path workspace = projectWorkspace(runtime, projectId);
            Instant attemptedAt = Instant.now();
            var scan = new LocalSourceInventoryScanner().scan(
                    workspace,
                    projectId,
                    normalizedRevision,
                    attemptedAt,
                    List.of(Path.of("openspec")));
            IncrementalSyncService syncService = new IncrementalSyncService(runtime.syncState);
            SyncPlan plan = syncService.prepare(scan, SyncPlan.Trigger.manual().forced(), attemptedAt);
            if (!scan.complete()) {
                syncService.fail(plan, Instant.now());
                throw ApiFailure.conflict("source scan is incomplete: " + scan.failures());
            }

            try {
                var normalized = new OpenSpecProjectContentReader().read(
                        workspace,
                        projectId,
                        new PersistentEntityIdentityResolver(runtime.identities));
                ProjectSnapshotImportResult imported = new ProjectSnapshotImportService(
                        runtime.snapshots,
                        runtime.requirements,
                        runtime.content,
                        runtime.traceability)
                        .publishFull(normalized, normalizedRevision, Instant.now());
                syncService.complete(plan, Instant.now());
                return map(
                        "projectId", projectId.toString(),
                        "snapshotId", imported.snapshot().id().toString(),
                        "mode", plan.mode().name(),
                        "fullRebuildReason", plan.fullRebuildReason().map(Enum::name).orElse("none"),
                        "sourceCount", scan.inventory().orElseThrow().entries().size(),
                        "requirementCount", imported.requirementCount(),
                        "traceabilityLinkCount", imported.traceabilityLinkCount(),
                        "diagnosticCount", imported.diagnostics().size(),
                        "published", true);
            } catch (RuntimeException failure) {
                syncService.fail(plan, Instant.now());
                throw failure;
            }
        }
    }

    public Object syncStatus(String projectIdValue, long maxAgeMinutes) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        requireRange("maxAgeMinutes", maxAgeMinutes, 1L, MAX_MAX_AGE_MINUTES);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireProject(runtime, projectId);
            SyncFreshness freshness = new SyncFreshnessService(runtime.syncState)
                    .assess(projectId, Instant.now(), Duration.ofMinutes(maxAgeMinutes));
            return map(
                    "projectId", projectId.toString(),
                    "state", freshness.state().name(),
                    "lastSuccessfulSyncAt", freshness.lastSuccessfulSyncAt().map(Instant::toString).orElse("unknown"),
                    "ageSeconds", freshness.ageSinceSuccessfulSync().map(Duration::toSeconds).map(Object::toString).orElse("unknown"),
                    "lastObservedChangeAt", freshness.lastObservedChangeAt().map(Instant::toString).orElse("unknown"),
                    "sourceRevision", freshness.sourceRevision().orElse("unknown"),
                    "lastSuccessfulMode", freshness.lastSuccessfulMode().map(Enum::name).orElse("unknown"),
                    "pendingFullRebuildReason", freshness.pendingFullRebuildReason().map(Enum::name).orElse("none"),
                    "currentSourceCount", freshness.currentSourceCount());
        }
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
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireChange(runtime, projectId, changeId);
            SnapshotPage<AcceptanceCriterion> result = business(runtime)
                    .activeAcceptanceCriteriaForChange(projectId, changeId, PageRequest.first(MAX_LIMIT))
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
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            ChangeCompletenessAssessment assessment = completeness(runtime, projectId, changeId);
            return map(
                    "snapshotId", activeSnapshot(runtime, projectId).id().toString(),
                    "changeId", changeId.toString(),
                    "status", "UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT",
                    "lifecycleState", "UNAVAILABLE",
                    "observableFacts", lifecycleFacts(assessment),
                    "reason", "Published snapshot content does not persist an explicit ChangeLifecycle state; MORPHEUS does not infer it.");
        }
    }

    public Object blockingConditions(String projectIdValue, String changeIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            ChangeCompletenessAssessment assessment = completeness(runtime, projectId, changeId);
            return map(
                    "snapshotId", activeSnapshot(runtime, projectId).id().toString(),
                    "changeId", changeId.toString(),
                    "currentRequirementCount", assessment.currentRequirementCount(),
                    "constraintCount", assessment.constraintCount(),
                    "designDecisionCount", assessment.designDecisionCount(),
                    "implementationTaskCount", assessment.implementationTaskCount(),
                    "observableFacts", lifecycleFacts(assessment),
                    "unavailableFacts", assessment.lifecycleFacts().unavailableFacts(),
                    "findings", assessment.findings().stream().map(this::finding).toList());
        }
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
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            activeSnapshot(runtime, projectId);
            QualityReportService service = new QualityReportService(
                    runtime.snapshots,
                    new RequirementQualityService(runtime.snapshots, runtime.requirements, runtime.traceability),
                    new TaskQualityService(runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability),
                    new AcceptanceQualityService(runtime.snapshots, runtime.content),
                    new ChangeCompletenessService(runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability),
                    new DecisionReferenceQualityService(
                            runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability, runtime.externalReferences));
            QualityReport report = service.assessActive(projectId)
                    .orElseThrow(() -> conflict("project has no ACTIVE snapshot: " + projectId));
            return new CompactQualityReportService().view(report);
        }
    }

    private Object project(ApiRuntime runtime, ProjectStoreEntry entry) {
        return map(
                "projectId", entry.id().toString(),
                "workspace", entry.rootLocator().value(),
                "activeSnapshotId", runtime.snapshots.activeSnapshot(entry.id()).map(snapshot -> snapshot.id().toString()).orElse("none"));
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

    private Path projectWorkspace(ApiRuntime runtime, ProjectSpecificationId projectId) {
        ProjectStoreEntry project = requireProject(runtime, projectId);
        if (!project.rootLocator().scheme().equals("file")) {
            throw conflict("local headless sync requires a file: project root");
        }
        Path workspace = Path.of(project.rootLocator().value()).toAbsolutePath().normalize();
        if (!Files.isDirectory(workspace)) {
            throw conflict("workspace is not a directory: " + workspace);
        }
        return workspace;
    }

    private Path existingDirectory(String workspace) {
        if (workspace == null || workspace.isBlank()) {
            throw ApiFailure.badRequest("workspace is required");
        }
        Path path;
        try {
            path = Path.of(workspace).toAbsolutePath().normalize();
        } catch (RuntimeException failure) {
            throw ApiFailure.badRequest("workspace is not a valid path: " + workspace);
        }
        if (!Files.isDirectory(path)) {
            throw ApiFailure.badRequest("workspace is not a directory: " + path);
        }
        return path;
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

    private ChangeCompletenessAssessment completeness(ApiRuntime runtime, ProjectSpecificationId projectId, ChangeId changeId) {
        activeSnapshot(runtime, projectId);
        var report = new ChangeCompletenessService(
                runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability)
                .assessActive(projectId)
                .orElseThrow(() -> conflict("project has no ACTIVE snapshot: " + projectId));
        return report.changes().stream()
                .filter(item -> item.change().id().equals(changeId))
                .findFirst()
                .orElseThrow(() -> notFound("change not found: " + changeId));
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

    private Object lifecycleFacts(ChangeCompletenessAssessment assessment) {
        var facts = assessment.lifecycleFacts();
        return map(
                "requirementsIdentified", facts.requirementsIdentified().name(),
                "criticalConstraintsKnown", facts.criticalConstraintsKnown().name(),
                "acceptanceCriteriaDefined", facts.acceptanceCriteriaDefined().name(),
                "designRequired", facts.designRequired().name(),
                "designDecisionsAvailable", facts.designDecisionsAvailable().name(),
                "planPresent", facts.planPresent().name(),
                "knownBlocker", facts.knownBlocker().name(),
                "blockingAcceptanceCriterionFailed", facts.blockingAcceptanceCriterionFailed().name(),
                "blockingAcceptanceCriterionUnverified", facts.blockingAcceptanceCriterionUnverified().name());
    }

    private Object finding(QualityFinding finding) {
        return map(
                "code", finding.code().name(),
                "severity", finding.severity().name(),
                "evidenceKind", finding.evidenceKind().name(),
                "subjectKind", finding.subject().kind().name(),
                "subjectId", finding.subject().identity().toString(),
                "message", finding.message(),
                "details", finding.details(),
                "confidence", finding.confidence().map(Object::toString).orElse(""),
                "evidenceIds", finding.evidenceIds().stream().map(Object::toString).toList());
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
