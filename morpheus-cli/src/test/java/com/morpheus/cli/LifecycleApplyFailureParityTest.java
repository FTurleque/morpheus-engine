package com.morpheus.cli;

import com.morpheus.api.MorpheusHttpServer;
import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.provider.synthetic.SyntheticSpecificationContentReader;
import com.morpheus.store.sqlite.SqliteChangeLifecycleMutationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code lifecycle.apply} is declared on all three transports; an invalid argument must fail the same way on
 * all three.
 *
 * <p>{@code contracts/public-surfaces.tsv} claims CLI/MCP/HTTP convergence, and the tests that back that claim
 * exercise the passing paths. The failure paths were never compared, and MCP -- the surface agents call -- was
 * the one whose failure paths were not exercised at all. This submits the same three malformed commands to the
 * real CLI, the real loopback HTTP server and a real STDIO MCP server process.</p>
 *
 * <p>What must converge is the <em>class</em> of failure: refused, and nothing written. The text does not
 * converge and must not -- each surface names the argument as its own caller spelled it, {@code --to} on the
 * CLI and {@code targetState} over HTTP and MCP, and a message naming an argument the caller did not send
 * would be worse than a differing one. Nor does the layer: MCP refuses at its published schema, before the
 * handler, because its contract is machine-readable and the other two surfaces' are not.</p>
 */
class LifecycleApplyFailureParityTest {
    private static final String IDEMPOTENCY_KEY = "parity-must-never-apply";

    @TempDir
    Path tempDirectory;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void anUnknownTargetStateIsRefusedByAllThreeSurfacesWithoutMutating() throws Exception {
        assertRefusedEverywhere("unknown-target-state", "SIDEWAYS", 0, "parity-actor");
    }

    @Test
    void aNegativeExpectedRevisionIsRefusedByAllThreeSurfacesWithoutMutating() throws Exception {
        assertRefusedEverywhere("negative-revision", "PROPOSED", -1, "parity-actor");
    }

    @Test
    void anAbsentActorIsRefusedByAllThreeSurfacesWithoutMutating() throws Exception {
        assertRefusedEverywhere("absent-actor", "PROPOSED", 0, null);
    }

    private void assertRefusedEverywhere(String label, String targetState, long expectedRevision, String actor)
            throws Exception {
        Path database = tempDirectory.resolve(label + ".db");
        Seed seed = seed(database);

        assertCliRefuses(database, seed, targetState, expectedRevision, actor);
        assertHttpRefuses(database, seed, targetState, expectedRevision, actor);
        assertMcpRefuses(database, seed, targetState, expectedRevision, actor);

        try (var mutations = new SqliteChangeLifecycleMutationStore(database)) {
            ProjectSpecificationId projectId = ProjectSpecificationId.parse(seed.projectId());
            ChangeId changeId = ChangeId.parse(seed.changeId());
            assertTrue(mutations.listAudit(projectId, changeId).isEmpty(),
                    () -> label + ": a refused command must leave no audit record on any surface");
            assertTrue(mutations.findState(projectId, changeId).isEmpty(),
                    () -> label + ": a refused command must leave no lifecycle state on any surface");
        }
    }

    private void assertCliRefuses(
            Path database, Seed seed, String targetState, long expectedRevision, String actor) {
        List<String> arguments = new java.util.ArrayList<>(List.of(
                "--db", database.toString(), "lifecycle", "apply",
                "--project", seed.projectId(),
                "--change", seed.changeId(),
                "--to", targetState,
                "--expected-revision", Long.toString(expectedRevision),
                "--idempotency-key", IDEMPOTENCY_KEY,
                "--confirm"));
        if (actor != null) {
            arguments.add("--actor");
            arguments.add(actor);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit;
        try (PrintStream outStream = new PrintStream(out, true, StandardCharsets.UTF_8);
             PrintStream errStream = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            exit = new MorpheusCli().run(
                    arguments.toArray(String[]::new), outStream, errStream, Map.of(), properties());
        }

        String diagnostics = err.toString(StandardCharsets.UTF_8) + out.toString(StandardCharsets.UTF_8);
        assertEquals(CliExitCode.USAGE.code(), exit,
                () -> "the CLI must refuse the command as a usage fault: " + diagnostics);
        assertTrue(diagnostics.contains("MORPHEUS error"), () -> diagnostics);
    }

    private void assertHttpRefuses(
            Path database, Seed seed, String targetState, long expectedRevision, String actor) throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            URI uri = URI.create("http://" + server.host() + ":" + server.port() + MorpheusHttpServer.API_PREFIX
                    + "/projects/" + seed.projectId() + "/changes/" + seed.changeId() + "/lifecycle-transitions");
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(uri)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    body(targetState, expectedRevision, actor)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(400, response.statusCode(),
                    () -> "the HTTP adapter must refuse the command as a bad request: " + response.body());
            assertTrue(response.body().contains("BAD_REQUEST"), response::body);
        }
    }

    private void assertMcpRefuses(
            Path database, Seed seed, String targetState, long expectedRevision, String actor) throws Exception {
        try (McpStdioSession session = McpStdioSession.start(database)) {
            session.send("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
                    + "\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                    + "\"clientInfo\":{\"name\":\"parity-test\",\"version\":\"1\"}}}");
            session.readLine(Duration.ofSeconds(10));
            session.send("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}");

            String arguments = "{\"projectId\":\"" + seed.projectId() + "\""
                    + ",\"changeId\":\"" + seed.changeId() + "\""
                    + ",\"idempotencyKey\":\"" + IDEMPOTENCY_KEY + "\""
                    + ",\"expectedRevision\":" + expectedRevision
                    + ",\"targetState\":\"" + targetState + "\""
                    + ",\"confirmed\":true"
                    + (actor == null ? "" : ",\"actor\":\"" + actor + "\"")
                    + "}";
            session.send("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{"
                    + "\"name\":\"apply_change_lifecycle_transition\",\"arguments\":" + arguments + "}}");
            String answer = session.readLine(Duration.ofSeconds(20));

            assertTrue(answer.contains("\"isError\":true"),
                    () -> "the MCP tool must refuse the command as a tool error: " + answer);
        }
    }

    private static String body(String targetState, long expectedRevision, String actor) {
        StringBuilder json = new StringBuilder("{\"idempotencyKey\":\"" + IDEMPOTENCY_KEY + "\"")
                .append(",\"expectedRevision\":").append(expectedRevision)
                .append(",\"targetState\":\"").append(targetState).append('"')
                .append(",\"confirmed\":true");
        if (actor != null) {
            json.append(",\"actor\":\"").append(actor).append('"');
        }
        return json.append('}').toString();
    }

    private Seed seed(Path database) {
        CliLayout layout = CliLayout.resolve(
                Optional.empty(), Optional.empty(), Optional.of(database), Map.of(), properties());
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            var normalized = new SyntheticSpecificationContentReader()
                    .read(
                            ProviderReadRequest.all(fixture("synthetic-basic"), projectId),
                            new PersistentEntityIdentityResolver(runtime.identities))
                    .content()
                    .orElseThrow();
            new ProjectSnapshotImportService(
                    runtime.snapshots, runtime.requirements, runtime.content, runtime.traceability)
                    .publishFull(normalized, Optional.of("parity-test"), Instant.parse("2026-09-09T15:00:00Z"));
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
