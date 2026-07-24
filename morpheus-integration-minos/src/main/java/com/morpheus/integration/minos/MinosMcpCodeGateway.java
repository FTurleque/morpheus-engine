package com.morpheus.integration.minos;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Real inter-process MINOS gateway using its validated MCP STDIO server. */
public final class MinosMcpCodeGateway implements MinosCodeGateway {
    public static final String MINOS_SERVER_CLASS = "com.minos.mcp.MinosMcpServer";
    public static final String TOOL_INDEX_STATUS = "minos_index_status";
    public static final String TOOL_FIND_SYMBOLS = "minos_find_symbols";
    private static final Set<String> REQUIRED_TOOLS = Set.of(TOOL_INDEX_STATUS, TOOL_FIND_SYMBOLS);

    private final McpSyncClient client;
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public MinosMcpCodeGateway(MinosIntegrationSettings settings) {
        Objects.requireNonNull(settings, "settings");
        if (!settings.enabled()) {
            throw new MinosIntegrationException("MINOS integration is not configured: "
                    + settings.configurationError().orElse(settings.state().name()));
        }
        Path jar = settings.jarPath().orElseThrow();
        var parameters = ServerParameters.builder(settings.javaCommand())
                .args("-cp", jar.toString(), MINOS_SERVER_CLASS);
        Map<String, String> processEnvironment = settings.processEnvironment();
        if (!processEnvironment.isEmpty()) {
            parameters.env(processEnvironment);
        }
        try {
            StdioClientTransport transport = new StdioClientTransport(parameters.build(), McpJsonDefaults.getMapper());
            this.client = McpClient.sync(transport)
                    .requestTimeout(settings.timeout())
                    .build();
            client.initialize();
            Set<String> available = client.listTools().tools().stream()
                    .map(tool -> tool.name())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (!available.containsAll(REQUIRED_TOOLS)) {
                client.closeGracefully();
                throw new MinosIntegrationException(
                        "MINOS MCP server is incompatible; required tools missing: "
                                + REQUIRED_TOOLS.stream().filter(tool -> !available.contains(tool)).sorted().toList());
            }
        } catch (MinosIntegrationException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new MinosIntegrationException("cannot start or initialize MINOS MCP server", failure);
        }
    }

    @Override
    public IndexStatus indexStatus(String project) {
        String json = call(TOOL_INDEX_STATUS, Map.of("project", requireText(project, "project")));
        try {
            IndexStatusPayload payload = mapper.readValue(json, IndexStatusPayload.class);
            return new IndexStatus(
                    payload.projectId(), payload.projectName(), payload.state(), payload.activeSnapshotId(),
                    payload.providerId(), payload.providerVersion());
        } catch (Exception failure) {
            throw new MinosIntegrationException("invalid MINOS index-status JSON", failure);
        }
    }

    @Override
    public List<Symbol> findSymbols(String project, String query, int limit) {
        requireText(project, "project");
        requireText(query, "query");
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("MINOS symbol limit must be between 1 and 1000");
        }
        String json = call(TOOL_FIND_SYMBOLS, Map.of("project", project, "query", query, "limit", limit));
        try {
            SymbolEnvelope payload = mapper.readValue(json, SymbolEnvelope.class);
            List<SymbolPayload> symbols = payload.symbols() == null ? List.of() : payload.symbols();
            return symbols.stream().map(this::symbol).toList();
        } catch (Exception failure) {
            throw new MinosIntegrationException("invalid MINOS symbol JSON", failure);
        }
    }

    @Override
    public void close() {
        try {
            client.closeGracefully();
        } catch (RuntimeException ignored) {
            // Closing an optional external process must not mask the primary resolution result.
        }
    }

    private Symbol symbol(SymbolPayload payload) {
        OriginPayload origin = payload.origin();
        return new Symbol(
                payload.id(), payload.symbolKey(), payload.projectId(), payload.moduleId(), payload.fileId(),
                payload.kind(), payload.name(), payload.qualifiedName(), payload.signature(), payload.language(),
                payload.resolutionStatus(),
                new Origin(
                        origin == null ? null : origin.providerId(),
                        origin == null ? null : origin.providerVersion(),
                        origin == null ? null : origin.indexRunId()));
    }

    private String call(String toolName, Map<String, Object> arguments) {
        try {
            var result = client.callTool(CallToolRequest.builder(toolName).arguments(arguments).build());
            if (Boolean.TRUE.equals(result.isError())) {
                throw new MinosIntegrationException("MINOS tool failed: " + toolName + ": " + text(result.content()));
            }
            String content = text(result.content());
            if (content.isBlank()) {
                throw new MinosIntegrationException("MINOS tool returned an empty payload: " + toolName);
            }
            return content;
        } catch (MinosIntegrationException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new MinosIntegrationException("MINOS MCP call failed: " + toolName, failure);
        }
    }

    private String text(List<?> content) {
        if (content == null || content.isEmpty() || !(content.getFirst() instanceof TextContent textContent)) {
            throw new MinosIntegrationException("MINOS MCP response does not contain TextContent");
        }
        return textContent.text() == null ? "" : textContent.text();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private record IndexStatusPayload(
            String projectId,
            String projectName,
            String state,
            String activeSnapshotId,
            String lastSuccessfulIndexAt,
            String providerId,
            String providerVersion) {
    }

    private record SymbolEnvelope(int count, List<SymbolPayload> symbols) {
    }

    private record SymbolPayload(
            String id,
            String symbolKey,
            String identityQuality,
            String projectId,
            String moduleId,
            String fileId,
            String kind,
            String name,
            String qualifiedName,
            String signature,
            String language,
            Object location,
            String resolutionStatus,
            OriginPayload origin,
            boolean external,
            boolean generated) {
    }

    private record OriginPayload(
            String providerId,
            String providerType,
            String providerVersion,
            String indexRunId,
            String sourceType) {
    }
}
