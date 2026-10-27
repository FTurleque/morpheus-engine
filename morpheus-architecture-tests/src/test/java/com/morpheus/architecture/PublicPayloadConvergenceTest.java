package com.morpheus.architecture;

import com.morpheus.api.local.MorpheusHttpServer;
import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.ingestion.ProjectContentIngestion;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.operability.OperationalRuntime;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.architecture.SharedDomainProjectionArchitectureTest.Transport;
import com.morpheus.cli.MorpheusMain;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.mcp.server.MorpheusMcpServer;
import com.morpheus.provider.synthetic.SyntheticSpecificationContentReader;
import com.morpheus.store.sqlite.adapter.SqliteEntityIdentityStore;
import com.morpheus.store.sqlite.adapter.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.adapter.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.adapter.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.adapter.SqliteVersionedRequirementStore;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transports show a shared domain type with the same keys and the same values.
 *
 * <p>{@code openspec-basic} is registered and published through the CLI into a temporary database, and
 * {@code synthetic-basic}, the fixture that carries acceptance criteria, is published into the same database by the
 * import service. The local HTTP server, the MCP tools and the CLI then answer from it. For each type of
 * {@link SharedDomainProjectionArchitectureTest#SHARED_TYPES}, every exposure obtains the shapes one transport answers
 * once the envelope it names is removed: the HTTP {@code data} member, a page's {@code items}, the member a detail or
 * context answer puts the shape under. Shapes are matched by {@code id} and compared key by key; a difference names
 * the type, the key and the two transports.</p>
 *
 * <p>The CLI shows five of the types with a shape of its own, listed in
 * {@link SharedDomainProjectionArchitectureTest#ADAPTER_SHAPES_DIFFERING_FROM_THE_SHARED_ONE}. Its differences are not
 * equalised: {@link #CLI_OMITTED_KEYS} and {@link #CLI_ABSENT_AS_NULL_KEYS} name them, and the comparison holds the
 * CLI to those keys and no other.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicPayloadConvergenceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Keys of the shared shape the CLI does not write, by type. */
    static final Map<String, Set<String>> CLI_OMITTED_KEYS = Map.of(
            ChangeProposal.class.getSimpleName(), Set.of("projectId"));

    /** Keys whose absent value the CLI writes as {@code null} where HTTP and MCP write {@code ""}, by type. */
    static final Map<String, Set<String>> CLI_ABSENT_AS_NULL_KEYS = Map.of(
            ChangeProposal.class.getSimpleName(), Set.of("key"),
            ImplementationTask.class.getSimpleName(), Set.of("key"),
            AcceptanceCriterion.class.getSimpleName(), Set.of("requirementId", "changeId"));

    @TempDir
    static Path temp;

    private MorpheusHttpServer server;
    private List<McpServerFeatures.SyncToolSpecification> tools;
    private List<String> projectIds;
    private Path dataDirectory;

    @BeforeAll
    void publishTheFixture() throws IOException {
        dataDirectory = temp.resolve("data");
        Path workspace = copyOfFixture("openspec-basic");
        String openSpecProject = value(cli("projects", "add", "--workspace", workspace.toString()), "projectId");
        String sync = cli("sync", "--project", openSpecProject);
        assertTrue(sync.contains("published=true"), sync);
        Path database = dataDirectory.resolve("morpheus.db");
        projectIds = List.of(openSpecProject, publishSyntheticBasic(database));
        server = MorpheusHttpServer.start(database, "127.0.0.1", 0);
        tools = MorpheusMcpServer.toolSpecifications(database, new ExternalReferenceResolverRegistry(List.of()),
                MorpheusMcpServer.unconfiguredTechnicalContext(), MorpheusMcpServer.deniedWriteCapability(),
                OperationalRecorder.noop());
    }

    @AfterAll
    void stopTheServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void httpAndMcpShowEverySharedTypeWithTheSameKeysAndValues() throws Exception {
        Map<String, List<String>> differences = new TreeMap<>();
        Map<String, Integer> compared = new TreeMap<>();
        for (Exposure exposure : exposures()) {
            Map<String, JsonNode> http = byId(exposure.http().shapes());
            Map<String, JsonNode> mcp = byId(exposure.mcp().shapes());
            compare(exposure, Transport.HTTP, http, Transport.MCP, mcp, Set.of(), Set.of(), differences);
            compared.merge(exposure.type(), http.size(), Integer::sum);
        }

        assertEquals(Map.of(), differences, "a shared domain type must have one public shape on every transport");
        Set<String> shared = new TreeSet<>();
        SharedDomainProjectionArchitectureTest.SHARED_TYPES.keySet().forEach(type -> shared.add(type.getSimpleName()));
        Set<String> exercised = new TreeSet<>();
        compared.forEach((type, count) -> {
            if (count > 0) {
                exercised.add(type);
            }
        });
        assertEquals(shared, exercised, "every shared type is compared on at least one value of the fixture: " + compared);
    }

    @Test
    void theCliDiffersFromTheSharedShapeByTheNamedKeysOnly() throws Exception {
        Map<String, List<String>> differences = new TreeMap<>();
        Set<String> exercised = new TreeSet<>();
        for (Exposure exposure : exposures()) {
            if (exposure.cli() == null) {
                continue;
            }
            Map<String, JsonNode> http = byId(exposure.http().shapes());
            Map<String, JsonNode> cli = byId(exposure.cli().shapes());
            compare(exposure, Transport.HTTP, http, Transport.CLI, cli,
                    CLI_OMITTED_KEYS.getOrDefault(exposure.type(), Set.of()),
                    CLI_ABSENT_AS_NULL_KEYS.getOrDefault(exposure.type(), Set.of()), differences);
            if (!cli.isEmpty()) {
                exercised.add(exposure.type());
            }
        }

        assertEquals(Map.of(), differences,
                "the CLI may differ from the shared shape only by the keys CLI_OMITTED_KEYS and "
                        + "CLI_ABSENT_AS_NULL_KEYS name");
        Set<String> shownByTheCli = new TreeSet<>();
        SharedDomainProjectionArchitectureTest.SHARED_TYPES.forEach((type, transports) -> {
            if (transports.contains(Transport.CLI)) {
                shownByTheCli.add(type.getSimpleName());
            }
        });
        assertEquals(shownByTheCli, exercised, "every type the CLI shows is compared on a value of the fixture");
    }

    @Test
    void theComparisonNamesTheTypeTheKeyAndTheTwoTransports() {
        Map<String, List<String>> differences = new TreeMap<>();
        JsonNode left = JSON.readTree("{\"id\":\"a\",\"title\":\"t\",\"statement\":\"s\"}");
        JsonNode right = JSON.readTree("{\"id\":\"a\",\"title\":\"other\"}");
        Exposure exposure = new Exposure("Requirement", null, null, null);

        compare(exposure, Transport.HTTP, Map.of("a", left), Transport.MCP, Map.of("a", right), Set.of(), Set.of(),
                differences);

        assertEquals(Map.of("Requirement", List.of(
                "a: key statement is \"s\" on HTTP and absent on MCP",
                "a: key title is \"t\" on HTTP and \"other\" on MCP")), differences);
    }

    private List<Exposure> exposures() throws Exception {
        List<Exposure> exposures = new ArrayList<>();
        for (String projectId : projectIds) {
            exposures.addAll(exposures(projectId));
        }
        return exposures;
    }

    private List<Exposure> exposures(String projectId) throws Exception {
        String project = "/api/v1/projects/" + projectId;
        List<Exposure> exposures = new ArrayList<>();
        List<String> specificationIds = ids(http(project + "/specifications?limit=100", "/data/items").shapes());
        exposures.add(new Exposure(Specification.class.getSimpleName(),
                http(project + "/specifications?limit=100", "/data/items"),
                mcp("get_current_specification", Map.of("projectId", projectId, "limit", 100), "/specifications/items"),
                null));
        for (String specificationId : specificationIds) {
            String context = project + "/specifications/" + specificationId + "/context?limit=100";
            Map<String, Object> arguments = Map.of("projectId", projectId, "specificationId", specificationId, "limit", 100);
            exposures.add(new Exposure(Specification.class.getSimpleName(), http(context, "/data/specification"),
                    mcp("get_specification_context", arguments, "/specification"), null));
            exposures.add(new Exposure(Requirement.class.getSimpleName(), http(context, "/data/requirements/items"),
                    mcp("get_specification_context", arguments, "/requirements/items"), null));
            exposures.add(new Exposure(Scenario.class.getSimpleName(), http(context, "/data/scenarios"),
                    mcp("get_specification_context", arguments, "/scenarios"), null));
            exposures.add(new Exposure(ChangeProposal.class.getSimpleName(), http(context, "/data/changes"),
                    mcp("get_specification_context", arguments, "/changes"), null));
        }
        exposures.add(new Exposure(ChangeProposal.class.getSimpleName(),
                http(project + "/changes?limit=100", "/data/items"),
                mcp("list_changes", Map.of("projectId", projectId, "limit", 100), "/items"),
                cliAnswer("/items", "changes", "list", "--project", projectId, "--limit", "100")));
        for (String changeId : ids(http(project + "/changes?limit=100", "/data/items").shapes())) {
            String change = project + "/changes/" + changeId;
            Map<String, Object> one = Map.of("projectId", projectId, "changeId", changeId);
            Map<String, Object> page = Map.of("projectId", projectId, "changeId", changeId, "limit", 100);
            exposures.add(new Exposure(ChangeProposal.class.getSimpleName(), http(change, "/data/change"),
                    mcp("get_change", one, "/change"),
                    cliAnswer("", "changes", "get", "--project", projectId, "--change", changeId)));
            exposures.add(new Exposure(Constraint.class.getSimpleName(),
                    http(change + "/constraints?limit=100", "/data/items"), mcp("get_constraints", page, "/items"),
                    cliAnswer("/items", "constraints", "list", "--project", projectId, "--change", changeId, "--limit", "100")));
            exposures.add(new Exposure(AcceptanceCriterion.class.getSimpleName(),
                    http(change + "/acceptance-criteria?limit=100", "/data/items"),
                    mcp("get_acceptance_criteria", page, "/items"),
                    cliAnswer("/items", "acceptance-criteria", "list", "--project", projectId, "--change", changeId,
                            "--limit", "100")));
            exposures.add(new Exposure(DesignDecision.class.getSimpleName(),
                    http(change + "/design-decisions?limit=100", "/data/items"),
                    mcp("get_design_decisions", page, "/items"),
                    cliAnswer("/items", "decisions", "list", "--project", projectId, "--change", changeId, "--limit", "100")));
            exposures.add(new Exposure(ImplementationTask.class.getSimpleName(),
                    http(change + "/implementation-tasks?limit=100", "/data/items"),
                    mcp("get_implementation_tasks", page, "/items"),
                    cliAnswer("/items", "tasks", "list", "--project", projectId, "--change", changeId, "--limit", "100")));
        }
        return exposures;
    }

    static void compare(
            Exposure exposure,
            Transport leftTransport,
            Map<String, JsonNode> left,
            Transport rightTransport,
            Map<String, JsonNode> right,
            Set<String> omittedOnTheRight,
            Set<String> absentAsNullOnTheRight,
            Map<String, List<String>> differences) {
        List<String> found = new ArrayList<>();
        Set<String> ids = new TreeSet<>(left.keySet());
        ids.addAll(right.keySet());
        for (String id : ids) {
            JsonNode leftShape = left.get(id);
            JsonNode rightShape = right.get(id);
            if (leftShape == null || rightShape == null) {
                found.add(id + ": shown on " + (leftShape == null ? rightTransport : leftTransport) + " only");
                continue;
            }
            Set<String> keys = new TreeSet<>(leftShape.propertyNames());
            keys.addAll(rightShape.propertyNames());
            for (String key : keys) {
                JsonNode leftValue = leftShape.get(key);
                JsonNode rightValue = rightShape.get(key);
                boolean agree;
                if (omittedOnTheRight.contains(key)) {
                    agree = leftValue != null && rightValue == null;
                } else if (absentAsNullOnTheRight.contains(key) && leftValue != null && leftValue.asString().isEmpty()) {
                    agree = rightValue != null && rightValue.isNull();
                } else {
                    agree = leftValue != null && leftValue.equals(rightValue);
                }
                if (!agree) {
                    found.add(id + ": key " + key + " is " + describe(leftValue) + " on " + leftTransport + " and "
                            + describe(rightValue) + " on " + rightTransport);
                }
            }
        }
        if (!found.isEmpty()) {
            differences.computeIfAbsent(exposure.type(), type -> new ArrayList<>()).addAll(found);
        }
    }

    private static String describe(JsonNode value) {
        return value == null ? "absent" : value.toString();
    }

    private static Map<String, JsonNode> byId(List<JsonNode> shapes) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode shape : shapes) {
            result.put(shape.get("id").asString(), shape);
        }
        return result;
    }

    private static List<String> ids(List<JsonNode> shapes) {
        return shapes.stream().map(shape -> shape.get("id").asString()).toList();
    }

    private Answer http(String path, String pointer) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://" + server.host() + ":" + server.port() + path)).GET().build();
        HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), path + ": " + response.body());
        return new Answer("HTTP GET " + path, shapes(response.body(), pointer));
    }

    private Answer mcp(String tool, Map<String, Object> arguments, String pointer) {
        McpServerFeatures.SyncToolSpecification specification = tools.stream()
                .filter(item -> item.tool().name().equals(tool))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tool not served: " + tool));
        McpSchema.CallToolResult result = specification.callHandler().apply(null, new McpSchema.CallToolRequest(tool, arguments));
        String text = result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(content -> ((McpSchema.TextContent) content).text())
                .reduce("", String::concat);
        assertTrue(!Boolean.TRUE.equals(result.isError()), tool + ": " + text);
        return new Answer("MCP " + tool, shapes(text, pointer));
    }

    private Answer cliAnswer(String pointer, String... command) {
        String[] arguments = Stream.concat(Stream.of("--json"), Stream.of(command)).toArray(String[]::new);
        return new Answer("CLI " + String.join(" ", command), shapes(cli(arguments), pointer));
    }

    private static List<JsonNode> shapes(String body, String pointer) {
        JsonNode node = JSON.readTree(body).at(pointer);
        assertTrue(!node.isMissingNode(), "no " + pointer + " in " + body);
        List<JsonNode> result = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(result::add);
        } else {
            result.add(node);
        }
        return result;
    }

    private String cli(String... command) {
        String[] arguments = new String[command.length + 2];
        arguments[0] = "--data-dir";
        arguments[1] = dataDirectory.toString();
        System.arraycopy(command, 0, arguments, 2, command.length);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Properties properties = new Properties();
        properties.setProperty("os.name", System.getProperty("os.name"));
        properties.setProperty("user.home", temp.resolve("home").toString());
        int exitCode;
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            exitCode = MorpheusMain.run(
                    OperationalRuntime.of(OperationalLogMode.OFF), arguments, out, err, Map.of(), properties);
        }
        assertEquals(0, exitCode, stderr.toString(StandardCharsets.UTF_8));
        return stdout.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String value(String output, String key) {
        String prefix = key + "=";
        return output.lines().filter(line -> line.startsWith(prefix)).findFirst()
                .map(line -> line.substring(prefix.length()).trim())
                .orElseThrow(() -> new AssertionError("missing " + key + " in output: " + output));
    }

    /**
     * {@code openspec-basic} shows seven of the shared types; the OpenSpec reader produces no acceptance criterion,
     * so {@code synthetic-basic}, which carries some, is published into the same database by the import service.
     */
    private static String publishSyntheticBasic(Path database) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var identities = new SqliteEntityIdentityStore(database)) {
            NormalizedProjectContent normalized = new ProjectContentIngestion(new SyntheticSpecificationContentReader())
                    .read(RepositoryRoot.fixture("synthetic-basic"),
                            projectId, new PersistentEntityIdentityResolver(identities));
            new ProjectSnapshotImportService(snapshots, requirements, content, traceability)
                    .publishFull(normalized, Optional.of("payload-convergence"), Instant.parse("2026-10-27T00:00:00Z"));
        }
        return projectId.toString();
    }

    private Path copyOfFixture(String name) throws IOException {
        Path source = RepositoryRoot.fixture(name);
        Path copy = temp.resolve("workspace-" + name);
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Path target = copy.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
        return copy;
    }

    /** The working directory of this module's tests is the module; the repository root is its parent. */

    /** What one transport answered for an exposure: where it was asked, and the shapes once its envelope is removed. */
    record Answer(String source, List<JsonNode> shapes) {
    }

    /** One way the transports show a shared type: the same question asked of HTTP, MCP and, where it shows it, the CLI. */
    record Exposure(String type, Answer http, Answer mcp, Answer cli) {
    }
}
