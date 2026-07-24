package com.morpheus.mcp;

import com.morpheus.application.quality.ChangeCompletenessAssessment;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.QualityFinding;
import com.morpheus.application.query.BusinessContentQueryService;
import com.morpheus.application.query.ChangeContextQueryService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.RequirementQueryService;
import com.morpheus.application.query.RequirementSearchQuery;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.query.SpecificationContextQueryService;
import com.morpheus.application.query.TraceRequirementQueryService;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.query.compact.CompactQueryViewService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.sync.SyncFreshness;
import com.morpheus.application.sync.SyncFreshnessService;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.temporal.TemporalState;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Executes the M10 read-only tool contract over the same persisted SQLite state used by the CLI. */
public final class MorpheusMcpToolService {
    private final Path databasePath;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    public MorpheusMcpToolService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    public String execute(String toolName, Map<String, Object> arguments) {
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(arguments, "arguments");
        try (MorpheusMcpRuntime runtime = new MorpheusMcpRuntime(databasePath)) {
            Object result = switch (toolName) {
                case "get_current_specification" -> currentSpecification(runtime, arguments);
                case "find_requirements" -> findRequirements(runtime, arguments);
                case "get_change" -> getChange(runtime, arguments);
                case "list_changes" -> listChanges(runtime, arguments);
                case "get_constraints" -> constraints(runtime, arguments);
                case "get_acceptance_criteria" -> acceptanceCriteria(runtime, arguments);
                case "get_design_decisions" -> decisions(runtime, arguments);
                case "get_implementation_tasks" -> tasks(runtime, arguments);
                case "trace_requirement" -> traceRequirement(runtime, arguments);
                case "get_change_context" -> changeContext(runtime, arguments);
                case "get_specification_context" -> specificationContext(runtime, arguments);
                case "get_change_status" -> changeStatus(runtime, arguments);
                case "get_blocking_conditions" -> blockingConditions(runtime, arguments);
                case "get_sync_status" -> syncStatus(runtime, arguments);
                default -> throw new IllegalArgumentException("unknown MCP tool: " + toolName);
            };
            return result instanceof String serialized ? serialized : json.toJson(result);
        }
    }

