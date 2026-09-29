package com.morpheus.mcp;

import com.morpheus.application.policy.PolicyPack;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.store.sqlite.SqlitePolicyPackStore;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.ToolInputValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three tools that read a collection which only ever grows take a page (ADR-0107, amendment on MCP-2).
 *
 * <p>{@code get_policy_audit}, {@code list_policy_pack_versions} and {@code list_saved_view_versions} rendered their
 * whole collection under a schema that forbade {@code offset} and {@code limit}. Past 1 MiB the transport answers
 * with a guidance to retry with a smaller {@code limit}; the SDK then refused the retry for an unknown argument.
 * Everything here is proved through the handler and through the schema validator the server arms, because the
 * defect lived in the space between the two.</p>
 */
class GrowingCollectionToolsPagingTest {
    private static final List<String> PAGED_KEYS = List.of("offset", "limit", "totalMatches", "hasMore", "items");

    @TempDir
    Path temporaryDirectory;

    /** One paged tool, an argument set that names its collection, and the key every item is ordered by. */
    private record Subject(
            String tool,
            List<McpServerFeatures.SyncToolSpecification> specifications,
            Map<String, Object> arguments,
            Function<Map<?, ?>, Object> orderKey,
            List<Object> expectedOrder) {
    }

    @Test
    void withoutOffsetAndLimitAShortCollectionComesBackWholeInOrderAsOnePage() throws Exception {
        for (Subject subject : subjects()) {
            Map<?, ?> page = page(subject, Map.of());

            assertEquals(Set.copyOf(PAGED_KEYS), Set.copyOf(page.keySet()), subject.tool() + " must answer with exactly the page envelope");
            assertEquals(0, page.get("offset"), subject.tool());
            assertEquals(PageArguments.DEFAULT_LIMIT, page.get("limit"), subject.tool());
            assertEquals(3, page.get("totalMatches"), subject.tool());
            assertEquals(false, page.get("hasMore"), subject.tool());
            assertEquals(subject.expectedOrder(), keys(subject, page), subject.tool() + " lost or reordered an element");
        }
    }

    @Test
    void aLimitOfOneReturnsOneElementAndSaysThereIsMore() throws Exception {
        for (Subject subject : subjects()) {
            Map<?, ?> first = page(subject, Map.of("limit", 1));

            assertEquals(1, ((List<?>) first.get("items")).size(), subject.tool());
            assertEquals(true, first.get("hasMore"), subject.tool());
            assertEquals(3, first.get("totalMatches"), subject.tool());
            assertEquals(1, first.get("limit"), subject.tool());
        }
    }

    @Test
    void walkingThePagesReturnsTheWholeCollectionOnceInTheSameOrder() throws Exception {
        for (Subject subject : subjects()) {
            List<Object> seen = new ArrayList<>();
            int offset = 0;
            boolean more = true;
            int pages = 0;
            while (more) {
                Map<?, ?> page = page(subject, Map.of("offset", offset, "limit", 1));
                seen.addAll(keys(subject, page));
                more = (Boolean) page.get("hasMore");
                offset += 1;
                assertTrue(++pages <= 3, subject.tool() + " never stopped saying hasMore");
            }

            assertEquals(subject.expectedOrder(), seen, subject.tool());
        }
    }

    @Test
    void anOffsetPastTheEndIsAnEmptyPageThatStillReportsTheTotal() throws Exception {
        for (Subject subject : subjects()) {
            Map<?, ?> page = page(subject, Map.of("offset", 10));

            assertEquals(List.of(), page.get("items"), subject.tool());
            assertEquals(3, page.get("totalMatches"), subject.tool());
            assertEquals(false, page.get("hasMore"), subject.tool());
            assertEquals(10, page.get("offset"), subject.tool());
        }
    }

    /**
     * The audit journal is ordered by {@code (at, id)} compared as values. The store sorts the column as text, and
     * an {@link Instant} with a fraction ({@code ...:00.5Z}) sorts before the same second without one
     * ({@code ...:00Z}) although it is later. The fixture writes exactly that, so an order taken from the column
     * would put the second record first.
     */
    @Test
    void theAuditJournalIsPagedInChronologicalOrderNotInTextOrder() throws Exception {
        Subject audit = subjects().stream().filter(item -> item.tool().equals(MorpheusPolicyMcpTools.AUDIT)).findFirst().orElseThrow();

        Map<?, ?> all = page(audit, Map.of());
        List<String> stamps = ((List<?>) all.get("items")).stream().map(item -> (String) ((Map<?, ?>) item).get("at")).toList();
        List<Instant> instants = stamps.stream().map(Instant::parse).toList();

        assertEquals(instants.stream().sorted().toList(), instants, "the journal must be chronological: " + stamps);
        assertFalse(stamps.equals(stamps.stream().sorted().toList()),
                "the fixture must discriminate: text order and chronological order have to differ, or this test proves nothing: " + stamps);
        assertEquals(List.of("CREATE", "UPDATE", "UPDATE"),
                ((List<?>) all.get("items")).stream().map(item -> ((Map<?, ?>) item).get("action")).toList());

        Map<?, ?> second = page(audit, Map.of("offset", 1, "limit", 1));
        assertEquals(stamps.get(1), ((Map<?, ?>) ((List<?>) second.get("items")).getFirst()).get("at"));
    }

