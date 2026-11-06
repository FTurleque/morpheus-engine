package com.morpheus.transport.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * One open client of an external MCP peer, and the one implementation of its life cycle: the bounded client is started,
 * the operation deadline armed, the required tools checked, a tool called with its answer bounded, and on close the
 * client is shut down and the verified copy of the peer's JAR deleted.
 *
 * <p>The gateway of each peer keeps what is its peer's own -- its tools, its payloads, the shape of its launch command
 * and the verification of the copy it is launched from -- and hands this session its {@link Peer}, a {@link Launch} and
 * the {@link Observers} that count. This package may depend on no other MORPHEUS package: a failure is raised in the
 * gateway's own exception type through {@link Peer#failure()}, and what fails without failing the operation is told to
 * an observer the gateway built on its recorder.</p>
 */
public final class McpPeerSession implements AutoCloseable {
    private final Peer peer;
    private final McpSyncClient client;
    private final PeerOperationDeadline deadline;
    private final Optional<Path> stagedJar;
    private final Observers observers;

    private McpPeerSession(
            Peer peer, McpSyncClient client, PeerOperationDeadline deadline, Optional<Path> stagedJar,
            Observers observers) {
        this.peer = peer;
        this.client = client;
        this.deadline = deadline;
        this.stagedJar = stagedJar;
        this.observers = observers;
    }

    /**
     * Starts the peer, initializes it and checks its required tools within the operation deadline. On failure the
     * client is closed and the verified copy deleted, their own failures attached to the primary one.
     */
    public static McpPeerSession open(Peer peer, Launch launch, Observers observers) {
        Objects.requireNonNull(peer, "peer");
        Objects.requireNonNull(launch, "launch");
        Objects.requireNonNull(observers, "observers");
        McpSyncClient started = start(peer, launch, observers);
        // Closing the client fails the pending request at once, so the operation ends at its deadline.
        PeerOperationDeadline armed = PeerOperationDeadline.arm(
                launch.operationDeadline(), observers.countingAbort(started::close));
        Set<String> available;
        try {
            started.initialize();
            available = started.listTools().tools().stream()
                    .map(Tool::name)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException failure) {
            boolean expired = abandon(started, armed, launch.stagedJar(), failure);
            if (expired) throw peer.deadlineExceeded(launch.operationDeadline(), failure);
            throw peer.failure().apply("cannot start or initialize " + peer.name() + " MCP server", failure);
        }
        if (!available.containsAll(peer.requiredTools())) {
            RuntimeException incompatible = peer.failure().apply(
                    peer.name() + " MCP server is incompatible; required tools missing: "
                            + peer.requiredTools().stream().filter(tool -> !available.contains(tool)).sorted().toList(),
                    null);
            boolean expired = abandon(started, armed, launch.stagedJar(), incompatible);
            if (expired) throw peer.deadlineExceeded(launch.operationDeadline(), incompatible);
            throw incompatible;
        }
        return new McpPeerSession(peer, started, armed, launch.stagedJar(), observers);
    }

    /** The text of the one content item {@code toolName} answers with, bounded, neither an error nor blank. */
    public String call(String toolName, Map<String, Object> arguments) {
        CallToolResult result;
        try {
            result = client.callTool(CallToolRequest.builder(toolName).arguments(arguments).build());
        } catch (RuntimeException failure) {
            if (deadline.expired()) throw peer.deadlineExceeded(deadline.deadline(), failure);
            throw peer.failure().apply(peer.name() + " MCP call failed: " + toolName, failure);
        }
        String content = peer.requireBoundedResponse(peer.text(result.content()), toolName);
        if (Boolean.TRUE.equals(result.isError())) {
            throw peer.failure().apply(peer.name() + " tool failed: " + toolName + ": " + content, null);
        }
        if (content.isBlank()) {
            throw peer.failure().apply(peer.name() + " tool returned an empty payload: " + toolName, null);
        }
        return content;
    }

    @Override
    public void close() {
        deadline.close();
        try {
            client.closeGracefully();
        } catch (RuntimeException shutdownFailure) {
            // Closing an optional external process must not mask the primary result.
            observers.shutdownFailures().accept(shutdownFailure);
        } finally {
            observers.deleteStagedCounting(stagedJar);
        }
    }

    private static McpSyncClient start(Peer peer, Launch launch, Observers observers) {
        try {
            BoundedStdioClientTransport transport = new BoundedStdioClientTransport(
                    launch.peer(), McpJsonDefaults.getMapper(), peer.maxResponseBytes(),
                    observers.processTreeFailures());
            return McpClient.sync(transport).requestTimeout(launch.requestTimeout()).build();
        } catch (RuntimeException failure) {
            deleteStagedSuppressing(launch.stagedJar(), failure);
            throw peer.failure().apply("cannot start or initialize " + peer.name() + " MCP server", failure);
        }
    }

