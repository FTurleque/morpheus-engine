package com.morpheus.integration.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resource-bounded MCP STDIO server transport.
 *
 * <p>Frames are bounded as UTF-8 bytes before JSON deserialization. Inbound messages are handled sequentially so an
 * untrusted peer cannot build an aggregate inbound queue. Outbound messages are serialized before enqueueing and use a
 * fixed-capacity queue, which bounds both individual frames and aggregate pending output.</p>
 */
public final class BoundedStdioServerTransportProvider implements McpServerTransportProvider {
    public static final int DEFAULT_MAX_FRAME_BYTES = 1024 * 1024;
    public static final int DEFAULT_MAX_PENDING_MESSAGES = 64;

    private static final System.Logger LOGGER =
            System.getLogger(BoundedStdioServerTransportProvider.class.getName());

    private final McpJsonMapper jsonMapper;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final int maxFrameBytes;
    private final int maxPendingMessages;
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final CountDownLatch terminated = new CountDownLatch(1);

    private final AtomicReference<McpServerSession> session = new AtomicReference<>();
    private final AtomicReference<BoundedSessionTransport> transport = new AtomicReference<>();

    @SuppressWarnings("java:S106") // System.out is the actual MCP wire-protocol stream, not a log write.
    public BoundedStdioServerTransportProvider(McpJsonMapper jsonMapper) {
        this(jsonMapper, System.in, System.out, DEFAULT_MAX_FRAME_BYTES, DEFAULT_MAX_PENDING_MESSAGES);
    }

    public BoundedStdioServerTransportProvider(McpJsonMapper jsonMapper, int maxFrameBytes) {
        this(jsonMapper, System.in, System.out, maxFrameBytes, DEFAULT_MAX_PENDING_MESSAGES);
    }

