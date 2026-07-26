package com.morpheus.cli;

import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.provider.synthetic.SyntheticSpecificationContentReader;
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
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MorpheusM16McpStdioIntegrationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void exposesBlockingConstraintReasonAndEvidenceOverRealStdio() throws Exception {
        Path database = tempDirectory.resolve("m16-mcp.db");
        Seed seed = seed(database);
        Path stderr = tempDirectory.resolve("m16-mcp-stderr.log");
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
            send(writer, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"m16-test\",\"version\":\"1\"}}}");
            readLine(reader, process, stderr, Duration.ofSeconds(10));
            send(writer, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}");

            send(writer, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"get_change_orchestration_state\",\"arguments\":{\"projectId\":\""
                    + seed.projectId() + "\",\"changeId\":\"" + seed.changeId()
                    + "\",\"lifecycleState\":\"IMPLEMENTING\"}}}");
            String state = readLine(reader, process, stderr, Duration.ofSeconds(10));
            assertTrue(state.contains("blockingConstraints"), state);
            assertTrue(state.contains("AVAILABLE"), state);
            assertTrue(state.contains("BLOCK_WHEN_VIOLATED"), state);
            assertTrue(state.contains("supportingEvidenceIds"), state);

            send(writer, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"evaluate_change_transition\",\"arguments\":{\"projectId\":\""
                    + seed.projectId() + "\",\"changeId\":\"" + seed.changeId()
                    + "\",\"fromState\":\"IMPLEMENTING\",\"targetState\":\"VERIFYING\"}}}");
            String transition = readLine(reader, process, stderr, Duration.ofSeconds(10));
            assertTrue(transition.contains("BLOCKED"), transition);
            assertTrue(transition.contains("BLOCKING_CONSTRAINT"), transition);
            assertTrue(transition.contains("constraintEvaluations"), transition);
            assertTrue(transition.contains("BLOCKING"), transition);
            assertTrue(transition.contains("supportingEvidenceIds"), transition);
            assertTrue(transition.contains("sourceEvidenceId"), transition);
            assertTrue(transition.contains("VERIFYING"), transition);
        } finally {
            process.destroy();
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
    }

    private Seed seed(Path database) {
        Properties properties = properties();
        CliLayout layout = CliLayout.resolve(
                Optional.empty(), Optional.empty(), Optional.of(database), Map.of(), properties);
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        Path fixture = fixture("synthetic-basic");
        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            var normalized = new SyntheticSpecificationContentReader()
                    .read(
                            ProviderReadRequest.all(fixture, projectId),
                            new PersistentEntityIdentityResolver(runtime.identities))
                    .content()
                    .orElseThrow();
            new ProjectSnapshotImportService(
                    runtime.snapshots,
                    runtime.requirements,
                    runtime.content,
                    runtime.traceability)
                    .publishFull(
                            normalized,
                            Optional.of("m16-mcp-test"),
                            Instant.parse("2026-07-26T13:00:00Z"));
            return new Seed(projectId.toString(), normalized.changes().getFirst().id().toString());
        }
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("os.name", System.getProperty("os.name", "Windows"));
        properties.setProperty("user.home", tempDirectory.resolve("home").toString());
        return properties;
    }

    private Path fixture(String name) {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("experiments/m0/fixtures").resolve(name);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("M0 fixture not found: " + name);
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

    private record Seed(String projectId, String changeId) {
    }
}
