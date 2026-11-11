package com.morpheus.mcp;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.payload.PublicPayloads;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import io.modelcontextprotocol.server.McpServerFeatures;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The acceptance-criteria family of the MCP server: the explicit criteria of one change. */
public final class MorpheusAcceptanceCriteriaMcpTools {
    private final Path databasePath;
    private final OperationalRecorder recorder;

    public MorpheusAcceptanceCriteriaMcpTools(Path databasePath, OperationalRecorder recorder) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool("get_acceptance_criteria", MorpheusAcceptanceCriteriaMcpTools::acceptanceCriteria));
    }

    private McpServerFeatures.SyncToolSpecification tool(String name, McpCatalogTool.Answer answer) {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpCatalogTool.definition(name))
                .callHandler((exchange, request) -> McpCatalogTool.answer(databasePath, recorder, request.arguments(), answer))
                .build();
    }

    private static Object acceptanceCriteria(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        ChangeId changeId = ChangeId.parse(McpArguments.requiredString(arguments, "changeId"));
        McpCatalogTool.requireChange(runtime, projectId, changeId);
        SnapshotPage<AcceptanceCriterion> result = McpCatalogTool.business(runtime)
                .activeAcceptanceCriteriaForChange(projectId, changeId, McpCatalogTool.page(arguments))
                .orElseThrow(() -> McpCatalogTool.notFound("project has no ACTIVE snapshot: " + projectId));
        return McpContentPayloads.page(result, result.items().stream().map(PublicPayloads::acceptanceCriterion).toList());
    }
}
