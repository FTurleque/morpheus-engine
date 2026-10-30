package com.morpheus.application.policy;

import com.morpheus.application.store.EntityNotFoundException;
import com.morpheus.application.store.EntityStateException;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The evaluator maps a fact to a decision by the rule's severity, applies an override on top of it without hiding the
 * original, and aggregates BLOCK over UNKNOWN over WARN over PASS. UNKNOWN is never turned into PASS or BLOCK.
 */
class PolicyEvaluationServiceTest {
    private static final Instant AT = Instant.parse("2026-10-30T08:00:00Z");

    private final HandWrittenPolicyPackStore store = new HandWrittenPolicyPackStore();
    private final Map<PolicyIds.RuleId, PolicyEvaluation.Fact> facts = new TreeMap<>();
    private final PolicyEvaluationService service = new PolicyEvaluationService(store,
            (scope, rule) -> facts.getOrDefault(rule.id(), PolicyEvaluation.Fact.pass("held", List.of())));
    private final PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());

    @Test
    void aFailingFactBlocksUnderABlockerAndWarnsUnderAnyOtherSeverity() {
        PolicyRule blocker = rule("blocker", PolicyRule.Severity.BLOCKER);
        PolicyRule warning = rule("warning", PolicyRule.Severity.WARNING);
        facts.put(blocker.id(), PolicyEvaluation.Fact.fail("broken", List.of("b", "a")));
        facts.put(warning.id(), PolicyEvaluation.Fact.fail("broken", List.of()));
        PolicyPack.Version version = active(blocker, warning);

        PolicyEvaluation.Report report = service.evaluatePack(scope, version.packId());

        assertEquals(PolicyEvaluation.Decision.BLOCK, report.decision());
        assertFalse(report.dryRun());
        assertEquals(PolicyEvaluation.Decision.BLOCK, result(report, blocker).effectiveDecision());
        assertEquals(List.of("a", "b"), result(report, blocker).evidence());
        assertEquals(PolicyEvaluation.Decision.WARN, result(report, warning).effectiveDecision());
    }

    @Test
    void anUnknownFactStaysUnknownAndOutranksAWarning() {
        PolicyRule unknown = rule("unknown", PolicyRule.Severity.BLOCKER);
        PolicyRule warning = rule("warning", PolicyRule.Severity.INFO);
        PolicyRule notApplicable = rule("not applicable", PolicyRule.Severity.BLOCKER);
        facts.put(unknown.id(), PolicyEvaluation.Fact.unknown("unseen", List.of()));
        facts.put(warning.id(), PolicyEvaluation.Fact.measured(false, 3, "measured", List.of()));
        facts.put(notApplicable.id(), PolicyEvaluation.Fact.notApplicable("no change"));
        PolicyPack.Version version = active(unknown, warning, notApplicable);

        PolicyEvaluation.Report report = service.evaluatePack(scope, version.packId());

        assertEquals(PolicyEvaluation.Decision.UNKNOWN, report.decision());
        assertEquals(PolicyEvaluation.Applicability.UNKNOWN, result(report, unknown).applicability());
        assertEquals(PolicyEvaluation.Decision.PASS, result(report, notApplicable).effectiveDecision());
        assertEquals(OptionalDouble.of(3), result(report, warning).observedValue());
    }

    @Test
    void aPackOfWarningsWarnsAndAPackOfPassesPasses() {
        PolicyRule warning = rule("warning", PolicyRule.Severity.WARNING);
        facts.put(warning.id(), PolicyEvaluation.Fact.fail("broken", List.of()));
        PolicyPack.Version warns = active(warning);
        PolicyPack.Version passes = active(rule("held", PolicyRule.Severity.BLOCKER));

        assertEquals(PolicyEvaluation.Decision.WARN, service.evaluatePack(scope, warns.packId()).decision());
        assertEquals(PolicyEvaluation.Decision.PASS, service.evaluatePack(scope, passes.packId()).decision());
    }

    @Test
    void anOverrideChangesTheEffectiveDecisionKeepsTheOriginalAndSaysWhy() {
        PolicyRule disabled = rule("disabled", PolicyRule.Severity.BLOCKER);
        PolicyRule warned = rule("warned", PolicyRule.Severity.BLOCKER);
        PolicyRule forced = rule("forced", PolicyRule.Severity.INFO);
        facts.put(disabled.id(), PolicyEvaluation.Fact.fail("broken", List.of()));
        facts.put(warned.id(), PolicyEvaluation.Fact.fail("broken", List.of()));
        PolicyPack.Version version = active(disabled, warned, forced);
        override(version, disabled, PolicyConfiguration.OverrideMode.DISABLE);
        override(version, warned, PolicyConfiguration.OverrideMode.FORCE_WARN);
        override(version, forced, PolicyConfiguration.OverrideMode.FORCE_BLOCK);

        PolicyEvaluation.Report report = service.evaluatePack(scope, version.packId());

        PolicyEvaluation.RuleResult off = result(report, disabled);
        assertEquals(PolicyEvaluation.Decision.BLOCK, off.originalDecision());
        assertEquals(PolicyEvaluation.Decision.PASS, off.effectiveDecision());
        assertEquals("broken; override=DISABLE by operator because accepted risk", off.reason());
        assertTrue(off.appliedOverride().isPresent());
        assertEquals(PolicyEvaluation.Decision.WARN, result(report, warned).effectiveDecision());
        assertEquals(PolicyEvaluation.Decision.BLOCK, result(report, forced).effectiveDecision());
        assertEquals(PolicyEvaluation.Decision.BLOCK, report.decision());
    }

    @Test
    void theGovernanceReportAggregatesEveryActivePackOfTheScope() {
        PolicyRule warning = rule("warning", PolicyRule.Severity.WARNING);
        PolicyRule unknown = rule("unknown", PolicyRule.Severity.WARNING);
        PolicyRule blocker = rule("blocker", PolicyRule.Severity.BLOCKER);
        facts.put(warning.id(), PolicyEvaluation.Fact.fail("broken", List.of()));
        facts.put(unknown.id(), PolicyEvaluation.Fact.unknown("unseen", List.of()));
        facts.put(blocker.id(), PolicyEvaluation.Fact.fail("broken", List.of()));

        assertEquals(PolicyEvaluation.Decision.PASS, service.evaluate(scope).decision());
        active(rule("held", PolicyRule.Severity.BLOCKER));
        assertEquals(PolicyEvaluation.Decision.PASS, service.evaluate(scope).decision());
        active(warning);
        assertEquals(PolicyEvaluation.Decision.WARN, service.evaluate(scope).decision());
        active(unknown);
        assertEquals(PolicyEvaluation.Decision.UNKNOWN, service.evaluate(scope).decision());
        active(blocker);
        PolicyEvaluation.GovernanceReport report = service.evaluate(scope);

        assertEquals(PolicyEvaluation.Decision.BLOCK, report.decision());
        assertEquals(4, report.packs().size());
        assertEquals(report.packs().stream().sorted().toList(), report.packs());
    }

    @Test
    void aDryRunEvaluatesAnInactiveVersionAndSaysSo() {
        PolicyPack.Version version = version(rule("held", PolicyRule.Severity.BLOCKER));
        store.putVersion(version);

        PolicyEvaluation.Report report = service.dryRun(scope, version.packId(), version.versionId());

        assertTrue(report.dryRun());
        assertEquals(PolicyEvaluation.Decision.PASS, report.decision());
    }

    @Test
    void anInactivePackOrAnUnknownVersionIsRefusedByName() {
        PolicyIds.PackId packId = PolicyIds.PackId.generate();
        PolicyIds.VersionId versionId = PolicyIds.VersionId.generate();

        EntityStateException inactive = assertThrows(EntityStateException.class,
                () -> service.evaluatePack(scope, packId));
        EntityNotFoundException unknown = assertThrows(EntityNotFoundException.class,
                () -> service.dryRun(scope, packId, versionId));

        assertEquals("policy pack is not active in scope: " + packId, inactive.getMessage());
        assertEquals("unknown policy pack version: " + packId + "/" + versionId, unknown.getMessage());
    }

    @Test
    void persistedActivationsBeyondTheBudgetAreAnInconsistentStore() {
        for (int index = 0; index <= PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE; index++) {
            store.putActivation(new PolicyConfiguration.Activation(
                    scope, PolicyIds.PackId.generate(), PolicyIds.VersionId.generate(), 1, "actor", AT));
        }

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> service.evaluate(scope));

        assertEquals("persisted active policy packs exceed configured budget", refused.getMessage());
    }

    private PolicyPack.Version active(PolicyRule... rules) {
        PolicyPack.Version version = version(rules);
        store.putVersion(version);
        store.putActivation(new PolicyConfiguration.Activation(
                scope, version.packId(), version.versionId(), 1, "operator", AT));
        return version;
    }

    private void override(PolicyPack.Version version, PolicyRule rule, PolicyConfiguration.OverrideMode mode) {
        store.putOverride(new PolicyConfiguration.Override(
                scope, version.packId(), rule.id(), mode, "accepted risk", "operator", 1, AT));
    }

    private static PolicyPack.Version version(PolicyRule... rules) {
        return new PolicyPack.Version(PolicyIds.PackId.generate(), PolicyIds.VersionId.generate(), 1, "pack",
                List.of(rules), AT);
    }

    private static PolicyRule rule(String description, PolicyRule.Severity severity) {
        return new PolicyRule(PolicyIds.RuleId.generate(), description, PolicyRule.Kind.QUALITY_THRESHOLD, severity,
                new PolicyRule.QualityThreshold(PolicyRule.QualityMetric.FINDINGS, PolicyRule.Comparison.LTE, 0));
    }

    private static PolicyEvaluation.RuleResult result(PolicyEvaluation.Report report, PolicyRule rule) {
        Optional<PolicyEvaluation.RuleResult> found = report.rules().stream()
                .filter(result -> result.ruleId().equals(rule.id()))
                .findFirst();
        return found.orElseThrow();
    }
}
