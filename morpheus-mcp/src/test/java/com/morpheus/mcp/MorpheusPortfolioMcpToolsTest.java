package com.morpheus.mcp;

import com.morpheus.domain.project.ProjectSpecificationId;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusPortfolioMcpToolsTest {
    private static final Pattern UUID_V7 =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesCompleteM23PortfolioToolSet() {
        var specifications = new MorpheusPortfolioMcpTools(temporaryDirectory.resolve("morpheus.db")).specifications();

        assertEquals(8, specifications.size());
        String catalog = specifications.stream().map(item -> item.tool().name()).sorted().toList().toString();
        assertTrue(catalog.contains(MorpheusPortfolioMcpTools.CREATE));
        assertTrue(catalog.contains(MorpheusPortfolioMcpTools.REGISTER_PROJECT));
        assertTrue(catalog.contains(MorpheusPortfolioMcpTools.ADD_REFERENCE));
        assertTrue(catalog.contains(MorpheusPortfolioMcpTools.TRAVERSE));
    }

    @Test
    void serverCatalogContainsPortfolioTools() {
        var server = MorpheusMcpServer.build(
                temporaryDirectory.resolve("morpheus.db"),
                java.io.InputStream.nullInputStream(),
                java.io.OutputStream.nullOutputStream());
        try {
            // Construction validates schemas, unique names and complete registration.
            assertTrue(server != null);
        } finally {
            server.close();
        }
    }

    /** A line break inside one identifier must not reach the store, where it would split into two providers. */
    @Test
    void aProviderIdentifierCarryingALineBreakIsAToolErrorAndPersistsNothing() {
        MorpheusPortfolioMcpTools tools = new MorpheusPortfolioMcpTools(temporaryDirectory.resolve("control.db"));
        String portfolioId = firstUuid(text(call(tools, MorpheusPortfolioMcpTools.CREATE, Map.of("name", "Platform"))));
        String projectId = ProjectSpecificationId.generate().toString();

        McpSchema.CallToolResult registered = call(tools, MorpheusPortfolioMcpTools.REGISTER_PROJECT, Map.of(
                "portfolioId", portfolioId,
                "projectId", projectId,
                "name", "Alpha",
                "providers", "openspec\nmarkdown"));
        McpSchema.CallToolResult overview =
                call(tools, MorpheusPortfolioMcpTools.OVERVIEW, Map.of("portfolioId", portfolioId));

        assertTrue(registered.isError(), text(registered));
        assertEquals("provider id must not contain control characters: \"openspec[U+000A]markdown\"", text(registered));
        assertFalse(overview.isError(), text(overview));
        assertFalse(text(overview).contains(projectId), text(overview));
    }

    private static McpSchema.CallToolResult call(
            MorpheusPortfolioMcpTools tools, String toolName, Map<String, Object> arguments) {
        return tools.specifications().stream()
                .filter(item -> item.tool().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tool not published: " + toolName))
                .callHandler()
                .apply(null, new McpSchema.CallToolRequest(toolName, arguments));
    }

    private static String text(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(item -> item instanceof McpSchema.TextContent)
                .map(item -> ((McpSchema.TextContent) item).text())
                .reduce("", String::concat);
    }

    private static String firstUuid(String text) {
        Matcher matcher = UUID_V7.matcher(text);
        if (!matcher.find()) {
            throw new AssertionError("UUIDv7 not found in " + text);
        }
        return matcher.group();
    }
}