    public BoundedStdioServerTransportProvider(
            McpJsonMapper jsonMapper,
            InputStream inputStream,
            OutputStream outputStream,
            int maxFrameBytes,
            int maxPendingMessages) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.inputStream = Objects.requireNonNull(inputStream, "inputStream");
        this.outputStream = Objects.requireNonNull(outputStream, "outputStream");
        if (maxFrameBytes < 1) throw new IllegalArgumentException("maxFrameBytes must be positive");
        if (maxPendingMessages < 1) throw new IllegalArgumentException("maxPendingMessages must be positive");
        this.maxFrameBytes = maxFrameBytes;
        this.maxPendingMessages = maxPendingMessages;
    }

    @Override
    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
        Objects.requireNonNull(sessionFactory, "sessionFactory");
        if (!initialized.compareAndSet(false, true)) {
            throw new IllegalStateException("MCP STDIO transport supports exactly one session");
        }
        BoundedSessionTransport createdTransport = new BoundedSessionTransport();
        this.transport.set(createdTransport);
        this.session.set(sessionFactory.create(createdTransport));
        createdTransport.start();
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        McpServerSession current = session.get();
        if (current == null) return Mono.error(new IllegalStateException("No MCP STDIO session is available"));
        return current.sendNotification(method, params);
    }

    @Override
    public Mono<Void> notifyClient(String sessionId, String method, Object params) {
        return Mono.defer(() -> {
            McpServerSession current = session.get();
            if (current == null) return Mono.error(new IllegalStateException("No MCP STDIO session is available"));
            if (!current.getId().equals(sessionId)) {
                return Mono.error(new IllegalStateException("Unknown MCP STDIO session: " + sessionId));
            }
            return current.sendNotification(method, params);
        });
    }

    @Override
    public Mono<Void> closeGracefully() {
        BoundedSessionTransport currentTransport = transport.get();
        if (currentTransport != null) currentTransport.requestStop();
        McpServerSession current = session.get();
        if (current == null) {
            closing.set(true);
            terminated.countDown();
            return Mono.empty();
        }
        return current.closeGracefully();
    }

    /** Blocks until stdin reaches EOF, the peer violates a transport bound, or the transport is closed. */
    public void awaitTermination() throws InterruptedException {
        terminated.await();
    }

    /** Test/embedding helper with an explicit deadline. */
    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        return terminated.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private final class BoundedSessionTransport implements McpServerTransport {
        private final ArrayBlockingQueue<OutboundFrame> outboundQueue = new ArrayBlockingQueue<>(maxPendingMessages);
        private final Scheduler inboundScheduler = Schedulers.fromExecutorService(
                Executors.newSingleThreadExecutor(), "morpheus-mcp-server-inbound");
        private final Scheduler outboundScheduler = Schedulers.fromExecutorService(
                Executors.newSingleThreadExecutor(), "morpheus-mcp-server-outbound");
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicInteger workersRemaining = new AtomicInteger(2);
        private final AtomicReference<CompletableFuture<Void>> activeHandler = new AtomicReference<>();

        @Override
        public Mono<Void> sendMessage(JSONRPCMessage message) {
            Objects.requireNonNull(message, "message");
            return Mono.defer(() -> {
                if (closing.get()) return Mono.error(new IllegalStateException("MCP STDIO transport is closing"));
                final OutboundFrame frame;
                try {
                    frame = encode(message);
                } catch (IOException failure) {
                    failClosed(failure);
                    return Mono.error(failure);
                }
                if (!outboundQueue.offer(frame)) {
                    IllegalStateException overflow =
                            new IllegalStateException("MCP STDIO outbound queue capacity exceeded");
                    failClosed(overflow);
                    return Mono.error(overflow);
                }
                return Mono.empty();
            });
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return jsonMapper.convertValue(data, typeRef);
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.fromRunnable(this::requestStop);
        }

        @Override
        public void close() {
            requestStop();
        }

        private void start() {
            if (!started.compareAndSet(false, true)) return;
            outboundScheduler.schedule(this::writeLoop);
            inboundScheduler.schedule(this::readLoop);
        }

        private void readLoop() {
            try {
                BufferedInputStream input = new BufferedInputStream(inputStream);
                while (!closing.get()) {
                    String line = readUtf8LineBounded(input, maxFrameBytes);
                    if (line == null || closing.get()) break;
                    JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(jsonMapper, line);
                    handleSequentially(message);
                }
            } catch (MessageTooLargeException oversized) {
                failClosed(oversized);
            } catch (Exception failure) {
                if (!closing.get()) failClosed(failure);
            } finally {
                if (closing.compareAndSet(false, true)) {
                    McpServerSession current = session.get();
                    if (current != null) current.close();
                }
                cancelActiveHandler();
                closeInputQuietly();
                workerFinished();
            }
        }

        private void handleSequentially(JSONRPCMessage message) throws Exception {
            CompletableFuture<Void> future = session.get().handle(message).toFuture();
            activeHandler.set(future);
            if (closing.get()) future.cancel(true);
            try {
                future.get();
            } catch (CancellationException cancelled) {
                if (!closing.get()) throw cancelled;
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Exception exception) throw exception;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException("MCP STDIO handler failed", cause);
            } finally {
                activeHandler.compareAndSet(future, null);
            }
        }

        private void writeLoop() {
            try {
                while (!closing.get() || !outboundQueue.isEmpty()) {
                    OutboundFrame frame = outboundQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (frame == null) continue;
                    synchronized (outputStream) {
                        outputStream.write(frame.encoded());
                        outputStream.write('\n');
                        outputStream.flush();
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException failure) {
                if (!closing.get()) failClosed(failure);
            } finally {
                workerFinished();
            }
        }

        private OutboundFrame encode(JSONRPCMessage message) throws IOException {
            String json = jsonMapper.writeValueAsString(message)
                    .replace("\r\n", "\\n")
                    .replace("\n", "\\n")
                    .replace("\r", "\\n");
            byte[] encoded = json.getBytes(StandardCharsets.UTF_8);
            if (encoded.length > maxFrameBytes) throw new MessageTooLargeException(maxFrameBytes);
            return new OutboundFrame(encoded);
        }

        private void requestStop() {
            closing.set(true);
            cancelActiveHandler();
            closeInputQuietly();
        }

        private void failClosed(Throwable failure) {
            if (closing.compareAndSet(false, true)) {
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "MCP STDIO server transport failed: {0}",
                        McpDiagnosticRedactor.describe(failure));
                McpServerSession current = session.get();
                if (current != null) current.close();
            }
            cancelActiveHandler();
            closeInputQuietly();
        }

        private void cancelActiveHandler() {
            CompletableFuture<Void> future = activeHandler.getAndSet(null);
            if (future != null) future.cancel(true);
        }

        private void workerFinished() {
            if (workersRemaining.decrementAndGet() == 0) {
                inboundScheduler.dispose();
                outboundScheduler.dispose();
                terminated.countDown();
            }
        }
    }

    private void closeInputQuietly() {
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // Closing stdin is best-effort cleanup used to unblock a pending read.
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
        return StrictUtf8.decode(bytes, length);
    }

    static final class MessageTooLargeException extends IOException {
        private MessageTooLargeException(int maximum) {
            super("MCP STDIO frame exceeds " + maximum + " bytes");
        }
    }
}
