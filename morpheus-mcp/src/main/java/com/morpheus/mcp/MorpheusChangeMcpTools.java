package com.morpheus.mcp;

import com.morpheus.application.foundation.PayloadMap;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.quality.ChangeCompletenessAssessment;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.query.ChangeContextQueryService;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.query.compact.CompactQueryViewService;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import io.modelcontextprotocol.server.McpServerFeatures;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The change family of the MCP server: one change, the list of changes, what is attached to a change, its context,
 * and the lifecycle facts observable on it.
 */
public final class MorpheusChangeMcpTools {
    private static final String NO_ACTIVE_SNAPSHOT = "project has no ACTIVE snapshot: ";

    private final Path databasePath;
    private final OperationalRecorder recorder;

    public MorpheusChangeMcpTools(Path databasePath, OperationalRecorder recorder) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool("get_change", MorpheusChangeMcpTools::getChange),
                tool("list_changes", MorpheusChangeMcpTools::listChanges),
                tool("get_constraints", MorpheusChangeMcpTools::constraints),
                tool("get_design_decisions", MorpheusChangeMcpTools::decisions),
                tool("get_implementation_tasks", MorpheusChangeMcpTools::tasks),
                tool("get_change_context", MorpheusChangeMcpTools::changeContext),
                tool("get_change_status", MorpheusChangeMcpTools::changeStatus),
                tool("get_blocking_conditions", MorpheusChangeMcpTools::blockingConditions));
    }

    private McpServerFeatures.SyncToolSpecification tool(String name, McpCatalogTool.Answer answer) {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpCatalogTool.definition(name))
                .callHandler((exchange, request) -> McpCatalogTool.answer(databasePath, recorder, request.arguments(), answer))
                .build();
    }

    private static Object getChange(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        ChangeId changeId = changeId(arguments);
        var result = McpCatalogTool.business(runtime).activeChange(projectId, changeId)
                .orElseThrow(() -> McpCatalogTool.notFound(NO_ACTIVE_SNAPSHOT + projectId));
        ChangeProposal change = result.item().orElseThrow(() -> McpCatalogTool.notFound("change not found: " + changeId));
        return PayloadMap.of("snapshotId", result.snapshot().id().toString(), "change", McpContentPayloads.change(change));
    }

    private static Object listChanges(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        SnapshotPage<ChangeProposal> result = McpCatalogTool.business(runtime)
                .listActiveChanges(projectId, McpCatalogTool.page(arguments))
                .orElseThrow(() -> McpCatalogTool.notFound(NO_ACTIVE_SNAPSHOT + projectId));
        return McpContentPayloads.page(result, result.items().stream().map(McpContentPayloads::change).toList());
    }

    private static Object constraints(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        ChangeId changeId = changeId(arguments);
        McpCatalogTool.requireChange(runtime, projectId, changeId);
        SnapshotPage<Constraint> result = McpCatalogTool.business(runtime)
                .activeConstraints(projectId, changeId, McpCatalogTool.page(arguments))
                .orElseThrow(() -> McpCatalogTool.notFound(NO_ACTIVE_SNAPSHOT + projectId));
        return McpContentPayloads.page(result, result.items().stream().map(McpContentPayloads::constraint).toList());
    }

    private static Object decisions(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        ChangeId changeId = changeId(arguments);
        McpCatalogTool.requireChange(runtime, projectId, changeId);
        SnapshotPage<DesignDecision> result = McpCatalogTool.business(runtime)
                .activeDesignDecisions(projectId, changeId, McpCatalogTool.page(arguments))
                .orElseThrow(() -> McpCatalogTool.notFound(NO_ACTIVE_SNAPSHOT + projectId));
        return McpContentPayloads.page(result, result.items().stream().map(McpContentPayloads::decision).toList());
    }

    private static Object tasks(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        ChangeId changeId = changeId(arguments);
        McpCatalogTool.requireChange(runtime, projectId, changeId);
        SnapshotPage<ImplementationTask> result = McpCatalogTool.business(runtime)
                .activeImplementationTasks(projectId, changeId, McpCatalogTool.page(arguments))
                .orElseThrow(() -> McpCatalogTool.notFound(NO_ACTIVE_SNAPSHOT + projectId));
        return McpContentPayloads.page(result, result.items().stream().map(McpContentPayloads::task).toList());
    }

    private static Object changeContext(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        ChangeId changeId = changeId(arguments);
        int depth = McpArguments.optionalInt(
                arguments, "depth", MorpheusMcpToolCatalog.DEFAULT_DEPTH, 1, MorpheusMcpToolCatalog.MAX_DEPTH);
        var result = new ChangeContextQueryService(
                runtime.snapshots(), runtime.content(), runtime.requirements(), runtime.traceability(), runtime.externalReferences())
                .active(projectId, changeId, depth, Set.of())
                .orElseThrow(() -> McpCatalogTool.notFound(NO_ACTIVE_SNAPSHOT + projectId));
        if (result.change().isEmpty()) {
            throw McpCatalogTool.notFound("change not found: " + changeId);
        }
        return McpCatalogTool.json(new CompactQueryViewService(runtime.content()).changeContext(result));
    }

    private static Object changeStatus(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        ChangeId changeId = changeId(arguments);
        ChangeCompletenessAssessment assessment = completeness(runtime, projectId, changeId);
        return PayloadMap.of(
                "snapshotId", activeSnapshotId(runtime, projectId),
                "changeId", changeId.toString(),
                "status", "UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT",
                "lifecycleState", "UNAVAILABLE",
                "observableFacts", McpContentPayloads.lifecycleFacts(assessment),
                "reason", "Published snapshot content does not persist an explicit ChangeLifecycle state; MORPHEUS does not infer it.");
    }

    private static Object blockingConditions(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        ChangeId changeId = changeId(arguments);
        ChangeCompletenessAssessment assessment = completeness(runtime, projectId, changeId);
        return PayloadMap.of(
                "snapshotId", activeSnapshotId(runtime, projectId),
                "changeId", changeId.toString(),
                "currentRequirementCount", assessment.currentRequirementCount(),
                "constraintCount", assessment.constraintCount(),
                "designDecisionCount", assessment.designDecisionCount(),
                "implementationTaskCount", assessment.implementationTaskCount(),
                "observableFacts", McpContentPayloads.lifecycleFacts(assessment),
                "unavailableFacts", assessment.lifecycleFacts().unavailableFacts(),
                "findings", assessment.findings().stream().map(McpContentPayloads::finding).toList());
    }

    private static ChangeId changeId(Map<String, Object> arguments) {
        return ChangeId.parse(McpArguments.requiredString(arguments, "changeId"));
    }

    private static ChangeCompletenessAssessment completeness(
            MorpheusMcpRuntime runtime, ProjectSpecificationId projectId, ChangeId changeId) {
        var report = new ChangeCompletenessService(
                runtime.snapshots(), runtime.content(), runtime.requirements(), runtime.traceability())
                .assessActive(projectId)
                .orElseThrow(() -> McpCatalogTool.notFound(NO_ACTIVE_SNAPSHOT + projectId));
        return report.changes().stream()
                .filter(item -> item.change().id().equals(changeId))
                .findFirst()
                .orElseThrow(() -> McpCatalogTool.notFound("change not found: " + changeId));
    }

    private static String activeSnapshotId(MorpheusMcpRuntime runtime, ProjectSpecificationId projectId) {
        return runtime.snapshots().activeSnapshot(projectId)
                .orElseThrow(() -> McpCatalogTool.notFound(NO_ACTIVE_SNAPSHOT + projectId))
                .id().toString();
    }
}
