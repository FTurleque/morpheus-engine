package com.morpheus.mcp;

import com.morpheus.application.query.dsl.QueryBudgets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusQueryMcpToolsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesCompleteM24IntentSetWithStrictSchemas() {
        var specifications = new MorpheusQueryMcpTools(temporaryDirectory.resolve("morpheus.db")).specifications();

        assertEquals(10, specifications.size());
        Set<String> names = specifications.stream().map(item -> item.tool().name()).collect(Collectors.toSet());
        assertEquals(Set.of(
                MorpheusQueryMcpTools.EXECUTE_QUERY,
                MorpheusQueryMcpTools.CREATE_SAVED_VIEW,
                MorpheusQueryMcpTools.LIST_SAVED_VIEWS,
                MorpheusQueryMcpTools.GET_SAVED_VIEW,
                MorpheusQueryMcpTools.LIST_SAVED_VIEW_VERSIONS,
                MorpheusQueryMcpTools.UPDATE_SAVED_VIEW,
                MorpheusQueryMcpTools.ARCHIVE_SAVED_VIEW,
                MorpheusQueryMcpTools.EXECUTE_SAVED_VIEW,
                MorpheusQueryMcpTools.EXPORT_QUERY,
                MorpheusQueryMcpTools.EXPORT_SAVED_VIEW), names);

        for (var specification : specifications) {
            Map<String, Object> schema = specification.tool().inputSchema();
            assertEquals(false, schema.get("additionalProperties"), specification.tool().name());
            assertTrue(schema.containsKey("required"), specification.tool().name());
            assertTrue(schema.containsKey("properties"), specification.tool().name());
        }
    }

    @Test
    void executeAndExportSchemasPublishTheM24PageAndFormatBudgets() {
        var specifications = new MorpheusQueryMcpTools(temporaryDirectory.resolve("morpheus.db")).specifications();
        var execute = specifications.stream()
                .filter(item -> item.tool().name().equals(MorpheusQueryMcpTools.EXECUTE_QUERY))
                .findFirst().orElseThrow().tool().inputSchema();
        var export = specifications.stream()
                .filter(item -> item.tool().name().equals(MorpheusQueryMcpTools.EXPORT_QUERY))
                .findFirst().orElseThrow().tool().inputSchema();

        @SuppressWarnings("unchecked")
        Map<String, Object> executeProperties = (Map<String, Object>) execute.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> limit = (Map<String, Object>) executeProperties.get("limit");
        assertEquals(QueryBudgets.MAX_PAGE_SIZE, limit.get("maximum"));

        @SuppressWarnings("unchecked")
        Map<String, Object> exportProperties = (Map<String, Object>) export.get("properties");
        assertTrue(exportProperties.get("format").toString().contains("JSON"));
        assertTrue(exportProperties.get("format").toString().contains("CSV"));
        assertTrue(exportProperties.get("format").toString().contains("MARKDOWN"));
        assertFalse(export.toString().toLowerCase().contains("sql"));
    }

    @Test
    void serverCatalogAcceptsAllM24ToolsWithoutNameOrSchemaCollision() {
        var server = MorpheusMcpServer.build(temporaryDirectory.resolve("morpheus.db"));
        try {
            assertTrue(server != null);
        } finally {
            server.close();
        }
    }
}
