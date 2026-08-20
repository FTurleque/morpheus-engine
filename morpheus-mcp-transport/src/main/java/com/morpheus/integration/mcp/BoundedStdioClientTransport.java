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
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * MCP STDIO client transport with a hard UTF-8 frame bound applied before JSON deserialization.
 *
 * <p>The upstream Java SDK 2.0.0 STDIO transport uses {@code BufferedReader.readLine()}, which can materialize an
 * unbounded line before the application sees the response. MORPHEUS reads the process stream as bounded bytes and
 * rejects an oversized JSON-RPC frame before creating the frame String or object graph.</p>
 */
public final class BoundedStdioClientTransport implements McpClientTransport {
    private static final System.Logger LOGGER = System.getLogger(BoundedStdioClientTransport.class.getName());
    private static final Set<Integer> NORMAL_EXIT_CODES = Set.of(0, 130, 141, 143);

    private final Sinks.Many<JSONRPCMessage> inboundSink = Sinks.many().unicast().onBackpressureBuffer();
    private final Sinks.Many<JSONRPCMessage> outboundSink = Sinks.many().unicast().onBackpressureBuffer();
    private final Sinks.Many<String> errorSink = Sinks.many().unicast().onBackpressureBuffer();
    private final ServerParameters parameters;
    private final McpJsonMapper jsonMapper;
    private final int maxInboundMessageBytes;
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
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        if (maxInboundMessageBytes < 1) {
            throw new IllegalArgumentException("maxInboundMessageBytes must be positive");
        }
        this.maxInboundMessageBytes = maxInboundMessageBytes;
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
        if (outboundSink.tryEmitNext(message).isSuccess()) return Mono.empty();
        return Mono.error(new IllegalStateException("failed to enqueue MCP message"));
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> closing = true)
                .then(Mono.defer(() -> {
                    inboundSink.tryEmitComplete();
                    outboundSink.tryEmitComplete();
                    errorSink.tryEmitComplete();
                    return Mono.delay(Duration.ofMillis(100)).then();
                }))
                .then(Mono.defer(() -> {
                    Process current = process;
                    if (current == null) return Mono.<Process>empty();
                    current.destroy();
                    return Mono.fromFuture(current.onExit());
                }))
                .doOnNext(exited -> {
                    int exitCode = exited.exitValue();
                    if (!NORMAL_EXIT_CODES.contains(exitCode)) {
                        LOGGER.log(System.Logger.Level.WARNING, "MCP process exited with code {0}", exitCode);
                    }
                })
                .then(Mono.fromRunnable(() -> {
                    inboundScheduler.dispose();
                    outboundScheduler.dispose();
                    errorScheduler.dispose();
                }))
                .then()
                .subscribeOn(Schedulers.boundedElastic());
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
                            }
                        });
    }

    private void handleIncomingErrors() {
        errorSink.asFlux().subscribe(
                stdErrorHandler,
                failure -> {
                    if (!closing) {
                        LOGGER.log(System.Logger.Level.WARNING, "MCP stderr processing failed", failure);
                    }
                });
    }

    private void startInboundProcessing() {
        inboundScheduler.schedule(() -> {
            try (InputStream input = new BufferedInputStream(process.getInputStream())) {
                String line;
                while (!closing && (line = readUtf8LineBounded(input, maxInboundMessageBytes)) != null) {
                    JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(jsonMapper, line);
                    if (!inboundSink.tryEmitNext(message).isSuccess()) {
                        if (!closing) {
                            failInbound(new IllegalStateException("failed to enqueue inbound MCP message"));
                        }
                        return;
                    }
                }
                if (!closing) inboundSink.tryEmitComplete();
            } catch (MessageTooLargeException oversized) {
                failInbound(oversized);
                destroyProcess();
            } catch (Exception failure) {
                if (!closing) failInbound(failure);
            }
        });
    }

    private void startErrorProcessing() {
        errorScheduler.schedule(() -> {
            try (InputStream input = new BufferedInputStream(process.getErrorStream())) {
                String line;
                while (!closing && (line = readUtf8LineBounded(input, maxInboundMessageBytes)) != null) {
                    if (!errorSink.tryEmitNext(line).isSuccess()) return;
                }
                if (!closing) errorSink.tryEmitComplete();
            } catch (MessageTooLargeException oversized) {
                if (!closing) {
                    errorSink.tryEmitNext("MCP stderr frame exceeded configured byte limit");
                    errorSink.tryEmitError(oversized);
                }
                destroyProcess();
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
                        String json = jsonMapper.writeValueAsString(message)
                                .replace("\r\n", "\\n")
                                .replace("\n", "\\n")
                                .replace("\r", "\\n");
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
                .doOnComplete(() -> {
                    closing = true;
                    outboundSink.tryEmitComplete();
                })
                .doOnError(failure -> {
                    if (!closing) {
                        closing = true;
                        outboundSink.tryEmitError(failure);
                    }
                })
                .subscribe(
                        ignored -> { },
                        failure -> LOGGER.log(System.Logger.Level.WARNING, "MCP outbound processing failed", failure));
    }

    private void failInbound(Throwable failure) {
        if (!closing) {
            inboundSink.tryEmitError(failure);
            LOGGER.log(System.Logger.Level.WARNING, "MCP inbound transport failed: {0}", failure.getMessage());
        }
    }

    private void destroyProcess() {
        Process current = process;
        if (current != null && current.isAlive()) current.destroyForcibly();
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
