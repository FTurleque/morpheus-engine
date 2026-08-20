package com.morpheus.integration.minos;

import com.morpheus.application.security.ExternalJarIntegrity;
import com.morpheus.integration.mcp.BoundedStdioClientTransport;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Real inter-process MINOS gateway using its validated MCP STDIO server. */
public final class MinosMcpCodeGateway implements MinosCodeGateway {
    public static final String MINOS_SERVER_CLASS = "com.minos.mcp.MinosMcpServer";
    public static final String TOOL_INDEX_STATUS = "minos_index_status";
    public static final String TOOL_FIND_SYMBOLS = "minos_find_symbols";
    static final int MAX_MCP_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_SYMBOLS = 1000;
    private static final Set<String> REQUIRED_TOOLS = Set.of(TOOL_INDEX_STATUS, TOOL_FIND_SYMBOLS);

    private final McpSyncClient client;
    private final Optional<Path> stagedJar;
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public MinosMcpCodeGateway(MinosIntegrationSettings settings) {
        this(launch(Objects.requireNonNull(settings, "settings")));
    }

    MinosMcpCodeGateway(
            String command,
            List<String> arguments,
            Map<String, String> environment,
            Duration timeout) {
        this(new Launch(command, arguments, environment, timeout, Optional.empty()));
    }

    private MinosMcpCodeGateway(Launch launch) {
        this.stagedJar = launch.stagedJar();
        McpSyncClient started = null;
        try {
            var parameters = ServerParameters.builder(launch.command())
                    .args(launch.arguments().toArray(String[]::new));
            if (!launch.environment().isEmpty()) {
                parameters.env(launch.environment());
            }
            BoundedStdioClientTransport transport = new BoundedStdioClientTransport(
                    parameters.build(), McpJsonDefaults.getMapper(), MAX_MCP_RESPONSE_BYTES);
            started = McpClient.sync(transport)
                    .requestTimeout(launch.timeout())
                    .build();
            started.initialize();
            Set<String> available = started.listTools().tools().stream()
                    .map(tool -> tool.name())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (!available.containsAll(REQUIRED_TOOLS)) {
                throw new MinosIntegrationException(
                        "MINOS MCP server is incompatible; required tools missing: "
                                + REQUIRED_TOOLS.stream().filter(tool -> !available.contains(tool)).sorted().toList());
            }
            this.client = started;
        } catch (RuntimeException failure) {
            closeStartedSuppressing(started, failure);
            deleteStagedSuppressing(stagedJar, failure);
            if (failure instanceof MinosIntegrationException integrationFailure) throw integrationFailure;
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
        if (limit < 1 || limit > MAX_SYMBOLS) {
            throw new IllegalArgumentException("MINOS symbol limit must be between 1 and " + MAX_SYMBOLS);
        }
        String json = call(TOOL_FIND_SYMBOLS, Map.of("project", project, "query", query, "limit", limit));
        try {
            SymbolEnvelope payload = mapper.readValue(json, SymbolEnvelope.class);
            List<SymbolPayload> symbols = payload.symbols() == null ? List.of() : payload.symbols();
            if (payload.count() < 0 || payload.count() != symbols.size()) {
                throw new MinosIntegrationException("MINOS symbol response count does not match payload size");
            }
            if (symbols.size() > limit || symbols.size() > MAX_SYMBOLS) {
                throw new MinosIntegrationException("MINOS symbol response exceeds requested limit " + limit);
            }
            return symbols.stream().map(this::symbol).toList();
        } catch (MinosIntegrationException failure) {
            throw failure;
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
        } finally {
            deleteStagedQuietly(stagedJar);
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
            String content = requireBoundedResponse(text(result.content()), toolName);
            if (Boolean.TRUE.equals(result.isError())) {
                throw new MinosIntegrationException("MINOS tool failed: " + toolName + ": " + content);
            }
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

    static String requireBoundedResponse(String content, String toolName) {
        Objects.requireNonNull(content, "content");
        if (content.length() > MAX_MCP_RESPONSE_BYTES
                || content.getBytes(StandardCharsets.UTF_8).length > MAX_MCP_RESPONSE_BYTES) {
            throw new MinosIntegrationException(
                    "MINOS MCP response exceeds " + MAX_MCP_RESPONSE_BYTES + " bytes: " + toolName);
        }
        return content;
    }

    private String text(List<?> content) {
        if (content == null || content.size() != 1 || !(content.getFirst() instanceof TextContent textContent)) {
            throw new MinosIntegrationException("MINOS MCP response must contain exactly one TextContent item");
        }
        return textContent.text() == null ? "" : textContent.text();
    }

    private static Launch launch(MinosIntegrationSettings settings) {
        if (!settings.enabled()) {
            throw new MinosIntegrationException("MINOS integration is not configured: "
                    + settings.configurationError().orElse(settings.state().name()));
        }
        Path jar = settings.jarPath().orElseThrow();
        Optional<Path> staged = Optional.empty();
        try {
            staged = settings.jarSha256().map(pin -> ExternalJarIntegrity.stageVerifiedCopy(jar, pin));
            Path launchJar = staged.orElse(jar);
            List<String> arguments = new ArrayList<>();
            settings.homeDirectory().ifPresent(home -> arguments.add("-Dminos.home=" + home));
            arguments.addAll(List.of("-cp", launchJar.toString(), MINOS_SERVER_CLASS));
            return new Launch(settings.javaCommand(), arguments, Map.of(), settings.timeout(), staged);
        } catch (IllegalArgumentException integrityFailure) {
            deleteStagedQuietly(staged);
            throw new MinosIntegrationException(
                    "MINOS JAR integrity verification failed immediately before launch", integrityFailure);
        } catch (RuntimeException failure) {
            deleteStagedQuietly(staged);
            throw failure;
        }
    }

    private static void closeStartedSuppressing(McpSyncClient started, Throwable primary) {
        if (started == null) return;
        try {
            started.closeGracefully();
        } catch (RuntimeException closeFailure) {
            primary.addSuppressed(closeFailure);
        }
    }

    private static void deleteStagedSuppressing(Optional<Path> staged, Throwable primary) {
        if (staged.isEmpty()) return;
        try {
            Files.deleteIfExists(staged.orElseThrow());
        } catch (IOException deleteFailure) {
            primary.addSuppressed(deleteFailure);
        }
    }

    private static void deleteStagedQuietly(Optional<Path> staged) {
        if (staged.isEmpty()) return;
        try {
            Files.deleteIfExists(staged.orElseThrow());
        } catch (IOException ignored) {
            // Best effort after the external process/classpath has been released.
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private record Launch(
            String command,
            List<String> arguments,
            Map<String, String> environment,
            Duration timeout,
            Optional<Path> stagedJar) {
        private Launch {
            command = requireText(command, "command");
            arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
            environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
            Objects.requireNonNull(timeout, "timeout");
            stagedJar = Objects.requireNonNull(stagedJar, "stagedJar")
                    .map(path -> path.toAbsolutePath().normalize());
        }
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
