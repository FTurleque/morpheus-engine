package com.morpheus.mcp;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.product.ProductMetadata;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.snapshot.RuntimeSnapshotRecovery;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Native STDIO MCP adapter. Stdout is owned exclusively by the MCP transport. */
public final class MorpheusMcpServer {
    public static final String SERVER_NAME = "morpheus";
    public static final String SERVER_VERSION = ProductMetadata.version();

    private MorpheusMcpServer() {
    }

    public static McpSyncServer build(Path databasePath) {
        return build(databasePath, new ExternalReferenceResolverRegistry(List.of()), disabledNexus(), deniedWrites());
    }

    public static McpSyncServer build(Path databasePath, ExternalReferenceResolverRegistry resolverRegistry) {
        return build(databasePath, resolverRegistry, disabledNexus(), deniedWrites());
    }

    public static McpSyncServer build(
            Path databasePath,
            ExternalReferenceResolverRegistry resolverRegistry,
            TechnicalContextProvider technicalContextProvider) {
        return build(databasePath, resolverRegistry, technicalContextProvider, deniedWrites());
    }

    public static McpSyncServer build(
            Path databasePath,
            ExternalReferenceResolverRegistry resolverRegistry,
            TechnicalContextProvider technicalContextProvider,
            ChangeWriteCapabilityResolver writeCapabilityResolver) {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(resolverRegistry, "resolverRegistry");
        Objects.requireNonNull(technicalContextProvider, "technicalContextProvider");
        Objects.requireNonNull(writeCapabilityResolver, "writeCapabilityResolver");
        try (SqliteSpecificationKnowledgeStore store = new SqliteSpecificationKnowledgeStore(databasePath)) {
            new RuntimeSnapshotRecovery(store).recoverAll(Instant.now());
        }
        MorpheusMcpToolCatalog catalog = new MorpheusMcpToolCatalog();
        MorpheusMcpToolService service = new MorpheusMcpToolService(databasePath);
        StdioServerTransportProvider transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .validateToolInputs(true)
                .build();

        for (MorpheusMcpToolCatalog.ToolDefinition definition : catalog.tools()) {
            server.addTool(tool(definition, service));
        }
        for (McpServerFeatures.SyncToolSpecification specification : new MorpheusProductMcpTools().specifications()) {
            server.addTool(specification);
        }
        for (McpServerFeatures.SyncToolSpecification specification : new MorpheusProviderPluginMcpTools().specifications()) {
            server.addTool(specification);
        }
        for (McpServerFeatures.SyncToolSpecification specification
                : new MorpheusPortfolioMcpTools(databasePath).specifications()) {
            server.addTool(specification);
        }
        for (McpServerFeatures.SyncToolSpecification specification
                : new MorpheusQueryMcpTools(databasePath).specifications()) {
            server.addTool(specification);
        }
        for (McpServerFeatures.SyncToolSpecification specification
                : new MorpheusPolicyMcpTools(databasePath).specifications()) {
            server.addTool(specification);
        }
        for (McpServerFeatures.SyncToolSpecification specification
                : new MorpheusExternalReferenceMcpTools(databasePath, resolverRegistry).specifications()) {
            server.addTool(specification);
        }
        for (McpServerFeatures.SyncToolSpecification specification
                : new MorpheusAugmentedContextMcpTools(databasePath, technicalContextProvider).specifications()) {
            server.addTool(specification);
        }
        for (McpServerFeatures.SyncToolSpecification specification
                : new MorpheusJarvisOrchestrationMcpTools(databasePath).specifications()) {
            server.addTool(specification);
        }
        for (McpServerFeatures.SyncToolSpecification specification
                : new MorpheusCompositionMcpTools(databasePath).specifications()) {
            server.addTool(specification);
        }
        for (McpServerFeatures.SyncToolSpecification specification
                : new MorpheusControlledLifecycleMcpTools(databasePath, writeCapabilityResolver).specifications()) {
            server.addTool(specification);
        }
        return server;
    }

    public static int run(Path databasePath) {
        return run(databasePath, new ExternalReferenceResolverRegistry(List.of()), disabledNexus(), deniedWrites());
    }

    public static int run(Path databasePath, ExternalReferenceResolverRegistry resolverRegistry) {
        return run(databasePath, resolverRegistry, disabledNexus(), deniedWrites());
    }

    public static int run(
            Path databasePath,
            ExternalReferenceResolverRegistry resolverRegistry,
            TechnicalContextProvider technicalContextProvider) {
        return run(databasePath, resolverRegistry, technicalContextProvider, deniedWrites());
    }

    public static int run(
            Path databasePath,
            ExternalReferenceResolverRegistry resolverRegistry,
            TechnicalContextProvider technicalContextProvider,
            ChangeWriteCapabilityResolver writeCapabilityResolver) {
        McpSyncServer server = build(databasePath, resolverRegistry, technicalContextProvider, writeCapabilityResolver);
        try {
            Thread.currentThread().join();
            return 0;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return 0;
        } finally {
            server.close();
        }
    }

    static McpServerFeatures.SyncToolSpecification tool(
            MorpheusMcpToolCatalog.ToolDefinition definition,
            MorpheusMcpToolService service) {
        McpSchema.Tool tool = McpSchema.Tool.builder(definition.name(), definition.inputSchema())
                .description(definition.description())
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> call(service, definition.name(), request.arguments()))
                .build();
    }

    private static McpSchema.CallToolResult call(
            MorpheusMcpToolService service,
            String toolName,
            Map<String, Object> arguments) {
        try {
            String result = service.execute(toolName, arguments == null ? Map.of() : arguments);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(result)))
                    .build();
        } catch (IllegalArgumentException | KnowledgeStoreException expected) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(safeMessage(expected))))
                    .isError(true)
                    .build();
        }
    }

    private static TechnicalContextProvider disabledNexus() {
        return new DisabledTechnicalContextProvider("NEXUS", "NEXUS integration is not configured");
    }

    private static ChangeWriteCapabilityResolver deniedWrites() {
        return projectId -> ChangeWriteCapabilityObservation.denied(
                "No WRITE_CHANGE provider capability resolver is configured for this MCP server");
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}