    @Test
    void theBoundsAreEnforcedByTheHandlerAndAnnouncedByTheSchemaAlike() throws Exception {
        JsonSchemaValidator validator = McpJsonDefaults.getSchemaValidator();
        for (Subject subject : subjects()) {
            McpSchema.Tool tool = tool(subject);
            Map<String, Object> id = new LinkedHashMap<>(subject.arguments());

            for (Map<String, Object> extra : List.of(
                    Map.<String, Object>of("limit", 0),
                    Map.<String, Object>of("limit", PageArguments.MAX_LIMIT + 1),
                    Map.<String, Object>of("offset", -1),
                    Map.<String, Object>of("offset", (long) Integer.MAX_VALUE + 1))) {
                Map<String, Object> arguments = new LinkedHashMap<>(id);
                arguments.putAll(extra);

                assertNotNull(ToolInputValidator.validate(tool, arguments, true, validator),
                        () -> subject.tool() + " schema accepted " + extra);
                McpSchema.CallToolResult refused = McpToolCall.call(subject.specifications(), subject.tool(), arguments);
                assertTrue(refused.isError(), () -> subject.tool() + " handler accepted " + extra);
            }

            Map<String, Object> widest = new LinkedHashMap<>(id);
            widest.put("limit", PageArguments.MAX_LIMIT);
            widest.put("offset", Integer.MAX_VALUE);
            assertNull(ToolInputValidator.validate(tool, widest, true, validator), subject.tool() + " schema refused the widest page");
            McpSchema.CallToolResult accepted = McpToolCall.call(subject.specifications(), subject.tool(), widest);
            assertFalse(accepted.isError(), () -> subject.tool() + " handler refused the widest page: " + McpToolCall.text(accepted));
        }
    }

    /**
     * The whole point of the finding. The transport's advice is to retry with a smaller {@code limit}; before this,
     * the schema validation the server arms refused exactly that retry.
     */
    @Test
    void theAdviceTheTransportGivesCanBeFollowedOnEveryTool() throws Exception {
        JsonSchemaValidator validator = McpJsonDefaults.getSchemaValidator();
        for (Subject subject : subjects()) {
            Map<String, Object> retry = new LinkedHashMap<>(subject.arguments());
            retry.put("limit", 1);

            assertNull(ToolInputValidator.validate(tool(subject), retry, true, validator),
                    () -> subject.tool() + " refuses {id, limit}, the retry " + MorpheusMcpServer.OVERSIZED_RESPONSE_GUIDANCE);

            Map<String, Object> unknown = new LinkedHashMap<>(subject.arguments());
            unknown.put("argumentTheSchemaDoesNotDeclare", 1);
            assertNotNull(ToolInputValidator.validate(tool(subject), unknown, true, validator),
                    () -> subject.tool() + " must stay strict about everything else");
        }
    }

    /** Widening these three must not widen their neighbour: a single-entity read has nothing to page. */
    @Test
    void aSingleEntityReadStillRefusesAPage() throws Exception {
        McpSchema.Tool get = new MorpheusPolicyMcpTools(database("neighbour.db")).specifications().stream()
                .map(McpServerFeatures.SyncToolSpecification::tool)
                .filter(tool -> tool.name().equals(MorpheusPolicyMcpTools.GET))
                .findFirst().orElseThrow();

        assertNotNull(ToolInputValidator.validate(get, Map.of("id", "x", "limit", 1), true, McpJsonDefaults.getSchemaValidator()));
    }

    // ------------------------------------------------------------------ fixtures

