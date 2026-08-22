package com.morpheus.cli;

import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MorpheusMcpStdioIntegrationTest {
    private static final int SERVER_MAX_FRAME_BYTES = 1024 * 1024;

    @TempDir
    Path tempDirectory;

    @Test
    void negotiatesListsCallsAndRejectsInvalidArgumentsOverRealStdio() throws Exception {
        Path database = tempDirectory.resolve("morpheus.db");
        Path stderr = tempDirectory.resolve("mcp-stderr.log");
        Process process = startProcess(database, stderr);

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            send(writer, """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"morpheus-m10-test","version":"1.0"}}}
                    """);
            String initialized = readLine(reader, process, stderr, Duration.ofSeconds(10));
            assertTrue(initialized.contains("\"id\":1"), initialized);
            assertTrue(initialized.contains("morpheus"), initialized);

            send(writer, """
                    {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                    """);
            send(writer, """
                    {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                    """);
            String tools = readLine(reader, process, stderr, Duration.ofSeconds(10));
            assertTrue(tools.contains("get_current_specification"), tools);
            assertTrue(tools.contains("get_sync_status"), tools);
            assertTrue(tools.contains("get_blocking_conditions"), tools);

            String projectId = ProjectSpecificationId.generate().toString();
            send(writer, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"get_sync_status\",\"arguments\":{\"projectId\":\"" + projectId + "\"}}}");
            String sync = readLine(reader, process, stderr, Duration.ofSeconds(10));
            assertTrue(sync.contains("\"id\":3"), sync);
            assertTrue(sync.contains("UNKNOWN"), sync);
            assertTrue(sync.contains(projectId), sync);

            String requirementId = RequirementId.generate().toString();
            send(writer, "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"trace_requirement\",\"arguments\":{\"projectId\":\"" + projectId + "\",\"requirementId\":\"" + requirementId + "\",\"depth\":99}}}");
            String rejected = readLine(reader, process, stderr, Duration.ofSeconds(10));
            assertTrue(rejected.contains("\"id\":4"), rejected);
            assertTrue(rejected.contains("isError") || rejected.contains("error"), rejected);
        } finally {
            terminate(process);
        }
    }

    @Test
    void exitsWhenClientClosesStdin() throws Exception {
        Path database = tempDirectory.resolve("eof.db");
        Path stderr = tempDirectory.resolve("eof-stderr.log");
        Process process = startProcess(database, stderr);

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            send(writer, """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"morpheus-eof-test","version":"1.0"}}}
                    """);
            String initialized = readLine(reader, process, stderr, Duration.ofSeconds(10));
            assertTrue(initialized.contains("\"id\":1"), initialized);

            writer.close();
            boolean exited = process.waitFor(5, TimeUnit.SECONDS);
            String stderrOutput = readStderr(stderr);
            assertTrue(exited, "MCP process did not exit after stdin EOF; stderr=" + stderrOutput);
            assertEquals(0, process.exitValue(), "stderr=" + stderrOutput);
        } finally {
            terminate(process);
        }
    }

    @Test
    void exitsWhenInboundFrameExceedsServerTransportBound() throws Exception {
        Path database = tempDirectory.resolve("oversized.db");
        Path stderr = tempDirectory.resolve("oversized-stderr.log");
        Process process = startProcess(database, stderr);

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write("x".repeat(SERVER_MAX_FRAME_BYTES + 1));
            writer.newLine();
            writer.flush();

            boolean exited = process.waitFor(5, TimeUnit.SECONDS);
            assertTrue(exited,
                    "MCP process did not fail closed after oversized frame; stderr=" + readStderr(stderr));
        } finally {
            terminate(process);
        }
    }

    private Process startProcess(Path database, Path stderr) throws IOException {
        return new ProcessBuilder(
                javaExecutable().toString(),
                "-cp", System.getProperty("java.class.path"),
                MorpheusMain.class.getName(),
                "--db", database.toString(),
                "mcp", "--stdio")
                .redirectError(stderr.toFile())
                .start();
    }

    private void terminate(Process process) throws InterruptedException {
        if (!process.isAlive()) return;
        process.destroy();
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
        }
    }

    private void send(BufferedWriter writer, String message) throws IOException {
        writer.write(message.strip());
        writer.newLine();
        writer.flush();
    }

    private String readLine(BufferedReader reader, Process process, Path stderr, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (reader.ready()) {
                String line = reader.readLine();
                if (line != null) return line;
            }
            if (!process.isAlive()) {
                fail("MCP process exited with " + process.exitValue() + "; stderr=" + readStderr(stderr));
            }
            Thread.sleep(10L);
        }
        fail("Timed out waiting for MCP response; stderr=" + readStderr(stderr));
        return "";
    }

    private String readStderr(Path path) throws IOException {
        return Files.exists(path) ? Files.readString(path) : "";
    }

    private Path javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }
}
