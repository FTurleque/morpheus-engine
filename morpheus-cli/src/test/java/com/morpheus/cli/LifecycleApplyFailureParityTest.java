package com.morpheus.cli;

import com.morpheus.api.local.MorpheusHttpServer;
import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.operability.OperationalRuntime;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.cli.command.CliExitCode;
import com.morpheus.cli.command.CliLayout;
import com.morpheus.cli.command.CliRuntime;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.provider.synthetic.SyntheticSpecificationContentReader;
import com.morpheus.store.sqlite.adapter.SqliteChangeLifecycleMutationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code lifecycle.apply} is declared on all three transports; an invalid argument must fail the same way on
 * all three.
 *
 * <p>{@code contracts/public-surfaces.tsv} claims CLI/MCP/HTTP convergence; this compares the failure paths of
 * that claim. It submits the same three malformed commands to the CLI through its launcher, the real loopback HTTP
 * server and a real STDIO MCP server process -- MCP being the surface agents call -- and asserts on each which
 * argument was refused.</p>
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
        assertRefusedEverywhere("unknown-target-state", "SIDEWAYS", 0, "parity-actor", new Refusal(
                "--to is not a valid MORPHEUS lifecycle state: SIDEWAYS",
                "targetState is not a valid MORPHEUS lifecycle state: SIDEWAYS",
                "/targetState"));
    }

    @Test
    void aNegativeExpectedRevisionIsRefusedByAllThreeSurfacesWithoutMutating() throws Exception {
        assertRefusedEverywhere("negative-revision", "PROPOSED", -1, "parity-actor", new Refusal(
                "--expected-revision must be between 0 and ",
                "expectedRevision must be a non-negative integer",
                "/expectedRevision"));
    }

    @Test
    void anAbsentActorIsRefusedByAllThreeSurfacesWithoutMutating() throws Exception {
        assertRefusedEverywhere("absent-actor", "PROPOSED", 0, null, new Refusal(
                "--actor is required",
                "actor is required and must be a non-blank string",
                "'actor'"));
    }

    private void assertRefusedEverywhere(
            String label, String targetState, long expectedRevision, String actor, Refusal refusal)
            throws Exception {
        Path database = tempDirectory.resolve(label + ".db");
        Seed seed = seed(database);

        assertCliRefuses(database, seed, targetState, expectedRevision, actor, refusal.cli());
        assertHttpRefuses(database, seed, targetState, expectedRevision, actor, refusal.http());
        assertMcpRefuses(database, seed, targetState, expectedRevision, actor, refusal.mcpField());

        try (var mutations = new SqliteChangeLifecycleMutationStore(database)) {
            ProjectSpecificationId projectId = ProjectSpecificationId.parse(seed.projectId());
            ChangeId changeId = ChangeId.parse(seed.changeId());
            assertEquals(0, mutations.listAudit(projectId, changeId, PageRequest.first(1)).totalMatches(),
                    () -> label + ": a refused command must leave no audit record on any surface");
            assertTrue(mutations.findState(projectId, changeId).isEmpty(),
                    () -> label + ": a refused command must leave no lifecycle state on any surface");
        }
    }

    /**
     * The command goes through the launcher, as the installed CLI runs it: the dispatcher alone does not serve
     * {@code lifecycle} and would refuse it as an unknown command, with the same exit code.
     */
    private void assertCliRefuses(
            Path database, Seed seed, String targetState, long expectedRevision, String actor, String refusal) {
        List<String> arguments = new ArrayList<>(List.of(
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
            exit = MorpheusMain.run(OperationalRuntime.of(OperationalLogMode.OFF),
                    arguments.toArray(String[]::new), outStream, errStream, Map.of(), properties());
        }

        String diagnostics = err.toString(StandardCharsets.UTF_8) + out.toString(StandardCharsets.UTF_8);
        assertEquals(CliExitCode.USAGE.code(), exit,
                () -> "the CLI must refuse the command as a usage fault: " + diagnostics);
        assertTrue(diagnostics.contains("MORPHEUS error [2]: " + refusal), () -> diagnostics);
        assertFalse(diagnostics.contains("unknown command"), () -> diagnostics);
    }

    private void assertHttpRefuses(
            Path database, Seed seed, String targetState, long expectedRevision, String actor, String refusal)
            throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            URI uri = URI.create(server.baseUri()
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
            assertTrue(response.body().contains("\"code\":\"BAD_REQUEST\",\"message\":\"" + refusal + "\""),
                    response::body);
        }
    }

    /**
     * The schema validator words its errors in the language of the JVM, so the field it names is asserted, not its
     * sentence.
     */
    private void assertMcpRefuses(
            Path database, Seed seed, String targetState, long expectedRevision, String actor, String field)
            throws Exception {
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

            assertTrue(answer.contains("\"id\":2,") && answer.contains("\"isError\":true"),
                    () -> "the MCP tool must refuse the command as a tool error: " + answer);
            assertTrue(answer.contains("input validation failed") && answer.contains(field),
                    () -> "the MCP tool must refuse at its schema, naming " + field + ": " + answer);
        }
    }

    /** What each surface answers for one malformed command: the CLI's and HTTP's refusal, the field MCP names. */
    private record Refusal(String cli, String http, String mcpField) {
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
        try (CliRuntime runtime = CliRuntime.recovering(layout.databasePath(), OperationalRecorder.noop())) {
            var normalized = new SyntheticSpecificationContentReader()
                    .read(
                            ProviderReadRequest.all(RepositoryRoot.fixture("synthetic-basic"), projectId),
                            new PersistentEntityIdentityResolver(runtime.identities()))
                    .content()
                    .orElseThrow();
            new ProjectSnapshotImportService(
                    runtime.snapshots(), runtime.requirements(), runtime.content(), runtime.traceability())
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

    private record Seed(String projectId, String changeId) {
    }
}
