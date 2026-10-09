package com.morpheus.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusMcpToolCatalogTest {

    @Test
    void exposesExactReadOnlyM10CatalogWithStrictObjectSchemas() {
        MorpheusMcpToolCatalog catalog = new MorpheusMcpToolCatalog();

        assertEquals(List.of(
                "get_current_specification",
                "find_requirements",
                "get_change",
                "list_changes",
                "get_constraints",
                "get_acceptance_criteria",
                "get_design_decisions",
                "get_implementation_tasks",
                "trace_requirement",
                "get_change_context",
                "get_specification_context",
                "get_change_status",
                "get_blocking_conditions",
                "get_sync_status"),
                catalog.tools().stream().map(MorpheusMcpToolCatalog.ToolDefinition::name).toList());

        for (MorpheusMcpToolCatalog.ToolDefinition tool : catalog.tools()) {
            assertEquals("object", tool.inputSchema().get("type"));
            assertEquals(false, tool.inputSchema().get("additionalProperties"));
            assertFalse(((Map<?, ?>) tool.inputSchema().get("properties")).isEmpty());
        }
    }

    @Test
    void boundsDepthPaginationAndFreshnessAtSchemaLevel() {
        MorpheusMcpToolCatalog catalog = new MorpheusMcpToolCatalog();

        Map<?, ?> traceProperties = properties(catalog.require("trace_requirement"));
        assertEquals(1L, ((Number) ((Map<?, ?>) traceProperties.get("depth")).get("minimum")).longValue());
        assertEquals(20L, ((Number) ((Map<?, ?>) traceProperties.get("depth")).get("maximum")).longValue());

        Map<?, ?> searchProperties = properties(catalog.require("find_requirements"));
        assertEquals(100L, ((Number) ((Map<?, ?>) searchProperties.get("limit")).get("maximum")).longValue());

        Map<?, ?> syncProperties = properties(catalog.require("get_sync_status"));
        assertEquals(525_600L, ((Number) ((Map<?, ?>) syncProperties.get("maxAgeMinutes")).get("maximum")).longValue());
    }

    /**
     * The tool used to call itself a summary while it returned every specification with its full description --
     * the payload that overflowed the MCP frame. It now pages that collection exactly as its paginated neighbours
     * do, and says so.
     */
    @Test
    void theCurrentSpecificationToolDescribesWhatItActuallyReturns() {
        MorpheusMcpToolCatalog catalog = new MorpheusMcpToolCatalog();
        MorpheusMcpToolCatalog.ToolDefinition tool = catalog.require("get_current_specification");

        Map<?, ?> properties = properties(tool);
        Map<?, ?> neighbour = properties(catalog.require("list_changes"));
        assertEquals(neighbour.get("offset"), properties.get("offset"));
        assertEquals(neighbour.get("limit"), properties.get("limit"));
        assertEquals(List.of("projectId"), tool.inputSchema().get("required"));

        assertFalse(tool.description().contains("summary"), tool.description());
        assertTrue(tool.description().contains("page"), tool.description());
        assertTrue(tool.description().contains("description"), tool.description());
    }

    @Test
    void rejectsUnknownToolName() {
        assertThrows(IllegalArgumentException.class, () -> new MorpheusMcpToolCatalog().require("write_requirement"));
    }

    private Map<?, ?> properties(MorpheusMcpToolCatalog.ToolDefinition definition) {
        return (Map<?, ?>) definition.inputSchema().get("properties");
    }
}
