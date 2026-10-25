package com.morpheus.mcp;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.product.ProductMetadata;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.acceptance.AcceptanceCriterionId;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;
import com.morpheus.store.sqlite.adapter.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.adapter.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.adapter.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.adapter.SqliteVersionedRequirementStore;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What each tool of the server answers, and how it refuses, recorded whole; and the catalogue the server publishes.
 *
 * <p>The tools are called through the specifications the server serves, the way the SDK calls them, on one published
 * snapshot whose entities carry fixed identifiers. Each answer is compared with a transcript under
 * {@code src/test/resources/com/morpheus/mcp/transcripts/}. What the run cannot reproduce is replaced before the
 * comparison, and only that: the identifiers the publication generated, the instants it read from the clock and the
 * product version. When a transcript differs, the run's text is written under {@code target/transcripts-actual/}.</p>
 *
 * <p>The tools read their database through SQLite only, so this test publishes into one; none can be given the memory
 * store. The module does not depend on a provider, so the fixture is built here rather than read from
 * {@code experiments/m0/fixtures/}.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpToolTranscriptTest {
    private static final String PROJECT = "01920000-0000-7000-8000-000000000101";
    private static final String SPECIFICATION = "01920000-0000-7000-8000-000000000102";
    private static final String REQUIREMENT = "01920000-0000-7000-8000-000000000103";
    private static final String OTHER_REQUIREMENT = "01920000-0000-7000-8000-000000000104";
    private static final String SCENARIO = "01920000-0000-7000-8000-000000000105";
    private static final String CHANGE = "01920000-0000-7000-8000-000000000106";
    private static final String CONSTRAINT = "01920000-0000-7000-8000-000000000107";
    private static final String DECISION = "01920000-0000-7000-8000-000000000108";
    private static final String TASK = "01920000-0000-7000-8000-000000000109";
    private static final String CRITERION = "01920000-0000-7000-8000-00000000010a";
    private static final String EVIDENCE = "01920000-0000-7000-8000-00000000010b";
    private static final String ABSENT = McpToolCall.ABSENT_PROJECT_ID;
    private static final String FIXED_PREFIX = "01920000-0000-7000-8000-";
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern INSTANT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z");

    /**
     * The traversals list their nodes and links by identifier, and two identifiers generated in the same millisecond
     * are in no fixed order (RFC 9562 UUIDv7 randomises the bits after the timestamp). These transcripts name every
     * generated identifier alike and compare each array of objects as a set; arrays of values keep their order. Every
     * other answer lists entities with the fixed identifiers of the fixture, in their order.
     */
    private static final Set<String> UNORDERED = Set.of("trace_requirement", "get_change_context");

    private Path tempDir;

    private List<McpServerFeatures.SyncToolSpecification> specifications;

    @BeforeAll
    void publishTheFixture(@TempDir Path directory) {
        tempDir = directory;
        Path database = tempDir.resolve("transcripts.db").toAbsolutePath().normalize();
        publish(database);
        specifications = MorpheusMcpServer.toolSpecifications(
                database,
                new ExternalReferenceResolverRegistry(List.of()),
                MorpheusMcpServer.unconfiguredTechnicalContext(),
                MorpheusMcpServer.deniedWriteCapability(),
                OperationalRecorder.noop());
    }

    @TestFactory
    Stream<DynamicTest> eachToolAnswersAndRefusesAsItsTranscriptSays() {
        List<Case> cases = cases();
        assertFalse(cases.isEmpty(), "the transcript factory built no tool case");
        return Stream.concat(
                Stream.of(DynamicTest.dynamicTest("catalogue", () -> compare("catalogue", catalogue()))),
                cases.stream().map(item -> DynamicTest.dynamicTest(item.name(), () -> verify(item))));
    }

    private static List<Case> cases() {
        List<Case> cases = new ArrayList<>();
        answeredArguments().forEach((tool, arguments) -> {
            cases.add(new Case(tool, "answer", arguments));
            cases.add(new Case(tool, "absent-project", withValue(arguments, "projectId", ABSENT)));
            cases.add(new Case(tool, "malformed-project", withValue(arguments, "projectId", "not-an-id")));
            if (arguments.containsKey("changeId")) {
                cases.add(new Case(tool, "absent-change", withValue(arguments, "changeId", ABSENT)));
            }
        });
        cases.add(new Case("find_requirements", "second-page",
                Map.of("projectId", PROJECT, "query", "session", "offset", 1, "limit", 1)));
        cases.add(new Case("trace_requirement", "absent-requirement",
                Map.of("projectId", PROJECT, "requirementId", ABSENT)));
        cases.add(new Case("trace_requirement", "depth-out-of-bounds",
                Map.of("projectId", PROJECT, "requirementId", REQUIREMENT, "depth", 21)));
        cases.add(new Case("get_specification_context", "absent-specification",
                Map.of("projectId", PROJECT, "specificationId", ABSENT)));
        cases.add(new Case("get_sync_status", "max-age-out-of-bounds", Map.of("projectId", PROJECT, "maxAgeMinutes", 0)));
        return List.copyOf(cases);
    }

    /** The arguments each tool answers on the fixture. */
    private static Map<String, Map<String, Object>> answeredArguments() {
        Map<String, Map<String, Object>> answered = new LinkedHashMap<>();
        answered.put("get_current_specification", Map.of("projectId", PROJECT));
        answered.put("find_requirements", Map.of("projectId", PROJECT, "query", "session"));
        answered.put("get_change", Map.of("projectId", PROJECT, "changeId", CHANGE));
        answered.put("list_changes", Map.of("projectId", PROJECT));
        answered.put("get_constraints", Map.of("projectId", PROJECT, "changeId", CHANGE));
        answered.put("get_acceptance_criteria", Map.of("projectId", PROJECT, "changeId", CHANGE));
        answered.put("get_design_decisions", Map.of("projectId", PROJECT, "changeId", CHANGE));
        answered.put("get_implementation_tasks", Map.of("projectId", PROJECT, "changeId", CHANGE));
        answered.put("trace_requirement", Map.of("projectId", PROJECT, "requirementId", REQUIREMENT));
        answered.put("get_change_context", Map.of("projectId", PROJECT, "changeId", CHANGE));
        answered.put("get_specification_context", Map.of("projectId", PROJECT, "specificationId", SPECIFICATION));
        answered.put("get_change_status", Map.of("projectId", PROJECT, "changeId", CHANGE));
        answered.put("get_blocking_conditions", Map.of("projectId", PROJECT, "changeId", CHANGE));
        answered.put("get_sync_status", Map.of("projectId", PROJECT));
        return answered;
    }

    private static Map<String, Object> withValue(Map<String, Object> arguments, String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>(arguments);
        result.put(key, value);
        return Map.copyOf(result);
    }

    private record Case(String tool, String variant, Map<String, Object> arguments) {
        String name() {
            return tool + "-" + variant;
        }
    }

    private void verify(Case item) throws IOException {
        McpSchema.CallToolResult result = McpToolCall.call(specifications, item.tool(), item.arguments());
        String transcript = String.join("\n",
                "tool: " + item.tool(),
                "arguments: " + new CanonicalJsonSerializer().toJson(new TreeMap<>(item.arguments())),
                "isError: " + result.isError(),
                "text:",
                McpToolCall.text(result));
        compare(item.name(), normalized(transcript, UNORDERED.contains(item.tool()) && item.variant().equals("answer")));
    }

    /** Every tool the server publishes, by name, with its description and input schema in canonical JSON. */
    private String catalogue() {
        List<McpSchema.Tool> tools = specifications.stream()
                .map(McpServerFeatures.SyncToolSpecification::tool)
                .sorted(Comparator.comparing(McpSchema.Tool::name))
                .toList();
        assertFalse(tools.isEmpty(), "the server publishes no tool");
        CanonicalJsonSerializer json = new CanonicalJsonSerializer();
        return tools.stream().map(tool -> {
            try {
                Object schema = McpJsonDefaults.getMapper().readValue(
                        McpJsonDefaults.getMapper().writeValueAsString(tool.inputSchema()), Object.class);
                return "tool: " + tool.name() + "\ndescription: " + tool.description() + "\ninputSchema: " + json.toJson(schema);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }).collect(Collectors.joining("\n\n", "", "\n"));
    }

    private static String normalized(String text, boolean unordered) {
        String result = text.replace("\r\n", "\n");
        if (unordered) {
            result = UUID.matcher(result).replaceAll(match -> match.group().startsWith(FIXED_PREFIX) ? match.group() : "<id>");
            result = result.lines()
                    .map(line -> line.startsWith("{") ? TranscriptJson.withObjectArraysSorted(line) : line)
                    .collect(Collectors.joining("\n"));
        }
        Map<String, String> generated = new LinkedHashMap<>();
        Matcher uuid = UUID.matcher(result);
        StringBuilder ids = new StringBuilder();
        while (uuid.find()) {
            String placeholder = uuid.group().startsWith(FIXED_PREFIX) ? uuid.group()
                    : generated.computeIfAbsent(uuid.group(), key -> "<id-" + (generated.size() + 1) + ">");
            uuid.appendReplacement(ids, Matcher.quoteReplacement(placeholder));
        }
        uuid.appendTail(ids);
        result = INSTANT.matcher(ids.toString()).replaceAll("<instant>");
        return result.replace(ProductMetadata.version(), "<version>");
    }

    private static void compare(String name, String actual) throws IOException {
        String expected;
        try (InputStream stream = McpToolTranscriptTest.class.getResourceAsStream("transcripts/" + name + ".txt")) {
            expected = stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        if (!actual.equals(expected)) {
            Path written = Path.of("target", "transcripts-actual", name + ".txt");
            Files.createDirectories(written.getParent());
            Files.writeString(written, actual, StandardCharsets.UTF_8);
        }
        assertEquals(expected, actual, "the transcript of " + name + " differs; the run's text is in target/transcripts-actual");
    }

    private static void publish(Path database) {
        try (SqliteSpecificationKnowledgeStore snapshots = new SqliteSpecificationKnowledgeStore(database);
             SqliteVersionedRequirementStore requirements = new SqliteVersionedRequirementStore(database);
             SqliteSnapshotBusinessContentStore businessContent = new SqliteSnapshotBusinessContentStore(database);
             SqliteTraceabilityStore traceability = new SqliteTraceabilityStore(database)) {
            new ProjectSnapshotImportService(snapshots, requirements, businessContent, traceability)
                    .publishFull(fixtureContent(), Optional.of("mcp-transcript"), Instant.parse("2026-10-25T10:00:00Z"));
        }
    }

    /** One specification with two requirements and a scenario, and one change with one entity of each kind. */
    private static NormalizedProjectContent fixtureContent() {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(PROJECT);
        SpecificationId specificationId = SpecificationId.parse(SPECIFICATION);
        RequirementId requirementId = RequirementId.parse(REQUIREMENT);
        ChangeId changeId = ChangeId.parse(CHANGE);
        SourceLocator source = SourceLocator.file("openspec/specs/auth-session/spec.md");
        Evidence evidence = new Evidence(EvidenceId.parse(EVIDENCE), source, Optional.empty(), Optional.empty());
        Provenance provenance = new Provenance(
                new ProviderId("mcp-transcript"), Optional.of("1.0"), source,
                Optional.of("fixture"), Optional.empty(), evidence.id());
        ProjectSpecification project =
                new ProjectSpecification(projectId, "MCP transcript fixture", SourceLocator.file("workspace"));
        Specification specification = new Specification(
                specificationId, projectId, "auth-session", "Authentication session",
                Optional.of("Describe the authenticated session."), provenance);
        Requirement requirement = new Requirement(
                requirementId, specificationId, Optional.of("auth-session/session-expiration"),
                "Session expiration",
                "The system SHALL expire an authenticated session after 30 minutes of inactivity.", provenance);
        Requirement other = new Requirement(
                RequirementId.parse(OTHER_REQUIREMENT), specificationId, Optional.of("auth-session/session-refresh"),
                "Session activity refresh",
                "The system SHALL refresh the inactivity timer on a protected action.", provenance);
        Scenario scenario = new Scenario(
                ScenarioId.parse(SCENARIO), Optional.of(requirementId), "Expire an inactive session",
                List.of("A session is authenticated"), "No activity occurs for 30 minutes",
                "The system requires authentication again", provenance);
        ChangeProposal change = new ChangeProposal(
                changeId, projectId, Optional.of("add-remember-me"), "Add remember-me sessions",
                "Keep a trusted session longer on request.", List.of("Persistent sessions"),
                List.of("Single sign-on"), List.of("Stolen device"), provenance);
        return new NormalizedProjectContent(
                project,
                List.of(specification),
                List.of(requirement, other),
                List.of(scenario),
                List.of(change),
                List.of(),
                List.of(Constraint.withUnknownSemantics(ConstraintId.parse(CONSTRAINT), changeId,
                        "The default session behaviour must not change.", provenance)),
                List.of(new DesignDecision(DesignDecisionId.parse(DECISION), changeId, "Use a separate credential",
                        "Issue a persistent credential distinct from the session.", provenance)),
                List.of(new ImplementationTask(TaskId.parse(TASK), changeId, Optional.of("TASK-1"),
                        "Define the persistent-session data model", false, provenance)),
                List.of(criterion(requirementId, changeId, provenance)),
                List.of(evidence),
                List.of());
    }

    private static AcceptanceCriterion criterion(RequirementId requirementId, ChangeId changeId, Provenance provenance) {
        return new AcceptanceCriterion(
                AcceptanceCriterionId.parse(CRITERION), Optional.of(requirementId), Optional.of(changeId),
                "Remember-me survives a restart", "A remembered session is restored after a browser restart",
                VerificationStatus.NOT_VERIFIED, List.of(), provenance);
    }
}
