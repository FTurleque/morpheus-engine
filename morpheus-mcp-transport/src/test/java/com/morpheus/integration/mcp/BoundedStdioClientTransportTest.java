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

import java.io.IOException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

            BoundedWait.until(
                    "the MCP parent process to exit, leaving its descendant orphaned",
                    Duration.ofSeconds(5),
                    BoundedWait.PROCESS_TRANSITION_POLL,
                    () -> !isAlive(parentPid),
                    () -> "parent " + parentPid + " alive=" + isAlive(parentPid));
            assertTrue(isAlive(childPid), "fixture descendant must still be alive after its MCP parent exits");

            transport.closeGracefully().block();
            long retainedChildPid = childPid;
            BoundedWait.until(
                    "the retained descendant to be terminated by closeGracefully",
                    Duration.ofSeconds(5),
                    BoundedWait.PROCESS_TRANSITION_POLL,
                    () -> !isAlive(retainedChildPid),
                    () -> "descendant " + retainedChildPid + " alive=" + isAlive(retainedChildPid));
        } finally {
            transport.closeGracefully().block();
            if (childPid > 0) {
                ProcessHandle.of(childPid).filter(ProcessHandle::isAlive).ifPresent(ProcessHandle::destroyForcibly);
            }
        }
    }

    /**
     * A peer that spawns a child and then halts in the same instant is outside what descendant observation can
     * cover: once the peer is gone the operating system has re-parented the child and no portable API still
     * attributes it to that peer. This test pins what MORPHEUS does guarantee in that case — it shuts down cleanly,
     * without stalling, leaking its own state, or reporting success it cannot back. The surviving grandchild is a
     * documented limitation of the stdio boundary, not a sandbox failure; see SECURITY.md.
     */
    @Test
    void peerThatOrphansAChildBeforeObservationStillShutsMorpheusDownCleanly() throws Exception {
        Path childPidFile = tempDir.resolve("immediate-exit-child.pid");
        Path peerPidFile = tempDir.resolve("immediate-exit-peer.pid");
        BoundedStdioClientTransport transport = new BoundedStdioClientTransport(
                peerParameters(
                        FixtureImmediateExitOrphaningMcpPeer.class,
                        childPidFile.toString(),
                        peerPidFile.toString()),
                McpJsonDefaults.getMapper(),
                4096);
        long childPid = -1L;
        try {
            transport.connect(message -> message).block();
            long peerPid = awaitPublishedPid(peerPidFile, Duration.ofSeconds(10));
            childPid = awaitPublishedPid(childPidFile, Duration.ofSeconds(10));

            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> transport.closeGracefully().block());
            // Idempotent close is part of the contract MORPHEUS does guarantee here.
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> transport.closeGracefully().block());

            assertFalse(isAlive(peerPid), "the MCP peer itself must always be terminated");
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
            // java:S2925, category one of three: a bounded poll of external state. The condition is that the
            // transport has observably closed, which it reports by throwing rather than by signalling, so the
            // loop retries until it does and assertTimeoutPreemptively is the bound. BoundedWait is the wrong
            // shape here: what is awaited is an exception, not a value a supplier could report.
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
    void handleErrorLineSurvivesThrowingStderrHandlerAndKeepsTransportUsable() throws Exception {
        BoundedStdioClientTransport transport = new BoundedStdioClientTransport(
                peerParameters(FixtureStderrChattyMcpServer.class),
                McpJsonDefaults.getMapper(),
                4096);
        CountDownLatch handlerInvoked = new CountDownLatch(1);
        transport.setStdErrorHandler(line -> {
            handlerInvoked.countDown();
            throw new RuntimeException("boom from stderr handler");
        });
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(5))
                .build();
        try {
            client.initialize();
            assertTrue(handlerInvoked.await(5, TimeUnit.SECONDS),
                    "stderr handler was never invoked with the fixture's diagnostic line");
            assertTrue(client.listTools().tools().stream()
                            .anyMatch(tool -> tool.name().equals(FixtureStderrChattyMcpServer.TOOL_ECHO)),
                    "transport must remain usable after its stderr handler threw");
        } finally {
            client.closeGracefully();
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

    private long awaitPublishedPid(Path path, Duration timeout) throws Exception {
        return BoundedWait.untilObserved(
                "the child process to publish its PID into " + path.getFileName(),
                timeout,
                BoundedWait.FILE_PUBLICATION_POLL,
                () -> readPublishedPid(path),
                published -> published > 0);
    }

    /** Returns 0 while the file is absent or half-written: publication is not atomic, so a partial read is normal. */
    private long readPublishedPid(Path path) {
        if (!Files.isRegularFile(path)) return 0L;
        try {
            String published = Files.readString(path).trim();
            return published.isEmpty() ? 0L : Long.parseLong(published);
        } catch (IOException | NumberFormatException transientPublicationRace) {
            // Files.writeString may make the entry visible before the PID bytes are observable.
            return 0L;
        }
    }

    private boolean isAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }
}
