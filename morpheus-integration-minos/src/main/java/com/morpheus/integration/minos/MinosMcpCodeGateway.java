package com.morpheus.integration.minos;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Real inter-process MINOS gateway using its validated MCP STDIO server. */
public final class MinosMcpCodeGateway implements MinosCodeGateway {
    public static final String MINOS_SERVER_CLASS = "com.minos.mcp.MinosMcpServer";
    public static final String TOOL_INDEX_STATUS = "minos_index_status";
    public static final String TOOL_FIND_SYMBOLS = "minos_find_symbols";
    static final int MAX_MCP_RESPONSE_BYTES = 4 * 1024 * 1024;
    /**
     * The requests one gateway sends in sequence, each bounded by the configured timeout:
     * initialize, tools/list, minos_index_status, minos_find_symbols.
     * The operation deadline, and through it the MCP server's handler bound, is derived from it (ADR-0106).
     */
    public static final int REQUESTS_PER_OPERATION = 4;
    private static final int MAX_SYMBOLS = 1000;
    static final McpPeerSession.Peer PEER = new McpPeerSession.Peer(
            "MINOS", Set.of(TOOL_INDEX_STATUS, TOOL_FIND_SYMBOLS), MAX_MCP_RESPONSE_BYTES,
            MinosIntegrationException::new);

    private final McpPeerSession session;
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** A gateway to the configured peer; what fails without failing the operation is counted on {@code recorder}. */
    public MinosMcpCodeGateway(MinosIntegrationSettings settings, OperationalRecorder recorder) {
        this(launch(Objects.requireNonNull(settings, "settings"), Objects.requireNonNull(recorder, "recorder")),
                recorder);
    }

    MinosMcpCodeGateway(
            String command,
            List<String> arguments,
            Map<String, String> environment,
            Duration timeout,
            OperationalRecorder recorder) {
        this(command, arguments, environment, timeout,
                PeerOperationDeadline.of(timeout, REQUESTS_PER_OPERATION), recorder);
    }

    MinosMcpCodeGateway(
            String command,
            List<String> arguments,
            Map<String, String> environment,
            Duration timeout,
            Duration operationDeadline,
            OperationalRecorder recorder) {
        this(new McpPeerSession.Launch(new McpPeerLaunch(command, arguments, environment), timeout,
                operationDeadline, Optional.empty()), recorder);
    }

    private MinosMcpCodeGateway(McpPeerSession.Launch launch, OperationalRecorder recorder) {
        this.session = McpPeerSession.open(PEER, launch, observers(recorder));
    }

    @Override
    public IndexStatus indexStatus(String project) {
        String json = session.call(TOOL_INDEX_STATUS, Map.of("project", requireText(project, "project")));
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
    public SymbolSearch findSymbols(String project, String query, int limit) {
        requireText(project, "project");
        requireText(query, "query");
        if (limit < 1 || limit > MAX_SYMBOLS) {
            throw new IllegalArgumentException("MINOS symbol limit must be between 1 and " + MAX_SYMBOLS);
        }
        String json = session.call(TOOL_FIND_SYMBOLS, Map.of("project", project, "query", query, "limit", limit));
        try {
            SymbolEnvelope payload = mapper.readValue(json, SymbolEnvelope.class);
            List<SymbolPayload> symbols = payload.symbols() == null ? List.of() : payload.symbols();
            if (payload.count() < 0 || payload.count() != symbols.size()) {
                throw new MinosIntegrationException("MINOS symbol response count does not match payload size");
            }
            if (symbols.size() > limit || symbols.size() > MAX_SYMBOLS) {
                throw new MinosIntegrationException("MINOS symbol response exceeds requested limit " + limit);
            }
            return new SymbolSearch(symbols.stream().map(this::symbol).toList(), symbols.size() == limit);
        } catch (MinosIntegrationException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new MinosIntegrationException("invalid MINOS symbol JSON", failure);
        }
    }

    @Override
    public void close() {
        session.close();
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

    private static McpPeerSession.Launch launch(MinosIntegrationSettings settings, OperationalRecorder recorder) {
        if (!settings.enabled()) {
            throw new MinosIntegrationException("MINOS integration is not configured: "
                    + settings.configurationError().orElse(settings.state().name()));
        }
        Path jar = settings.jarPath().orElseThrow();
        Optional<Path> staged = Optional.empty();
        try {
            staged = settings.jarSha256().map(pin -> ExternalJarIntegrity.stageVerifiedCopy(jar, pin));
            Path launchJar = staged.orElseThrow();
            List<String> arguments = new ArrayList<>();
            settings.homeDirectory().ifPresent(home -> arguments.add("-Dminos.home=" + home));
            arguments.addAll(List.of("-cp", launchJar.toString(), MINOS_SERVER_CLASS));
            return new McpPeerSession.Launch(new McpPeerLaunch(settings.javaCommand(), arguments, Map.of()),
                    settings.timeout(), PeerOperationDeadline.of(settings.timeout(), REQUESTS_PER_OPERATION), staged);
        } catch (IllegalArgumentException integrityFailure) {
            observers(recorder).deleteStagedCounting(staged);
            throw new MinosIntegrationException(
                    "MINOS JAR integrity verification failed immediately before launch", integrityFailure);
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
