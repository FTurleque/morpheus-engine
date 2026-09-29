package com.morpheus.mcp;

import com.morpheus.application.composition.CompositionCandidate;
import com.morpheus.application.composition.CompositionConflict;
import com.morpheus.application.composition.CompositionEntityType;
import com.morpheus.application.composition.CompositionProviderState;
import com.morpheus.application.composition.CompositionResolution;
import com.morpheus.application.composition.CompositionSnapshotState;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.store.sqlite.SqliteCompositionStateStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MorpheusCompositionMcpToolsTest {
    @TempDir
    Path tempDirectory;

    @Test
    void exposesTwoReadOnlyCompositionToolSpecifications() {
        var specifications = new MorpheusCompositionMcpTools(tempDirectory.resolve("morpheus.db")).specifications();

        assertEquals(2, specifications.size());
        specifications.forEach(specification -> assertNotNull(specification.tool()));
    }

    /**
     * {@code list_composition_conflicts} takes its page bounds from {@link PageArguments}, like the other paged
     * tools, so its schema now announces the maximum {@code offset} its handler always enforced.
     */
    @Test
    void theConflictsToolPublishesThePageBoundsTheHandlerEnforcesAndTheStatusToolPublishesNone() {
        var specifications = new MorpheusCompositionMcpTools(tempDirectory.resolve("morpheus.db")).specifications();
        Map<String, Object> conflicts = properties(specifications, MorpheusCompositionMcpTools.CONFLICTS_TOOL);
        Map<String, Object> status = properties(specifications, MorpheusCompositionMcpTools.STATUS_TOOL);

        assertEquals(PageArguments.properties().get("offset"), conflicts.get("offset"));
        assertEquals(PageArguments.properties().get("limit"), conflicts.get("limit"));
        assertEquals(Set.of("projectId"), status.keySet());
    }

    /**
     * The test above compares two things that are the same object by construction. This one runs the handler on a
     * stored composition state of three conflicts, written out of order, and reads what comes back.
     */
    @Test
    void theConflictsToolPagesTheStoredConflictsInTheOrderTheStateKeeps() throws Exception {
        Path database = tempDirectory.resolve("composition.db").toAbsolutePath().normalize();
        String projectId = storeCompositionOfThreeConflicts(database);
        var specifications = new MorpheusCompositionMcpTools(database).specifications();

        Map<?, ?> whole = conflicts(specifications, Map.of("projectId", projectId));
        assertEquals(List.of("a/first", "b/second", "c/third"), logicalKeys(whole), "conflicts must come back in the order of the state");
        assertEquals(0, whole.get("offset"));
        assertEquals(PageArguments.DEFAULT_LIMIT, whole.get("limit"));
        assertEquals(3, whole.get("totalMatches"));
        assertEquals(false, whole.get("hasMore"));

        Map<?, ?> first = conflicts(specifications, Map.of("projectId", projectId, "limit", 1));
        assertEquals(List.of("a/first"), logicalKeys(first));
        assertEquals(1, first.get("limit"));
        assertEquals(3, first.get("totalMatches"));
        assertEquals(true, first.get("hasMore"));

        Map<?, ?> last = conflicts(specifications, Map.of("projectId", projectId, "offset", 2, "limit", 1));
        assertEquals(List.of("c/third"), logicalKeys(last));
        assertEquals(false, last.get("hasMore"));

        Map<?, ?> beyond = conflicts(specifications, Map.of("projectId", projectId, "offset", 10));
        assertEquals(List.of(), logicalKeys(beyond));
        assertEquals(3, beyond.get("totalMatches"));
        assertEquals(false, beyond.get("hasMore"));
        assertEquals(10, beyond.get("offset"));
    }

    private Map<?, ?> conflicts(
            List<io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification> specifications,
            Map<String, Object> arguments) throws Exception {
        McpSchema.CallToolResult result = McpToolCall.call(
                specifications, MorpheusCompositionMcpTools.CONFLICTS_TOOL, arguments);
        assertFalse(result.isError(), () -> "refused " + arguments + ": " + McpToolCall.text(result));
        return McpJsonDefaults.getMapper().readValue(McpToolCall.text(result), Map.class);
    }

    private static List<Object> logicalKeys(Map<?, ?> page) {
        return ((List<?>) page.get("items")).stream().<Object>map(item -> ((Map<?, ?>) item).get("logicalKey")).toList();
    }

    /** Publishes a project and stores three conflicts on its active snapshot, deliberately not in logical-key order. */
    private static String storeCompositionOfThreeConflicts(Path database) {
        McpToolCall.PublishedProject project = McpToolCall.publish(database);
        ProviderId primary = new ProviderId("openspec");
        ProviderId secondary = new ProviderId("structured-markdown");
        try (SqliteSpecificationKnowledgeStore snapshots = new SqliteSpecificationKnowledgeStore(database);
             SqliteCompositionStateStore compositions = new SqliteCompositionStateStore(database)) {
            var snapshot = snapshots.activeSnapshot(ProjectSpecificationId.parse(project.projectId())).orElseThrow();
            compositions.save(new CompositionSnapshotState(
                    snapshot.id(),
                    primary,
                    List.of(new CompositionProviderState(primary, 100, true, true, 0),
                            new CompositionProviderState(secondary, 50, false, true, 0)),
                    List.of(conflict("c/third", primary, secondary),
                            conflict("a/first", primary, secondary),
                            conflict("b/second", primary, secondary))));
        }
        return project.projectId();
    }

    private static CompositionConflict conflict(String logicalKey, ProviderId primary, ProviderId secondary) {
        return new CompositionConflict(
                CompositionEntityType.REQUIREMENT,
                logicalKey,
                "statement",
                List.of(new CompositionCandidate(primary, 100, "30 minutes", "file:openspec/spec.md", "evidence-primary"),
                        new CompositionCandidate(secondary, 50, "45 minutes", "file:morpheus/specification.md", "evidence-secondary")),
                CompositionResolution.PRECEDENCE_RECORDED,
                Optional.of(primary),
                "the primary provider has the higher configured precedence");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(
            java.util.List<io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification> specifications, String tool) {
        return (Map<String, Object>) specifications.stream()
                .map(specification -> specification.tool())
                .filter(candidate -> candidate.name().equals(tool))
                .findFirst().orElseThrow().inputSchema().get("properties");
    }
}