    private Object currentSpecification(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = projectId(arguments);
        var snapshot = runtime.snapshots.activeSnapshot(projectId)
                .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
        var content = runtime.content.findSnapshotContent(snapshot.id())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "published snapshot has no business-content projection: " + snapshot.id()));
        String specificationVersionId = runtime.requirements.findSnapshotVersion(snapshot.id())
                .map(binding -> binding.specificationVersionId().toString())
                .orElse("unknown");
        long currentRequirementCount = runtime.requirements.listRequirementVersions(snapshot.id()).stream()
                .map(RequirementVersionRecord::entityVersion)
                .filter(version -> version.temporalState() == TemporalState.CURRENT)
                .count();
        return map(
                "projectId", projectId.toString(),
                "snapshotId", snapshot.id().toString(),
                "snapshotState", snapshot.state().name(),
                "specificationVersionId", specificationVersionId,
                "specifications", content.specifications().stream().map(this::specification).toList(),
                "requirementCount", currentRequirementCount,
                "scenarioCount", content.scenarios().size(),
                "changeCount", content.changes().size());
    }

    private Object findRequirements(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = projectId(arguments);
        String queryText = optionalString(arguments, "query").orElse("");
        PageRequest pageRequest = page(arguments);
        var result = new RequirementQueryService(runtime.snapshots, runtime.requirements)
                .findActive(projectId, new RequirementSearchQuery(queryText), pageRequest)
                .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
        return map(
                "snapshotId", result.snapshot().id().toString(),
                "query", queryText,
                "totalMatches", result.totalMatches(),
                "hasMore", result.hasMore(),
                "items", result.items().stream().map(item -> requirement(item.entityVersion().content())).toList());
    }

    private Object getChange(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = projectId(arguments);
        ChangeId changeId = ChangeId.parse(requiredString(arguments, "changeId"));
        var result = business(runtime).activeChange(projectId, changeId)
                .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
        ChangeProposal change = result.item().orElseThrow(() -> notFound("change not found: " + changeId));
        return map("snapshotId", result.snapshot().id().toString(), "change", change(change));
    }

    private Object listChanges(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = projectId(arguments);
        SnapshotPage<ChangeProposal> result = business(runtime).listActiveChanges(projectId, page(arguments))
                .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
        return page(result, result.items().stream().map(this::change).toList());
    }

    private Object constraints(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = projectId(arguments);
        ChangeId changeId = ChangeId.parse(requiredString(arguments, "changeId"));
        requireChange(runtime, projectId, changeId);
        SnapshotPage<Constraint> result = business(runtime).activeConstraints(projectId, changeId, page(arguments))
                .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
        return page(result, result.items().stream().map(this::constraint).toList());
    }

    private Object acceptanceCriteria(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = projectId(arguments);
        ChangeId changeId = ChangeId.parse(requiredString(arguments, "changeId"));
        var resolved = requireChange(runtime, projectId, changeId);
        return map(
                "snapshotId", resolved.snapshot().id().toString(),
                "changeId", changeId.toString(),
                "status", "UNAVAILABLE_IN_NORMALIZED_MODEL",
                "criteria", List.of(),
                "reason", "No explicit AcceptanceCriterion projection is persisted; MORPHEUS never converts Scenario into AcceptanceCriterion.");
    }

    private Object decisions(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = projectId(arguments);
        ChangeId changeId = ChangeId.parse(requiredString(arguments, "changeId"));
        requireChange(runtime, projectId, changeId);
        SnapshotPage<DesignDecision> result = business(runtime).activeDesignDecisions(projectId, changeId, page(arguments))
                .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
        return page(result, result.items().stream().map(this::decision).toList());
    }

    private Object tasks(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = projectId(arguments);
        ChangeId changeId = ChangeId.parse(requiredString(arguments, "changeId"));
        requireChange(runtime, projectId, changeId);
        SnapshotPage<ImplementationTask> result = business(runtime).activeImplementationTasks(projectId, changeId, page(arguments))
                .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
        return page(result, result.items().stream().map(this::task).toList());
    }

    private Object traceRequirement(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = projectId(arguments);
        RequirementId requirementId = RequirementId.parse(requiredString(arguments, "requirementId"));
        int depth = intValue(arguments, "depth", MorpheusMcpToolCatalog.DEFAULT_DEPTH, 1, MorpheusMcpToolCatalog.MAX_DEPTH);
        var result = new TraceRequirementQueryService(
                runtime.snapshots, runtime.requirements, runtime.traceability, runtime.externalReferences)
                .active(projectId, requirementId, depth, java.util.Set.of())
                .orElseThrow(() -> notFound("requirement or ACTIVE snapshot not found: " + requirementId));
        return json.toJson(new CompactQueryViewService(runtime.content).traceRequirement(result));
    }

    private Object changeContext(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = projectId(arguments);
        ChangeId changeId = ChangeId.parse(requiredString(arguments, "changeId"));
        int depth = intValue(arguments, "depth", MorpheusMcpToolCatalog.DEFAULT_DEPTH, 1, MorpheusMcpToolCatalog.MAX_DEPTH);
        var result = new ChangeContextQueryService(
                runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability, runtime.externalReferences)
                .active(projectId, changeId, depth, java.util.Set.of())
                .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
        if (result.change().isEmpty()) {
            throw notFound("change not found: " + changeId);
        }
        return json.toJson(new CompactQueryViewService(runtime.content).changeContext(result));
    }

    private Object specificationContext(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = projectId(arguments);
        SpecificationId specificationId = SpecificationId.parse(requiredString(arguments, "specificationId"));
        var result = new SpecificationContextQueryService(
                runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability)
                .active(projectId, specificationId, page(arguments))
                .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
        return map(
                "snapshotId", result.snapshot().id().toString(),
                "specification", specification(result.specification()),
                "requirements", page(result.requirements(), result.requirements().items().stream().map(this::requirement).toList()),
                "scenarios", result.scenarios().stream().map(this::scenario).toList(),
                "changes", result.changes().stream().map(this::change).toList());
    }

    private Object changeStatus(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = projectId(arguments);
        ChangeId changeId = ChangeId.parse(requiredString(arguments, "changeId"));
        ChangeCompletenessAssessment assessment = completeness(runtime, projectId, changeId);
        return map(
                "snapshotId", activeSnapshotId(runtime, projectId),
                "changeId", changeId.toString(),
                "status", "UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT",
                "lifecycleState", "UNAVAILABLE",
                "observableFacts", lifecycleFacts(assessment),
                "reason", "Published snapshot content does not persist an explicit ChangeLifecycle state; MORPHEUS does not infer it.");
    }

    private Object blockingConditions(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = projectId(arguments);
        ChangeId changeId = ChangeId.parse(requiredString(arguments, "changeId"));
        ChangeCompletenessAssessment assessment = completeness(runtime, projectId, changeId);
        return map(
                "snapshotId", activeSnapshotId(runtime, projectId),
                "changeId", changeId.toString(),
                "currentRequirementCount", assessment.currentRequirementCount(),
                "constraintCount", assessment.constraintCount(),
                "designDecisionCount", assessment.designDecisionCount(),
                "implementationTaskCount", assessment.implementationTaskCount(),
                "observableFacts", lifecycleFacts(assessment),
                "unavailableFacts", assessment.lifecycleFacts().unavailableFacts(),
                "findings", assessment.findings().stream().map(this::finding).toList());
    }

    private Object syncStatus(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = projectId(arguments);
        long maxAgeMinutes = longValue(arguments, "maxAgeMinutes", MorpheusMcpToolCatalog.DEFAULT_MAX_AGE_MINUTES,
                1L, MorpheusMcpToolCatalog.MAX_MAX_AGE_MINUTES);
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

    private BusinessContentQueryService business(MorpheusMcpRuntime runtime) {
        return new BusinessContentQueryService(runtime.snapshots, runtime.content);
    }

    private com.morpheus.application.query.SnapshotItemResult<ChangeProposal> requireChange(
            MorpheusMcpRuntime runtime, ProjectSpecificationId projectId, ChangeId changeId) {
        var result = business(runtime).activeChange(projectId, changeId)
                .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
        if (result.item().isEmpty()) {
            throw notFound("change not found: " + changeId);
        }
        return result;
    }

    private ChangeCompletenessAssessment completeness(
            MorpheusMcpRuntime runtime, ProjectSpecificationId projectId, ChangeId changeId) {
        var report = new ChangeCompletenessService(
                runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability)
                .assessActive(projectId)
                .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
        return report.changes().stream()
                .filter(item -> item.change().id().equals(changeId))
                .findFirst()
                .orElseThrow(() -> notFound("change not found: " + changeId));
    }

    private String activeSnapshotId(MorpheusMcpRuntime runtime, ProjectSpecificationId projectId) {
        return runtime.snapshots.activeSnapshot(projectId)
                .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId))
                .id().toString();
    }

    private PageRequest page(Map<String, Object> arguments) {
        return new PageRequest(
                intValue(arguments, "offset", 0, 0, 1_000_000),
                intValue(arguments, "limit", MorpheusMcpToolCatalog.DEFAULT_LIMIT, 1, MorpheusMcpToolCatalog.MAX_LIMIT));
    }

    private ProjectSpecificationId projectId(Map<String, Object> arguments) {
        return ProjectSpecificationId.parse(requiredString(arguments, "projectId"));
    }

    private String requiredString(Map<String, Object> arguments, String name) {
        Object raw = arguments.get(name);
        if (!(raw instanceof String value) || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required and must be a non-blank string");
        }
        return value.trim();
    }

    private Optional<String> optionalString(Map<String, Object> arguments, String name) {
        Object raw = arguments.get(name);
        if (raw == null) {
            return Optional.empty();
        }
        if (!(raw instanceof String value)) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return Optional.of(value.trim());
    }

    private int intValue(Map<String, Object> arguments, String name, int defaultValue, int minimum, int maximum) {
        Object raw = arguments.get(name);
        if (raw == null) {
            return defaultValue;
        }
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        long value = number.longValue();
        if (Double.compare(number.doubleValue(), (double) value) != 0 || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be an integer between " + minimum + " and " + maximum);
        }
        return Math.toIntExact(value);
    }

    private long longValue(Map<String, Object> arguments, String name, long defaultValue, long minimum, long maximum) {
        Object raw = arguments.get(name);
        if (raw == null) {
            return defaultValue;
        }
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        long value = number.longValue();
        if (Double.compare(number.doubleValue(), (double) value) != 0 || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be an integer between " + minimum + " and " + maximum);
        }
        return value;
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

    private Object specification(Specification item) {
        return map(
                "id", item.id().toString(),
                "projectId", item.projectId().toString(),
                "key", item.key(),
                "title", item.title(),
                "description", item.description().orElse(""));
    }

    private Object requirement(Requirement item) {
        return map(
                "id", item.id().toString(),
                "specificationId", item.specificationId().toString(),
                "key", item.key().orElse(""),
                "title", item.title(),
                "statement", item.statement());
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

    private Object decision(DesignDecision item) {
        return map(
                "id", item.id().toString(), "changeId", item.changeId().toString(),
                "title", item.title(), "decision", item.decision());
    }

    private Object task(ImplementationTask item) {
        return map(
                "id", item.id().toString(), "changeId", item.changeId().toString(),
                "key", item.key().orElse(""), "title", item.title(), "completed", item.completed());
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

    private Map<String, Object> map(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("map entries must be key/value pairs");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], Objects.requireNonNull(entries[index + 1], "map value"));
        }
        return Map.copyOf(result);
    }

    private KnowledgeStoreException notFound(String message) {
        return new KnowledgeStoreException(message);
    }
}
