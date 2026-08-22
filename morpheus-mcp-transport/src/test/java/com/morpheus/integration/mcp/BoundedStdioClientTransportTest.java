package com.morpheus.integration.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedStdioClientTransportTest {

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
        return transport(maxBytes, BoundedStdioClientTransport.DEFAULT_MAX_PENDING_MESSAGES);
    }

    private BoundedStdioClientTransport transport(int maxBytes, int maxPendingMessages) {
        ServerParameters parameters = ServerParameters.builder(javaExecutable())
                .args(serverArguments().toArray(String[]::new))
                .build();
        return new BoundedStdioClientTransport(
                parameters,
                McpJsonDefaults.getMapper(),
                maxBytes,
                maxPendingMessages);
    }

    private String javaExecutable() {
        return Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java")
                .toString();
    }

    private List<String> serverArguments() {
        String testClasspath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path"));
        return List.of("-cp", testClasspath, FixtureBoundedMcpServer.class.getName());
    }
}
