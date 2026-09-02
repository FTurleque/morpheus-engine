package com.morpheus.cli;

import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.provider.synthetic.SyntheticSpecificationContentReader;
import com.morpheus.store.sqlite.SqliteChangeLifecycleMutationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusM17McpStdioIntegrationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void exposesSeparateWriteToolButOfficialLauncherDeniesWithoutWriteCapableProductionProvider() throws Exception {
        Path database = tempDirectory.resolve("m17-mcp.db");
        Seed seed = seed(database);

        try (McpStdioSession session = McpStdioSession.start(database)) {
            session.send("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"m17-test\",\"version\":\"1\"}}}");
            session.readLine(Duration.ofSeconds(10));
            session.send("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}");

            session.send("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"apply_change_lifecycle_transition\",\"arguments\":{\"projectId\":\""
                    + seed.projectId() + "\",\"changeId\":\"" + seed.changeId()
                    + "\",\"idempotencyKey\":\"m17-stdio-denied\",\"expectedRevision\":0,\"targetState\":\"PROPOSED\",\"actor\":\"m17-stdio-test\",\"confirmed\":true}}}");
            String mutation = session.readLine(Duration.ofSeconds(10));
            assertTrue(mutation.contains("NOT_AUTHORIZED"), mutation);
            assertTrue(mutation.contains("WRITE_CHANGE"), mutation);

            session.send("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"evaluate_change_transition\",\"arguments\":{\"projectId\":\""
                    + seed.projectId() + "\",\"changeId\":\"" + seed.changeId()
                    + "\",\"fromState\":\"DRAFT\",\"targetState\":\"PROPOSED\"}}}");
            String evaluation = session.readLine(Duration.ofSeconds(10));
            assertTrue(evaluation.contains("ALLOWED"), evaluation);
        }

        try (var mutations = new SqliteChangeLifecycleMutationStore(database)) {
            assertTrue(mutations.listAudit(
                    ProjectSpecificationId.parse(seed.projectId()), ChangeId.parse(seed.changeId())).isEmpty(),
                    "denied MCP write must not create audit or state");
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
                            Optional.of("m17-mcp-test"),
                            Instant.parse("2026-07-26T15:00:00Z"));
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

    private record Seed(String projectId, String changeId) {
    }
}
