package com.morpheus.cli;

import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusMcpStdioIntegrationTest {
    private static final int SERVER_MAX_FRAME_BYTES = 1024 * 1024;

    @TempDir
    Path tempDirectory;

    @Test
    void negotiatesListsCallsAndRejectsInvalidArgumentsOverRealStdio() throws Exception {
        try (McpStdioSession session = McpStdioSession.start(tempDirectory.resolve("morpheus.db"))) {
            session.send("""
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"morpheus-m10-test","version":"1.0"}}}
                    """);
            String initialized = session.readLine(Duration.ofSeconds(10));
            assertTrue(initialized.contains("\"id\":1"), initialized);
            assertTrue(initialized.contains("morpheus"), initialized);

            session.send("""
                    {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                    """);
            session.send("""
                    {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                    """);
            String tools = session.readLine(Duration.ofSeconds(10));
            assertTrue(tools.contains("get_current_specification"), tools);
            assertTrue(tools.contains("get_sync_status"), tools);
            assertTrue(tools.contains("get_blocking_conditions"), tools);

            String projectId = ProjectSpecificationId.generate().toString();
            session.send("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"get_sync_status\",\"arguments\":{\"projectId\":\"" + projectId + "\"}}}");
            String sync = session.readLine(Duration.ofSeconds(10));
            assertTrue(sync.contains("\"id\":3"), sync);
            assertTrue(sync.contains("UNKNOWN"), sync);
            assertTrue(sync.contains(projectId), sync);

            String requirementId = RequirementId.generate().toString();
            session.send("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"trace_requirement\",\"arguments\":{\"projectId\":\"" + projectId + "\",\"requirementId\":\"" + requirementId + "\",\"depth\":99}}}");
            String rejected = session.readLine(Duration.ofSeconds(10));
            assertTrue(rejected.contains("\"id\":4"), rejected);
            assertTrue(rejected.contains("isError") || rejected.contains("error"), rejected);
        }
    }

    @Test
    void exitsWhenClientClosesStdin() throws Exception {
        try (McpStdioSession session = McpStdioSession.start(tempDirectory.resolve("eof.db"))) {
            session.send("""
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"morpheus-eof-test","version":"1.0"}}}
                    """);
            String initialized = session.readLine(Duration.ofSeconds(10));
            assertTrue(initialized.contains("\"id\":1"), initialized);

            session.closeStdin();
            boolean exited = session.process().waitFor(5, TimeUnit.SECONDS);
            assertTrue(exited, "MCP process did not exit after stdin EOF; stderr=" + session.stderr());
            assertEquals(0, session.process().exitValue(), "stderr=" + session.stderr());
        }
    }

    @Test
    void exitsWhenInboundFrameExceedsServerTransportBound() throws Exception {
        try (McpStdioSession session = McpStdioSession.start(tempDirectory.resolve("oversized.db"))) {
            session.send("x".repeat(SERVER_MAX_FRAME_BYTES + 1));

            boolean exited = session.process().waitFor(5, TimeUnit.SECONDS);
            assertTrue(exited,
                    "MCP process did not fail closed after oversized frame; stderr=" + session.stderr());
        }
    }
}
