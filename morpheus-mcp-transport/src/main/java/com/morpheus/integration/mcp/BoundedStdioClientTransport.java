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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * MCP STDIO client transport with hard per-frame and aggregate queue bounds.
 *
 * <p>The upstream Java SDK 2.0.0 STDIO transport can materialize unbounded lines and uses unbounded Reactor queues.
 * MORPHEUS reads process streams as bounded UTF-8 bytes before JSON parsing, caps pending inbound/outbound messages,
 * handles stderr synchronously on its reader thread, and fails closed when a peer exceeds a resource budget.</p>
 */
public final class BoundedStdioClientTransport implements McpClientTransport {
    public static final int DEFAULT_MAX_PENDING_MESSAGES = 64;

    private static final System.Logger LOGGER = System.getLogger(BoundedStdioClientTransport.class.getName());
    private static final Set<Integer> NORMAL_EXIT_CODES = Set.of(0, 130, 141, 143);
    private static final Duration PROCESS_SHUTDOWN_GRACE = Duration.ofSeconds(2);
    private static final Duration PROCESS_SHUTDOWN_FORCE = Duration.ofSeconds(2);

    private final Sinks.Many<JSONRPCMessage> inboundSink;
    private final Sinks.Many<OutboundFrame> outboundSink;
    private final ServerParameters parameters;
    private final McpJsonMapper jsonMapper;
    private final int maxMessageBytes;
    private final Scheduler inboundScheduler;
    private final Scheduler outboundScheduler;
    private final Scheduler errorScheduler;

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
            Map<String, String> environment = builder.environment();
            environment.putAll(parameters.getEnv());

            try {
                process = builder.start();
            } catch (IOException failure) {
                disposeSchedulers();
                throw new IllegalStateException("failed to start MCP process", failure);
            }
            if (process.getInputStream() == null || process.getOutputStream() == null) {
                destroyProcessTree(process);
                disposeSchedulers();
                throw new IllegalStateException("MCP process input or output stream is unavailable");
            }

            startInboundProcessing();
            startOutboundProcessing();
            startErrorProcessing();
        }).subscribeOn(Schedulers.boundedElastic());
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
                .flatMap(message -> Mono.just(message).transform(handler))
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
                    if (!inboundSink.tryEmitNext(message).isSuccess()) {
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
        destroyProcessTree(process);
        disposeSchedulers();
    }

    private void shutdownProcess() {
        Process current = process;
        if (current == null) return;
        List<ProcessHandle> descendants = current.descendants().toList();
        if (current.isAlive()) {
            destroyHandles(descendants, false);
            current.destroy();
            try {
                if (!current.waitFor(PROCESS_SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                    descendants = mergeHandles(descendants, current.descendants().toList());
                    destroyHandles(descendants, true);
                    current.destroyForcibly();
                    if (!current.waitFor(PROCESS_SHUTDOWN_FORCE.toMillis(), TimeUnit.MILLISECONDS)) {
                        LOGGER.log(System.Logger.Level.WARNING, "MCP process remained alive after forced shutdown deadline");
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                descendants = mergeHandles(descendants, current.descendants().toList());
                destroyHandles(descendants, true);
                current.destroyForcibly();
            }
        }
        destroyHandles(descendants, true);
        if (!current.isAlive()) {
            int exitCode = current.exitValue();
            if (!NORMAL_EXIT_CODES.contains(exitCode)) {
                LOGGER.log(System.Logger.Level.WARNING, "MCP process exited with code {0}", exitCode);
            }
        }
    }

    private List<ProcessHandle> mergeHandles(List<ProcessHandle> first, List<ProcessHandle> second) {
        List<ProcessHandle> merged = new ArrayList<>(first);
        for (ProcessHandle candidate : second) {
            if (merged.stream().noneMatch(existing -> existing.pid() == candidate.pid())) merged.add(candidate);
        }
        return merged;
    }

    private void destroyProcessTree(Process current) {
        if (current == null) return;
        List<ProcessHandle> descendants = current.descendants().toList();
        destroyHandles(descendants, true);
        if (current.isAlive()) current.destroyForcibly();
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
        return new String(bytes, 0, length, StandardCharsets.UTF_8);
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
