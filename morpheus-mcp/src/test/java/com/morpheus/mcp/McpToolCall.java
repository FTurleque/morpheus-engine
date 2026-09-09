package com.morpheus.mcp;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Calls an MCP tool the way the SDK does, and publishes the snapshot a handler needs to get past its own
 * "project not found" guard.
 *
 * <p>The MCP test suite was, until ADR-0102, a suite of schema tests: it asserted what the tools declared and
 * never what they did with an argument. Reaching a handler needs two things -- the invocation shape below,
 * which already existed in two tests, and a published project, without which every read tool refuses before it
 * has read anything.</p>
 */
final class McpToolCall {
    /** An identifier that parses but names nothing, for the "resource absent" case. */
    static final String ABSENT_PROJECT_ID = "01920000-0000-7000-8000-0000000000ff";
    static final String ABSENT_CHANGE_ID = "01920000-0000-7000-8000-0000000000fe";
    static final String ABSENT_REFERENCE_ID = "01920000-0000-7000-8000-0000000000fd";

    private McpToolCall() {
    }

    static McpSchema.CallToolResult call(
            List<McpServerFeatures.SyncToolSpecification> specifications,
            String toolName,
            Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification specification = specifications.stream()
                .filter(item -> item.tool().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tool not published: " + toolName));
        return specification.callHandler().apply(
                null, new McpSchema.CallToolRequest(toolName, arguments));
    }

    static String text(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(item -> item instanceof McpSchema.TextContent)
                .map(item -> ((McpSchema.TextContent) item).text())
                .reduce("", (left, right) -> left + right);
    }

    /** The arguments a tool declares required, each carrying a value that is valid in isolation. */
    static Map<String, Object> validRequiredArguments(McpSchema.Tool tool, PublishedProject project) {
        Map<String, Object> arguments = new java.util.LinkedHashMap<>();
        for (String name : requiredNames(tool)) {
            arguments.put(name, sampleValue(tool, name, project));
        }
        return arguments;
    }

    @SuppressWarnings("unchecked")
    static List<String> requiredNames(McpSchema.Tool tool) {
        Object required = tool.inputSchema() == null ? null : tool.inputSchema().get("required");
        return required instanceof List<?> names ? List.copyOf((List<String>) names) : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Object sampleValue(McpSchema.Tool tool, String name, PublishedProject project) {
        Map<String, Object> property = property(tool, name);
        Object type = property.get("type");
        if ("integer".equals(type) || "number".equals(type)) {
            return property.get("minimum") instanceof Number minimum ? minimum : 0;
        }
        if ("boolean".equals(type)) {
            return true;
        }
        if ("array".equals(type)) {
            return List.of();
        }
        if (property.get("enum") instanceof List<?> values && !values.isEmpty()) {
            return values.get(0);
        }
        return switch (name) {
            case "projectId", "startProjectId", "sourceProjectId", "targetProjectId" -> project.projectId();
            case "changeId" -> project.changeId();
            case "requirementId" -> project.requirementId();
            case "specificationId" -> project.specificationId();
            case "ownerId", "startId", "sourceId", "targetId" -> project.requirementId();
            case "referenceId" -> ABSENT_REFERENCE_ID;
            case "scopeId" -> project.projectId();
            default -> "sample";
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> property(McpSchema.Tool tool, String name) {
        Object properties = tool.inputSchema() == null ? null : tool.inputSchema().get("properties");
        if (!(properties instanceof Map<?, ?> map) || !(map.get(name) instanceof Map<?, ?> property)) {
            return Map.of();
        }
        return (Map<String, Object>) property;
    }

    /** Publishes one ACTIVE snapshot holding a specification, a requirement and a change. */
    static PublishedProject publish(Path database) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SpecificationId specificationId = SpecificationId.generate();
        RequirementId requirementId = RequirementId.generate();
        ChangeId changeId = ChangeId.generate();
        SourceLocator source = SourceLocator.file("openspec/specs/auth/spec.md");
        Evidence evidence = new Evidence(EvidenceId.generate(), source, Optional.empty(), Optional.empty());
        Provenance provenance = new Provenance(
                new ProviderId("mcp-failure-contract"), Optional.of("1.0"), source,
                Optional.of("fixture"), Optional.empty(), evidence.id());
        ProjectSpecification project =
                new ProjectSpecification(projectId, "MCP failure contract fixture", SourceLocator.file("workspace"));
        Specification specification = new Specification(
                specificationId, projectId, "auth", "Authentication",
                Optional.of("Authentication behavior"), provenance);
        Requirement requirement = new Requirement(
                requirementId, specificationId, Optional.of("auth/session"),
                "Session expiration", "The system SHALL expire inactive sessions.", provenance);
        ChangeProposal change = new ChangeProposal(
                changeId, projectId, Optional.of("tighten-session"), "Tighten session expiration",
                "Reduce exposure from idle sessions.", List.of("Session timeout"), List.of(), List.of(), provenance);
        NormalizedProjectContent content = new NormalizedProjectContent(
                project,
                List.of(specification),
                List.of(requirement),
                List.of(),
                List.of(change),
                List.of(),
                List.of(),
                List.of(),
                List.of(evidence),
                List.of());

        try (SqliteSpecificationKnowledgeStore snapshots = new SqliteSpecificationKnowledgeStore(database);
             SqliteVersionedRequirementStore requirements = new SqliteVersionedRequirementStore(database);
             SqliteSnapshotBusinessContentStore businessContent = new SqliteSnapshotBusinessContentStore(database);
             SqliteTraceabilityStore traceability = new SqliteTraceabilityStore(database)) {
            new ProjectSnapshotImportService(snapshots, requirements, businessContent, traceability)
                    .publishFull(content, Optional.of("mcp-failure-contract"),
                            Instant.parse("2026-09-09T10:00:00Z"));
        }
        return new PublishedProject(
                projectId.toString(),
                specificationId.toString(),
                requirementId.toString(),
                changeId.toString(),
                ExternalReferenceId.generate().toString());
    }

    record PublishedProject(
            String projectId,
            String specificationId,
            String requirementId,
            String changeId,
            String unusedReferenceId) {
    }
}
