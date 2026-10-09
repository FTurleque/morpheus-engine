package com.morpheus.architecture.m25;

import com.morpheus.application.policy.PolicyBudgets;
import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.application.policy.PolicyScope;
import com.morpheus.application.store.PolicyPackStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.memory.MemoryPolicyPackStore;
import com.morpheus.store.sqlite.SqlitePolicyPackStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyStoreAtomicityContractTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC);
    private static final String ACTOR = "atomicity-test";

    @TempDir
    Path tempDir;

    @Test
    void memoryRejectsMismatchedAuditBeforeMutation() {
        assertAuditMismatchRejected(new MemoryPolicyPackStore());
    }

    @Test
    void sqliteRejectsMismatchedAuditBeforeMutation() {
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(tempDir.resolve("audit-target.db"))) {
            assertAuditMismatchRejected(store);
        }
    }

    @Test
    void memoryActivationBudgetIsAtomic() throws Exception {
        MemoryPolicyPackStore store = new MemoryPolicyPackStore();
        assertActivationBudgetAtomic(store, store);
    }

    @Test
    void sqliteActivationBudgetIsAtomicAcrossIndependentStores() throws Exception {
        Path database = tempDir.resolve("activation-budget.db");
        try (SqlitePolicyPackStore first = new SqlitePolicyPackStore(database);
             SqlitePolicyPackStore second = new SqlitePolicyPackStore(database)) {
            assertActivationBudgetAtomic(first, second);
        }
    }

    @Test
    void memoryOverrideBudgetIsAtomic() throws Exception {
        MemoryPolicyPackStore store = new MemoryPolicyPackStore();
        assertOverrideBudgetAtomic(store, store);
    }

    @Test
    void sqliteOverrideBudgetIsAtomicAcrossIndependentStores() throws Exception {
        Path database = tempDir.resolve("override-budget.db");
        try (SqlitePolicyPackStore first = new SqlitePolicyPackStore(database);
             SqlitePolicyPackStore second = new SqlitePolicyPackStore(database)) {
            assertOverrideBudgetAtomic(first, second);
        }
    }

    private void assertAuditMismatchRejected(PolicyPackStore store) {
        PolicyPackService service = new PolicyPackService(store, CLOCK);
        PackRef target = createPack(service, "target", 1);
        PackRef other = createPack(service, "other", 1);
        PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
        PolicyScope otherScope = new PolicyScope.Project(ProjectSpecificationId.generate());
        PolicyConfiguration.Activation replacement = activation(scope, target);
        int targetAuditCount = store.listAudit(target.packId()).size();
        int otherAuditCount = store.listAudit(other.packId()).size();

        assertThrows(IllegalArgumentException.class, () -> store.compareAndSetActivation(
                scope,
                target.packId(),
                0,
                replacement,
                audit(
                        PolicyConfiguration.AuditAction.UPDATE,
                        target.packId(),
                        Optional.of(target.versionId()),
                        Optional.empty(),
                        Optional.of(scope),
                        "wrong action")));

        assertThrows(IllegalArgumentException.class, () -> store.compareAndSetActivation(
                scope,
                target.packId(),
                0,
                replacement,
                audit(
                        PolicyConfiguration.AuditAction.ACTIVATE,
                        other.packId(),
                        Optional.of(other.versionId()),
                        Optional.empty(),
                        Optional.of(scope),
                        "wrong pack")));

        assertThrows(IllegalArgumentException.class, () -> store.compareAndSetActivation(
                scope,
                target.packId(),
                0,
                replacement,
                audit(
                        PolicyConfiguration.AuditAction.ACTIVATE,
                        target.packId(),
                        Optional.of(target.versionId()),
                        Optional.empty(),
                        Optional.of(otherScope),
                        "wrong scope")));

        assertThrows(IllegalArgumentException.class, () -> store.compareAndSetActivation(
                scope,
                target.packId(),
                0,
                replacement,
                audit(
                        PolicyConfiguration.AuditAction.ACTIVATE,
                        target.packId(),
                        Optional.of(other.versionId()),
                        Optional.empty(),
                        Optional.of(scope),
                        "wrong version")));

        assertFalse(store.findActivation(scope, target.packId()).isPresent());
        assertEquals(targetAuditCount, store.listAudit(target.packId()).size());
        assertEquals(otherAuditCount, store.listAudit(other.packId()).size());
    }

    private void assertActivationBudgetAtomic(PolicyPackStore first, PolicyPackStore second) throws Exception {
        PolicyPackService service = new PolicyPackService(first, CLOCK);
        List<PackRef> packs = new ArrayList<>();
        for (int index = 0; index < PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE + 1; index++) {
            packs.add(createPack(service, "activation-" + index, 1));
        }
        PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
        for (int index = 0; index < PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE - 1; index++) {
            activate(first, scope, packs.get(index), "prefill activation " + index);
        }

        PackRef left = packs.get(PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE - 1);
        PackRef right = packs.get(PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE);
        int successes = race(
                () -> activate(first, scope, left, "left activation race"),
                () -> activate(second, scope, right, "right activation race"));

        assertEquals(1, successes);
        assertEquals(PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE, first.listActivations(scope).size());
    }

    private void assertOverrideBudgetAtomic(PolicyPackStore first, PolicyPackStore second) throws Exception {
        PolicyPackService service = new PolicyPackService(first, CLOCK);
        List<PackRef> packs = List.of(
                createPack(service, "override-a", PolicyBudgets.MAX_RULES_PER_PACK),
                createPack(service, "override-b", PolicyBudgets.MAX_RULES_PER_PACK),
                createPack(service, "override-c", 1));
        PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
        for (PackRef pack : packs) {
            activate(first, scope, pack, "activate override pack");
        }

        List<OverrideRef> overrideRefs = new ArrayList<>();
        for (PackRef pack : packs) {
            for (PolicyIds.RuleId ruleId : pack.ruleIds()) {
                overrideRefs.add(new OverrideRef(pack.packId(), pack.versionId(), ruleId));
            }
        }
        assertEquals(PolicyBudgets.MAX_OVERRIDES_PER_SCOPE + 1, overrideRefs.size());

        for (int index = 0; index < PolicyBudgets.MAX_OVERRIDES_PER_SCOPE - 1; index++) {
            putOverride(first, scope, overrideRefs.get(index), "prefill override " + index);
        }

        OverrideRef left = overrideRefs.get(PolicyBudgets.MAX_OVERRIDES_PER_SCOPE - 1);
        OverrideRef right = overrideRefs.get(PolicyBudgets.MAX_OVERRIDES_PER_SCOPE);
        int successes = race(
                () -> putOverride(first, scope, left, "left override race"),
                () -> putOverride(second, scope, right, "right override race"));

        assertEquals(1, successes);
        assertEquals(PolicyBudgets.MAX_OVERRIDES_PER_SCOPE, first.listOverrides(scope).size());
    }

    private PackRef createPack(PolicyPackService service, String name, int ruleCount) {
        List<PolicyRule> rules = new ArrayList<>();
        for (int index = 0; index < ruleCount; index++) {
            rules.add(rule(name + "-" + index));
        }
        var definition = service.create(name, rules, ACTOR, "create " + name);
        var version = service.versions(definition.id()).getFirst();
        return new PackRef(
                definition.id(),
                version.versionId(),
                version.rules().stream().map(PolicyRule::id).toList());
    }

    private PolicyConfiguration.Activation activation(PolicyScope scope, PackRef pack) {
        return new PolicyConfiguration.Activation(
                scope,
                pack.packId(),
                pack.versionId(),
                1L,
                ACTOR,
                CLOCK.instant());
    }

    private void activate(PolicyPackStore store, PolicyScope scope, PackRef pack, String reason) {
        store.compareAndSetActivation(
                scope,
                pack.packId(),
                0L,
                activation(scope, pack),
                audit(
                        PolicyConfiguration.AuditAction.ACTIVATE,
                        pack.packId(),
                        Optional.of(pack.versionId()),
                        Optional.empty(),
                        Optional.of(scope),
                        reason));
    }

    private void putOverride(PolicyPackStore store, PolicyScope scope, OverrideRef ref, String reason) {
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
                audit(
                        PolicyConfiguration.AuditAction.PUT_OVERRIDE,
                        ref.packId(),
                        Optional.of(ref.versionId()),
                        Optional.of(ref.ruleId()),
                        Optional.of(scope),
                        reason));
    }

    private PolicyConfiguration.AuditRecord audit(
            PolicyConfiguration.AuditAction action,
            PolicyIds.PackId packId,
            Optional<PolicyIds.VersionId> versionId,
            Optional<PolicyIds.RuleId> ruleId,
            Optional<PolicyScope> scope,
            String reason) {
        return new PolicyConfiguration.AuditRecord(
                DomainIdentity.generate(),
                action,
                packId,
                versionId,
                ruleId,
                scope,
                ACTOR,
                reason,
                CLOCK.instant());
    }

    private int race(Runnable left, Runnable right) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> leftResult = executor.submit(() -> participate(ready, start, left));
            Future<Boolean> rightResult = executor.submit(() -> participate(ready, start, right));
            ready.await();
            start.countDown();
            return (leftResult.get() ? 1 : 0) + (rightResult.get() ? 1 : 0);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean participate(CountDownLatch ready, CountDownLatch start, Runnable action) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            action.run();
            return true;
        } catch (IllegalArgumentException budgetFailure) {
            assertTrue(budgetFailure.getMessage().contains("budget"), budgetFailure::getMessage);
            return false;
        }
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
