package com.morpheus.mcp;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.product.ProductMetadata;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.snapshot.RuntimeSnapshotRecovery;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.integration.mcp.BoundedStdioServerTransportProvider;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
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
        return build(
                databasePath,
                resolverRegistry,
                technicalContextProvider,
                writeCapabilityResolver,
                new BoundedStdioServerTransportProvider(McpJsonDefaults.getMapper()));
    }

    private static McpSyncServer build(
            Path databasePath,
            ExternalReferenceResolverRegistry resolverRegistry,
            TechnicalContextProvider technicalContextProvider,
            ChangeWriteCapabilityResolver writeCapabilityResolver,
            BoundedStdioServerTransportProvider transport) {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(resolverRegistry, "resolverRegistry");
        Objects.requireNonNull(technicalContextProvider, "technicalContextProvider");
        Objects.requireNonNull(writeCapabilityResolver, "writeCapabilityResolver");
        Objects.requireNonNull(transport, "transport");
        try (SqliteSpecificationKnowledgeStore store = new SqliteSpecificationKnowledgeStore(databasePath)) {
            new RuntimeSnapshotRecovery(store).recoverAll(Instant.now());
        }
        MorpheusMcpToolCatalog catalog = new MorpheusMcpToolCatalog();
        MorpheusMcpToolService service = new MorpheusMcpToolService(databasePath);
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();

        for (MorpheusMcpToolCatalog.ToolDefinition definition : catalog.tools()) {
            tools.add(tool(definition, service));
        }
        tools.addAll(new MorpheusProductMcpTools().specifications());
        tools.addAll(new MorpheusProviderPluginMcpTools().specifications());
        tools.addAll(new MorpheusPortfolioMcpTools(databasePath).specifications());
        tools.addAll(new MorpheusQueryMcpTools(databasePath).specifications());
        tools.addAll(new MorpheusPolicyMcpTools(databasePath).specifications());
        tools.addAll(new MorpheusPolicyMcpManagementTools(databasePath).specifications());
        tools.addAll(new MorpheusReasoningMcpTools().specifications());
        tools.addAll(new MorpheusExternalReferenceMcpTools(databasePath, resolverRegistry).specifications());
        tools.addAll(new MorpheusAugmentedContextMcpTools(databasePath, technicalContextProvider).specifications());
        tools.addAll(new MorpheusJarvisOrchestrationMcpTools(databasePath).specifications());
        tools.addAll(new MorpheusCompositionMcpTools(databasePath).specifications());
        tools.addAll(new MorpheusControlledLifecycleMcpTools(databasePath, writeCapabilityResolver).specifications());

        return McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .validateToolInputs(true)
                .tools(tools)
                .build();
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
        BoundedStdioServerTransportProvider transport =
                new BoundedStdioServerTransportProvider(McpJsonDefaults.getMapper());
        McpSyncServer server = build(
                databasePath,
                resolverRegistry,
                technicalContextProvider,
                writeCapabilityResolver,
                transport);
        try {
            transport.awaitTermination();
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
                    .addTextContent(result)
                    .isError(false)
                    .build();
        } catch (IllegalArgumentException | KnowledgeStoreException expected) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(safeMessage(expected))
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
