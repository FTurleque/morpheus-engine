package com.morpheus.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-session STDIO server transport with bounded UTF-8 frames and bounded aggregate queues.
 */
final class BoundedStdioServerTransportProvider implements McpServerTransportProvider {
    static final int DEFAULT_MAX_MESSAGE_BYTES = 65_536;
    static final int DEFAULT_QUEUE_CAPACITY = 64;

    private static final System.Logger LOGGER = System.getLogger(BoundedStdioServerTransportProvider.class.getName());

    private final McpJsonMapper jsonMapper;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final int maxMessageBytes;
    private final int queueCapacity;
    private final AtomicBoolean closing = new AtomicBoolean();
    private final Sinks.One<Void> completion = Sinks.one();
    private volatile McpServerSession session;
    private volatile SessionTransport transport;

    BoundedStdioServerTransportProvider(McpJsonMapper jsonMapper) {
        this(jsonMapper, System.in, System.out, DEFAULT_MAX_MESSAGE_BYTES, DEFAULT_QUEUE_CAPACITY);
    }

    BoundedStdioServerTransportProvider(
            McpJsonMapper jsonMapper,
            InputStream inputStream,
            OutputStream outputStream,
            int maxMessageBytes,
            int queueCapacity) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.inputStream = Objects.requireNonNull(inputStream, "inputStream");
        this.outputStream = Objects.requireNonNull(outputStream, "outputStream");
        if (maxMessageBytes < 1) throw new IllegalArgumentException("maxMessageBytes must be positive");
        if (queueCapacity < 1) throw new IllegalArgumentException("queueCapacity must be positive");
        this.maxMessageBytes = maxMessageBytes;
        this.queueCapacity = queueCapacity;
    }

    @Override
    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
        Objects.requireNonNull(sessionFactory, "sessionFactory");
        SessionTransport createdTransport = new SessionTransport();
        this.transport = createdTransport;
        this.session = sessionFactory.create(createdTransport);
        createdTransport.start();
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        McpServerSession current = session;
        if (current == null) return Mono.error(new IllegalStateException("No session to notify"));
        return current.sendNotification(method, params);
    }

    @Override
    public Mono<Void> notifyClient(String sessionId, String method, Object params) {
        return Mono.defer(() -> {
            McpServerSession current = session;
            if (current == null) return Mono.error(new IllegalStateException("No session to notify"));
            if (!current.getId().equals(sessionId)) {
                return Mono.error(new IllegalStateException("Existing session does not match notification target"));
            }
            return current.sendNotification(method, params);
        });
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.defer(() -> {
            if (!closing.compareAndSet(false, true)) return completion.asMono();
            SessionTransport currentTransport = transport;
            McpServerSession currentSession = session;
            Mono<Void> sessionClose = currentSession == null ? Mono.empty() : currentSession.closeGracefully();
            return sessionClose
                    .onErrorResume(failure -> Mono.empty())
                    .then(Mono.fromRunnable(() -> {
                        if (currentTransport != null) currentTransport.closeNow();
                        completion.tryEmitEmpty();
                    }));
        });
    }

    Mono<Void> completion() {
        return completion.asMono();
    }

    private void failClosed(Throwable failure) {
        if (closing.compareAndSet(false, true)) {
            LOGGER.log(System.Logger.Level.WARNING, "MCP server STDIO transport failed: {0}", failure.getMessage());
        }
        SessionTransport currentTransport = transport;
        if (currentTransport != null) currentTransport.closeNow();
        McpServerSession currentSession = session;
        if (currentSession != null) currentSession.close();
        completion.tryEmitEmpty();
    }

    private final class SessionTransport implements McpServerTransport {
        private final Sinks.Many<JSONRPCMessage> inbound = Sinks.many().unicast()
                .onBackpressureBuffer(new ArrayBlockingQueue<>(queueCapacity));
        private final Sinks.Many<JSONRPCMessage> outbound = Sinks.many().unicast()
                .onBackpressureBuffer(new ArrayBlockingQueue<>(queueCapacity));
        private final Scheduler inboundScheduler = Schedulers.fromExecutorService(
                Executors.newSingleThreadExecutor(), "morpheus-mcp-server-inbound");
        private final Scheduler outboundScheduler = Schedulers.fromExecutorService(
                Executors.newSingleThreadExecutor(), "morpheus-mcp-server-outbound");
        private final AtomicBoolean started = new AtomicBoolean();

        @Override
        public Mono<Void> sendMessage(JSONRPCMessage message) {
            Objects.requireNonNull(message, "message");
            if (closing.get()) return Mono.error(new IllegalStateException("MCP server transport is closing"));
            try {
                ensureOutboundLimit(message);
            } catch (RuntimeException failure) {
                failClosed(failure);
                return Mono.error(failure);
            }
            Sinks.EmitResult result = outbound.tryEmitNext(message);
            if (result.isSuccess()) return Mono.empty();
            IllegalStateException failure = new IllegalStateException("outbound MCP server queue saturated: " + result);
            failClosed(failure);
            return Mono.error(failure);
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return jsonMapper.convertValue(data, typeRef);
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.fromRunnable(this::closeNow);
        }

        @Override
        public void close() {
            closeNow();
        }

        void start() {
            if (!started.compareAndSet(false, true)) return;
            inbound.asFlux()
                    .concatMap(message -> session.handle(message))
                    .subscribe(
                            ignored -> { },
                            BoundedStdioServerTransportProvider.this::failClosed,
                            () -> outbound.tryEmitComplete());
            outbound.asFlux()
                    .publishOn(outboundScheduler)
                    .subscribe(
                            this::writeMessage,
                            BoundedStdioServerTransportProvider.this::failClosed);
            inboundScheduler.schedule(this::readLoop);
        }

        private void readLoop() {
            try (InputStream input = new BufferedInputStream(inputStream)) {
                String line;
                while (!closing.get() && (line = readUtf8LineBounded(input, maxMessageBytes)) != null) {
                    JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(jsonMapper, line);
                    Sinks.EmitResult result = inbound.tryEmitNext(message);
                    if (!result.isSuccess()) {
                        throw new IllegalStateException("inbound MCP server queue saturated: " + result);
                    }
                }
                if (!closing.get()) {
                    closing.set(true);
                    inbound.tryEmitComplete();
                    McpServerSession current = session;
                    if (current != null) current.close();
                    completion.tryEmitEmpty();
                }
            } catch (Exception failure) {
                if (!closing.get()) failClosed(failure);
            } finally {
                inbound.tryEmitComplete();
                inboundScheduler.dispose();
            }
        }

        private void writeMessage(JSONRPCMessage message) {
            if (closing.get()) return;
            try {
                String json = serializedFrame(message);
                byte[] encoded = json.getBytes(StandardCharsets.UTF_8);
                synchronized (outputStream) {
                    outputStream.write(encoded);
                    outputStream.write('\n');
                    outputStream.flush();
                }
            } catch (IOException failure) {
                failClosed(new IllegalStateException("failed to write MCP server response", failure));
            }
        }

        private void ensureOutboundLimit(JSONRPCMessage message) {
            if (serializedFrame(message).getBytes(StandardCharsets.UTF_8).length > maxMessageBytes) {
                throw new IllegalArgumentException(
                        "outbound MCP server STDIO frame exceeds " + maxMessageBytes + " bytes");
            }
        }

        private String serializedFrame(JSONRPCMessage message) {
            return jsonMapper.writeValueAsString(message)
                    .replace("\r\n", "\\n")
                    .replace("\n", "\\n")
                    .replace("\r", "\\n");
        }

        void closeNow() {
            inbound.tryEmitComplete();
            outbound.tryEmitComplete();
            inboundScheduler.dispose();
            outboundScheduler.dispose();
            completion.tryEmitEmpty();
        }
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
