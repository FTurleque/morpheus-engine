package com.morpheus.integration.nexus;

import com.morpheus.application.context.TechnicalContextBundle;
import com.morpheus.application.context.TechnicalContextItem;
import com.morpheus.application.context.TechnicalContextRequest;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Real inter-process gateway to the validated NEXUS MCP STDIO runner. */
public final class NexusMcpContextGateway implements NexusContextGateway {
    public static final String TOOL_LIST_PROJECTS = "list_projects";
    public static final String TOOL_BUILD_CONTEXT = "build_context";
    public static final String TOOL_EXPLAIN_CONTEXT = "explain_context";
    static final int MAX_MCP_RESPONSE_BYTES = 4 * 1024 * 1024;
    static final int MAX_PROJECTS = 4_096;
    static final int MAX_CONTEXT_ITEMS = 4_096;
    static final int MAX_EXCLUDED_ITEMS = 4_096;
    static final int MAX_METADATA_ENTRIES = 1_024;
    private static final Set<String> REQUIRED_TOOLS = Set.of(
            TOOL_LIST_PROJECTS, TOOL_BUILD_CONTEXT, TOOL_EXPLAIN_CONTEXT);

    private final McpSyncClient client;
    private final Optional<Path> stagedJar;
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public NexusMcpContextGateway(NexusIntegrationSettings settings) {
        this(launch(Objects.requireNonNull(settings, "settings")));
    }

    NexusMcpContextGateway(String command, List<String> arguments, Map<String, String> environment, Duration timeout) {
        this(new Launch(command, arguments, environment, timeout, Optional.empty()));
    }