    private List<Subject> subjects() throws Exception {
        Path policyDatabase = database("policy.db");
        String packId = writePolicyPack(policyDatabase);
        List<McpServerFeatures.SyncToolSpecification> policy = new MorpheusPolicyMcpTools(policyDatabase).specifications();

        Path queryDatabase = database("query.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(queryDatabase);
        List<McpServerFeatures.SyncToolSpecification> query = new MorpheusQueryMcpTools(queryDatabase).specifications();
        String viewId = writeSavedView(query, project);

        return List.of(
                new Subject(MorpheusPolicyMcpTools.VERSIONS, policy, Map.of("id", packId),
                        item -> ((Number) item.get("versionNumber")).intValue(), List.of(1, 2, 3)),
                new Subject(MorpheusPolicyMcpTools.AUDIT, policy, Map.of("id", packId),
                        item -> item.get("action"), List.of("CREATE", "UPDATE", "UPDATE")),
                new Subject(MorpheusQueryMcpTools.LIST_SAVED_VIEW_VERSIONS, query, Map.of("id", viewId),
                        item -> ((Number) item.get("revision")).intValue(), List.of(1, 2, 3)));
    }

    /** A pack with three versions and three audit records, stamped with instants that sort differently as text. */
    private String writePolicyPack(Path database) {
        SteppedClock clock = new SteppedClock(Instant.parse("2026-09-01T10:00:00Z"));
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(database)) {
            PolicyPackService service = new PolicyPackService(store, clock);
            PolicyPack.Definition created = service.create("growing", rules("first"), "alice", "baseline");
            clock.set("2026-09-01T10:00:00.5Z");
            service.update(created.id(), 1, "growing", rules("second"), "bob", "second version");
            clock.set("2026-09-01T10:00:01Z");
            service.update(created.id(), 2, "growing", rules("third"), "carol", "third version");
            return created.id().toString();
        }
    }

    private static List<PolicyRule> rules(String description) {
        return List.of(new PolicyRule(
                com.morpheus.application.policy.PolicyIds.RuleId.generate(),
                description,
                PolicyRule.Kind.QUALITY_THRESHOLD,
                PolicyRule.Severity.BLOCKER,
                new PolicyRule.QualityThreshold(PolicyRule.QualityMetric.FINDINGS, PolicyRule.Comparison.LTE, 0.0d)));
    }

    /** A saved view updated twice: three immutable revisions, written through the tools a client would use. */
    private String writeSavedView(
            List<McpServerFeatures.SyncToolSpecification> query, McpToolCall.PublishedProject project) throws Exception {
        McpSchema.CallToolResult created = McpToolCall.call(query, MorpheusQueryMcpTools.CREATE_SAVED_VIEW, Map.of(
                "name", "revision one", "scopeKind", "PROJECT", "scopeId", project.projectId(), "entity", "REQUIREMENT"));
        assertFalse(created.isError(), () -> McpToolCall.text(created));
        String id = (String) json(McpToolCall.text(created)).get("id");
        for (int revision = 1; revision <= 2; revision++) {
            int expected = revision;
            McpSchema.CallToolResult updated = McpToolCall.call(query, MorpheusQueryMcpTools.UPDATE_SAVED_VIEW, Map.of(
                    "id", id, "expectedRevision", expected, "name", "revision " + (expected + 1), "entity", "REQUIREMENT"));
            assertFalse(updated.isError(), () -> McpToolCall.text(updated));
        }
        return id;
    }

    // ------------------------------------------------------------------ helpers

    private Map<?, ?> page(Subject subject, Map<String, Object> extra) throws Exception {
        Map<String, Object> arguments = new LinkedHashMap<>(subject.arguments());
        arguments.putAll(extra);
        McpSchema.CallToolResult result = McpToolCall.call(subject.specifications(), subject.tool(), arguments);
        assertFalse(result.isError(), () -> subject.tool() + " refused " + arguments + ": " + McpToolCall.text(result));
        return json(McpToolCall.text(result));
    }

    private static List<Object> keys(Subject subject, Map<?, ?> page) {
        return ((List<?>) page.get("items")).stream().map(item -> subject.orderKey().apply((Map<?, ?>) item)).toList();
    }

    private static McpSchema.Tool tool(Subject subject) {
        return subject.specifications().stream()
                .map(McpServerFeatures.SyncToolSpecification::tool)
                .filter(tool -> tool.name().equals(subject.tool()))
                .findFirst().orElseThrow();
    }

    private static Map<?, ?> json(String text) throws Exception {
        return McpJsonDefaults.getMapper().readValue(text, Map.class);
    }

    private Path database(String name) {
        return temporaryDirectory.resolve(name).toAbsolutePath().normalize();
    }

    /** A clock the fixture moves by hand, because the audit order under test depends on the exact instants. */
    private static final class SteppedClock extends Clock {
        private Instant now;

        SteppedClock(Instant start) {
            this.now = start;
        }

        void set(String instant) {
            this.now = Instant.parse(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
