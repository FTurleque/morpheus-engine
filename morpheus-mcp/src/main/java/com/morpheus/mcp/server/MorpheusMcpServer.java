package com.morpheus.mcp.server;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.product.ProductMetadata;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.snapshot.RuntimeSnapshotRecovery;
import com.morpheus.integration.mcp.BoundedStdioServerTransportProvider;
import com.morpheus.mcp.MorpheusAcceptanceCriteriaMcpTools;
import com.morpheus.mcp.MorpheusAugmentedContextMcpTools;
import com.morpheus.mcp.MorpheusChangeMcpTools;
import com.morpheus.mcp.MorpheusCompositionMcpTools;
import com.morpheus.mcp.MorpheusControlledLifecycleMcpTools;
import com.morpheus.mcp.MorpheusExternalReferenceMcpTools;
import com.morpheus.mcp.MorpheusJarvisOrchestrationMcpTools;
import com.morpheus.mcp.MorpheusMcpRuntime;
import com.morpheus.mcp.MorpheusMcpToolCatalog;
import com.morpheus.mcp.MorpheusPolicyMcpManagementTools;
import com.morpheus.mcp.MorpheusPolicyMcpTools;
import com.morpheus.mcp.MorpheusPortfolioMcpTools;
import com.morpheus.mcp.MorpheusProductMcpTools;
import com.morpheus.mcp.MorpheusProviderPluginMcpTools;
import com.morpheus.mcp.MorpheusQueryMcpTools;
import com.morpheus.mcp.MorpheusReasoningMcpTools;
import com.morpheus.mcp.MorpheusRequirementMcpTools;
import com.morpheus.mcp.MorpheusSpecificationMcpTools;
import com.morpheus.mcp.MorpheusSyncStatusMcpTools;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Native STDIO MCP adapter. Stdout is owned exclusively by the MCP transport. */
public final class MorpheusMcpServer {
    public static final String SERVER_NAME = "morpheus";
    public static final String SERVER_VERSION = ProductMetadata.version();
    /** The peer closed stdin: the session ended the way the protocol says it ends. */
    public static final int EXIT_END_OF_INPUT = 0;
    /**
     * The transport failed the session closed, or the wait was interrupted before end of input. Converges with
     * {@code CliExitCode.IO_ERROR} without depending on the CLI, which depends on this module; the equality is
     * pinned by a test in morpheus-architecture-tests (ADR-0106).
     */
    public static final int EXIT_TRANSPORT_FAILURE = 5;
    /**
     * What a client should do when a response overflows the MCP frame. The transport knows no catalog, so this layer
     * says it. Generic on purpose: the tool that overflowed is not known here either, only that paginated read tools
     * share one pair of parameters.
     */
    public static final String OVERSIZED_RESPONSE_GUIDANCE =
            "paginated read tools accept offset and limit: retry with a smaller limit, then page with offset";

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