    /** Stops the clock, closes the client and deletes the copy; answers whether the deadline had passed. */
    private static boolean abandon(
            McpSyncClient started, PeerOperationDeadline armed, Optional<Path> stagedJar, RuntimeException failure) {
        boolean expired = armed.expired();
        armed.close();
        try {
            started.closeGracefully();
        } catch (RuntimeException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
        deleteStagedSuppressing(stagedJar, failure);
        return expired;
    }

    private static void deleteStagedSuppressing(Optional<Path> staged, Throwable primary) {
        if (staged.isEmpty()) return;
        try {
            Files.deleteIfExists(staged.orElseThrow());
        } catch (IOException deleteFailure) {
            primary.addSuppressed(deleteFailure);
        }
    }

    /**
     * What a gateway says of its peer: the name its messages carry, the tools it requires, the bound of one answer and
     * how a failure is raised in its own exception type (message, then a cause that may be {@code null}).
     */
    public record Peer(
            String name,
            Set<String> requiredTools,
            int maxResponseBytes,
            BiFunction<String, Throwable, ? extends RuntimeException> failure) {
        public Peer {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) throw new IllegalArgumentException("peer name must not be blank");
            requiredTools = Set.copyOf(Objects.requireNonNull(requiredTools, "requiredTools"));
            if (maxResponseBytes < 1) throw new IllegalArgumentException("peer response bound must be positive");
            Objects.requireNonNull(failure, "failure");
        }

        /** Refuses an answer longer than the bound, in characters or in UTF-8 bytes, before it is parsed. */
        public String requireBoundedResponse(String content, String toolName) {
            Objects.requireNonNull(content, "content");
            if (content.length() > maxResponseBytes
                    || content.getBytes(StandardCharsets.UTF_8).length > maxResponseBytes) {
                throw failure.apply(
                        name + " MCP response exceeds " + maxResponseBytes + " bytes: " + toolName, null);
            }
            return content;
        }

        private String text(List<?> content) {
            if (content == null || content.size() != 1 || !(content.getFirst() instanceof TextContent textContent)) {
                throw failure.apply(name + " MCP response must contain exactly one TextContent item", null);
            }
            return textContent.text() == null ? "" : textContent.text();
        }

        private RuntimeException deadlineExceeded(Duration operationDeadline, RuntimeException cause) {
            return failure.apply(
                    name + " MCP operation exceeded its " + operationDeadline.toMillis() + " ms deadline", cause);
        }
    }

    /**
     * How one peer is launched: the process MORPHEUS configured, the timeout of each request, the deadline of the
     * whole operation, and the verified copy of the peer's JAR it runs from, deleted when the session ends.
     */
    public record Launch(
            McpPeerLaunch peer, Duration requestTimeout, Duration operationDeadline, Optional<Path> stagedJar) {
        public Launch {
            Objects.requireNonNull(peer, "peer");
            peer = new McpPeerLaunch(peer.command().trim(), peer.arguments(), peer.explicitEnvironment());
            Objects.requireNonNull(requestTimeout, "requestTimeout");
            Objects.requireNonNull(operationDeadline, "operationDeadline");
            stagedJar = Objects.requireNonNull(stagedJar, "stagedJar").map(path -> path.toAbsolutePath().normalize());
        }
    }

    /**
     * What counts, built by the gateway on its recorder: an abort or a shutdown of the peer that failed, a verified
     * copy left on disk, and a process of the peer the transport could not list or signal.
     */
    public record Observers(
            Consumer<Throwable> shutdownFailures,
            Consumer<Throwable> stagedCopyResidues,
            Consumer<Throwable> processTreeFailures) {
        public Observers {
            Objects.requireNonNull(shutdownFailures, "shutdownFailures");
            Objects.requireNonNull(stagedCopyResidues, "stagedCopyResidues");
            Objects.requireNonNull(processTreeFailures, "processTreeFailures");
        }

        /** The abort of an operation that passed its deadline, counting its own failure before letting it go on. */
        public Runnable countingAbort(Runnable abort) {
            Objects.requireNonNull(abort, "abort");
            return () -> {
                try {
                    abort.run();
                } catch (RuntimeException abortFailure) {
                    shutdownFailures.accept(abortFailure);
                    throw abortFailure;
                }
            };
        }

        /**
         * Deletes the verified copy once the peer has released it. A copy that cannot be deleted stays on disk;
         * nothing can be done about it here, and it is counted so that the residue is not discovered by its size.
         */
        public void deleteStagedCounting(Optional<Path> staged) {
            if (staged.isEmpty()) return;
            try {
                Files.deleteIfExists(staged.orElseThrow());
            } catch (IOException residue) {
                stagedCopyResidues.accept(residue);
            }
        }
    }
}
