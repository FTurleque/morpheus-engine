package com.morpheus.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(
            java.util.List<io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification> specifications, String tool) {
        return (Map<String, Object>) specifications.stream()
                .map(specification -> specification.tool())
                .filter(candidate -> candidate.name().equals(tool))
                .findFirst().orElseThrow().inputSchema().get("properties");
    }
}
