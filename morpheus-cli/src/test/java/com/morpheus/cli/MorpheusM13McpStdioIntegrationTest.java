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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MorpheusM13McpStdioIntegrationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void discoversBothM13ToolsOverRealStdioWithoutNexusInstalled() throws Exception {
        Path database = tempDirectory.resolve("m13-mcp.db");
        Path stderr = tempDirectory.resolve("m13-mcp-stderr.log");
        String testClasspath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path"));

        Process process = new ProcessBuilder(
                javaExecutable().toString(),
                "-cp", testClasspath,
                MorpheusMain.class.getName(),
                "--db", database.toString(),
                "mcp", "--stdio")
                .redirectError(stderr.toFile())
                .start();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            send(writer, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"m13-test\",\"version\":\"1\"}}}");
            readLine(reader, process, stderr, Duration.ofSeconds(10));
            send(writer, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}");
            send(writer, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
            String tools = readLine(reader, process, stderr, Duration.ofSeconds(10));
            assertTrue(tools.contains("get_augmented_requirement_context"), tools);
            assertTrue(tools.contains("get_augmented_change_context"), tools);
            assertTrue(tools.contains("list_external_references"), tools);

            String projectId = ProjectSpecificationId.generate().toString();
            String requirementId = RequirementId.generate().toString();
            send(writer, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"get_augmented_requirement_context\",\"arguments\":{\"projectId\":\""
                    + projectId + "\",\"requirementId\":\"" + requirementId
                    + "\",\"nexusProject\":\"morpheus-engine\"}}}");
            String missingProject = readLine(reader, process, stderr, Duration.ofSeconds(10));
            assertTrue(missingProject.contains("isError") || missingProject.contains("error"), missingProject);
            assertTrue(missingProject.contains("project not found"), missingProject);
        } finally {
            process.destroy();
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
    }

    private void send(BufferedWriter writer, String message) throws IOException {
        writer.write(message);
        writer.newLine();
        writer.flush();
    }

    private String readLine(BufferedReader reader, Process process, Path stderr, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (reader.ready()) {
                String line = reader.readLine();
                if (line != null) {
                    return line;
                }
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