    /**
     * A server built without a recorder counts nowhere: the MCP server serves no counters, and only the launcher, which
     * hands the recorder of the process to {@link #run(Path, ExternalReferenceResolverRegistry, TechnicalContextProvider,
     * ChangeWriteCapabilityResolver, OperationalRecorder)}, has somewhere to put them.
     */
    @SuppressWarnings("java:S106") // System.out is the actual MCP wire-protocol stream, not a log write.
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
                OperationalRecorder.noop(),
                transport(System.in, System.out));
    }

    public static McpSyncServer build(Path databasePath, InputStream inputStream, OutputStream outputStream) {
        Objects.requireNonNull(inputStream, "inputStream");
        Objects.requireNonNull(outputStream, "outputStream");
        return build(
                databasePath,
                new ExternalReferenceResolverRegistry(List.of()),
                disabledNexus(),
                deniedWrites(),
                OperationalRecorder.noop(),
                transport(inputStream, outputStream));
    }

    private static BoundedStdioServerTransportProvider transport(InputStream inputStream, OutputStream outputStream) {
        return new BoundedStdioServerTransportProvider(
                McpJsonDefaults.getMapper(),
                inputStream,
                outputStream,
                BoundedStdioServerTransportProvider.DEFAULT_MAX_FRAME_BYTES,
                BoundedStdioServerTransportProvider.DEFAULT_MAX_PENDING_MESSAGES,
                OVERSIZED_RESPONSE_GUIDANCE);
    }

    private static McpSyncServer build(
            Path databasePath,
            ExternalReferenceResolverRegistry resolverRegistry,
            TechnicalContextProvider technicalContextProvider,
            ChangeWriteCapabilityResolver writeCapabilityResolver,
            OperationalRecorder recorder,
            BoundedStdioServerTransportProvider transport) {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(recorder, "recorder");
        Objects.requireNonNull(resolverRegistry, "resolverRegistry");
        Objects.requireNonNull(technicalContextProvider, "technicalContextProvider");
        Objects.requireNonNull(writeCapabilityResolver, "writeCapabilityResolver");
        Objects.requireNonNull(transport, "transport");
        try (MorpheusMcpRuntime runtime = new MorpheusMcpRuntime(databasePath, recorder)) {
            new RuntimeSnapshotRecovery(runtime.snapshots(), recorder).recoverAll(Instant.now());
        }
        List<McpServerFeatures.SyncToolSpecification> tools = toolSpecifications(
                databasePath, resolverRegistry, technicalContextProvider, writeCapabilityResolver, recorder);

        return McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .validateToolInputs(true)
                .tools(tools)
                .build();
    }

    /**
     * Every tool this server serves, assembled once so a contract test can hold the same list the transport
     * does. A thirteenth tool class wired into the server but absent from the failure contract would otherwise
     * be invisible to the tests that exist to catch exactly that.
     */
    public static List<McpServerFeatures.SyncToolSpecification> toolSpecifications(
            Path databasePath,
            ExternalReferenceResolverRegistry resolverRegistry,
            TechnicalContextProvider technicalContextProvider,
            ChangeWriteCapabilityResolver writeCapabilityResolver,
            OperationalRecorder recorder) {
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>(catalogueTools(databasePath, recorder));
        tools.addAll(new MorpheusProductMcpTools().specifications());
        tools.addAll(new MorpheusProviderPluginMcpTools().specifications());
        tools.addAll(new MorpheusPortfolioMcpTools(databasePath, recorder).specifications());
        tools.addAll(new MorpheusQueryMcpTools(databasePath, recorder).specifications());
        tools.addAll(new MorpheusPolicyMcpTools(databasePath, recorder).specifications());
        tools.addAll(new MorpheusPolicyMcpManagementTools(databasePath, recorder).specifications());
        tools.addAll(new MorpheusReasoningMcpTools(recorder).specifications());
        tools.addAll(new MorpheusExternalReferenceMcpTools(databasePath, recorder, resolverRegistry).specifications());
        tools.addAll(new MorpheusAugmentedContextMcpTools(databasePath, recorder, technicalContextProvider)
                .specifications());
        tools.addAll(new MorpheusJarvisOrchestrationMcpTools(databasePath, recorder).specifications());
        tools.addAll(new MorpheusCompositionMcpTools(databasePath, recorder).specifications());
        tools.addAll(new MorpheusControlledLifecycleMcpTools(databasePath, recorder, writeCapabilityResolver)
                .specifications());
        return List.copyOf(tools);
    }

    /** The catalogue tools the capability classes serve, by name; a name served twice stops the server from being built. */
    static Map<String, McpServerFeatures.SyncToolSpecification> byName(
            List<McpServerFeatures.SyncToolSpecification> specifications) {
        Map<String, McpServerFeatures.SyncToolSpecification> byName = new LinkedHashMap<>();
        for (McpServerFeatures.SyncToolSpecification specification : specifications) {
            if (byName.putIfAbsent(specification.tool().name(), specification) != null) {
                throw new IllegalStateException(
                        "catalogue tool served by two capability classes: " + specification.tool().name());
            }
        }
        return byName;
    }

    /**
     * The tools {@link MorpheusMcpToolCatalog} defines, in its order, each served by its capability class. A definition
     * no class serves, and a tool served under a name the catalogue does not define, stop the server from being built.
     */
    private static List<McpServerFeatures.SyncToolSpecification> catalogueTools(
            Path databasePath, OperationalRecorder recorder) {
        Map<String, McpServerFeatures.SyncToolSpecification> served = byName(Stream.of(
                        new MorpheusSpecificationMcpTools(databasePath, recorder).specifications(),
                        new MorpheusRequirementMcpTools(databasePath, recorder).specifications(),
                        new MorpheusChangeMcpTools(databasePath, recorder).specifications(),
                        new MorpheusAcceptanceCriteriaMcpTools(databasePath, recorder).specifications(),
                        new MorpheusSyncStatusMcpTools(databasePath, recorder).specifications())
                .flatMap(List::stream)
                .toList());
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();
        for (MorpheusMcpToolCatalog.ToolDefinition definition : new MorpheusMcpToolCatalog().tools()) {
            McpServerFeatures.SyncToolSpecification tool = served.remove(definition.name());
            if (tool == null) {
                throw new IllegalStateException("catalogue tool served by no capability class: " + definition.name());
            }
            tools.add(tool);
        }
        if (!served.isEmpty()) {
            throw new IllegalStateException("tools served as catalogue tools but absent from the catalogue: "
                    + served.keySet());
        }
        return tools;
    }

    public static TechnicalContextProvider unconfiguredTechnicalContext() {
        return disabledNexus();
    }

    public static ChangeWriteCapabilityResolver deniedWriteCapability() {
        return deniedWrites();
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
        return run(databasePath, resolverRegistry, technicalContextProvider, writeCapabilityResolver,
                OperationalRecorder.noop());
    }

    /** Serves STDIO until end of input; every tool call counts what it puts on the database on {@code recorder}. */
    @SuppressWarnings("java:S106") // System.out is the actual MCP wire-protocol stream, not a log write.
    public static int run(
            Path databasePath,
            ExternalReferenceResolverRegistry resolverRegistry,
            TechnicalContextProvider technicalContextProvider,
            ChangeWriteCapabilityResolver writeCapabilityResolver,
            OperationalRecorder recorder) {
        return serve(
                databasePath,
                resolverRegistry,
                technicalContextProvider,
                writeCapabilityResolver,
                recorder,
                transport(System.in, System.out));
    }

    public static int run(Path databasePath, InputStream inputStream, OutputStream outputStream) {
        Objects.requireNonNull(inputStream, "inputStream");
        Objects.requireNonNull(outputStream, "outputStream");
        return serve(
                databasePath,
                new ExternalReferenceResolverRegistry(List.of()),
                disabledNexus(),
                deniedWrites(),
                OperationalRecorder.noop(),
                transport(inputStream, outputStream));
    }

    private static int serve(
            Path databasePath,
            ExternalReferenceResolverRegistry resolverRegistry,
            TechnicalContextProvider technicalContextProvider,
            ChangeWriteCapabilityResolver writeCapabilityResolver,
            OperationalRecorder recorder,
            BoundedStdioServerTransportProvider transport) {
        McpSyncServer server = build(
                databasePath,
                resolverRegistry,
                technicalContextProvider,
                writeCapabilityResolver,
                recorder,
                transport);
        try {
            transport.awaitTermination();
            return transport.terminatedInFailure() ? EXIT_TRANSPORT_FAILURE : EXIT_END_OF_INPUT;
        } catch (InterruptedException interrupted) {
            // The server never reached the end of its input stream, so this is not the clean exit EOF reports.
            Thread.currentThread().interrupt();
            return EXIT_TRANSPORT_FAILURE;
        } finally {
            server.close();
        }
    }

    private static TechnicalContextProvider disabledNexus() {
        return new DisabledTechnicalContextProvider("NEXUS", "NEXUS integration is not configured");
    }

    private static ChangeWriteCapabilityResolver deniedWrites() {
        return projectId -> ChangeWriteCapabilityObservation.denied(
                "No WRITE_CHANGE provider capability resolver is configured for this MCP server");
    }

}
