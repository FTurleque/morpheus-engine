package com.morpheus.mcp;

import com.morpheus.application.query.dsl.QueryBudgets;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.ToolInputValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void executeSchemaPublishesThePageBudgetAndExportSchemaPublishesTheFormats() {
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

    /**
     * The weak guard: what {@code export_query} declares, spelled out. It cannot say what the handler reads, because
     * the handler reads through {@code query(...)}, which {@code execute_query}, {@code create_saved_view} and
     * {@code update_saved_view} share: a scan of the class sees {@code offset} and {@code limit} read for all four,
     * which is how the schema came to declare them for an export that ignored them. What holds the class "a declared
     * parameter is honoured or refused" is the behavioural test below, not this list.
     */
    @Test
    void anExportDeclaresOnlyWhatDescribesTheQueryAndItsFormat() {
        Map<String, Object> export = tool(MorpheusQueryMcpTools.EXPORT_QUERY).inputSchema();

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) export.get("properties");
        assertEquals(Set.of("scopeKind", "scopeId", "entity", "filter", "sort", "fields", "format"), properties.keySet());
        assertEquals(List.of("scopeKind", "scopeId", "entity", "format"), export.get("required"));
        assertEquals(false, export.get("additionalProperties"));
        String description = tool(MorpheusQueryMcpTools.EXPORT_QUERY).description();
        assertTrue(description.contains("always complete") && description.contains("no offset or limit"), description);
        assertTrue(description.contains(Integer.toString(QueryBudgets.MAX_EXPORT_ROWS)), description);
    }

    @Test
    void aPageIsRefusedByAnExportAndStillAcceptedByAQuery() {
        JsonSchemaValidator validator = McpJsonDefaults.getSchemaValidator();
        Map<String, Object> scope = Map.of(
                "scopeKind", "PROJECT", "scopeId", McpToolCall.ABSENT_PROJECT_ID, "entity", "REQUIREMENT");
        McpSchema.Tool export = tool(MorpheusQueryMcpTools.EXPORT_QUERY);
        McpSchema.Tool execute = tool(MorpheusQueryMcpTools.EXECUTE_QUERY);

        Map<String, Object> plain = new LinkedHashMap<>(scope);
        plain.put("format", "JSON");
        assertNull(ToolInputValidator.validate(export, plain, true, validator));
        for (String paging : List.of("limit", "offset")) {
            Map<String, Object> paged = new LinkedHashMap<>(plain);
            paged.put(paging, 1);
            assertNotNull(ToolInputValidator.validate(export, paged, true, validator),
                    () -> "an export must refuse " + paging + " rather than accept and ignore it");
        }
        Map<String, Object> query = new LinkedHashMap<>(scope);
        query.put("limit", 1);
        query.put("offset", 0);
        assertNull(ToolInputValidator.validate(execute, query, true, validator));
    }

    /**
     * Over the real server wiring, so the SDK's validation stands between the client and the handler as it does in
     * production. The project holds more requirements than the default page: the export without a page carries every
     * one, and the same export asking for one row, or to skip some, is refused rather than answered.
     */
    @Test
    void anExportIsCompleteAndARequestedPageIsRefusedOverTheServer() throws Exception {
        Path database = temporaryDirectory.resolve("export.db").toAbsolutePath().normalize();
        int requirements = MorpheusQueryMcpTools.DEFAULT_LIMIT + 20;
        McpToolCall.PublishedProject project = McpToolCall.publish(database, requirements);
        String input = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"export-test","version":"1.0"}}}
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"export_query","arguments":{"scopeKind":"PROJECT","scopeId":"%1$s","entity":"REQUIREMENT","format":"JSON"}}}
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"export_query","arguments":{"scopeKind":"PROJECT","scopeId":"%1$s","entity":"REQUIREMENT","format":"JSON","limit":1}}}
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"export_query","arguments":{"scopeKind":"PROJECT","scopeId":"%1$s","entity":"REQUIREMENT","format":"JSON","offset":5}}}
                """.formatted(project.projectId());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int exit = MorpheusMcpServer.run(
                database, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), output);

        assertEquals(MorpheusMcpServer.EXIT_END_OF_INPUT, exit);
        Map<?, ?> complete = result(output, 2);
        assertEquals(Boolean.FALSE, complete.get("isError"), complete.toString());
        Map<?, ?> export = McpJsonDefaults.getMapper().readValue(text(complete), Map.class);
        assertEquals(requirements, export.get("totalMatches"));
        assertEquals(requirements, ((List<?>) export.get("rows")).size());
        assertEquals(Boolean.TRUE, result(output, 3).get("isError"), "an export asked for one row must be refused");
        assertEquals(Boolean.TRUE, result(output, 4).get("isError"), "an export asked to skip rows must be refused");
    }

    private static Map<?, ?> result(ByteArrayOutputStream output, int id) throws Exception {
        String line = output.toString(StandardCharsets.UTF_8).lines()
                .filter(candidate -> candidate.contains("\"id\":" + id + ","))
                .findFirst()
                .orElseThrow(() -> new AssertionError("call " + id + " was not answered: " + output));
        return (Map<?, ?>) McpJsonDefaults.getMapper().readValue(line, Map.class).get("result");
    }

    private static String text(Map<?, ?> result) {
        return (String) ((Map<?, ?>) ((List<?>) result.get("content")).getFirst()).get("text");
    }

    private McpSchema.Tool tool(String name) {
        return new MorpheusQueryMcpTools(temporaryDirectory.resolve("morpheus.db")).specifications().stream()
                .map(item -> item.tool())
                .filter(item -> item.name().equals(name))
                .findFirst().orElseThrow();
    }

    @Test
    void serverCatalogAcceptsAllM24ToolsWithoutNameOrSchemaCollision() {
        var server = MorpheusMcpServer.build(
                temporaryDirectory.resolve("morpheus.db"),
                java.io.InputStream.nullInputStream(),
                java.io.OutputStream.nullOutputStream());
        try {
            assertTrue(server != null);
        } finally {
            server.close();
        }
    }
}
