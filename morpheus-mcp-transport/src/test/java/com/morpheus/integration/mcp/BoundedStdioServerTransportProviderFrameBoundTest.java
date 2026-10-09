package com.morpheus.integration.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that the frame bound reacts differently in each direction.
 *
 * <p>Inbound, the bound is defensive: a peer past it is hostile or broken, and the session is failed closed.
 * Outbound, the frame is MORPHEUS's own: a response past it is answered with a named error carrying the same id,
 * and the session keeps serving the next request.</p>
 */
class BoundedStdioServerTransportProviderFrameBoundTest {
    private static final int FRAME_BOUND = 512;
    private static final String GUIDANCE = "reduce limit, then page with offset";

    @Test
    void aResponseLargerThanTheFrameBoundIsAnsweredWithAnErrorAndTheSessionSurvives() throws Exception {
        String input = request(1) + request(2);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BoundedStdioServerTransportProvider provider = new BoundedStdioServerTransportProvider(
                McpJsonDefaults.getMapper(),
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                output,
                FRAME_BOUND,
                4,
                GUIDANCE);

        provider.setSessionFactory(transport -> echoSession(transport, id -> id.equals(1) ? "x".repeat(4096) : "ok"));

        assertTrue(provider.awaitTermination(Duration.ofSeconds(5)));
        List<String> frames = frames(output);
        assertEquals(2, frames.size(), () -> "both requests must be answered: " + frames);

        String oversized = frames.get(0);
        assertTrue(oversized.contains("\"id\":1"), oversized);
        assertTrue(oversized.contains("\"error\""), oversized);
        assertTrue(oversized.contains("MCP_RESPONSE_TOO_LARGE"), oversized);
        assertTrue(oversized.contains(GUIDANCE), () -> "the owner's guidance must reach the client: " + oversized);
        assertTrue(oversized.contains("offset") && oversized.contains("limit"), oversized);
        assertTrue(oversized.contains(Integer.toString(FRAME_BOUND)), oversized);
        assertFalse(oversized.contains("xxxx"), () -> "the error must not leak the overflowing content: " + oversized);

        String next = frames.get(1);
        assertTrue(next.contains("\"id\":2"), next);
        assertTrue(next.contains("\"ok\""), next);
        assertFalse(provider.terminatedInFailure(), "an oversized response is not a transport failure");
    }

    /**
     * This transport is shared by the MORPHEUS server and by the MINOS and NEXUS clients. It knows no tool catalog,
     * so its own diagnostic states the refusal -- the named state, the size produced, the bound -- and nothing else.
     */
    @Test
    void theOversizedResponseErrorDoesNotNameAToolTheTransportCannotKnow() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BoundedStdioServerTransportProvider provider = provider(
                new ByteArrayInputStream(request(1).getBytes(StandardCharsets.UTF_8)), output);

        provider.setSessionFactory(transport -> echoSession(transport, id -> "x".repeat(4096)));

