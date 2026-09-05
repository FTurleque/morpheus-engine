package com.morpheus.integration.mcp;

import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * MCP STDIO client transport with hard per-frame and aggregate queue bounds.
 *
 * <p>The upstream Java SDK 2.0.1 STDIO transport can materialize unbounded lines and uses unbounded Reactor queues.
 * MORPHEUS reads process streams as bounded UTF-8 bytes before JSON parsing, caps pending inbound/outbound messages,
 * minimizes inherited child-process environment, retains observed descendants for deterministic cleanup, handles stderr
 * synchronously on its reader thread, and fails closed when a peer exceeds a resource budget.</p>
 *
 * <p>The child-process boundary is lifecycle and environment isolation, not an operating-system security sandbox. An
 * explicitly configured MCP peer still runs as the MORPHEUS operating-system account and must therefore be trusted for
 * filesystem and network access.</p>
 */
public final class BoundedStdioClientTransport implements McpClientTransport {
    public static final int DEFAULT_MAX_PENDING_MESSAGES = 64;

    private static final System.Logger LOGGER = System.getLogger(BoundedStdioClientTransport.class.getName());
    private static final Set<Integer> NORMAL_EXIT_CODES = Set.of(0, 130, 141, 143);
    private static final Duration PROCESS_SHUTDOWN_GRACE = Duration.ofSeconds(2);
    private static final Duration PROCESS_SHUTDOWN_FORCE = Duration.ofSeconds(2);
    private static final long PROCESS_OBSERVATION_POLL_MILLIS = 10L;
    private static final Set<String> SAFE_ENVIRONMENT_KEYS = Set.of(
            "SYSTEMROOT",
            "WINDIR",
            "PATH",
            "PATHEXT",
            "COMSPEC",
            "TEMP",
            "TMP",
            "TMPDIR",
            "LANG",
            "LC_ALL",
            "LC_CTYPE");

    /**
     * Lifecycle of one transport instance, and of exactly one peer process.
     *
     * <p>A transport used to be told it was connected by a field that only recorded the last process started, so
     * two subscriptions to {@code connect()} -- sequential or concurrent -- each started a peer and the second
     * overwrote the reference to the first. That is how a peer becomes unreachable while still running under the
     * MORPHEUS account: nothing left in the process could name it in order to stop it. This state is what makes a
     * second start impossible rather than merely unlikely.</p>
     *
     * <p>{@code CLOSING} is the teardown ticket: whichever of graceful close and fail-closed claims it owns the
     * cleanup, and the other waits for that cleanup instead of repeating it. {@code FAILED} and {@code CLOSED} are
     * both terminal and both mean the peer and the schedulers are gone; they differ only in what ended the
     * transport.</p>
     */
    enum State {
        NEW,
        CONNECTING,
        CONNECTED,
        CLOSING,
        CLOSED,
        FAILED
    }

    private final Sinks.Many<JSONRPCMessage> inboundSink;
    private final Sinks.Many<OutboundFrame> outboundSink;
    private final ServerParameters parameters;
    private final McpJsonMapper jsonMapper;
    private final int maxMessageBytes;
    private final int maxPendingMessages;
    private final Semaphore inboundCapacity;
    private final Scheduler inboundScheduler;
    private final Scheduler outboundScheduler;
    private final Scheduler errorScheduler;
    private final Scheduler lifecycleScheduler;
    private final Map<Long, ProcessHandle> observedProcesses = new ConcurrentHashMap<>();

    private final AtomicReference<Process> process = new AtomicReference<>();
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    private final Object lifecycleLock = new Object();
    private Consumer<String> stdErrorHandler = error -> LOGGER.log(
            System.Logger.Level.INFO, "MCP STDERR: {0}", McpDiagnosticRedactor.redact(error));

    public BoundedStdioClientTransport(
            ServerParameters parameters,
            McpJsonMapper jsonMapper,
            int maxInboundMessageBytes) {
        this(parameters, jsonMapper, maxInboundMessageBytes, DEFAULT_MAX_PENDING_MESSAGES);
    }