    private NexusMcpContextGateway(Launch launch) {
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
            started = McpClient.sync(transport).requestTimeout(launch.timeout()).build();
            started.initialize();
            Set<String> available = started.listTools().tools().stream()
                    .map(tool -> tool.name())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (!available.containsAll(REQUIRED_TOOLS)) {
                throw new NexusIntegrationException(
                        "NEXUS MCP server is incompatible; required tools missing: "
                                + REQUIRED_TOOLS.stream().filter(tool -> !available.contains(tool)).sorted().toList());
            }
            this.client = started;
        } catch (RuntimeException failure) {
            closeStartedSuppressing(started, failure);
            deleteStagedSuppressing(stagedJar, failure);
            if (failure instanceof NexusIntegrationException integrationFailure) throw integrationFailure;
            throw new NexusIntegrationException("cannot start or initialize NEXUS MCP server", failure);
        }
    }

    @Override
    public List<ProjectInfo> listProjects() {
        String json = call(TOOL_LIST_PROJECTS, Map.of());
        try {
            ProjectPayload[] payload = mapper.readValue(json, ProjectPayload[].class);
            if (payload == null) {
                return List.of();
            }
            requireCardinality(payload.length, MAX_PROJECTS, "project list");
            return java.util.Arrays.stream(payload)
                    .map(project -> new ProjectInfo(project.id(), project.name(), project.indexStatus()))
                    .toList();
        } catch (NexusIntegrationException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new NexusIntegrationException("invalid NEXUS projects JSON", failure);
        }
    }

    @Override
    public TechnicalContextBundle buildContext(TechnicalContextRequest request) {
        Objects.requireNonNull(request, "request");
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("project", request.options().externalProject());
        arguments.put("query", request.query());
        arguments.put("tokenBudget", request.options().tokenBudget());
        if (!request.options().requestedSources().isEmpty()) {
            arguments.put("requestedSources", request.options().requestedSources().stream().sorted().toList());
        }
        if (!request.options().constraints().isEmpty()) {
            arguments.put("constraints", request.options().constraints());
        }
        String tool = request.options().explain() ? TOOL_EXPLAIN_CONTEXT : TOOL_BUILD_CONTEXT;
        String json = call(tool, arguments);
        try {
            ContextPayload payload = mapper.readValue(json, ContextPayload.class);
            ProjectPayload project = Objects.requireNonNull(payload.project(), "NEXUS context project");
            List<ItemPayload> items = payload.items() == null ? List.of() : payload.items();
            List<String> excluded = payload.excluded() == null ? List.of() : payload.excluded();
            Map<String, Object> metadata = payload.metadata() == null ? Map.of() : payload.metadata();
            requireCardinality(items.size(), MAX_CONTEXT_ITEMS, "context items");
            requireCardinality(excluded.size(), MAX_EXCLUDED_ITEMS, "excluded items");
            requireCardinality(metadata.size(), MAX_METADATA_ENTRIES, "metadata entries");
            return new TechnicalContextBundle(
                    project.id(),
                    project.name(),
                    payload.query(),
                    payload.explain(),
                    payload.durationMs(),
                    payload.tokenBudget(),
                    payload.estimatedTokens(),
                    items.stream().map(this::item).toList(),
                    excluded,
                    metadata);
        } catch (NexusIntegrationException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new NexusIntegrationException("invalid NEXUS context JSON", failure);
        }
    }

    @Override
    public void close() {
        try {
            client.closeGracefully();
        } catch (RuntimeException ignored) {
            // Optional external process cleanup must not mask the primary result.
        } finally {
            deleteStagedQuietly(stagedJar);
        }
    }

    private TechnicalContextItem item(ItemPayload item) {
        return new TechnicalContextItem(
                item.type(), item.path(), item.symbol(), item.startLine(), item.endLine(), item.content(), item.score(),
                item.scoreComponents() == null ? Map.of() : item.scoreComponents(),
                item.reasons() == null ? List.of() : item.reasons(), item.estimatedTokens(), item.truncated());
    }

    private String call(String toolName, Map<String, Object> arguments) {
        try {
            var result = client.callTool(CallToolRequest.builder(toolName).arguments(arguments).build());
            String content = requireBoundedResponse(text(result.content()), toolName);
            if (Boolean.TRUE.equals(result.isError())) {
                throw new NexusIntegrationException("NEXUS tool failed: " + toolName + ": " + content);
            }
            if (content.isBlank()) {
                throw new NexusIntegrationException("NEXUS tool returned an empty payload: " + toolName);
            }
            return content;
        } catch (NexusIntegrationException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new NexusIntegrationException("NEXUS MCP call failed: " + toolName, failure);
        }
    }

    static String requireBoundedResponse(String content, String toolName) {
        Objects.requireNonNull(content, "content");
        if (content.length() > MAX_MCP_RESPONSE_BYTES
                || content.getBytes(StandardCharsets.UTF_8).length > MAX_MCP_RESPONSE_BYTES) {
            throw new NexusIntegrationException(
                    "NEXUS MCP response exceeds " + MAX_MCP_RESPONSE_BYTES + " bytes: " + toolName);
        }
        return content;
    }

    static void requireCardinality(int size, int maximum, String label) {
        if (size < 0 || size > maximum) {
            throw new NexusIntegrationException("NEXUS " + label + " exceeds configured limit " + maximum);
        }
    }

    private String text(List<?> content) {
        if (content == null || content.size() != 1 || !(content.getFirst() instanceof TextContent textContent)) {
            throw new NexusIntegrationException("NEXUS MCP response must contain exactly one TextContent item");
        }
        return textContent.text() == null ? "" : textContent.text();
    }

    private static Launch launch(NexusIntegrationSettings settings) {
        if (!settings.enabled()) {
            throw new NexusIntegrationException("NEXUS integration is not configured: "
                    + settings.configurationError().orElse(settings.state().name()));
        }
        Path jar = settings.jarPath().orElseThrow();
        Optional<Path> staged = Optional.empty();
        try {
            staged = settings.jarSha256().map(pin -> ExternalJarIntegrity.stageVerifiedCopy(jar, pin));
            Path launchJar = staged.orElse(jar);
            List<String> arguments = new ArrayList<>();
            settings.homeDirectory().ifPresent(home -> arguments.add("-Dnexus.home=" + home));
            arguments.addAll(List.of("-jar", launchJar.toString()));
            return new Launch(settings.javaCommand(), arguments, Map.of(), settings.timeout(), staged);
        } catch (IllegalArgumentException integrityFailure) {
            deleteStagedQuietly(staged);
            throw new NexusIntegrationException(
                    "NEXUS JAR integrity verification failed immediately before launch", integrityFailure);
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

    private record ProjectPayload(
            String id,
            String name,
            String rootPath,
            String sourceType,
            List<String> languages,
            List<String> technologies,
            String lastIndexedAt,
            String indexStatus) {
    }

    private record ContextPayload(
            ProjectPayload project,
            String query,
            boolean explain,
            long durationMs,
            int tokenBudget,
            int estimatedTokens,
            List<ItemPayload> items,
            List<String> excluded,
            Map<String, Object> metadata) {
    }

    private record ItemPayload(
            String type,
            String path,
            String symbol,
            int startLine,
            int endLine,
            String content,
            double score,
            Map<String, Double> scoreComponents,
            List<String> reasons,
            int estimatedTokens,
            boolean truncated) {
    }
}
