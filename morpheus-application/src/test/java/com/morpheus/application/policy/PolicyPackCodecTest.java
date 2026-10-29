package com.morpheus.application.policy;

import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryEntityType;
import com.morpheus.application.query.dsl.QueryPage;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stored form of a policy pack version, and the public view of the same version. */
class PolicyPackCodecTest {
    private static final Instant CREATED = Instant.parse("2026-10-29T12:00:00Z");

    private final PolicyPackCodec codec = new PolicyPackCodec();

    @Test
    void everyRuleKindSurvivesARoundTripAndIsProjectedWithItsOwnConfiguration() {
        ChangeId changeId = ChangeId.generate();
        PolicyPack.Version version = version(List.of(
                rule(PolicyRule.Kind.CONSTRAINT_GUARD, new PolicyRule.ConstraintGuard(changeId, ChangeLifecycleState.COMPLETED)),
                rule(PolicyRule.Kind.LIFECYCLE_GUARD, new PolicyRule.LifecycleGuard(changeId,
                        ChangeLifecycleState.PROPOSED, ChangeLifecycleState.SPECIFIED)),
                rule(PolicyRule.Kind.QUALITY_THRESHOLD, new PolicyRule.QualityThreshold(
                        PolicyRule.QualityMetric.REQUIREMENT_COVERAGE_PERCENT, PolicyRule.Comparison.GTE, 80.5d)),
                rule(PolicyRule.Kind.QUERY_ASSERTION, new PolicyRule.QueryAssertion(QueryDefinition.all(
                        new ProjectQueryScope(ProjectSpecificationId.generate()), QueryEntityType.REQUIREMENT,
                        QueryPage.first(10)), PolicyRule.Comparison.EQ, 3L))));

        String encoded = codec.encode(version);

        assertEquals(version, codec.decode(encoded));
        assertEquals(encoded, codec.encode(codec.decode(encoded)), "the encoding is deterministic");

        PolicyPublicViews.VersionView view = PolicyPublicViews.version(version);
        assertEquals(version.versionId().toString(), view.versionId());
        assertEquals(4, view.rules().size());
        List<PolicyPublicViews.RuleView> rules = view.rules();
        PolicyPublicViews.RuleView threshold = rules.stream()
                .filter(rule -> rule.kind().equals("QUALITY_THRESHOLD")).findFirst().orElseThrow();
        assertEquals(80.5d, threshold.config().threshold().orElseThrow());
        PolicyPublicViews.RuleView lifecycle = rules.stream()
                .filter(rule -> rule.kind().equals("LIFECYCLE_GUARD")).findFirst().orElseThrow();
        assertEquals("PROPOSED", lifecycle.config().sourceState().orElseThrow());
        PolicyPublicViews.RuleView query = rules.stream()
                .filter(rule -> rule.kind().equals("QUERY_ASSERTION")).findFirst().orElseThrow();
        assertEquals(3L, query.config().expectedCount().orElseThrow());
        assertEquals(List.of(view), PolicyPublicViews.versions(List.of(version)));
    }

    @Test
    void aDefinitionIsProjectedWithItsRevisionAndTimes() {
        PolicyPack.Definition definition = new PolicyPack.Definition(PolicyIds.PackId.generate(), "Pack", 2, 3,
                CREATED, CREATED.plusSeconds(60));

        PolicyPublicViews.DefinitionView view = PolicyPublicViews.definition(definition);

        assertEquals(new PolicyPublicViews.DefinitionView(definition.id().toString(), "Pack", 2, 3,
                CREATED.toString(), CREATED.plusSeconds(60).toString()), view);
        assertEquals(List.of(view), PolicyPublicViews.definitions(List.of(definition)));
    }

    @Test
    void anEncodingThatIsNotAPolicyPackVersionIsRefusedByItsCause() throws IOException {
        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class, () -> codec.decode(" "));
        assertEquals("encoded policy pack version must not be blank", blank.getMessage());

        IllegalArgumentException version = assertThrows(IllegalArgumentException.class,
                () -> codec.decode(encode(out -> out.writeInt(2))));
        assertEquals("unsupported policy pack codec version: 2", version.getMessage());

        IllegalArgumentException count = assertThrows(IllegalArgumentException.class,
                () -> codec.decode(encode(out -> {
                    out.writeInt(1);
                    out.writeUTF(PolicyIds.PackId.generate().toString());
                    out.writeUTF(PolicyIds.VersionId.generate().toString());
                    out.writeLong(1);
                    out.writeUTF("Pack");
                    out.writeUTF(CREATED.toString());
                    out.writeInt(0);
                })));
        assertEquals("invalid encoded policy rule count: 0", count.getMessage());

        String valid = codec.encode(version(List.of(rule(PolicyRule.Kind.CONSTRAINT_GUARD,
                new PolicyRule.ConstraintGuard(ChangeId.generate(), ChangeLifecycleState.COMPLETED)))));
        byte[] bytes = Base64.getUrlDecoder().decode(valid);
        byte[] trailing = java.util.Arrays.copyOf(bytes, bytes.length + 1);
        IllegalArgumentException extra = assertThrows(IllegalArgumentException.class,
                () -> codec.decode(Base64.getUrlEncoder().withoutPadding().encodeToString(trailing)));
        assertEquals("encoded policy pack contains trailing data", extra.getMessage());

        byte[] truncated = java.util.Arrays.copyOf(bytes, bytes.length - 4);
        IllegalArgumentException broken = assertThrows(IllegalArgumentException.class,
                () -> codec.decode(Base64.getUrlEncoder().withoutPadding().encodeToString(truncated)));
        assertEquals("invalid encoded policy pack version", broken.getMessage());
        assertTrue(broken.getCause() instanceof IOException, () -> "a short read is the cause: " + broken.getCause());
    }

    private static PolicyPack.Version version(List<PolicyRule> rules) {
        return new PolicyPack.Version(PolicyIds.PackId.generate(), PolicyIds.VersionId.generate(), 1, "Pack", rules,
                CREATED);
    }

    private static PolicyRule rule(PolicyRule.Kind kind, PolicyRule.Config config) {
        return new PolicyRule(PolicyIds.RuleId.generate(), "Rule " + kind, kind, PolicyRule.Severity.BLOCKER, config);
    }

    private static String encode(Writer writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writer.write(out);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }
}
