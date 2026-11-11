package com.morpheus.integration.nexus;

import com.morpheus.application.context.TechnicalContextBundle;
import com.morpheus.application.context.TechnicalContextItem;
import com.morpheus.application.context.TechnicalContextRequest;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.operability.SuppressedFailureFamily;
import com.morpheus.application.security.ExternalJarIntegrity;
import com.morpheus.transport.mcp.McpPeerLaunch;
import com.morpheus.transport.mcp.McpPeerSession;
import com.morpheus.transport.mcp.PeerOperationDeadline;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Real inter-process gateway to the validated NEXUS MCP STDIO runner. */
public final class NexusMcpContextGateway implements NexusContextGateway {
    public static final String TOOL_LIST_PROJECTS = "list_projects";
    public static final String TOOL_BUILD_CONTEXT = "build_context";
    public static final String TOOL_EXPLAIN_CONTEXT = "explain_context";
    static final int MAX_MCP_RESPONSE_BYTES = 4 * 1024 * 1024;
    /**
     * The requests one gateway sends in sequence, each bounded by the configured timeout:
     * initialize, tools/list, then build_context, explain_context or list_projects.
     * The operation deadline, and through it the MCP server's handler bound, is derived from it (ADR-0106).
     */
    public static final int REQUESTS_PER_OPERATION = 3;
    static final int MAX_PROJECTS = 4_096;
    static final int MAX_CONTEXT_ITEMS = 4_096;
    static final int MAX_EXCLUDED_ITEMS = 4_096;
    static final int MAX_METADATA_ENTRIES = 1_024;
    static final McpPeerSession.Peer PEER = new McpPeerSession.Peer(
            "NEXUS", Set.of(TOOL_LIST_PROJECTS, TOOL_BUILD_CONTEXT, TOOL_EXPLAIN_CONTEXT), MAX_MCP_RESPONSE_BYTES,
            NexusIntegrationException::new);

    private final McpPeerSession session;
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** A gateway to the configured peer; what fails without failing the operation is counted on {@code recorder}. */
    public NexusMcpContextGateway(NexusIntegrationSettings settings, OperationalRecorder recorder) {
        this(launch(Objects.requireNonNull(settings, "settings"), Objects.requireNonNull(recorder, "recorder")),
                recorder);
    }

    NexusMcpContextGateway(
            String command,
            List<String> arguments,
            Map<String, String> environment,
            Duration timeout,
            OperationalRecorder recorder) {
        this(command, arguments, environment, timeout,
                PeerOperationDeadline.of(timeout, REQUESTS_PER_OPERATION), recorder);
    }

    NexusMcpContextGateway(
            String command,
            List<String> arguments,
            Map<String, String> environment,
            Duration timeout,
            Duration operationDeadline,
            OperationalRecorder recorder) {
        this(new McpPeerSession.Launch(new McpPeerLaunch(command, arguments, environment), timeout,
                operationDeadline, Optional.empty()), recorder);
    }

    private NexusMcpContextGateway(McpPeerSession.Launch launch, OperationalRecorder recorder) {
        this.session = McpPeerSession.open(PEER, launch, observers(recorder));
    }

    @Override
    public List<ProjectInfo> listProjects() {
        String json = session.call(TOOL_LIST_PROJECTS, Map.of());
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
        String json = session.call(tool, arguments);
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
        session.close();
    }

    private TechnicalContextItem item(ItemPayload item) {
        return new TechnicalContextItem(
                item.type(), item.path(), item.symbol(), item.startLine(), item.endLine(), item.content(), item.score(),
                item.scoreComponents() == null ? Map.of() : item.scoreComponents(),
                item.reasons() == null ? List.of() : item.reasons(), item.estimatedTokens(), item.truncated());
    }

    static void requireCardinality(int size, int maximum, String label) {
        if (size < 0 || size > maximum) {
            throw new NexusIntegrationException("NEXUS " + label + " exceeds configured limit " + maximum);
        }
    }

    private static McpPeerSession.Launch launch(NexusIntegrationSettings settings, OperationalRecorder recorder) {
        if (!settings.enabled()) {
            throw new NexusIntegrationException("NEXUS integration is not configured: "
                    + settings.configurationError().orElse(settings.state().name()));
        }
        Path jar = settings.jarPath().orElseThrow();
        Optional<Path> staged = Optional.empty();
        try {
            staged = settings.jarSha256().map(pin -> ExternalJarIntegrity.stageVerifiedCopy(jar, pin));
            Path launchJar = staged.orElseThrow();
            List<String> arguments = new ArrayList<>();
            settings.homeDirectory().ifPresent(home -> arguments.add("-Dnexus.home=" + home));
            arguments.addAll(List.of("-jar", launchJar.toString()));
            return new McpPeerSession.Launch(new McpPeerLaunch(settings.javaCommand(), arguments, Map.of()),
                    settings.timeout(), PeerOperationDeadline.of(settings.timeout(), REQUESTS_PER_OPERATION), staged);
        } catch (IllegalArgumentException integrityFailure) {
            observers(recorder).deleteStagedCounting(staged);
            throw new NexusIntegrationException(
                    "NEXUS JAR integrity verification failed immediately before launch", integrityFailure);
        } catch (RuntimeException failure) {
            observers(recorder).deleteStagedCounting(staged);
            throw failure;
        }
    }

    /** What counts the failures the session sets aside, on the recorder the gateway was handed. */
    static McpPeerSession.Observers observers(OperationalRecorder recorder) {
        return new McpPeerSession.Observers(
                recorder.suppressing(SuppressedFailureFamily.MCP_PEER_SHUTDOWN),
                recorder.suppressing(SuppressedFailureFamily.STAGED_ARTIFACT_CLEANUP),
                processTreeFailures(recorder));
    }

    /**
     * What the transport is told when a process of the peer could not be listed or signalled. The transport may
     * depend on no other MORPHEUS package and so cannot count; the gateway that builds it can, under the family whose
     * non-zero value says that a process of the peer may have outlived it.
     */
    static Consumer<Throwable> processTreeFailures(OperationalRecorder recorder) {
        return recorder.suppressing(SuppressedFailureFamily.MCP_PEER_PROCESS_TREE);
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
