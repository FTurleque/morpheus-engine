package com.morpheus.integration.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedStdioServerTransportProviderTest {

    @Test
    void initializesOverBoundedStdioAndTerminatesAtEof() throws Exception {
        String input = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"bounded-test","version":"1.0"}}}
                {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                """;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BoundedStdioServerTransportProvider provider = new BoundedStdioServerTransportProvider(
                McpJsonDefaults.getMapper(),
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                output,
                64 * 1024,
                4);

        McpSyncServer server = server(provider);
        try {
            assertTrue(provider.awaitTermination(Duration.ofSeconds(5)));
        } finally {
            server.close();
        }

        String response = output.toString(StandardCharsets.UTF_8);
        assertTrue(response.contains("\"id\":1"), response);
        assertTrue(response.contains("bounded-server"), response);
    }

    @Test
    void closeGracefullyCancelsNonTerminatingActiveHandler() throws Exception {
        String input = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/test\",\"params\":{}}\n";
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch handlerCancelled = new CountDownLatch(1);
        BoundedStdioServerTransportProvider provider = new BoundedStdioServerTransportProvider(
                McpJsonDefaults.getMapper(),
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream(),
                4096,
                4);

        provider.setSessionFactory(transport -> new McpServerSession(
                "non-terminating-session",
                Duration.ofSeconds(30),
                transport,
                null,
                Map.of(),
                Map.of()) {
            @Override
            public Mono<Void> handle(McpSchema.JSONRPCMessage message) {
                handlerStarted.countDown();
                return Mono.<Void>never().doOnCancel(handlerCancelled::countDown);
            }
        });

        assertTrue(handlerStarted.await(2, TimeUnit.SECONDS));
        provider.closeGracefully().block();
        assertTrue(handlerCancelled.await(2, TimeUnit.SECONDS));
        assertTrue(provider.awaitTermination(Duration.ofSeconds(2)));
    }

    @Test
    void failsClosedWhenSerializedOutboundFrameExceedsLimit() throws Exception {
        BlockingInputStream input = new BlockingInputStream();
        BoundedStdioServerTransportProvider provider = new BoundedStdioServerTransportProvider(
                McpJsonDefaults.getMapper(), input, new ByteArrayOutputStream(), 128, 4);

        McpSyncServer server = server(provider);
        try {
            assertThrows(RuntimeException.class, () -> provider.notifyClients(
                    "notifications/test", Map.of("value", "x".repeat(1024))).block());
            assertTrue(provider.awaitTermination(Duration.ofSeconds(5)));
        } finally {
            server.close();
        }
    }

    @Test
    void failsClosedWhenOutboundQueueCapacityIsExceeded() throws Exception {
        BlockingInputStream input = new BlockingInputStream();
        BlockingOutputStream output = new BlockingOutputStream();
        BoundedStdioServerTransportProvider provider = new BoundedStdioServerTransportProvider(
                McpJsonDefaults.getMapper(), input, output, 4096, 1);

        McpSyncServer server = server(provider);
        try {
            provider.notifyClients("notifications/test", Map.of("sequence", 1)).block();
            assertTrue(output.awaitWrite(Duration.ofSeconds(2)));
            provider.notifyClients("notifications/test", Map.of("sequence", 2)).block();
            assertThrows(RuntimeException.class, () -> provider.notifyClients(
                    "notifications/test", Map.of("sequence", 3)).block());
            output.release();
            assertTrue(provider.awaitTermination(Duration.ofSeconds(5)));
        } finally {
            output.release();
            server.close();
        }
    }

    @Test
    void rejectsUnknownSessionAndSupportsCloseBeforeSessionCreation() throws Exception {
        BlockingInputStream input = new BlockingInputStream();
        BoundedStdioServerTransportProvider provider = new BoundedStdioServerTransportProvider(
                McpJsonDefaults.getMapper(), input, new ByteArrayOutputStream(), 4096, 4);

        McpSyncServer server = server(provider);
        try {
            assertThrows(RuntimeException.class, () -> provider.notifyClient(
                    "not-the-session", "notifications/test", Map.of()).block());
        } finally {
            server.close();
        }
        assertTrue(provider.awaitTermination(Duration.ofSeconds(5)));

        BoundedStdioServerTransportProvider unopened = new BoundedStdioServerTransportProvider(
                McpJsonDefaults.getMapper(), new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), 64, 1);
        unopened.closeGracefully().block();
        assertTrue(unopened.awaitTermination(Duration.ofSeconds(1)));
    }

    @Test
    void validatesResourceBudgets() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedStdioServerTransportProvider(
                McpJsonDefaults.getMapper(), new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new BoundedStdioServerTransportProvider(
                McpJsonDefaults.getMapper(), new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), 1, 0));
    }

    @Test
    void convenienceConstructorsUseBoundedDefaultsAndCanCloseBeforeSessionCreation() throws Exception {
        BoundedStdioServerTransportProvider defaults =
                new BoundedStdioServerTransportProvider(McpJsonDefaults.getMapper());
        defaults.closeGracefully().block();
        assertTrue(defaults.awaitTermination(Duration.ofSeconds(1)));

        BoundedStdioServerTransportProvider customFrameLimit =
                new BoundedStdioServerTransportProvider(McpJsonDefaults.getMapper(), 2048);
        customFrameLimit.closeGracefully().block();
        assertTrue(customFrameLimit.awaitTermination(Duration.ofSeconds(1)));
    }

    @Test
    void acceptsInboundFrameAtConfiguredBoundary() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\"}";
        byte[] frame = (json + "\n").getBytes(StandardCharsets.UTF_8);

        assertEquals(json, BoundedStdioServerTransportProvider.readUtf8LineBounded(
                new ByteArrayInputStream(frame), json.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    void rejectsInboundFrameBeforeMaterializingPastByteLimit() {
        byte[] frame = "12345\n".getBytes(StandardCharsets.UTF_8);

        assertThrows(BoundedStdioServerTransportProvider.MessageTooLargeException.class, () ->
                BoundedStdioServerTransportProvider.readUtf8LineBounded(new ByteArrayInputStream(frame), 4));
    }

    @Test
    void countsUtf8BytesInsteadOfCharacters() {
        byte[] frame = "é\n".getBytes(StandardCharsets.UTF_8);

        assertThrows(BoundedStdioServerTransportProvider.MessageTooLargeException.class, () ->
                BoundedStdioServerTransportProvider.readUtf8LineBounded(new ByteArrayInputStream(frame), 1));
    }

    private McpSyncServer server(BoundedStdioServerTransportProvider provider) {
        return McpServer.sync(provider)
                .serverInfo("bounded-server", "1.0")
                .capabilities(McpSchema.ServerCapabilities.builder().build())
                .build();
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

    private static final class BlockingOutputStream extends OutputStream {
        private final CountDownLatch writeStarted = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        @Override
        public void write(int value) throws IOException {
            awaitRelease();
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            awaitRelease();
        }

        boolean awaitWrite(Duration timeout) throws InterruptedException {
            return writeStarted.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        void release() {
            released.countDown();
        }

        private void awaitRelease() throws IOException {
            writeStarted.countDown();
            try {
                released.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("output interrupted", interrupted);
            }
        }
    }
}
