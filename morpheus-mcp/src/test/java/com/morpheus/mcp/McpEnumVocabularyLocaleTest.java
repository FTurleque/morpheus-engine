package com.morpheus.mcp;

import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryDefinitionCodec;
import com.morpheus.application.query.dsl.QueryDslParser;
import com.morpheus.domain.project.ProjectSpecificationId;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The MCP enum vocabulary must mean the same thing on every machine.
 *
 * <p>These tools accept enum names as text and upper-case them before {@code valueOf}. Doing that without a
 * locale is a defect that is invisible in English and French: Turkish and Azerbaijani map {@code i} to
 * {@code İ}, so {@code "implementing"} became {@code İMPLEMENTİNG} and every MORPHEUS enum containing an
 * {@code i} -- {@code IMPLEMENTING}, {@code SPECIFIED}, {@code MISSING}, {@code INCOMING} -- was refused for
 * input that is valid everywhere else. The CLI already normalised through {@code Locale.ROOT}, so the same
 * operator on the same machine got a working command and a rejected tool call for one value.</p>
 *
 * <p>Each test runs under a Turkish default locale and asserts on the failure *reason*: these tools reach a
 * database that holds no pack, portfolio or view, so a call cannot succeed outright. What it can do is fail for
 * the right reason -- a rejected enum name says the argument never parsed, and that is the regression.</p>
 */
class McpEnumVocabularyLocaleTest {
    private static final Locale HOSTILE = Locale.forLanguageTag("tr-TR");
    private static final String PROJECT_ID = "01920000-0000-7000-8000-000000000001";

    @TempDir
    Path temporaryDirectory;

    private Locale original;

