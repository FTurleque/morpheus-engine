package com.morpheus.store.memory;

import com.morpheus.application.policy.PolicyBudgets;
import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.application.policy.PolicyScope;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryPolicyPackStoreAtomicityTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-06T14:15:00Z"), ZoneOffset.UTC);
    private static final String ACTOR = "memory-atomicity-test";

    @Test
    void auditIdentityIsValidatedBeforeMutationAndValidLifecycleStillWorks() {
        MemoryPolicyPackStore store = new MemoryPolicyPackStore();
        PolicyPackService service = new PolicyPackService(store, CLOCK);
        PackRef target = createPack(service, "target", 1);
        PackRef other = createPack(service, "other", 1);
        PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
        PolicyScope otherScope = new PolicyScope.Project(ProjectSpecificationId.generate());
        PolicyConfiguration.Activation replacement = activation(scope, target);
        int auditBefore = store.listAudit(target.packId()).size();

        assertThrows(IllegalArgumentException.class, () -> store.compareAndSetActivation(
                scope, target.packId(), 0L, replacement,
                audit(PolicyConfiguration.AuditAction.UPDATE, target.packId(),
                        Optional.of(target.versionId()), Optional.empty(), Optional.of(scope), "wrong action")));
        assertThrows(IllegalArgumentException.class, () -> store.compareAndSetActivation(
                scope, target.packId(), 0L, replacement,
                audit(PolicyConfiguration.AuditAction.ACTIVATE, other.packId(),
                        Optional.of(target.versionId()), Optional.empty(), Optional.of(scope), "wrong pack")));
        assertThrows(IllegalArgumentException.class, () -> store.compareAndSetActivation(
                scope, target.packId(), 0L, replacement,
                audit(PolicyConfiguration.AuditAction.ACTIVATE, target.packId(),
                        Optional.of(other.versionId()), Optional.empty(), Optional.of(scope), "wrong version")));
        assertThrows(IllegalArgumentException.class, () -> store.compareAndSetActivation(
                scope, target.packId(), 0L, replacement,
                audit(PolicyConfiguration.AuditAction.ACTIVATE, target.packId(),
                        Optional.of(target.versionId()), Optional.of(target.ruleIds().getFirst()), Optional.of(scope), "wrong rule")));
        assertThrows(IllegalArgumentException.class, () -> store.compareAndSetActivation(
                scope, target.packId(), 0L, replacement,
                audit(PolicyConfiguration.AuditAction.ACTIVATE, target.packId(),
                        Optional.of(target.versionId()), Optional.empty(), Optional.of(otherScope), "wrong scope")));

        assertFalse(store.findActivation(scope, target.packId()).isPresent());
        assertEquals(auditBefore, store.listAudit(target.packId()).size());

        var updated = service.update(target.packId(), 1L, "target-v2", List.of(rule("updated")), ACTOR, "update");
        var updatedVersion = service.versions(updated.id()).getLast();
        service.activate(scope, updated.id(), updatedVersion.versionId(), 0L, ACTOR, "activate");
        PolicyIds.RuleId updatedRule = updatedVersion.rules().getFirst().id();
        service.putOverride(scope, updated.id(), updatedRule,
                PolicyConfiguration.OverrideMode.FORCE_WARN, 0L, ACTOR, "override");
        service.putOverride(scope, updated.id(), updatedRule,
                PolicyConfiguration.OverrideMode.FORCE_BLOCK, 1L, ACTOR, "override update");
        service.removeOverride(scope, updated.id(), updatedRule, 2L, ACTOR, "remove override");
        service.deactivate(scope, updated.id(), 1L, ACTOR, "deactivate");

        assertFalse(store.findActivation(scope, updated.id()).isPresent());
        assertFalse(store.findOverride(scope, updated.id(), updatedRule).isPresent());
        assertEquals(7, store.listAudit(updated.id()).size());
    }

    @Test
    void activationAndOverrideBudgetsAreEnforcedInsideTheStore() {
        MemoryPolicyPackStore store = new MemoryPolicyPackStore();
        PolicyPackService service = new PolicyPackService(store, CLOCK);
        PolicyScope activationScope = new PolicyScope.Project(ProjectSpecificationId.generate());
        List<PackRef> activationPacks = new ArrayList<>();
        for (int index = 0; index <= PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE; index++) {
            activationPacks.add(createPack(service, "activation-" + index, 1));
        }
        for (int index = 0; index < PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE; index++) {
            activateDirect(store, activationScope, activationPacks.get(index), "activation " + index);
        }
        PackRef overflowActivation = activationPacks.getLast();
        assertThrows(IllegalArgumentException.class,
                () -> activateDirect(store, activationScope, overflowActivation, "activation overflow"));
        assertEquals(PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE, store.listActivations(activationScope).size());

        PolicyScope overrideScope = new PolicyScope.Project(ProjectSpecificationId.generate());
        PackRef first = createPack(service, "override-a", PolicyBudgets.MAX_RULES_PER_PACK);
        PackRef second = createPack(service, "override-b", PolicyBudgets.MAX_RULES_PER_PACK);
        PackRef overflow = createPack(service, "override-overflow", 1);
        activateDirect(store, overrideScope, first, "activate first override pack");
        activateDirect(store, overrideScope, second, "activate second override pack");
        activateDirect(store, overrideScope, overflow, "activate overflow override pack");

        List<OverrideRef> overrides = new ArrayList<>();
        addOverrides(overrides, first);
        addOverrides(overrides, second);
        addOverrides(overrides, overflow);
        for (int index = 0; index < PolicyBudgets.MAX_OVERRIDES_PER_SCOPE; index++) {
            putOverrideDirect(store, overrideScope, overrides.get(index), "override " + index);
        }
        OverrideRef overflowOverride = overrides.getLast();
        assertThrows(IllegalArgumentException.class,
                () -> putOverrideDirect(store, overrideScope, overflowOverride, "override overflow"));
        assertEquals(PolicyBudgets.MAX_OVERRIDES_PER_SCOPE, store.listOverrides(overrideScope).size());
    }

    private PackRef createPack(PolicyPackService service, String name, int ruleCount) {
        List<PolicyRule> rules = new ArrayList<>();
        for (int index = 0; index < ruleCount; index++) {
            rules.add(rule(name + "-" + index));
        }
        var definition = service.create(name, rules, ACTOR, "create " + name);
        var version = service.versions(definition.id()).getFirst();
        return new PackRef(definition.id(), version.versionId(), version.rules().stream().map(PolicyRule::id).toList());
    }

    private PolicyRule rule(String description) {
        return new PolicyRule(
                PolicyIds.RuleId.generate(),
                description,
                PolicyRule.Kind.LIFECYCLE_GUARD,
                PolicyRule.Severity.WARNING,
                new PolicyRule.LifecycleGuard(
                        ChangeId.generate(),
                        ChangeLifecycleState.PROPOSED,
                        ChangeLifecycleState.SPECIFIED));
    }

    private PolicyConfiguration.Activation activation(PolicyScope scope, PackRef pack) {
        return new PolicyConfiguration.Activation(
                scope, pack.packId(), pack.versionId(), 1L, ACTOR, CLOCK.instant());
    }

    private void activateDirect(MemoryPolicyPackStore store, PolicyScope scope, PackRef pack, String reason) {
        store.compareAndSetActivation(
                scope,
                pack.packId(),
                0L,
                activation(scope, pack),
                audit(PolicyConfiguration.AuditAction.ACTIVATE, pack.packId(),
                        Optional.of(pack.versionId()), Optional.empty(), Optional.of(scope), reason));
    }

    private void addOverrides(List<OverrideRef> values, PackRef pack) {
        for (PolicyIds.RuleId ruleId : pack.ruleIds()) {
            values.add(new OverrideRef(pack.packId(), pack.versionId(), ruleId));
        }
    }

    private void putOverrideDirect(MemoryPolicyPackStore store, PolicyScope scope, OverrideRef ref, String reason) {
        PolicyConfiguration.Override replacement = new PolicyConfiguration.Override(
                scope,
                ref.packId(),
                ref.ruleId(),
                PolicyConfiguration.OverrideMode.FORCE_WARN,
                reason,
                ACTOR,
                1L,
                CLOCK.instant());
        store.compareAndSetOverride(
                scope,
                ref.packId(),
                ref.ruleId(),
                0L,
                replacement,
                audit(PolicyConfiguration.AuditAction.PUT_OVERRIDE, ref.packId(),
                        Optional.of(ref.versionId()), Optional.of(ref.ruleId()), Optional.of(scope), reason));
    }

    private PolicyConfiguration.AuditRecord audit(
            PolicyConfiguration.AuditAction action,
            PolicyIds.PackId packId,
            Optional<PolicyIds.VersionId> versionId,
            Optional<PolicyIds.RuleId> ruleId,
            Optional<PolicyScope> scope,
            String reason) {
        return new PolicyConfiguration.AuditRecord(
                DomainIdentity.generate(), action, packId, versionId, ruleId, scope, ACTOR, reason, CLOCK.instant());
    }

    private record PackRef(
            PolicyIds.PackId packId,
            PolicyIds.VersionId versionId,
            List<PolicyIds.RuleId> ruleIds) {
    }

    private record OverrideRef(
            PolicyIds.PackId packId,
            PolicyIds.VersionId versionId,
            PolicyIds.RuleId ruleId) {
    }
}
