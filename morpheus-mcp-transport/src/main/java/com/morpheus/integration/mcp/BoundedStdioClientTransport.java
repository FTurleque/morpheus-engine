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
 * MCP STDIO client transport with hard UTF-8 frame and queue bounds applied before JSON deserialization.
 */
public final class BoundedStdioClientTransport implements McpClientTransport {
    private static final System.Logger LOGGER = System.getLogger(BoundedStdioClientTransport.class.getName());
    private static final Set<Integer> NORMAL_EXIT_CODES = Set.of(0, 130, 141, 143);
    static final int DEFAULT_INBOUND_QUEUE_CAPACITY = 64;
    static final int DEFAULT_OUTBOUND_QUEUE_CAPACITY = 64;
    static final int DEFAULT_STDERR_QUEUE_CAPACITY = 128;
    private static final long PROCESS_GRACEFUL_SHUTDOWN_MILLIS = 1_000L;
    private static final long PROCESS_FORCED_SHUTDOWN_MILLIS = 1_000L;

    private final Sinks.Many<JSONRPCMessage> inboundSink;
    private final Sinks.Many<JSONRPCMessage> outboundSink;
    private final Sinks.Many<String> errorSink;
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
        this(parameters, jsonMapper, maxInboundMessageBytes,
                DEFAULT_INBOUND_QUEUE_CAPACITY, DEFAULT_OUTBOUND_QUEUE_CAPACITY, DEFAULT_STDERR_QUEUE_CAPACITY);
    }

    BoundedStdioClientTransport(
            ServerParameters parameters,
            McpJsonMapper jsonMapper,
            int maxMessageBytes,
            int inboundQueueCapacity,
            int outboundQueueCapacity,
            int stderrQueueCapacity) {
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        if (maxMessageBytes < 1) throw new IllegalArgumentException("maxMessageBytes must be positive");
        if (inboundQueueCapacity < 1 || outboundQueueCapacity < 1 || stderrQueueCapacity < 1) {
            throw new IllegalArgumentException("MCP queue capacities must be positive");
        }
        this.maxMessageBytes = maxMessageBytes;
        this.inboundSink = boundedSink(inboundQueueCapacity);
        this.outboundSink = boundedSink(outboundQueueCapacity);
        this.errorSink = boundedSink(stderrQueueCapacity);
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
            handleIncomingErrors();

            List<String> command = new ArrayList<>();
            command.add(parameters.getCommand());
            command.addAll(parameters.getArgs());
            ProcessBuilder builder = new ProcessBuilder(command);
            Map<String, String> environment = builder.environment();
            environment.putAll(parameters.getEnv());

            try {
                process = builder.start();
            } catch (IOException failure) {
                throw new IllegalStateException("failed to start MCP process", failure);
            }
            if (process.getInputStream() == null || process.getOutputStream() == null) {
                process.destroy();
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
        if (closing) return Mono.error(new IllegalStateException("MCP transport is closing"));
        try {
            ensureFrameWithinLimit(message);
        } catch (RuntimeException failure) {
            return Mono.error(failure);
        }
        Sinks.EmitResult result = outboundSink.tryEmitNext(message);
        if (result.isSuccess()) return Mono.empty();
        failClosed("outbound MCP queue is full or unavailable");
        return Mono.error(new IllegalStateException("failed to enqueue MCP message: " + result));
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> {
                    closing = true;
                    inboundSink.tryEmitComplete();
                    outboundSink.tryEmitComplete();
                    errorSink.tryEmitComplete();
                    try {
                        terminateProcessBounded();
                    } finally {
                        inboundScheduler.dispose();
                        outboundScheduler.dispose();
                        errorScheduler.dispose();
                    }
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
                .concatMap(message -> Mono.just(message).transform(handler))
                .subscribe(
                        ignored -> { },
                        failure -> {
                            if (!closing) {
                                LOGGER.log(System.Logger.Level.WARNING, "MCP inbound processing failed", failure);
                                failClosed("MCP inbound handler failed");
                            }
                        });
    }

    private void handleIncomingErrors() {
        errorSink.asFlux().subscribe(
                stdErrorHandler,
                failure -> {
                    if (!closing) LOGGER.log(System.Logger.Level.WARNING, "MCP stderr processing failed", failure);
                });
    }

    private void startInboundProcessing() {
        inboundScheduler.schedule(() -> {
            try (InputStream input = new BufferedInputStream(process.getInputStream())) {
                String line;
                while (!closing && (line = readUtf8LineBounded(input, maxMessageBytes)) != null) {
                    JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(jsonMapper, line);
                    Sinks.EmitResult result = inboundSink.tryEmitNext(message);
                    if (!result.isSuccess()) {
                        if (!closing) failClosed("inbound MCP queue is full or unavailable");
                        return;
                    }
                }
                if (!closing) inboundSink.tryEmitComplete();
            } catch (MessageTooLargeException oversized) {
                failInbound(oversized);
                failClosed("inbound MCP frame exceeded configured byte limit");
            } catch (Exception failure) {
                if (!closing) {
                    failInbound(failure);
                    failClosed("MCP inbound reader failed");
                }
            }
        });
    }

    private void startErrorProcessing() {
        errorScheduler.schedule(() -> {
            try (InputStream input = new BufferedInputStream(process.getErrorStream())) {
                String line;
                while (!closing && (line = readUtf8LineBounded(input, maxMessageBytes)) != null) {
                    Sinks.EmitResult result = errorSink.tryEmitNext(line);
                    if (!result.isSuccess()) {
                        LOGGER.log(System.Logger.Level.WARNING, "MCP stderr queue saturated; terminating peer");
                        failClosed("MCP stderr queue is full or unavailable");
                        return;
                    }
                }
                if (!closing) errorSink.tryEmitComplete();
            } catch (MessageTooLargeException oversized) {
                if (!closing) errorSink.tryEmitNext("MCP stderr frame exceeded configured byte limit");
                failClosed("MCP stderr frame exceeded configured byte limit");
            } catch (Exception failure) {
                if (!closing) errorSink.tryEmitError(failure);
            }
        });
    }

    private void startOutboundProcessing() {
        handleOutbound(messages -> messages
                .publishOn(outboundScheduler)
                .handle((message, sink) -> {
                    if (message == null || closing) return;
                    try {
                        String json = serializedFrame(message);
                        byte[] encoded = json.getBytes(StandardCharsets.UTF_8);
                        var output = process.getOutputStream();
                        synchronized (output) {
                            output.write(encoded);
                            output.write('\n');
                            output.flush();
                        }
                        sink.next(message);
                    } catch (IOException failure) {
                        sink.error(new IllegalStateException("failed to write MCP message", failure));
                    }
                }));
    }

    private void handleOutbound(Function<Flux<JSONRPCMessage>, Flux<JSONRPCMessage>> consumer) {
        consumer.apply(outboundSink.asFlux())
                .doOnComplete(() -> outboundSink.tryEmitComplete())
                .doOnError(failure -> {
                    if (!closing) failClosed("MCP outbound processing failed");
                })
                .subscribe(
                        ignored -> { },
                        failure -> LOGGER.log(System.Logger.Level.WARNING, "MCP outbound processing failed", failure));
    }

    private String serializedFrame(JSONRPCMessage message) throws IOException {
        return jsonMapper.writeValueAsString(message)
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    private void ensureFrameWithinLimit(JSONRPCMessage message) {
        try {
            String json = serializedFrame(message);
            if (json.getBytes(StandardCharsets.UTF_8).length > maxMessageBytes) {
                throw new IllegalArgumentException("outbound MCP STDIO frame exceeds " + maxMessageBytes + " bytes");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("failed to serialize MCP message", failure);
        }
    }

    private void failInbound(Throwable failure) {
        if (!closing) {
            inboundSink.tryEmitError(failure);
            LOGGER.log(System.Logger.Level.WARNING, "MCP inbound transport failed: {0}", failure.getMessage());
        }
    }

    private void failClosed(String reason) {
        if (!closing) LOGGER.log(System.Logger.Level.WARNING, reason);
        closing = true;
        inboundSink.tryEmitError(new IllegalStateException(reason));
        outboundSink.tryEmitError(new IllegalStateException(reason));
        errorSink.tryEmitComplete();
        destroyProcessTree(true);
    }

    private void terminateProcessBounded() {
        Process current = process;
        if (current == null) return;
        if (!current.isAlive()) {
            logExitCode(current);
            return;
        }
        destroyProcessTree(false);
        if (waitFor(current, PROCESS_GRACEFUL_SHUTDOWN_MILLIS)) {
            logExitCode(current);
            return;
        }
        destroyProcessTree(true);
        waitFor(current, PROCESS_FORCED_SHUTDOWN_MILLIS);
        if (!current.isAlive()) logExitCode(current);
        else LOGGER.log(System.Logger.Level.WARNING, "MCP process did not terminate after forced shutdown deadline");
    }

    private void destroyProcessTree(boolean forcibly) {
        Process current = process;
        if (current == null) return;
        List<ProcessHandle> descendants = current.toHandle().descendants().toList();
        for (int i = descendants.size() - 1; i >= 0; i--) {
            ProcessHandle child = descendants.get(i);
            if (child.isAlive()) {
                if (forcibly) child.destroyForcibly();
                else child.destroy();
            }
        }
        if (current.isAlive()) {
            if (forcibly) current.destroyForcibly();
            else current.destroy();
        }
    }

    private static boolean waitFor(Process process, long timeoutMillis) {
        try {
            return process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void logExitCode(Process exited) {
        int exitCode = exited.exitValue();
        if (!NORMAL_EXIT_CODES.contains(exitCode)) {
            LOGGER.log(System.Logger.Level.WARNING, "MCP process exited with code {0}", exitCode);
        }
    }

    static <T> Sinks.Many<T> boundedSink(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        return Sinks.many().unicast().onBackpressureBuffer(new ArrayBlockingQueue<>(capacity));
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

    static final class MessageTooLargeException extends IOException {
        private MessageTooLargeException(int maximum) {
            super("MCP STDIO frame exceeds " + maximum + " bytes");
        }
    }
}
