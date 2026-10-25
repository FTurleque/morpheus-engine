package com.morpheus.mcp;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.SpecificationContextQueryService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import io.modelcontextprotocol.server.McpServerFeatures;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The specification family of the MCP server: the current specification of a project and one specification's context. */
public final class MorpheusSpecificationMcpTools {
    private final Path databasePath;
    private final OperationalRecorder recorder;

    public MorpheusSpecificationMcpTools(Path databasePath, OperationalRecorder recorder) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool("get_current_specification", MorpheusSpecificationMcpTools::currentSpecification),
                tool("get_specification_context", MorpheusSpecificationMcpTools::specificationContext));
    }

    private McpServerFeatures.SyncToolSpecification tool(String name, McpCatalogTool.Answer answer) {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpCatalogTool.definition(name))
                .callHandler((exchange, request) -> McpCatalogTool.answer(databasePath, recorder, request.arguments(), answer))
                .build();
    }

    private static Object currentSpecification(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
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

    private static Object specificationContext(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
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
}
