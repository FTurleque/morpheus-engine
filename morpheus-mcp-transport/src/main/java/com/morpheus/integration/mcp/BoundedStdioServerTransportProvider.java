package com.morpheus.integration.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

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
 *
 * <p>The frame bound reacts differently in each direction. Inbound it is defensive: a peer past it is hostile or
 * broken, and the session is failed closed. Outbound the frame is MORPHEUS's own, so overstepping it is a MORPHEUS
 * defect the client must not pay for with its session: an oversized response is replaced by a
 * {@value #RESPONSE_TOO_LARGE} error carrying the same id, and any other oversized message is refused to its local
 * sender while the session stays open (ADR-0106).</p>
 *
 * <p>This transport is shared by the MORPHEUS server and by the MINOS and NEXUS clients, and it knows no tool
 * catalog. Its own diagnostic therefore names the refusal and nothing else; the layer that owns a catalog may supply
 * a fixed piece of guidance at construction, appended verbatim.</p>
 */
public final class BoundedStdioServerTransportProvider implements McpServerTransportProvider {
    public static final int DEFAULT_MAX_FRAME_BYTES = 1024 * 1024;
    public static final int DEFAULT_MAX_PENDING_MESSAGES = 64;
    public static final String RESPONSE_TOO_LARGE = "MCP_RESPONSE_TOO_LARGE";
    /** Keeps the substitute error fixed-size whatever its owner supplies. */
    public static final int MAX_GUIDANCE_CHARS = 256;

    private static final System.Logger LOGGER =
            System.getLogger(BoundedStdioServerTransportProvider.class.getName());

    private final McpJsonMapper jsonMapper;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final int maxFrameBytes;
    private final int maxPendingMessages;
    private final String oversizedResponseGuidance;
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean failedClosed = new AtomicBoolean(false);
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
        this(jsonMapper, inputStream, outputStream, maxFrameBytes, maxPendingMessages, "");
    }

    /**
     * @param oversizedResponseGuidance what a client should do instead when a response overflows the frame, from the
     *                                  layer that knows the tools; empty for none. At most {@value #MAX_GUIDANCE_CHARS}
     *                                  characters, so the substitute error keeps a fixed bound.
     */
    public BoundedStdioServerTransportProvider(
            McpJsonMapper jsonMapper,
            InputStream inputStream,
            OutputStream outputStream,
            int maxFrameBytes,
            int maxPendingMessages,
            String oversizedResponseGuidance) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.inputStream = Objects.requireNonNull(inputStream, "inputStream");
        this.outputStream = Objects.requireNonNull(outputStream, "outputStream");
        if (maxFrameBytes < 1) throw new IllegalArgumentException("maxFrameBytes must be positive");
        if (maxPendingMessages < 1) throw new IllegalArgumentException("maxPendingMessages must be positive");
        this.maxFrameBytes = maxFrameBytes;
        this.maxPendingMessages = maxPendingMessages;
        this.oversizedResponseGuidance =
                Objects.requireNonNull(oversizedResponseGuidance, "oversizedResponseGuidance");
        if (oversizedResponseGuidance.length() > MAX_GUIDANCE_CHARS) {
            throw new IllegalArgumentException(
                    "oversizedResponseGuidance must not exceed " + MAX_GUIDANCE_CHARS + " characters");
        }
    }

    // java:S1181 catches Error deliberately: the transport's two worker executors exist before the factory runs,
    // and only a started transport can dispose them. An Error from the factory must release them just as a
    // RuntimeException does. The failure is rethrown unchanged, never swallowed.
    @Override
    @SuppressWarnings("java:S1181")
    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
        Objects.requireNonNull(sessionFactory, "sessionFactory");
        if (!initialized.compareAndSet(false, true)) {
            throw new IllegalStateException("MCP STDIO transport supports exactly one session");
        }
        // The transport allocates its two worker executors as soon as it is constructed, but only workerFinished()
        // disposes them, and that cannot run until start() has launched both workers. A session factory that
        // fails in between therefore used to leak both threads for the life of the process and leave
        // awaitTermination() waiting on a latch nothing would ever count down.
        BoundedSessionTransport createdTransport = new BoundedSessionTransport();
        this.transport.set(createdTransport);
        try {
            this.session.set(sessionFactory.create(createdTransport));
            createdTransport.start();
        } catch (RuntimeException | Error failure) {
            abandonUnstartedTransport(createdTransport, failure);
            throw failure;
        }
    }

    /**
     * Releases a transport that never reached a running session, and marks the provider terminated so a caller
     * blocked in {@link #awaitTermination()} is released instead of waiting forever.
     */
    @SuppressWarnings("java:S1181") // Releasing the workers must not itself be defeated by an Error.
    private void abandonUnstartedTransport(BoundedSessionTransport createdTransport, Throwable primary) {
        closing.set(true);
        try {
            createdTransport.releaseWorkers();
        } catch (RuntimeException | Error releaseFailure) {
            primary.addSuppressed(releaseFailure);
        }
        transport.set(null);
        session.set(null);
        terminated.countDown();
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

    /**
     * Whether the session was failed closed rather than ended by EOF or by a requested stop. Only the outcome is
     * exposed, never the cause: the cause may be peer-controlled, and it has already been logged through
     * {@link McpDiagnosticRedactor}.
     */
    public boolean terminatedInFailure() {
        return failedClosed.get();
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
                } catch (OutboundMessageRefusedException refused) {
                    return Mono.error(refused);
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
                BoundedStdioLineReader frames = new BoundedStdioLineReader(inputStream);
                while (!closing.get()) {
                    String line = frames.readLine(maxFrameBytes);
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
            byte[] encoded = serialize(message);
            if (encoded.length <= maxFrameBytes) return new OutboundFrame(encoded);
            if (message instanceof JSONRPCResponse response && response.id() != null) {
                return responseTooLarge(response.id(), encoded.length);
            }
            // A notification or a server-initiated request has no pending client request to answer, so there is
            // nothing to substitute on the wire. The session stays open, the trace names what was refused, and the
            // local sender receives an explicit error instead of a silent success.
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "MCP STDIO server refused an outbound {0} of {1} bytes past the {2}-byte frame bound",
                    OutboundMessageRefusedException.kindOf(message),
                    Integer.toString(encoded.length),
                    Integer.toString(maxFrameBytes));
            throw new OutboundMessageRefusedException(message, encoded.length, maxFrameBytes);
        }

        /**
         * Answers the pending request with a fixed-shape error naming the overflow, followed by the owner's guidance
         * when there is one. It carries sizes only, never a fragment of the content that overflowed. An error that
         * cannot fit either -- only possible when the peer chose an id close to the bound itself -- raises the ordinary
         * frame exception, which the caller fails closed.
         */
        private OutboundFrame responseTooLarge(Object id, int producedBytes) throws IOException {
            String diagnostic = RESPONSE_TOO_LARGE + ": the response is " + producedBytes + " bytes, past the "
                    + maxFrameBytes + "-byte MCP STDIO frame bound"
                    + (oversizedResponseGuidance.isEmpty() ? "" : "; " + oversizedResponseGuidance);
            byte[] error = serialize(JSONRPCResponse.error(
                    id, new JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.INTERNAL_ERROR, diagnostic)));
            if (error.length > maxFrameBytes) throw new MessageTooLargeException(maxFrameBytes);
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "MCP STDIO server replaced a {0}-byte response with {1}; the frame bound is {2} bytes",
                    Integer.toString(producedBytes),
                    RESPONSE_TOO_LARGE,
                    Integer.toString(maxFrameBytes));
            return new OutboundFrame(error);
        }

        private byte[] serialize(JSONRPCMessage message) throws IOException {
            String json = jsonMapper.writeValueAsString(message)
                    .replace("\r\n", "\\n")
                    .replace("\n", "\\n")
                    .replace("\r", "\\n");
            return json.getBytes(StandardCharsets.UTF_8);
        }

        private void requestStop() {
            closing.set(true);
            cancelActiveHandler();
            closeInputQuietly();
        }

        private void failClosed(Throwable failure) {
            if (closing.compareAndSet(false, true)) {
                failedClosed.set(true);
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
                try {
                    SchedulerRelease.disposeAll(inboundScheduler, outboundScheduler);
                } finally {
                    // Whoever waits on termination must be released even when a scheduler refused to stop.
                    terminated.countDown();
                }
            }
        }

        /**
         * Disposes the worker executors directly, for a transport whose workers never ran. Disposal is idempotent
         * in Reactor, so this stays safe if a worker did start and later reaches {@link #workerFinished()}.
         */
        private void releaseWorkers() {
            SchedulerRelease.disposeAll(inboundScheduler, outboundScheduler);
        }
    }

    private void closeInputQuietly() {
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // Closing stdin is best-effort cleanup used to unblock a pending read.
        }
    }
}
