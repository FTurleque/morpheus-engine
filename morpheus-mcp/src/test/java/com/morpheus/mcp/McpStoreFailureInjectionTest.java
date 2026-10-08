package com.morpheus.mcp;

import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.ToolInputValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A store that cannot be opened is answered as a tool refusal by every handler that reaches one (ADR-0102 section
 * 2, amendment of 8 October 2026).
 *
 * <p>Every published tool is called with the smallest arguments its own schema accepts, so it gets past its argument
 * checks and has to open the store. Two ways the store can fail to open are injected: a database file the driver
 * cannot read, and a database reserved by an offline maintenance. Narrowing one handler's {@code catch} to drop
 * {@link com.morpheus.application.store.KnowledgeStoreException} lets the failure escape that handler, and this test
 * names it.</p>
 *
 * <p>The tool classes are listed by hand, beside the server's own assembly, so that each one is checked under its
 * own name; {@link #theInjectedClassesAreExactlyTheServedOnes()} refuses a list that drifts from what is served.</p>
 */
class McpStoreFailureInjectionTest {
    /** The classes whose handlers never open a store: they must answer, and nothing more is asked of them. */
    private static final Set<String> STORELESS_CLASSES = Set.of("Product", "ProviderPlugin", "Reasoning");

    @TempDir
    Path temporaryDirectory;

    @Test
    void aDatabaseTheDriverCannotReadIsRefusedByEveryHandlerThatReachesTheStore() throws Exception {
        Path database = database();
        Files.writeString(database, "not a SQLite database, long enough to fill a header page".repeat(16),
                StandardCharsets.US_ASCII);

        assertEveryHandlerRefusesTheStore(database);
    }

    @Test
    void aDatabaseReservedForExclusiveMaintenanceIsRefusedByEveryHandlerThatReachesTheStore() throws Exception {
        Path database = database();
        new SqliteSpecificationKnowledgeStore(database).close();
        Path lockFile = database.resolveSibling(database.getFileName() + ".access.lock");

        // The lease reads any exclusive lock on its sidecar as an offline maintenance in progress.
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock exclusive = channel.lock()) {
            assertTrue(exclusive.isValid());

            assertEveryHandlerRefusesTheStore(database);
        }
    }

    @Test
    void theInjectedClassesAreExactlyTheServedOnes() {
        Path database = database();
        List<String> injected = toolClasses(database).values().stream()
                .flatMap(List::stream)
                .map(specification -> specification.tool().name())
                .sorted()
                .toList();
        List<String> served = MorpheusMcpServer.toolSpecifications(
                        database,
                        new ExternalReferenceResolverRegistry(List.of()),
                        MorpheusMcpServer.unconfiguredTechnicalContext(),
                        MorpheusMcpServer.deniedWriteCapability())
                .stream()
                .map(specification -> specification.tool().name())
                .sorted()
                .toList();

        assertEquals(served, injected);
    }

    private void assertEveryHandlerRefusesTheStore(Path database) {
        JsonSchemaValidator validator = McpJsonDefaults.getSchemaValidator();
        for (Map.Entry<String, List<McpServerFeatures.SyncToolSpecification>> toolClass
                : toolClasses(database).entrySet()) {
            if (!STORELESS_CLASSES.contains(toolClass.getKey())) {
                assertFalse(toolClass.getValue().isEmpty(), () -> toolClass.getKey() + " serves no tool");
            }
            for (McpServerFeatures.SyncToolSpecification specification : toolClass.getValue()) {
                McpSchema.Tool tool = specification.tool();
                String name = toolClass.getKey() + "." + tool.name();
                Map<String, Object> arguments = McpSchemaArguments.valid(tool.inputSchema());
                // Arguments the SDK would refuse never reach a handler in production; reaching it here would prove
                // nothing about the served path.
                assertNull(ToolInputValidator.validate(tool, arguments, true, validator),
                        () -> name + " was given arguments its schema refuses: " + arguments);

                McpSchema.CallToolResult result = assertDoesNotThrow(
                        () -> McpToolCall.call(List.of(specification), tool.name(), arguments),
                        () -> name + " let a store failure escape its handler");

                if (STORELESS_CLASSES.contains(toolClass.getKey())) {
                    continue;
                }
                String text = McpSchemaArguments.text(result);
                assertTrue(Boolean.TRUE.equals(result.isError()), () -> name + " did not refuse: " + text);
                assertTrue(text.startsWith("Cannot initialize SQLite "),
                        () -> name + " refused for another reason than the store: " + text);
                assertFalse(text.contains("Caused by"), () -> name + ": " + text);
                assertFalse(text.contains(temporaryDirectory.toString()), () -> name + ": " + text);
                assertFalse(text.contains(temporaryDirectory.toString().replace('\\', '/')), () -> name + ": " + text);
            }
        }
    }

    private static Map<String, List<McpServerFeatures.SyncToolSpecification>> toolClasses(Path database) {
        Map<String, List<McpServerFeatures.SyncToolSpecification>> classes = new LinkedHashMap<>();
        MorpheusMcpToolService service = new MorpheusMcpToolService(database);
        List<McpServerFeatures.SyncToolSpecification> catalog = new ArrayList<>();
        for (MorpheusMcpToolCatalog.ToolDefinition definition : new MorpheusMcpToolCatalog().tools()) {
            catalog.add(MorpheusMcpServer.tool(definition, service));
        }
        classes.put("McpServer", catalog);
        classes.put("Product", new MorpheusProductMcpTools().specifications());
        classes.put("ProviderPlugin", new MorpheusProviderPluginMcpTools().specifications());
        classes.put("Portfolio", new MorpheusPortfolioMcpTools(database).specifications());
        classes.put("Query", new MorpheusQueryMcpTools(database).specifications());
        classes.put("Policy", new MorpheusPolicyMcpTools(database).specifications());
        classes.put("PolicyManagement", new MorpheusPolicyMcpManagementTools(database).specifications());
        classes.put("Reasoning", new MorpheusReasoningMcpTools().specifications());
        classes.put("ExternalReference", new MorpheusExternalReferenceMcpTools(
                database, new ExternalReferenceResolverRegistry(List.of())).specifications());
        classes.put("AugmentedContext", new MorpheusAugmentedContextMcpTools(
                database, MorpheusMcpServer.unconfiguredTechnicalContext()).specifications());
        classes.put("Jarvis", new MorpheusJarvisOrchestrationMcpTools(database).specifications());
        classes.put("Composition", new MorpheusCompositionMcpTools(database).specifications());
        classes.put("ControlledLifecycle", new MorpheusControlledLifecycleMcpTools(
                database, MorpheusMcpServer.deniedWriteCapability()).specifications());
        return classes;
    }

    private Path database() {
        return temporaryDirectory.resolve("store-failure.db").toAbsolutePath().normalize();
    }
}
