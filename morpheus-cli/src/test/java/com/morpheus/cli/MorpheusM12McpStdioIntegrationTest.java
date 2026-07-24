package com.morpheus.cli;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.sqlite.SqliteExternalReferenceStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MorpheusM12McpStdioIntegrationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void listsAndResolvesExternalReferencesOverRealStdioWithoutMinosInstalled() throws Exception {
        Path database = tempDirectory.resolve("m12-mcp.db");
        Path stderr = tempDirectory.resolve("m12-mcp-stderr.log");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        DomainIdentity ownerId = DomainIdentity.generate();
        ExternalReference reference = seed(database, projectId, snapshotId, ownerId);
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
            send(writer, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"m12-test\",\"version\":\"1\"}}}");
            readLine(reader, process, stderr, Duration.ofSeconds(10));
            send(writer, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}");
            send(writer, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
            String tools = readLine(reader, process, stderr, Duration.ofSeconds(10));
            assertTrue(tools.contains("list_external_references"), tools);
            assertTrue(tools.contains("resolve_external_reference"), tools);

            send(writer, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"list_external_references\",\"arguments\":{\"projectId\":\"" + projectId + "\",\"ownerId\":\"" + ownerId + "\"}}}");
            String list = readLine(reader, process, stderr, Duration.ofSeconds(10));
            assertTrue(list.contains(reference.id().toString()), list);

            send(writer, "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"resolve_external_reference\",\"arguments\":{\"projectId\":\"" + projectId + "\",\"referenceId\":\"" + reference.id() + "\"}}}");
            String resolution = readLine(reader, process, stderr, Duration.ofSeconds(10));
            assertTrue(resolution.contains("NO_RESOLVER"), resolution);
            assertTrue(resolution.contains("persisted"), resolution);
        } finally {
            process.destroy();
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
    }

    private ExternalReference seed(
            Path database,
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId,
            DomainIdentity ownerId) {
        ExternalReference reference = ExternalReference.unvalidated(
                ExternalReferenceId.generate(), ownerId,
                new ExternalReferenceTarget("MINOS", Optional.of("morpheus-engine"), "SYMBOL",
                        "symbol:RequirementService", Optional.empty()), Optional.empty());
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-" + projectId)));
            snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                    snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.READY,
                    Optional.of("rev"), Instant.parse("2026-07-24T12:00:00Z")));
            snapshots.activateSnapshot(snapshotId, Optional.empty());
            references.putReference(snapshotId, reference);
        }
        return reference;
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