    @BeforeEach
    void useAHostileLocale() {
        original = Locale.getDefault();
        Locale.setDefault(HOSTILE);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    void aLifecycleStateIsAcceptedInLowerCase() {
        String message = call(
                new MorpheusPolicyMcpTools(database("policy-lifecycle.db")).specifications(),
                MorpheusPolicyMcpTools.CREATE,
                Map.of(
                        "scopeKind", "project",
                        "scopeId", "01920000-0000-7000-8000-000000000001",
                        "name", "pack",
                        "actor", "auditor",
                        "reason", "coverage",
                        "rules", List.of(Map.of(
                                "description", "guard",
                                "kind", "lifecycle_guard",
                                "severity", "warning",
                                "changeId", "01920000-0000-7000-8000-000000000002",
                                "sourceState", "specified",
                                "targetState", "implementing"))));

        assertNoEnumWasRejected(message, "IMPLEMENTING", "SPECIFIED", "LIFECYCLE_GUARD", "WARNING");
    }

    @Test
    void aQualityRuleVocabularyIsAcceptedInLowerCase() {
        String message = call(
                new MorpheusPolicyMcpTools(database("policy-quality.db")).specifications(),
                MorpheusPolicyMcpTools.CREATE,
                Map.of(
                        "scopeKind", "portfolio",
                        "scopeId", "01920000-0000-7000-8000-000000000001",
                        "name", "pack",
                        "actor", "auditor",
                        "reason", "coverage",
                        "rules", List.of(Map.of(
                                "description", "threshold",
                                "kind", "quality_threshold",
                                "severity", "info",
                                "qualityMetric", "orphan_requirements",
                                "comparison", "lte",
                                "threshold", 3.0d))));

        assertNoEnumWasRejected(message, "QUALITY_THRESHOLD", "INFO", "ORPHAN_REQUIREMENTS", "LTE");
    }

    @Test
    void aConstraintGuardVocabularyIsAcceptedInLowerCase() {
        String message = call(
                new MorpheusPolicyMcpTools(database("policy-constraint.db")).specifications(),
                MorpheusPolicyMcpTools.CREATE,
                Map.of(
                        "scopeKind", "project",
                        "scopeId", "01920000-0000-7000-8000-000000000001",
                        "name", "pack",
                        "actor", "auditor",
                        "reason", "coverage",
                        "rules", List.of(Map.of(
                                "description", "guard",
                                "kind", "constraint_guard",
                                "severity", "blocker",
                                "changeId", "01920000-0000-7000-8000-000000000002",
                                "targetState", "verifying"))));

        assertNoEnumWasRejected(message, "CONSTRAINT_GUARD", "BLOCKER", "VERIFYING");
    }

    @Test
    void aQueryAssertionVocabularyIsAcceptedInLowerCase() {
        String message = call(
                new MorpheusPolicyMcpTools(database("policy-assertion.db")).specifications(),
                MorpheusPolicyMcpTools.CREATE,
                Map.of(
                        "scopeKind", "project",
                        "scopeId", PROJECT_ID,
                        "name", "pack",
                        "actor", "auditor",
                        "reason", "coverage",
                        "rules", List.of(Map.of(
                                "description", "assertion",
                                "kind", "query_assertion",
                                "severity", "warning",
                                "queryDefinition", encodedRequirementQuery(),
                                "comparison", "gte",
                                "expectedCount", 1))));

        assertNoEnumWasRejected(message, "QUERY_ASSERTION", "GTE");
    }

    @Test
    void anOverrideModeIsAcceptedInLowerCase() {
        String message = call(
                new MorpheusPolicyMcpTools(database("policy-override.db")).specifications(),
                MorpheusPolicyMcpTools.PUT_OVERRIDE,
                Map.of(
                        "scopeKind", "project",
                        "scopeId", "01920000-0000-7000-8000-000000000001",
                        "id", "01920000-0000-7000-8000-000000000003",
                        "ruleId", "01920000-0000-7000-8000-000000000004",
                        "mode", "force_block",
                        "expectedRevision", 0,
                        "actor", "auditor",
                        "reason", "escalation"));

        assertNoEnumWasRejected(message, "FORCE_BLOCK");
    }

    @Test
    void aPolicyManagementScopeKindIsAcceptedInLowerCase() {
        String message = call(
                new MorpheusPolicyMcpManagementTools(database("policy-management.db")).specifications(),
                MorpheusPolicyMcpManagementTools.LIST_ACTIVATIONS,
                Map.of(
                        "scopeKind", "portfolio",
                        "scopeId", "01920000-0000-7000-8000-000000000001"));

        assertFalse(message.contains("scopeKind must be"),
                () -> "a lower-case scopeKind must reach the registry, got: " + message);
    }

    @Test
    void aFreshnessStateAndTraversalDirectionAreAcceptedInLowerCase() {
        List<McpServerFeatures.SyncToolSpecification> tools =
                new MorpheusPortfolioMcpTools(database("portfolio.db")).specifications();

        String freshness = call(tools, MorpheusPortfolioMcpTools.OBSERVE_FRESHNESS, Map.of(
                "portfolioId", "01920000-0000-7000-8000-000000000001",
                "projectId", "01920000-0000-7000-8000-000000000002",
                "state", "missing"));
        assertNoEnumWasRejected(freshness, "MISSING");

        String traversal = call(tools, MorpheusPortfolioMcpTools.TRAVERSE, Map.of(
                "portfolioId", "01920000-0000-7000-8000-000000000001",
                "startProjectId", "01920000-0000-7000-8000-000000000002",
                "startType", "requirement",
                "startId", "01920000-0000-7000-8000-000000000003",
                "direction", "incoming"));
        assertNoEnumWasRejected(traversal, "INCOMING");
    }

    @Test
    void anExportFormatAndQueryScopeKindAreAcceptedInLowerCase() {
        List<McpServerFeatures.SyncToolSpecification> tools =
                new MorpheusQueryMcpTools(database("query.db")).specifications();

        String export = call(tools, MorpheusQueryMcpTools.EXPORT_QUERY, Map.of(
                "scopeKind", "project",
                "scopeId", "01920000-0000-7000-8000-000000000001",
                "entity", "requirement",
                "format", "markdown"));

        assertFalse(export.contains("format must be JSON, CSV or MARKDOWN"),
                () -> "a lower-case export format must parse, got: " + export);
        assertFalse(export.contains("scopeKind must be"),
                () -> "a lower-case scopeKind must parse, got: " + export);
    }

    /**
     * A rejected enum name is the signature of the defect: it means the text never became a constant. Anything
     * else the tool reports -- an absent pack, an unknown portfolio -- happened after parsing succeeded.
     */
    private static void assertNoEnumWasRejected(String message, String... constants) {
        assertFalse(message.contains("No enum constant"),
                () -> "an enum name was rejected under " + Locale.getDefault() + ": " + message);
        for (String constant : constants) {
            assertFalse(message.contains(constant + "İ") || message.contains("İ"),
                    () -> "the dotted capital I of the Turkish locale reached " + constant + ": " + message);
        }
        assertTrue(true);
    }

    /**
     * A query assertion decodes its definition before its comparison is parsed, so the rule only reaches the
     * enum under test when the definition is genuinely valid.
     */
    private static String encodedRequirementQuery() {
        return new QueryDefinitionCodec().encode(new QueryDslParser().parse(
                new ProjectQueryScope(ProjectSpecificationId.parse(PROJECT_ID)),
                "requirement", null, null, null, 0, 10));
    }

    private Path database(String name) {
        return temporaryDirectory.resolve(name).toAbsolutePath().normalize();
    }

    private static String call(
            List<McpServerFeatures.SyncToolSpecification> specifications,
            String toolName,
            Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification specification = specifications.stream()
                .filter(item -> item.tool().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tool not published: " + toolName));
        McpSchema.CallToolResult result =
                specification.callHandler().apply(null, new McpSchema.CallToolRequest(toolName, arguments));
        return result.content().stream()
                .filter(item -> item instanceof McpSchema.TextContent)
                .map(item -> ((McpSchema.TextContent) item).text())
                .reduce("", (left, right) -> left + right);
    }
}