        assertTrue(provider.awaitTermination(Duration.ofSeconds(5)));
        List<String> frames = frames(output);
        assertEquals(1, frames.size(), () -> "the request must be answered: " + frames);
        String oversized = frames.get(0);
        assertTrue(oversized.contains("MCP_RESPONSE_TOO_LARGE"), oversized);
        assertTrue(oversized.contains(Integer.toString(FRAME_BOUND)), oversized);
        assertFalse(oversized.contains("find_requirements"),
                () -> "a transport shared with MCP clients cannot name a server tool: " + oversized);
        assertFalse(oversized.contains(" such as "),
                () -> "without guidance from its owner the transport must not suggest a surface: " + oversized);
    }

    /** The guidance is appended to a substitute that must stay fixed-size, so its owner cannot make it unbounded. */
    @Test
    void guidanceLongerThanItsBoundIsRefusedAtConstruction() {
        String atTheBound = "g".repeat(BoundedStdioServerTransportProvider.MAX_GUIDANCE_CHARS);
        new BoundedStdioServerTransportProvider(McpJsonDefaults.getMapper(), new BlockingInputStream(),
                new ByteArrayOutputStream(), FRAME_BOUND, 4, atTheBound);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> new BoundedStdioServerTransportProvider(McpJsonDefaults.getMapper(), new BlockingInputStream(),
                        new ByteArrayOutputStream(), FRAME_BOUND, 4, atTheBound + "g"));
        assertEquals("oversizedResponseGuidance must not exceed "
                + BoundedStdioServerTransportProvider.MAX_GUIDANCE_CHARS + " characters", refused.getMessage());
        assertThrows(NullPointerException.class,
                () -> new BoundedStdioServerTransportProvider(McpJsonDefaults.getMapper(), new BlockingInputStream(),
                        new ByteArrayOutputStream(), FRAME_BOUND, 4, null));
    }

    @Test
    void anOversizedNotificationIsDroppedWithoutClosingTheSession() throws Exception {
        BlockingInputStream input = new BlockingInputStream();
        FrameSignallingOutputStream output = new FrameSignallingOutputStream();
        BoundedStdioServerTransportProvider provider = provider(input, output);

        McpSyncServer server = server(provider);
        try {
            assertThrows(RuntimeException.class, () -> provider.notifyClients(
                    "notifications/test", Map.of("value", "x".repeat(4096))).block());
            provider.notifyClients("notifications/test", Map.of("value", "still-open")).block();

            assertFalse(provider.awaitTermination(Duration.ofMillis(300)),
                    "an oversized notification must not close the session");
            assertTrue(output.awaitFrame(Duration.ofSeconds(5)), "the next notification must reach the wire");
            List<String> frames = frames(output);
            assertEquals(1, frames.size(), () -> "only the small notification may be written: " + frames);
            assertTrue(frames.get(0).contains("still-open"), frames.get(0));
        } finally {
            server.close();
        }
        assertTrue(provider.awaitTermination(Duration.ofSeconds(5)));
        assertFalse(provider.terminatedInFailure(), "a requested stop is not a transport failure");
    }

    @Test
    void aResponseWhoseErrorSubstituteAlsoOverflowsStillFailsClosed() throws Exception {
        String longId = "i".repeat(FRAME_BOUND - 72);
        String input = "{\"jsonrpc\":\"2.0\",\"id\":\"" + longId + "\",\"method\":\"test/echo\",\"params\":{}}\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BoundedStdioServerTransportProvider provider = new BoundedStdioServerTransportProvider(
                McpJsonDefaults.getMapper(),
                new SequenceInputStream(
                        new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), new BlockingInputStream()),
                output,
                FRAME_BOUND,
                4);

        provider.setSessionFactory(transport -> echoSession(transport, id -> "x".repeat(FRAME_BOUND * 8)));

        assertTrue(provider.awaitTermination(Duration.ofSeconds(5)),
                "an error substitute that cannot fit is a real failure and must still fail closed");
        assertTrue(provider.terminatedInFailure());
        assertEquals(List.of(), frames(output));
    }

    @Test
    void anInboundFrameOverTheBoundStillFailsClosed() throws Exception {
        String oversized = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/test\",\"params\":{\"value\":\""
                + "x".repeat(FRAME_BOUND * 2) + "\"}}\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BoundedStdioServerTransportProvider provider = provider(
                new SequenceInputStream(
                        new ByteArrayInputStream(oversized.getBytes(StandardCharsets.UTF_8)), new BlockingInputStream()),
                output);

        provider.setSessionFactory(transport -> echoSession(transport, id -> "never"));

        assertTrue(provider.awaitTermination(Duration.ofSeconds(5)),
                "an inbound frame past the bound must close the session, not wait for more input");
        assertTrue(provider.terminatedInFailure());
        assertEquals(List.of(), frames(output));
    }

    private static BoundedStdioServerTransportProvider provider(InputStream input, ByteArrayOutputStream output) {
        return new BoundedStdioServerTransportProvider(McpJsonDefaults.getMapper(), input, output, FRAME_BOUND, 4);
    }

    private static String request(int id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"test/echo\",\"params\":{}}\n";
    }

    private static McpServerSession echoSession(
            McpServerTransport transport, java.util.function.Function<Object, String> payload) {
        return new McpServerSession("frame-bound-session", Duration.ofSeconds(30), transport, null, Map.of(), Map.of()) {
            @Override
            public Mono<Void> handle(McpSchema.JSONRPCMessage message) {
                McpSchema.JSONRPCRequest request = (McpSchema.JSONRPCRequest) message;
                return transport.sendMessage(
                        McpSchema.JSONRPCResponse.result(request.id(), Map.of("value", payload.apply(request.id()))));
            }
        };
    }

    private static McpSyncServer server(BoundedStdioServerTransportProvider provider) {
        return McpServer.sync(provider)
                .serverInfo("bounded-server", "1.0")
                .capabilities(McpSchema.ServerCapabilities.builder().build())
                .build();
    }

    private static List<String> frames(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8).lines().filter(line -> !line.isBlank()).toList();
    }

    /** The transport's writer flushes after every frame, so a flush is the signal that a frame reached the wire. */
    private static final class FrameSignallingOutputStream extends ByteArrayOutputStream {
        private final CountDownLatch frameWritten = new CountDownLatch(1);

        @Override
        public void flush() {
            frameWritten.countDown();
        }

        boolean awaitFrame(Duration timeout) throws InterruptedException {
            return frameWritten.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private static final class BlockingInputStream extends InputStream {
        private final CountDownLatch closed = new CountDownLatch(1);

        @Override
        public int read() throws IOException {
            try {
                closed.await();
                return -1;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("input interrupted", interrupted);
            }
        }

        @Override
        public void close() {
            closed.countDown();
        }
    }
}
