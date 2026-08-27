package com.morpheus.integration.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedStdioClientTransportTest {
    @TempDir
    Path tempDir;

    @Test
    void exchangesJsonRpcFramesOverRealStdioProcess() {
        BoundedStdioClientTransport transport = transport(64 * 1024);
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(5))
                .build();
        try {
            client.initialize();
            assertTrue(client.listTools().tools().stream()
                    .anyMatch(tool -> tool.name().equals(FixtureBoundedMcpServer.TOOL_ECHO)));

            var result = client.callTool(CallToolRequest.builder(FixtureBoundedMcpServer.TOOL_ECHO)
                    .arguments(Map.of("value", "hello-bounded-stdio"))
                    .build());

            assertFalse(Boolean.TRUE.equals(result.isError()));
            assertEquals(1, result.content().size());
            assertEquals("hello-bounded-stdio", ((TextContent) result.content().getFirst()).text());
        } finally {
            client.closeGracefully();
        }
    }

    @Test
    void childEnvironmentDropsInheritedSecretsAndKeepsExplicitConfiguration() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("PATH", "safe-path");
        environment.put("LANG", "fr_FR.UTF-8");
        environment.put("MORPHEUS_SERVER_TLS_PASSWORD", "must-not-leak");
        environment.put("JAVA_TOOL_OPTIONS", "-Dinherited=true");

        BoundedStdioClientTransport.sanitizeEnvironment(
                environment,
                Map.of("MCP_EXPLICIT_SETTING", "kept", "JAVA_TOOL_OPTIONS", "-Dexplicit=true"));

        assertEquals("safe-path", environment.get("PATH"));
        assertEquals("fr_FR.UTF-8", environment.get("LANG"));
        assertFalse(environment.containsKey("MORPHEUS_SERVER_TLS_PASSWORD"));
        assertEquals("-Dexplicit=true", environment.get("JAVA_TOOL_OPTIONS"));
        assertEquals("kept", environment.get("MCP_EXPLICIT_SETTING"));
    }

    @Test
    void closesDescendantObservedBeforePeerParentExits() throws Exception {
        Path childPidFile = tempDir.resolve("mcp-child.pid");
        Path parentExitMarker = tempDir.resolve("mcp-parent-exit.pid");
        BoundedStdioClientTransport transport = new BoundedStdioClientTransport(
                peerParameters(
                        FixtureOrphaningMcpPeer.class,
                        childPidFile.toString(),
                        parentExitMarker.toString()),
                McpJsonDefaults.getMapper(),
                4096);
        long childPid = -1L;
        try {
            transport.connect(message -> message).block();
            childPid = awaitPublishedPid(childPidFile, Duration.ofSeconds(5));
            long parentPid = awaitPublishedPid(parentExitMarker, Duration.ofSeconds(5));

            awaitCondition(Duration.ofSeconds(5), () -> !isAlive(parentPid));
            assertTrue(isAlive(childPid), "fixture descendant must still be alive after its MCP parent exits");

            transport.closeGracefully().block();
            long retainedChildPid = childPid;
            awaitCondition(Duration.ofSeconds(5), () -> !isAlive(retainedChildPid));
        } finally {
            transport.closeGracefully().block();
            if (childPid > 0) {
                ProcessHandle.of(childPid).filter(ProcessHandle::isAlive).ifPresent(ProcessHandle::destroyForcibly);
            }
        }
    }

    @Test
    void aggregateInboundBudgetIncludesActiveHandlersAndFailsClosed() throws Exception {
        BoundedStdioClientTransport transport = new BoundedStdioClientTransport(
                peerParameters(FixtureFloodingMcpPeer.class),
                McpJsonDefaults.getMapper(),
                4096,
                1);
        CountDownLatch firstHandlerStarted = new CountDownLatch(1);
        try {
            transport.connect(message -> {
                firstHandlerStarted.countDown();
                return Mono.never();
            }).block();

            assertTrue(firstHandlerStarted.await(2, TimeUnit.SECONDS));
            McpSchema.JSONRPCNotification outbound =
                    new McpSchema.JSONRPCNotification("notifications/test", Map.of("value", "probe"));
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                while (true) {
                    try {
                        transport.sendMessage(outbound).block();
                        TimeUnit.MILLISECONDS.sleep(10);
                    } catch (RuntimeException expectedClosedTransport) {
                        return;
                    }
                }
            });
        } finally {
            transport.closeGracefully().block();
        }
    }

    @Test
    void terminatesPeerWhenRealInboundFrameExceedsTransportLimit() {
        BoundedStdioClientTransport transport = transport(2048);
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(2))
                .build();
        try {
            client.initialize();
            assertThrows(RuntimeException.class, () -> client.callTool(
                    CallToolRequest.builder(FixtureBoundedMcpServer.TOOL_LARGE)
                            .arguments(Map.of("size", 8192))
                            .build()));
        } finally {
            client.closeGracefully();
        }
    }

    @Test
    void failsClosedWhenOutboundQueueCapacityIsExceeded() {
        BoundedStdioClientTransport transport = transport(1024, 1);
        McpSchema.JSONRPCNotification message =
                new McpSchema.JSONRPCNotification("notifications/test", Map.of("value", "bounded"));

        transport.sendMessage(message).block();

        assertThrows(RuntimeException.class, () -> transport.sendMessage(message).block());
    }

    @Test
    void rejectsOversizedOutboundFrameBeforeEnqueue() {
        BoundedStdioClientTransport transport = transport(128, 4);
        McpSchema.JSONRPCNotification message = new McpSchema.JSONRPCNotification(
                "notifications/test", Map.of("value", "x".repeat(512)));

        assertThrows(RuntimeException.class, () -> transport.sendMessage(message).block());
    }

    @Test
    void cleansUpSchedulersWhenPeerProcessCannotStart() {
        ServerParameters parameters = ServerParameters.builder("morpheus-command-that-does-not-exist-20260822").build();
        BoundedStdioClientTransport transport = new BoundedStdioClientTransport(
                parameters, McpJsonDefaults.getMapper(), 1024);

        assertThrows(RuntimeException.class, () -> transport.connect(message -> message).block());
    }

    @Test
    void acceptsCustomStderrHandler() {
        BoundedStdioClientTransport transport = transport(1024);
        try {
            transport.setStdErrorHandler(ignored -> { });
        } finally {
            transport.closeGracefully().block();
        }
    }

    @Test
    void rejectsNonPositiveTransportLimits() {
        ServerParameters parameters = serverParameters();

        assertThrows(IllegalArgumentException.class, () -> new BoundedStdioClientTransport(
                parameters, McpJsonDefaults.getMapper(), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new BoundedStdioClientTransport(
                parameters, McpJsonDefaults.getMapper(), 1024, 0));
    }

    @Test
    void acceptsFrameAtExactByteLimitAndStripsCrLfDelimiter() throws Exception {
        String json = "{\"id\":1}";
        byte[] line = (json + "\r\n").getBytes(StandardCharsets.UTF_8);

        assertEquals(json, BoundedStdioClientTransport.readUtf8LineBounded(
                new ByteArrayInputStream(line), json.getBytes(StandardCharsets.UTF_8).length + 1));
    }

    @Test
    void rejectsFrameBeforeCreatingStringPastByteLimit() {
        byte[] oversized = "12345\n".getBytes(StandardCharsets.UTF_8);

        assertThrows(BoundedStdioClientTransport.MessageTooLargeException.class, () ->
                BoundedStdioClientTransport.readUtf8LineBounded(new ByteArrayInputStream(oversized), 4));
    }

    @Test
    void countsUtf8BytesRatherThanCharacters() {
        byte[] multibyte = "é\n".getBytes(StandardCharsets.UTF_8);

        assertThrows(BoundedStdioClientTransport.MessageTooLargeException.class, () ->
                BoundedStdioClientTransport.readUtf8LineBounded(new ByteArrayInputStream(multibyte), 1));
    }

    @Test
    void returnsNullForCleanEndOfStream() throws Exception {
        assertNull(BoundedStdioClientTransport.readUtf8LineBounded(
                new ByteArrayInputStream(new byte[0]), 16));
    }

    private BoundedStdioClientTransport transport(int maxBytes) {
        return new BoundedStdioClientTransport(
                serverParameters(),
                McpJsonDefaults.getMapper(),
                maxBytes);
    }

    private BoundedStdioClientTransport transport(int maxBytes, int maxPendingMessages) {
        return new BoundedStdioClientTransport(
                serverParameters(),
                McpJsonDefaults.getMapper(),
                maxBytes,
                maxPendingMessages);
    }

    private ServerParameters serverParameters() {
        return peerParameters(FixtureBoundedMcpServer.class);
    }

    private ServerParameters peerParameters(Class<?> mainClass, String... extraArguments) {
        List<String> arguments = new ArrayList<>(peerArguments(mainClass));
        arguments.addAll(List.of(extraArguments));
        return ServerParameters.builder(javaExecutable())
                .args(arguments.toArray(String[]::new))
                .build();
    }

    private String javaExecutable() {
        return Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java")
                .toString();
    }

    private List<String> peerArguments(Class<?> mainClass) {
        String testClasspath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path"));
        return List.of("-cp", testClasspath, mainClass.getName());
    }

    private long awaitPublishedPid(Path path, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            if (Files.isRegularFile(path)) {
                try {
                    String published = Files.readString(path).trim();
                    if (!published.isEmpty()) return Long.parseLong(published);
                } catch (IOException | NumberFormatException transientPublicationRace) {
                    // Files.writeString may make the entry visible before the PID bytes are observable.
                }
            }
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("PID was not published within " + timeout + ": " + path.getFileName());
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
    }

    private void awaitCondition(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("condition was not satisfied within " + timeout);
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
    }

    private boolean isAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }
}
