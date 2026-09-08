package com.morpheus.api;

import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryDefinitionCodec;
import com.morpheus.application.query.dsl.QueryDslParser;
import com.morpheus.domain.project.ProjectSpecificationId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The HTTP policy vocabulary must mean the same thing on every machine.
 *
 * <p>Rule kinds, severities, lifecycle states, quality metrics and comparisons arrive as text and are
 * upper-cased before {@code valueOf}. Doing that without a locale is invisible in English and French and fatal
 * in Turkish or Azerbaijani, where {@code i} maps to {@code İ}: {@code "implementing"} became
 * {@code İMPLEMENTİNG}, {@code "specified"} became {@code SPECİFİED}, and the pack was refused. The CLI already
 * normalised through {@code Locale.ROOT}, so one operator on one machine got a working
 * {@code policy pack create} and a rejected POST for the same rule.</p>
 *
 * <p>Creating a pack needs no pre-existing state, so unlike the MCP surface these calls must actually succeed.
 * Every rule kind is exercised, because each one parses a different set of enums.</p>
 */
class PolicyApiEnumVocabularyLocaleTest {
    private static final Locale HOSTILE = Locale.forLanguageTag("tr-TR");
    private static final String CHANGE_ID = "01920000-0000-7000-8000-000000000002";

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
    void aConstraintGuardIsCreatedFromLowerCaseVocabulary() {
        assertNotNull(create("constraint-guard.db", new MorpheusPolicyApiService.RuleRequest(
                null, "guard", "constraint_guard", "blocker",
                CHANGE_ID, null, "implementing",
                null, null, null, null, null)));
    }

    @Test
    void aLifecycleGuardIsCreatedFromLowerCaseVocabulary() {
        assertNotNull(create("lifecycle-guard.db", new MorpheusPolicyApiService.RuleRequest(
                null, "guard", "lifecycle_guard", "warning",
                CHANGE_ID, "specified", "implementing",
                null, null, null, null, null)));
    }

    @Test
    void aQualityThresholdIsCreatedFromLowerCaseVocabulary() {
        assertNotNull(create("quality-threshold.db", new MorpheusPolicyApiService.RuleRequest(
                null, "threshold", "quality_threshold", "info",
                null, null, null,
                "orphan_requirements", "lte", 3.0d, null, null)));
    }

    @Test
    void aQueryAssertionIsCreatedFromLowerCaseVocabulary() {
        assertNotNull(create("query-assertion.db", new MorpheusPolicyApiService.RuleRequest(
                null, "assertion", "query_assertion", "warning",
                null, null, null,
                null, "gte", null, encodedRequirementQuery(), 1L)));
    }

    /**
     * A query assertion decodes its definition before its comparison is parsed, so the rule only reaches the
     * enum under test when the definition is genuinely valid.
     */
    private static String encodedRequirementQuery() {
        return new QueryDefinitionCodec().encode(new QueryDslParser().parse(
                new ProjectQueryScope(ProjectSpecificationId.generate()),
                "requirement", null, null, null, 0, 10));
    }

    private Object create(String databaseName, MorpheusPolicyApiService.RuleRequest rule) {
        Path database = temporaryDirectory.resolve(databaseName).toAbsolutePath().normalize();
        return new MorpheusPolicyApiService(database).create(new MorpheusPolicyApiService.CreateRequest(
                "Governance", List.of(rule), "auditor", "baseline"));
    }
}
