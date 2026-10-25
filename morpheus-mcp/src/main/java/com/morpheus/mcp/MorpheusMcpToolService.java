package com.morpheus.mcp;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.query.SpecificationContextQueryService;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.sync.SyncFreshness;
import com.morpheus.application.sync.SyncFreshnessService;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Executes the M10 read-only tool contract over the same persisted SQLite state used by the CLI. */
public final class MorpheusMcpToolService {
    private final Path databasePath;
    private final OperationalRecorder recorder;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    public MorpheusMcpToolService(Path databasePath, OperationalRecorder recorder) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public String execute(String toolName, Map<String, Object> arguments) {
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(arguments, "arguments");
        try (MorpheusMcpRuntime runtime = new MorpheusMcpRuntime(databasePath, recorder)) {
            Object result = switch (toolName) {
                case "get_current_specification" -> currentSpecification(runtime, arguments);
                case "get_acceptance_criteria" -> acceptanceCriteria(runtime, arguments);
                case "get_specification_context" -> specificationContext(runtime, arguments);
                case "get_sync_status" -> syncStatus(runtime, arguments);
                default -> throw new IllegalArgumentException("unknown MCP tool: " + toolName);
            };
            return result instanceof String serialized ? serialized : json.toJson(result);
        }
    }

    private Object currentSpecification(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        var snapshot = runtime.snapshots().activeSnapshot(projectId)
                .orElseThrow(() -> McpCatalogTool.notFound("project has no ACTIVE snapshot: " + projectId));
        var content = runtime.content().findSnapshotContent(snapshot.id())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "published snapshot has no business-content projection: " + snapshot.id()));
        String specificationVersionId = runtime.requirements().findSnapshotVersion(snapshot.id())
                .map(binding -> binding.specificationVersionId().toString())
                .orElse("unknown");
        long currentRequirementCount = runtime.requirements().listCurrentRequirementVersions(snapshot.id()).size();
        PageRequest pageRequest = McpCatalogTool.page(arguments);
        List<Specification> specifications = content.specifications().stream()
                .sorted(Comparator.comparing(Specification::id))
                .toList();
        return McpContentPayloads.map(
                "projectId", projectId.toString(),
                "snapshotId", snapshot.id().toString(),
                "snapshotState", snapshot.state().name(),
                "specificationVersionId", specificationVersionId,
                "specifications", PagedEnvelope.slice(
                        pageRequest.offset(), pageRequest.limit(), specifications, McpContentPayloads::specification),
                "requirementCount", currentRequirementCount,
                "scenarioCount", content.scenarios().size(),
                "changeCount", content.changes().size(),
                "acceptanceCriterionCount", content.acceptanceCriteria().size());
    }

    private Object acceptanceCriteria(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        ChangeId changeId = ChangeId.parse(McpArguments.requiredString(arguments, "changeId"));
        McpCatalogTool.requireChange(runtime, projectId, changeId);
        SnapshotPage<AcceptanceCriterion> result = McpCatalogTool.business(runtime)
                .activeAcceptanceCriteriaForChange(projectId, changeId, McpCatalogTool.page(arguments))
                .orElseThrow(() -> McpCatalogTool.notFound("project has no ACTIVE snapshot: " + projectId));
        return McpContentPayloads.page(result, result.items().stream().map(McpContentPayloads::acceptanceCriterion).toList());
    }

    private Object specificationContext(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        SpecificationId specificationId = SpecificationId.parse(McpArguments.requiredString(arguments, "specificationId"));
        var result = new SpecificationContextQueryService(
                runtime.snapshots(), runtime.content(), runtime.requirements(), runtime.traceability())
                .active(projectId, specificationId, McpCatalogTool.page(arguments))
                .orElseThrow(() -> McpCatalogTool.notFound("project has no ACTIVE snapshot: " + projectId));
        return McpContentPayloads.map(
                "snapshotId", result.snapshot().id().toString(),
                "specification", McpContentPayloads.specification(result.specification()),
                "requirements", McpContentPayloads.page(result.requirements(), result.requirements().items().stream().map(McpContentPayloads::requirement).toList()),
                "scenarios", result.scenarios().stream().map(McpContentPayloads::scenario).toList(),
                "changes", result.changes().stream().map(McpContentPayloads::change).toList());
    }

    private Object syncStatus(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        long maxAgeMinutes = McpArguments.optionalInteger(arguments, "maxAgeMinutes", MorpheusMcpToolCatalog.DEFAULT_MAX_AGE_MINUTES,
                1L, MorpheusMcpToolCatalog.MAX_MAX_AGE_MINUTES);
        SyncFreshness freshness = new SyncFreshnessService(runtime.syncState())
                .assess(projectId, Instant.now(), Duration.ofMinutes(maxAgeMinutes));
        return McpContentPayloads.map(
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