    public BoundedStdioClientTransport(
            ServerParameters parameters,
            McpJsonMapper jsonMapper,
            int maxMessageBytes,
            int maxPendingMessages) {
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        if (maxMessageBytes < 1) throw new IllegalArgumentException("maxMessageBytes must be positive");
        if (maxPendingMessages < 1) throw new IllegalArgumentException("maxPendingMessages must be positive");
        this.maxMessageBytes = maxMessageBytes;
        this.maxPendingMessages = maxPendingMessages;
        this.inboundCapacity = new Semaphore(maxPendingMessages);
        this.inboundSink = Sinks.many().unicast()
                .onBackpressureBuffer(new ArrayBlockingQueue<JSONRPCMessage>(maxPendingMessages));
        this.outboundSink = Sinks.many().unicast()
                .onBackpressureBuffer(new ArrayBlockingQueue<OutboundFrame>(maxPendingMessages));
        this.inboundScheduler = transportScheduler("morpheus-mcp-inbound");
        this.outboundScheduler = transportScheduler("morpheus-mcp-outbound");
        this.errorScheduler = transportScheduler("morpheus-mcp-stderr");
        this.lifecycleScheduler = transportScheduler("morpheus-mcp-lifecycle");
    }

    /**
     * One named thread per transport role.
     *
     * <p>The scheduler already carried these names, but its worker did not, so a stuck or leaked MCP transport
     * showed up in a thread dump as an anonymous pool thread. Naming the thread is what makes the four threads a
     * transport owns identifiable -- to an operator reading a dump, and to a test asserting they really went
     * away.</p>
     */
    private static Scheduler transportScheduler(String role) {
        return Schedulers.fromExecutorService(
                Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, role)), role);
    }

    public void setStdErrorHandler(Consumer<String> handler) {
        Consumer<String> downstream = Objects.requireNonNull(handler, "handler");
        this.stdErrorHandler = error -> downstream.accept(McpDiagnosticRedactor.redact(error));
    }

    /**
     * Starts the one peer this transport will ever own.
     *
     * <p>The lifecycle claim happens before the handler is wired and before anything is spawned, so a second
     * subscription -- another caller, a retry, a {@code Mono} subscribed twice -- is refused while the peer of the
     * first one is still the only process that exists. A transport that has been closed or has failed is refused
     * on the same check: it has no way back to a running peer, and pretending otherwise would start a process
     * whose teardown has already run.</p>
     */
    @Override
    public Mono<Void> connect(Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler) {
        Objects.requireNonNull(handler, "handler");
        return Mono.<Void>fromRunnable(() -> {
            if (!state.compareAndSet(State.NEW, State.CONNECTING)) {
                throw new IllegalStateException(
                        "MCP transport cannot connect twice; current state is " + state.get());
            }
            handleIncomingMessages(handler::apply);
            Process started = startPeer();
            if (started.getInputStream() == null || started.getOutputStream() == null) {
                IllegalStateException unusable =
                        new IllegalStateException("MCP process input or output stream is unavailable");
                failClosed(unusable);
                throw unusable;
            }

            startLifecycleObservation();
            startInboundProcessing();
            startOutboundProcessing();
            startErrorProcessing();
            // A close that arrived during startup already owns the teardown; leaving CONNECTING behind would
            // publish a CONNECTED transport whose peer has just been destroyed.
            state.compareAndSet(State.CONNECTING, State.CONNECTED);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Spawns the peer and publishes it, under the same lock teardown takes.
     *
     * <p>Publishing the handle is what makes the peer reachable for termination, and it cannot be allowed to
     * happen after a concurrent close has already looked. Holding the lock across both makes the outcome one of
     * two: the close sees a peer and stops it, or the close precedes the spawn and the spawn does not happen.</p>
     */
    private Process startPeer() {
        List<String> command = new ArrayList<>();
        command.add(parameters.getCommand());
        command.addAll(parameters.getArgs());
        ProcessBuilder builder = new ProcessBuilder(command);
        sanitizeEnvironment(builder.environment(), parameters.getEnv());
        synchronized (lifecycleLock) {
            if (state.get() != State.CONNECTING) {
                throw new IllegalStateException("MCP transport was closed while connecting");
            }
            try {
                Process started = builder.start();
                process.set(started);
                observeTree(started.toHandle());
                return started;
            } catch (IOException failure) {
                IllegalStateException unstarted = new IllegalStateException("failed to start MCP process", failure);
                failClosed(unstarted);
                throw unstarted;
            }
        }
    }

    /**
     * Keeps only process-launch essentials from the MORPHEUS environment, then overlays explicitly configured peer
     * variables. Explicit variables are trusted integration configuration and therefore may intentionally use keys that
     * are not part of the inherited allowlist.
     */
    static void sanitizeEnvironment(Map<String, String> environment, Map<String, String> explicitEnvironment) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(explicitEnvironment, "explicitEnvironment");
        Map<String, String> inherited = new LinkedHashMap<>(environment);
        environment.clear();
        for (Map.Entry<String, String> entry : inherited.entrySet()) {
            if (SAFE_ENVIRONMENT_KEYS.contains(entry.getKey().toUpperCase(Locale.ROOT))) {
                environment.put(entry.getKey(), entry.getValue());
            }
        }
        environment.putAll(explicitEnvironment);
    }

    @Override
    public Mono<Void> sendMessage(JSONRPCMessage message) {
        Objects.requireNonNull(message, "message");
        return Mono.defer(() -> {
            if (isClosing()) return Mono.error(new IllegalStateException("MCP transport is closing"));
            final OutboundFrame frame;
            try {
                frame = encode(message);
            } catch (IOException failure) {
                failClosed(failure);
                return Mono.error(failure);
            }
            if (outboundSink.tryEmitNext(frame).isSuccess()) return Mono.empty();
            IllegalStateException overflow = new IllegalStateException("MCP outbound queue capacity exceeded");
            failClosed(overflow);
            return Mono.error(overflow);
        });
    }

    /**
     * Stops the peer and this transport's threads, exactly once.
     *
     * <p>Close is idempotent and concurrent-safe in the only sense that is useful to a caller: a second close
     * does not repeat the teardown, and it does not return until the first one has finished. Waiting is the point
     * -- a caller that is told the transport is closed while its peer is still being destroyed has been told
     * something that is not yet true.</p>
     */
    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> {
                    synchronized (lifecycleLock) {
                        if (!claimTeardown()) return;
                        inboundSink.tryEmitComplete();
                        outboundSink.tryEmitComplete();
                        // Stopping the peer walks its process tree and reads its exit status, both of which can
                        // fail. The four schedulers are this transport's own threads, one set per configured
                        // peer, and they were disposed after that walk -- so a failure there left them running
                        // for the rest of the process. Disposal is driven by leaving the block, not by it
                        // succeeding, and so is reaching the terminal state.
                        try {
                            shutdownProcess();
                        } finally {
                            disposeSchedulers();
                            state.set(State.CLOSED);
                        }
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
        return jsonMapper.convertValue(data, typeRef);
    }

    private void handleIncomingMessages(UnaryOperator<Mono<JSONRPCMessage>> handler) {
        inboundSink.asFlux()
                .flatMap(
                        message -> Mono.defer(() -> Mono.just(message).transform(handler))
                                .doFinally(ignored -> inboundCapacity.release()),
                        maxPendingMessages)
                .subscribe(
                        ignored -> { },
                        failure -> {
                            if (!isClosing()) failClosed(failure);
                        });
    }

    private void startInboundProcessing() {
        inboundScheduler.schedule(() -> {
            try (InputStream input = new BufferedInputStream(process.get().getInputStream())) {
                String line;
                while (!isClosing() && (line = readUtf8LineBounded(input, maxMessageBytes)) != null) {
                    if (!processInboundLine(line)) return;
                }
                if (!isClosing()) inboundSink.tryEmitComplete();
            } catch (MessageTooLargeException oversized) {
                failClosed(oversized);
            } catch (Exception failure) {
                if (!isClosing()) failClosed(failure);
            }
        });
    }

    /** Returns {@code false} when a transport bound was exceeded and the caller must stop reading. */
    private boolean processInboundLine(String line) throws Exception {
        JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(jsonMapper, line);
        if (!inboundCapacity.tryAcquire()) {
            failClosed(new IllegalStateException("MCP inbound pending-message capacity exceeded"));
            return false;
        }
        if (!inboundSink.tryEmitNext(message).isSuccess()) {
            inboundCapacity.release();
            if (!isClosing()) failClosed(new IllegalStateException("MCP inbound queue capacity exceeded"));
            return false;
        }
        return true;
    }

    private void startErrorProcessing() {
        errorScheduler.schedule(() -> {
            try (InputStream input = new BufferedInputStream(process.get().getErrorStream())) {
                String line;
                while (!isClosing() && (line = readUtf8LineBounded(input, maxMessageBytes)) != null) {
                    handleErrorLine(line);
                }
            } catch (MessageTooLargeException oversized) {
                if (!isClosing()) {
                    LOGGER.log(System.Logger.Level.WARNING, "MCP stderr frame exceeded configured byte limit");
                    failClosed(oversized);
                }
            } catch (Exception failure) {
                if (!isClosing()) failClosed(failure);
            }
        });
    }

    private void handleErrorLine(String line) {
        try {
            stdErrorHandler.accept(line);
        } catch (RuntimeException handlerFailure) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "MCP stderr handler failed: {0}",
                    McpDiagnosticRedactor.describe(handlerFailure));
        }
    }

    private void startOutboundProcessing() {
        handleOutbound(frames -> frames
                .publishOn(outboundScheduler)
                .handle((frame, sink) -> {
                    if (frame == null || isClosing()) return;
                    try {
                        var output = process.get().getOutputStream();
                        synchronized (output) {
                            output.write(frame.encoded());
                            output.write('\n');
                            output.flush();
                        }
                        sink.next(frame);
                    } catch (IOException failure) {
                        sink.error(new IllegalStateException("failed to write MCP message", failure));
                    }
                }));
    }

    private void startLifecycleObservation() {
        Process current = process.get();
        if (current == null) return;
        ProcessHandle root = current.toHandle();
        lifecycleScheduler.schedule(() -> {
            try {
                while (!isClosing() && root.isAlive()) {
                    observeTree(root);
                    TimeUnit.MILLISECONDS.sleep(PROCESS_OBSERVATION_POLL_MILLIS);
                }
                observeTree(root);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                observeTree(root);
            }
        });
    }

    private void handleOutbound(Function<Flux<OutboundFrame>, Flux<OutboundFrame>> consumer) {
        consumer.apply(outboundSink.asFlux())
                .doOnError(failure -> {
                    if (!isClosing()) failClosed(failure);
                })
                .subscribe(
                        ignored -> { },
                        failure -> LOGGER.log(
                                System.Logger.Level.WARNING,
                                "MCP outbound processing failed: {0}",
                                McpDiagnosticRedactor.describe(failure)));
    }

    private OutboundFrame encode(JSONRPCMessage message) throws IOException {
        String json = jsonMapper.writeValueAsString(message)
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
        byte[] encoded = json.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maxMessageBytes) throw new MessageTooLargeException(maxMessageBytes);
        return new OutboundFrame(encoded);
    }

    private void failClosed(Throwable failure) {
        synchronized (lifecycleLock) {
            if (!claimTeardown()) return;
            inboundSink.tryEmitError(failure);
            outboundSink.tryEmitError(failure);
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "MCP transport failed closed: {0}",
                    McpDiagnosticRedactor.describe(failure));
            try {
                destroyObservedProcessTree(process.get());
            } finally {
                disposeSchedulers();
                state.set(State.FAILED);
            }
        }
    }

    /**
     * Takes ownership of teardown, or reports that someone else already has it.
     *
     * <p>Callers hold {@code lifecycleLock} across the whole teardown, so a losing caller only returns once the
     * winning one is done. Every non-terminal state is a valid place to start tearing down, including
     * {@code CONNECTING}: a peer that failed halfway through startup still has a process and four schedulers to
     * account for.</p>
     */
    private boolean claimTeardown() {
        State current = state.get();
        if (current == State.CLOSING || current == State.CLOSED || current == State.FAILED) return false;
        state.set(State.CLOSING);
        return true;
    }

    /** Whether the transport has stopped accepting work, whichever way it stopped. */
    private boolean isClosing() {
        State current = state.get();
        return current == State.CLOSING || current == State.CLOSED || current == State.FAILED;
    }

    /** Current lifecycle state, for tests that assert the transport really is single-connect. */
    State state() {
        return state.get();
    }

    private void shutdownProcess() {
        Process current = process.get();
        if (current == null) {
            destroyHandles(List.copyOf(observedProcesses.values()), true);
            return;
        }
        ProcessHandle root = current.toHandle();
        observeTree(root);
        if (current.isAlive()) {
            destroyObservedDescendants(root, false);
            current.destroy();
            try {
                if (!current.waitFor(PROCESS_SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                    observeTree(root);
                    destroyObservedDescendants(root, true);
                    current.destroyForcibly();
                    if (!current.waitFor(PROCESS_SHUTDOWN_FORCE.toMillis(), TimeUnit.MILLISECONDS)) {
                        LOGGER.log(System.Logger.Level.WARNING, "MCP process remained alive after forced shutdown deadline");
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                observeTree(root);
                destroyObservedDescendants(root, true);
                current.destroyForcibly();
            }
        }
        destroyObservedDescendants(root, true);
        if (!current.isAlive()) {
            int exitCode = current.exitValue();
            if (!NORMAL_EXIT_CODES.contains(exitCode)) {
                LOGGER.log(System.Logger.Level.WARNING, "MCP process exited with code {0}", exitCode);
            }
        }
    }

    private void destroyObservedProcessTree(Process current) {
        if (current == null) {
            destroyHandles(List.copyOf(observedProcesses.values()), true);
            return;
        }
        ProcessHandle root = current.toHandle();
        observeTree(root);
        destroyObservedDescendants(root, true);
        if (root.isAlive()) {
            try {
                root.destroyForcibly();
            } catch (RuntimeException failure) {
                LOGGER.log(
                        System.Logger.Level.DEBUG,
                        "Unable to terminate MCP root process: {0}",
                        McpDiagnosticRedactor.describe(failure));
            }
        }
        destroyObservedDescendants(root, true);
    }

    private void observeTree(ProcessHandle root) {
        observedProcesses.putIfAbsent(root.pid(), root);
        for (ProcessHandle seed : List.copyOf(observedProcesses.values())) {
            if (!seed.isAlive()) continue;
            try {
                seed.descendants().forEach(handle -> observedProcesses.putIfAbsent(handle.pid(), handle));
            } catch (RuntimeException ignored) {
                // A process can disappear between isAlive() and descendants(); retained handles remain available.
            }
        }
    }

    private void destroyObservedDescendants(ProcessHandle root, boolean force) {
        List<ProcessHandle> descendants = observedProcesses.values().stream()
                .filter(handle -> handle.pid() != root.pid())
                .toList();
        destroyHandles(descendants, force);
    }

    private void destroyHandles(List<ProcessHandle> handles, boolean force) {
        for (ProcessHandle handle : handles) {
            if (!handle.isAlive()) continue;
            try {
                if (force) handle.destroyForcibly();
                else handle.destroy();
            } catch (RuntimeException failure) {
                LOGGER.log(
                        System.Logger.Level.DEBUG,
                        "Unable to terminate MCP descendant process: {0}",
                        McpDiagnosticRedactor.describe(failure));
            }
        }
    }

    private void disposeSchedulers() {
        SchedulerRelease.disposeAll(inboundScheduler, outboundScheduler, errorScheduler, lifecycleScheduler);
    }

    static String readUtf8LineBounded(InputStream input, int maxBytes) throws IOException {
        Objects.requireNonNull(input, "input");
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        int next;
        while ((next = input.read()) != -1) {
            if (next == '\n') break;
            if (buffer.size() >= maxBytes) throw new MessageTooLargeException(maxBytes);
            buffer.write(next);
        }
        if (next == -1 && buffer.size() == 0) return null;
        byte[] bytes = buffer.toByteArray();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') length--;
        return StrictUtf8.decode(bytes, length);
    }

    static final class MessageTooLargeException extends IOException {
        private MessageTooLargeException(int maximum) {
            super("MCP STDIO frame exceeds " + maximum + " bytes");
        }
    }
}