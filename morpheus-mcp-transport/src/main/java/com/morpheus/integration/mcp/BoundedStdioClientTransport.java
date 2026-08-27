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
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * MCP STDIO client transport with hard per-frame and aggregate queue bounds.
 *
 * <p>The upstream Java SDK 2.0.0 STDIO transport can materialize unbounded lines and uses unbounded Reactor queues.
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

    private volatile Process process;
    private volatile boolean closing;
    private Consumer<String> stdErrorHandler = error -> LOGGER.log(System.Logger.Level.INFO, "MCP STDERR: {0}", error);

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
        this.inboundScheduler = Schedulers.fromExecutorService(
                Executors.newSingleThreadExecutor(), "morpheus-mcp-inbound");
        this.outboundScheduler = Schedulers.fromExecutorService(
                Executors.newSingleThreadExecutor(), "morpheus-mcp-outbound");
        this.errorScheduler = Schedulers.fromExecutorService(
                Executors.newSingleThreadExecutor(), "morpheus-mcp-stderr");
        this.lifecycleScheduler = Schedulers.fromExecutorService(
                Executors.newSingleThreadExecutor(), "morpheus-mcp-lifecycle");
    }

    public void setStdErrorHandler(Consumer<String> handler) {
        this.stdErrorHandler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public Mono<Void> connect(Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler) {
        Objects.requireNonNull(handler, "handler");
        return Mono.<Void>fromRunnable(() -> {
            handleIncomingMessages(handler);

            List<String> command = new ArrayList<>();
            command.add(parameters.getCommand());
            command.addAll(parameters.getArgs());
            ProcessBuilder builder = new ProcessBuilder(command);
            sanitizeEnvironment(builder.environment(), parameters.getEnv());

            try {
                process = builder.start();
            } catch (IOException failure) {
                disposeSchedulers();
                throw new IllegalStateException("failed to start MCP process", failure);
            }
            observeTree(process.toHandle());
            if (process.getInputStream() == null || process.getOutputStream() == null) {
                destroyObservedProcessTree(process);
                disposeSchedulers();
                throw new IllegalStateException("MCP process input or output stream is unavailable");
            }

            startLifecycleObservation();
            startInboundProcessing();
            startOutboundProcessing();
            startErrorProcessing();
        }).subscribeOn(Schedulers.boundedElastic());
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
            if (closing) return Mono.error(new IllegalStateException("MCP transport is closing"));
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

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> {
                    closing = true;
                    inboundSink.tryEmitComplete();
                    outboundSink.tryEmitComplete();
                    shutdownProcess();
                    disposeSchedulers();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
        return jsonMapper.convertValue(data, typeRef);
    }

    private void handleIncomingMessages(Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler) {
        inboundSink.asFlux()
                .flatMap(
                        message -> Mono.defer(() -> Mono.just(message).transform(handler))
                                .doFinally(ignored -> inboundCapacity.release()),
                        maxPendingMessages)
                .subscribe(
                        ignored -> { },
                        failure -> {
                            if (!closing) {
                                LOGGER.log(System.Logger.Level.WARNING, "MCP inbound processing failed", failure);
                                failClosed(failure);
                            }
                        });
    }

    private void startInboundProcessing() {
        inboundScheduler.schedule(() -> {
            try (InputStream input = new BufferedInputStream(process.getInputStream())) {
                String line;
                while (!closing && (line = readUtf8LineBounded(input, maxMessageBytes)) != null) {
                    JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(jsonMapper, line);
                    if (!inboundCapacity.tryAcquire()) {
                        failClosed(new IllegalStateException("MCP inbound pending-message capacity exceeded"));
                        return;
                    }
                    if (!inboundSink.tryEmitNext(message).isSuccess()) {
                        inboundCapacity.release();
                        if (!closing) failClosed(new IllegalStateException("MCP inbound queue capacity exceeded"));
                        return;
                    }
                }
                if (!closing) inboundSink.tryEmitComplete();
            } catch (MessageTooLargeException oversized) {
                failClosed(oversized);
            } catch (Exception failure) {
                if (!closing) failClosed(failure);
            }
        });
    }

    private void startErrorProcessing() {
        errorScheduler.schedule(() -> {
            try (InputStream input = new BufferedInputStream(process.getErrorStream())) {
                String line;
                while (!closing && (line = readUtf8LineBounded(input, maxMessageBytes)) != null) {
                    try {
                        stdErrorHandler.accept(line);
                    } catch (RuntimeException handlerFailure) {
                        LOGGER.log(System.Logger.Level.WARNING, "MCP stderr handler failed", handlerFailure);
                    }
                }
            } catch (MessageTooLargeException oversized) {
                if (!closing) {
                    LOGGER.log(System.Logger.Level.WARNING, "MCP stderr frame exceeded configured byte limit");
                    failClosed(oversized);
                }
            } catch (Exception failure) {
                if (!closing) failClosed(failure);
            }
        });
    }

    private void startOutboundProcessing() {
        handleOutbound(frames -> frames
                .publishOn(outboundScheduler)
                .handle((frame, sink) -> {
                    if (frame == null || closing) return;
                    try {
                        var output = process.getOutputStream();
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
        Process current = process;
        if (current == null) return;
        ProcessHandle root = current.toHandle();
        lifecycleScheduler.schedule(() -> {
            try {
                while (!closing && root.isAlive()) {
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
                    if (!closing) failClosed(failure);
                })
                .subscribe(
                        ignored -> { },
                        failure -> LOGGER.log(System.Logger.Level.WARNING, "MCP outbound processing failed", failure));
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
        if (closing) return;
        closing = true;
        inboundSink.tryEmitError(failure);
        outboundSink.tryEmitError(failure);
        LOGGER.log(System.Logger.Level.WARNING, "MCP transport failed closed: {0}", failure.getMessage());
        destroyObservedProcessTree(process);
        disposeSchedulers();
    }

    private void shutdownProcess() {
        Process current = process;
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
                LOGGER.log(System.Logger.Level.DEBUG, "Unable to terminate MCP root process", failure);
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
                LOGGER.log(System.Logger.Level.DEBUG, "Unable to terminate MCP descendant process", failure);
            }
        }
    }

    private void disposeSchedulers() {
        inboundScheduler.dispose();
        outboundScheduler.dispose();
        errorScheduler.dispose();
        lifecycleScheduler.dispose();
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

    private record OutboundFrame(byte[] encoded) {
        private OutboundFrame {
            encoded = Objects.requireNonNull(encoded, "encoded");
        }
    }

    static final class MessageTooLargeException extends IOException {
        private MessageTooLargeException(int maximum) {
            super("MCP STDIO frame exceeds " + maximum + " bytes");
        }
    }
}
