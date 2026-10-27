package com.morpheus.mcp;

import com.morpheus.application.foundation.PayloadMap;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.RequirementQueryService;
import com.morpheus.application.query.RequirementSearchQuery;
import com.morpheus.application.query.TraceRequirementQueryService;
import com.morpheus.application.query.compact.CompactQueryViewService;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;
import io.modelcontextprotocol.server.McpServerFeatures;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The requirement family of the MCP server: {@code find_requirements} and {@code trace_requirement}. */
public final class MorpheusRequirementMcpTools {
    static final String FIND_TOOL = "find_requirements";
    static final String TRACE_TOOL = "trace_requirement";

    private final Path databasePath;
    private final OperationalRecorder recorder;

    public MorpheusRequirementMcpTools(Path databasePath, OperationalRecorder recorder) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool(FIND_TOOL, MorpheusRequirementMcpTools::findRequirements),
                tool(TRACE_TOOL, MorpheusRequirementMcpTools::traceRequirement));
    }

    /** The handler is declared here, so the tool's owner is this class (ADR-0149). */
    private McpServerFeatures.SyncToolSpecification tool(String name, McpCatalogTool.Answer answer) {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpCatalogTool.definition(name))
                .callHandler((exchange, request) -> McpCatalogTool.answer(databasePath, recorder, request.arguments(), answer))
                .build();
    }

    private static Object findRequirements(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        String queryText = McpArguments.optionalText(arguments, "query").orElse("");
        PageRequest pageRequest = McpCatalogTool.page(arguments);
        var result = new RequirementQueryService(runtime.snapshots(), runtime.requirements())
                .findActive(projectId, new RequirementSearchQuery(queryText), pageRequest)
                .orElseThrow(() -> McpCatalogTool.notFound("project has no ACTIVE snapshot: " + projectId));
        return PagedEnvelope.following(
                PayloadMap.of("snapshotId", result.snapshot().id().toString(), "query", queryText),
                PagedEnvelope.page(result.pageRequest(), result.totalMatches(), result.hasMore(),
                        result.items().stream()
                                .map(item -> McpContentPayloads.requirement(item.entityVersion().content()))
                                .toList()));
    }

    private static Object traceRequirement(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        RequirementId requirementId = RequirementId.parse(McpArguments.requiredString(arguments, "requirementId"));
        int depth = McpArguments.optionalInt(
                arguments, "depth", MorpheusMcpToolCatalog.DEFAULT_DEPTH, 1, MorpheusMcpToolCatalog.MAX_DEPTH);
        var result = new TraceRequirementQueryService(
                runtime.snapshots(), runtime.requirements(), runtime.traceability(), runtime.externalReferences())
                .active(projectId, requirementId, depth, Set.of())
                .orElseThrow(() -> McpCatalogTool.notFound("requirement or ACTIVE snapshot not found: " + requirementId));
        return McpCatalogTool.json(new CompactQueryViewService(runtime.content()).traceRequirement(result));
    }
}
