package com.morpheus.architecture.m25;

import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPack;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyPersistenceParityTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-29T11:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqliteExposeSameVersionActivationOverrideAndAuditSemantics() {
        Snapshot memory = exercise(new MemoryPolicyPackStore());
        try (SqlitePolicyPackStore sqlite = new SqlitePolicyPackStore(tempDir.resolve("parity.db"))) {
            Snapshot persistent = exercise(sqlite);
            assertEquals(memory, persistent);
        }
    }

    @Test
    void sqliteV015SurvivesCloseAndReopen() {
        Path database = tempDir.resolve("reopen.db");
        PolicyIds.PackId packId;
        PolicyIds.VersionId activeVersion;
        PolicyIds.RuleId ruleId;
        PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());

        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(database)) {
            PolicyPackService service = new PolicyPackService(store, CLOCK);
            var definition = service.create("Governance", List.of(rule()), "alice", "create");
            service.versions(definition.id()).getFirst();
            var updated = service.update(definition.id(), 1, "Governance 2", List.of(rule()), "alice", "update");
            var v2 = service.versions(updated.id()).getLast();
            service.activate(scope, definition.id(), v2.versionId(), 0, "alice", "activate");
            ruleId = v2.rules().getFirst().id();
            service.putOverride(scope, definition.id(), ruleId,
                    PolicyConfiguration.OverrideMode.FORCE_WARN, 0, "alice", "temporary waiver");
            packId = definition.id();
            activeVersion = v2.versionId();
        }

        try (SqlitePolicyPackStore reopened = new SqlitePolicyPackStore(database)) {
            PolicyPackService service = new PolicyPackService(reopened, CLOCK);
            assertEquals(2, service.get(packId).revision());
            assertEquals(2, service.versions(packId).size());
            assertEquals(activeVersion, service.activation(scope, packId).orElseThrow().versionId());
            assertEquals(ruleId, service.overrides(scope).getFirst().ruleId());
            assertEquals(4, service.audit(packId).size());
        }
    }

    private Snapshot exercise(PolicyPackStore store) {
        PolicyPackService service = new PolicyPackService(store, CLOCK);
        PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
        var definition = service.create("Governance", List.of(rule()), "alice", "create");
        var first = service.versions(definition.id()).getFirst();
        var secondDefinition = service.update(definition.id(), 1, "Governance v2", List.of(rule()), "alice", "update");
        var second = service.versions(definition.id()).getLast();
        service.activate(scope, definition.id(), second.versionId(), 0, "alice", "activate");
        service.putOverride(scope, definition.id(), second.rules().getFirst().id(),
                PolicyConfiguration.OverrideMode.FORCE_WARN, 0, "alice", "waiver");

        assertEquals(1, first.versionNumber());
        assertEquals(2, second.versionNumber());
        assertTrue(!first.versionId().equals(second.versionId()));
        return new Snapshot(
                secondDefinition.revision(),
                secondDefinition.latestVersionNumber(),
                service.versions(definition.id()).size(),
                service.activations(scope).size(),
                service.overrides(scope).size(),
                service.audit(definition.id()).size(),
                service.activation(scope, definition.id()).orElseThrow().revision(),
                service.overrides(scope).getFirst().revision());
    }

    @Test
    void removingAMissingActivationIsRefusedAsNotFoundByBothStores() {
        assertBothRefuse("missing-activation", new Refusal("EntityNotFoundException", "policy activation does not exist: %s"),
                (store, pack) -> store.removeActivation(pack.scope(), pack.packId(), 1L,
                        audit(PolicyConfiguration.AuditAction.DEACTIVATE, pack.packId(), Optional.of(pack.versionId()),
                                Optional.empty(), Optional.of(pack.scope()))));
    }

    @Test
    void removingAMissingOverrideIsRefusedAsNotFoundByBothStores() {
        assertBothRefuse("missing-override", new Refusal("EntityNotFoundException", "policy override does not exist: %r"),
                (store, pack) -> store.removeOverride(pack.scope(), pack.packId(), pack.ruleId(), 1L,
                        audit(PolicyConfiguration.AuditAction.REMOVE_OVERRIDE, pack.packId(), Optional.empty(),
                                Optional.of(pack.ruleId()), Optional.of(pack.scope()))));
    }

    /** The caller's audit is checked before the stored state, so a missing row cannot hide a malformed audit. */
    @Test
    void aMismatchedAuditIsRefusedBeforeTheMissingRowByBothStores() {
        assertBothRefuse("mismatched-audit", new Refusal("IllegalArgumentException", "policy audit target mismatch for DEACTIVATE"),
                (store, pack) -> store.removeActivation(pack.scope(), pack.packId(), 1L,
                        audit(PolicyConfiguration.AuditAction.DEACTIVATE, pack.packId(), Optional.of(pack.versionId()),
                                Optional.empty(), Optional.of(new PolicyScope.Project(ProjectSpecificationId.generate())))));
    }

    @Test
    void anUpdateThatSkipsARevisionIsRefusedBeforeAnythingIsWrittenByBothStores() {
        assertBothRefuse("skipped-revision",
                new Refusal("IllegalArgumentException", "policy update must advance revision and version by exactly one"),
                (store, pack) -> {
                    PolicyIds.VersionId next = PolicyIds.VersionId.generate();
                    store.compareAndSetDefinition(pack.packId(), 1L,
                            new PolicyPack.Definition(pack.packId(), "Governance", 3L, 2L, CLOCK.instant(), CLOCK.instant()),
                            new PolicyPack.Version(pack.packId(), next, 2L, "Governance", List.of(rule()), CLOCK.instant()),
                            audit(PolicyConfiguration.AuditAction.UPDATE, pack.packId(), Optional.of(next),
                                    Optional.empty(), Optional.empty()));
                });
    }

    @Test
    void aStaleRevisionIsAConflictInBothStores() {
        assertBothRefuse("stale-revision",
                new Refusal("PolicyConflictException", "stale policy activation revision: expected 2 but current is 1"),
                (store, pack) -> new PolicyPackService(store, CLOCK).activate(
                        pack.scope(), pack.packId(), pack.versionId(), 0, "alice", "activate"),
                (store, pack) -> store.removeActivation(pack.scope(), pack.packId(), 2L,
                        audit(PolicyConfiguration.AuditAction.DEACTIVATE, pack.packId(), Optional.of(pack.versionId()),
                                Optional.empty(), Optional.of(pack.scope()))));
    }

    /**
     * Runs one invalid write against a fresh pack in each store, and requires both to refuse it with the expected type
     * and message, to leave the pack's state as it was, and to agree with each other.
     */
    private void assertBothRefuse(String name, Refusal expected, StoreWrite write) {
        assertBothRefuse(name, expected, (store, pack) -> { }, write);
    }

    private void assertBothRefuse(String name, Refusal expected, StoreWrite prepare, StoreWrite write) {
        Refusal memory = refusalOf(new MemoryPolicyPackStore(), prepare, write, expected);
        Refusal sqlite;
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(tempDir.resolve(name + ".db"))) {
            sqlite = refusalOf(store, prepare, write, expected);
        }
        assertEquals(memory, sqlite, "both stores must refuse the same write the same way");
    }

    private Refusal refusalOf(PolicyPackStore store, StoreWrite prepare, StoreWrite write, Refusal expected) {
        PackFixture pack = pack(store);
        prepare.apply(store, pack);
        int audits = store.listAudit(pack.packId()).size();
        RuntimeException failure = assertThrows(RuntimeException.class, () -> write.apply(store, pack),
                () -> store.getClass().getSimpleName() + " accepted the write");
        Refusal actual = new Refusal(failure.getClass().getSimpleName(), failure.getMessage());
        assertEquals(expected.resolve(pack), actual, store.getClass().getSimpleName());
        assertEquals(1L, store.findDefinition(pack.packId()).orElseThrow().revision());
        assertEquals(1, store.listVersions(pack.packId()).size());
        assertEquals(audits, store.listAudit(pack.packId()).size(), "a refused write records no audit");
        return actual.withPlaceholders(pack);
    }

    private PackFixture pack(PolicyPackStore store) {
        PolicyPackService service = new PolicyPackService(store, CLOCK);
        var definition = service.create("Governance", List.of(rule()), "alice", "create");
        var version = service.versions(definition.id()).getFirst();
        return new PackFixture(definition.id(), version.versionId(), version.rules().getFirst().id(),
                new PolicyScope.Project(ProjectSpecificationId.generate()));
    }

    private PolicyConfiguration.AuditRecord audit(
            PolicyConfiguration.AuditAction action,
            PolicyIds.PackId packId,
            Optional<PolicyIds.VersionId> versionId,
            Optional<PolicyIds.RuleId> ruleId,
            Optional<PolicyScope> scope) {
        return new PolicyConfiguration.AuditRecord(
                DomainIdentity.generate(), action, packId, versionId, ruleId, scope, "alice", "parity", CLOCK.instant());
    }

    @FunctionalInterface
    private interface StoreWrite {
        void apply(PolicyPackStore store, PackFixture pack);
    }

    private record PackFixture(
            PolicyIds.PackId packId, PolicyIds.VersionId versionId, PolicyIds.RuleId ruleId, PolicyScope scope) {
    }

    /** {@code %s} in the message stands for the pack identifier, {@code %r} for the rule identifier. */
    private record Refusal(String type, String message) {
        Refusal resolve(PackFixture pack) {
            return new Refusal(type, message.replace("%s", pack.packId().toString()).replace("%r", pack.ruleId().toString()));
        }

        /** Each store generates its own pack, so two refusals are compared with their identifiers abstracted. */
        Refusal withPlaceholders(PackFixture pack) {
            return new Refusal(type, message.replace(pack.packId().toString(), "%s").replace(pack.ruleId().toString(), "%r"));
        }
    }

    private PolicyRule rule() {
        return new PolicyRule(
                PolicyIds.RuleId.generate(),
                "Lifecycle guard",
                PolicyRule.Kind.LIFECYCLE_GUARD,
                PolicyRule.Severity.WARNING,
                new PolicyRule.LifecycleGuard(
                        ChangeId.generate(), ChangeLifecycleState.PROPOSED, ChangeLifecycleState.SPECIFIED));
    }

    private record Snapshot(
            long definitionRevision,
            long latestVersion,
            int versionCount,
            int activationCount,
            int overrideCount,
            int auditCount,
            long activationRevision,
            long overrideRevision) {
    }
}