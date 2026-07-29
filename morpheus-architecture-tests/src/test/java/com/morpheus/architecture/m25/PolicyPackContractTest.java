package com.morpheus.architecture.m25;

import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyConflictException;
import com.morpheus.application.policy.PolicyEvaluation;
import com.morpheus.application.policy.PolicyEvaluationService;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.application.policy.PolicyScope;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.memory.MemoryPolicyPackStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyPackContractTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void versionsAreImmutableAndStaleCasIsRejected() {
        MemoryPolicyPackStore store = new MemoryPolicyPackStore();
        PolicyPackService service = new PolicyPackService(store, CLOCK);
        var first = service.create("Governance", List.of(rule()), "alice", "initial policy");
        var firstVersion = service.versions(first.id()).getFirst();

        var second = service.update(first.id(), 1, "Governance v2", List.of(rule()), "alice", "tighten policy");

        assertEquals(2, second.revision());
        assertEquals(2, second.latestVersionNumber());
        assertEquals(2, service.versions(first.id()).size());
        assertEquals("Governance", service.version(first.id(), firstVersion.versionId()).name());
        assertThrows(PolicyConflictException.class,
                () -> service.update(first.id(), 1, "stale", List.of(rule()), "bob", "stale write"));
    }

    @Test
    void unknownRemainsUnknownUnlessExplicitOverrideForcesBlock() {
        MemoryPolicyPackStore store = new MemoryPolicyPackStore();
        PolicyPackService service = new PolicyPackService(store, CLOCK);
        var definition = service.create("Governance", List.of(rule()), "alice", "initial policy");
        var version = service.versions(definition.id()).getFirst();
        PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
        service.activate(scope, definition.id(), version.versionId(), 0, "alice", "enable policy");

        PolicyEvaluationService evaluation = new PolicyEvaluationService(
                store,
                (ignoredScope, ignoredRule) -> PolicyEvaluation.Fact.unknown("missing evidence", List.of("evidence:missing")));

        var before = evaluation.evaluatePack(scope, definition.id());
        assertEquals(PolicyEvaluation.Decision.UNKNOWN, before.decision());
        assertEquals(PolicyEvaluation.Decision.UNKNOWN, before.rules().getFirst().originalDecision());
        assertEquals(PolicyEvaluation.Decision.UNKNOWN, before.rules().getFirst().effectiveDecision());

        service.putOverride(
                scope,
                definition.id(),
                version.rules().getFirst().id(),
                PolicyConfiguration.OverrideMode.FORCE_BLOCK,
                0,
                "security-owner",
                "explicit governance exception");

        var after = evaluation.evaluatePack(scope, definition.id());
        assertEquals(PolicyEvaluation.Decision.BLOCK, after.decision());
        assertEquals(PolicyEvaluation.Decision.UNKNOWN, after.rules().getFirst().originalDecision());
        assertEquals(PolicyEvaluation.Decision.BLOCK, after.rules().getFirst().effectiveDecision());
        assertTrue(after.rules().getFirst().reason().contains("explicit governance exception"));
    }

    @Test
    void dryRunDoesNotWriteActivationOverrideOrAudit() {
        MemoryPolicyPackStore store = new MemoryPolicyPackStore();
        PolicyPackService service = new PolicyPackService(store, CLOCK);
        var definition = service.create("Governance", List.of(rule()), "alice", "initial policy");
        var version = service.versions(definition.id()).getFirst();
        PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
        int auditBefore = service.audit(definition.id()).size();

        PolicyEvaluationService evaluation = new PolicyEvaluationService(
                store,
                (ignoredScope, ignoredRule) -> PolicyEvaluation.Fact.pass("observed", List.of("fact:1")));
        var report = evaluation.dryRun(scope, definition.id(), version.versionId());

        assertTrue(report.dryRun());
        assertEquals(PolicyEvaluation.Decision.PASS, report.decision());
        assertTrue(service.activations(scope).isEmpty());
        assertTrue(service.overrides(scope).isEmpty());
        assertEquals(auditBefore, service.audit(definition.id()).size());
    }

    private PolicyRule rule() {
        return new PolicyRule(
                PolicyIds.RuleId.generate(),
                "No explicit blockers",
                PolicyRule.Kind.CONSTRAINT_GUARD,
                PolicyRule.Severity.BLOCKER,
                new PolicyRule.ConstraintGuard(ChangeId.generate(), ChangeLifecycleState.IMPLEMENTING));
    }
}