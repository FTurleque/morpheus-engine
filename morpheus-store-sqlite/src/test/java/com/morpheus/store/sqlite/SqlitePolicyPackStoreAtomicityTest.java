package com.morpheus.store.sqlite;

import com.morpheus.application.policy.PolicyBudgets;
import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyConflictException;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPack;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.application.policy.PolicyScope;
import com.morpheus.application.store.EntityNotFoundException;
import com.morpheus.application.store.EntityStateException;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlitePolicyPackStoreAtomicityTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-06T14:30:00Z"), ZoneOffset.UTC);
    private static final String ACTOR = "sqlite-atomicity-test";

    @TempDir
    Path tempDir;

    @Test
    void auditIdentityIsValidatedBeforeMutationAndValidLifecycleStillWorks() {
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(tempDir.resolve("audit.db"))) {
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
            PolicyIds.RuleId ruleId = updatedVersion.rules().getFirst().id();
            service.putOverride(scope, updated.id(), ruleId,
                    PolicyConfiguration.OverrideMode.FORCE_WARN, 0L, ACTOR, "override");
            service.putOverride(scope, updated.id(), ruleId,
                    PolicyConfiguration.OverrideMode.FORCE_BLOCK, 1L, ACTOR, "override update");
            service.removeOverride(scope, updated.id(), ruleId, 2L, ACTOR, "remove override");
            service.deactivate(scope, updated.id(), 1L, ACTOR, "deactivate");

            assertFalse(store.findActivation(scope, updated.id()).isPresent());
            assertFalse(store.findOverride(scope, updated.id(), ruleId).isPresent());
            assertEquals(7, store.listAudit(updated.id()).size());

            assertThrows(IllegalArgumentException.class, () -> store.removeActivation(
                    scope,
                    updated.id(),
                    1L,
                    audit(PolicyConfiguration.AuditAction.DEACTIVATE, updated.id(),
                            Optional.empty(), Optional.empty(), Optional.of(scope), "missing version")));
            assertThrows(PolicyConflictException.class, () -> store.removeActivation(
                    scope,
                    updated.id(),
                    1L,
                    audit(PolicyConfiguration.AuditAction.DEACTIVATE, updated.id(),
                            Optional.of(updatedVersion.versionId()), Optional.empty(), Optional.of(scope), "stale deactivate")));
        }
    }

    @Test
    void activationAndOverrideBudgetsAreEnforcedInsideSqliteWrites() {
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(tempDir.resolve("budgets.db"))) {
            PolicyPackService service = new PolicyPackService(store, CLOCK);
            PolicyScope activationScope = new PolicyScope.Project(ProjectSpecificationId.generate());
            List<PackRef> activationPacks = new ArrayList<>();
            for (int index = 0; index <= PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE; index++) {
                activationPacks.add(createPack(service, "activation-" + index, 1));
            }
            for (int index = 0; index < PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE; index++) {
                activateDirect(store, activationScope, activationPacks.get(index), "activation " + index);
            }
            assertThrows(IllegalArgumentException.class,
                    () -> activateDirect(store, activationScope, activationPacks.getLast(), "activation overflow"));
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
            assertThrows(IllegalArgumentException.class,
                    () -> putOverrideDirect(store, overrideScope, overrides.getLast(), "override overflow"));
            assertEquals(PolicyBudgets.MAX_OVERRIDES_PER_SCOPE, store.listOverrides(overrideScope).size());
        }
    }

    @Test
    void aClosedStoreRefusesEveryOperationByName() {
        SqlitePolicyPackStore store = new SqlitePolicyPackStore(tempDir.resolve("closed.db"));
        PackRef pack = createPack(new PolicyPackService(store, CLOCK), "closed", 1);
        PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
        PolicyIds.RuleId rule = pack.ruleIds().getFirst();
        PolicyPack.Definition definition = store.findDefinition(pack.packId()).orElseThrow();
        PolicyPack.Version version = store.findVersion(pack.packId(), pack.versionId()).orElseThrow();
        store.close();

        List<Executable> operations = List.of(
                () -> store.create(definition, version, audit(PolicyConfiguration.AuditAction.CREATE, pack.packId(),
                        Optional.of(pack.versionId()), Optional.empty(), Optional.empty(), "create")),
                () -> store.findDefinition(pack.packId()),
                store::listDefinitions,
                () -> store.findVersion(pack.packId(), pack.versionId()),
                () -> store.listVersions(pack.packId()),
                () -> store.compareAndSetDefinition(pack.packId(), 1L, definition, version,
                        audit(PolicyConfiguration.AuditAction.UPDATE, pack.packId(), Optional.of(pack.versionId()),
                                Optional.empty(), Optional.empty(), "update")),
                () -> store.findActivation(scope, pack.packId()),
                () -> store.listActivations(scope),
                () -> activateDirect(store, scope, pack, "activate"),
                () -> store.removeActivation(scope, pack.packId(), 1L,
                        audit(PolicyConfiguration.AuditAction.DEACTIVATE, pack.packId(), Optional.of(pack.versionId()),
                                Optional.empty(), Optional.of(scope), "deactivate")),
                () -> store.findOverride(scope, pack.packId(), rule),
                () -> store.listOverrides(scope),
                () -> putOverrideDirect(store, scope, new OverrideRef(pack.packId(), pack.versionId(), rule), "put"),
                () -> store.removeOverride(scope, pack.packId(), rule, 1L,
                        audit(PolicyConfiguration.AuditAction.REMOVE_OVERRIDE, pack.packId(), Optional.empty(),
                                Optional.of(rule), Optional.of(scope), "remove")),
                () -> store.listAudit(pack.packId()));
        for (Executable operation : operations) {
            IllegalStateException refusal = assertThrows(IllegalStateException.class, operation);
            assertEquals("SQLite policy-pack store is closed", refusal.getMessage());
        }
    }

    /** {@code PolicyPackService.activate} with a revision of 1 or more reaches this branch; nothing else ran it. */
    @Test
    void reactivatingAPackOnANewVersionAdvancesTheRevisionAndRecordsItsAudit() {
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(tempDir.resolve("reactivate.db"))) {
            PolicyPackService service = new PolicyPackService(store, CLOCK);
            PackRef pack = createPack(service, "reactivate", 1);
            PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
            service.activate(scope, pack.packId(), pack.versionId(), 0L, ACTOR, "activate v1");
            service.update(pack.packId(), 1L, "reactivate-v2", List.of(rule("v2")), ACTOR, "update");
            List<PolicyPack.Version> versions = store.listVersions(pack.packId());
            assertEquals(List.of(1L, 2L), versions.stream().map(PolicyPack.Version::versionNumber).toList());
            PolicyIds.VersionId v2 = versions.getLast().versionId();
            PolicyConfiguration.Activation replacement =
                    new PolicyConfiguration.Activation(scope, pack.packId(), v2, 2L, ACTOR, CLOCK.instant());
            PolicyConfiguration.AuditRecord audit = audit(PolicyConfiguration.AuditAction.ACTIVATE, pack.packId(),
                    Optional.of(v2), Optional.empty(), Optional.of(scope), "activate v2");

            assertEquals(replacement, store.compareAndSetActivation(scope, pack.packId(), 1L, replacement, audit));
            assertEquals(Optional.of(replacement), store.findActivation(scope, pack.packId()));
            assertEquals(audit, store.listAudit(pack.packId()).getLast());

            PolicyConflictException stale = assertThrows(PolicyConflictException.class,
                    () -> store.compareAndSetActivation(scope, pack.packId(), 1L, replacement,
                            audit(PolicyConfiguration.AuditAction.ACTIVATE, pack.packId(), Optional.of(v2),
                                    Optional.empty(), Optional.of(scope), "stale")));
            assertEquals("stale policy activation revision: expected 1 but current is 2", stale.getMessage());
        }
    }

    @Test
    void aMismatchedAuditOnADefinitionWriteIsRefusedByNameAndChangesNothing() {
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(tempDir.resolve("mismatch-definition.db"))) {
            PackRef pack = createPack(new PolicyPackService(store, CLOCK), "mismatch", 1);
            PolicyIds.PackId fresh = PolicyIds.PackId.generate();
            PolicyIds.VersionId freshVersion = PolicyIds.VersionId.generate();

            assertRefused("policy audit target mismatch for CREATE", () -> store.create(
                    definition(fresh, 1L), version(fresh, freshVersion, 1L),
                    audit(PolicyConfiguration.AuditAction.UPDATE, fresh, Optional.of(freshVersion), Optional.empty(),
                            Optional.empty(), "wrong action")));
            assertRefused("policy audit version mismatch for CREATE", () -> store.create(
                    definition(fresh, 1L), version(fresh, freshVersion, 1L),
                    audit(PolicyConfiguration.AuditAction.CREATE, fresh, Optional.of(PolicyIds.VersionId.generate()),
                            Optional.empty(), Optional.empty(), "wrong version")));
            assertEquals(Optional.empty(), store.findDefinition(fresh));
            assertEquals(List.of(), store.listAudit(fresh));

            int audits = store.listAudit(pack.packId()).size();
            PolicyIds.VersionId v2 = PolicyIds.VersionId.generate();
            assertRefused("policy audit target mismatch for UPDATE", () -> store.compareAndSetDefinition(
                    pack.packId(), 1L, definition(pack.packId(), 2L), version(pack.packId(), v2, 2L),
                    audit(PolicyConfiguration.AuditAction.CREATE, pack.packId(), Optional.of(v2), Optional.empty(),
                            Optional.empty(), "wrong action")));
            assertRefused("policy audit version mismatch for UPDATE", () -> store.compareAndSetDefinition(
                    pack.packId(), 1L, definition(pack.packId(), 2L), version(pack.packId(), v2, 2L),
                    audit(PolicyConfiguration.AuditAction.UPDATE, pack.packId(), Optional.empty(), Optional.empty(),
                            Optional.empty(), "no version")));
            assertEquals(1L, store.findDefinition(pack.packId()).orElseThrow().revision());
            assertEquals(1, store.listVersions(pack.packId()).size());
            assertEquals(audits, store.listAudit(pack.packId()).size());
        }
    }

    @Test
    void aMismatchedAuditOnADeactivationIsRefusedByNameAndChangesNothing() {
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(tempDir.resolve("mismatch-activation.db"))) {
            PackRef pack = createPack(new PolicyPackService(store, CLOCK), "mismatch", 1);
            PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
            PolicyScope otherScope = new PolicyScope.Project(ProjectSpecificationId.generate());
            activateDirect(store, scope, pack, "activate");
            int audits = store.listAudit(pack.packId()).size();

            assertRefused("policy audit target mismatch for DEACTIVATE", () -> store.removeActivation(
                    scope, pack.packId(), 1L, audit(PolicyConfiguration.AuditAction.DEACTIVATE, pack.packId(),
                            Optional.of(pack.versionId()), Optional.empty(), Optional.of(otherScope), "wrong scope")));
            assertRefused("policy audit version mismatch for DEACTIVATE", () -> store.removeActivation(
                    scope, pack.packId(), 1L, audit(PolicyConfiguration.AuditAction.DEACTIVATE, pack.packId(),
                            Optional.of(PolicyIds.VersionId.generate()), Optional.empty(), Optional.of(scope),
                            "wrong version")));
            assertTrue(store.findActivation(scope, pack.packId()).isPresent());
            assertEquals(audits, store.listAudit(pack.packId()).size());
        }
    }

    @Test
    void aMismatchedAuditOnAnOverrideWriteIsRefusedByNameAndChangesNothing() {
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(tempDir.resolve("mismatch-override.db"))) {
            PackRef pack = createPack(new PolicyPackService(store, CLOCK), "mismatch", 2);
            PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
            PolicyIds.RuleId ruleA = pack.ruleIds().getFirst();
            PolicyIds.RuleId ruleB = pack.ruleIds().getLast();
            activateDirect(store, scope, pack, "activate");
            int audits = store.listAudit(pack.packId()).size();

            assertRefused("policy audit target mismatch for PUT_OVERRIDE", () -> store.compareAndSetOverride(
                    scope, pack.packId(), ruleA, 0L, override(scope, pack.packId(), ruleA, 1L),
                    audit(PolicyConfiguration.AuditAction.PUT_OVERRIDE, pack.packId(), Optional.of(pack.versionId()),
                            Optional.of(ruleB), Optional.of(scope), "wrong rule")));
            assertRefused("policy audit version mismatch for PUT_OVERRIDE", () -> store.compareAndSetOverride(
                    scope, pack.packId(), ruleA, 0L, override(scope, pack.packId(), ruleA, 1L),
                    audit(PolicyConfiguration.AuditAction.PUT_OVERRIDE, pack.packId(),
                            Optional.of(PolicyIds.VersionId.generate()), Optional.of(ruleA), Optional.of(scope),
                            "wrong version")));
            assertEquals(Optional.empty(), store.findOverride(scope, pack.packId(), ruleA));
            assertEquals(audits, store.listAudit(pack.packId()).size());

            PolicyConfiguration.AuditRecord put = audit(PolicyConfiguration.AuditAction.PUT_OVERRIDE, pack.packId(),
                    Optional.of(pack.versionId()), Optional.of(ruleA), Optional.of(scope), "put");
            PolicyConfiguration.Override written = override(scope, pack.packId(), ruleA, 1L);
            assertEquals(written, store.compareAndSetOverride(scope, pack.packId(), ruleA, 0L, written, put));
            assertEquals(put, store.listAudit(pack.packId()).getLast());

            assertRefused("policy audit target mismatch for REMOVE_OVERRIDE", () -> store.removeOverride(
                    scope, pack.packId(), ruleA, 1L, audit(PolicyConfiguration.AuditAction.DEACTIVATE, pack.packId(),
                            Optional.empty(), Optional.of(ruleA), Optional.of(scope), "wrong action")));
            assertRefused("policy audit version mismatch for REMOVE_OVERRIDE", () -> store.removeOverride(
                    scope, pack.packId(), ruleA, 1L, audit(PolicyConfiguration.AuditAction.REMOVE_OVERRIDE,
                            pack.packId(), Optional.of(pack.versionId()), Optional.of(ruleA), Optional.of(scope),
                            "version given")));
            assertTrue(store.findOverride(scope, pack.packId(), ruleA).isPresent());
            assertEquals(audits + 1, store.listAudit(pack.packId()).size());
        }
    }

    @Test
    void anUnknownPackAnUnknownVersionAndAnInactivePackAreRefusedByName() {
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(tempDir.resolve("unknown.db"))) {
            PolicyPackService service = new PolicyPackService(store, CLOCK);
            PackRef pack = createPack(service, "inactive", 1);
            PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
            int audits = store.listAudit(pack.packId()).size();

            PolicyIds.PackId unknown = PolicyIds.PackId.generate();
            PolicyIds.VersionId unknownPackVersion = PolicyIds.VersionId.generate();
            EntityNotFoundException unknownPack = assertThrows(EntityNotFoundException.class,
                    () -> store.compareAndSetDefinition(unknown, 1L, definition(unknown, 2L),
                            version(unknown, unknownPackVersion, 2L),
                            audit(PolicyConfiguration.AuditAction.UPDATE, unknown, Optional.of(unknownPackVersion),
                                    Optional.empty(), Optional.empty(), "unknown pack")));
            assertEquals("unknown policy pack: " + unknown, unknownPack.getMessage());

            PolicyIds.VersionId unknownVersion = PolicyIds.VersionId.generate();
            EntityNotFoundException unknownVersionRefusal = assertThrows(EntityNotFoundException.class,
                    () -> store.compareAndSetActivation(scope, pack.packId(), 0L,
                            new PolicyConfiguration.Activation(
                                    scope, pack.packId(), unknownVersion, 1L, ACTOR, CLOCK.instant()),
                            audit(PolicyConfiguration.AuditAction.ACTIVATE, pack.packId(), Optional.of(unknownVersion),
                                    Optional.empty(), Optional.of(scope), "unknown version")));
            assertEquals("unknown policy version: " + unknownVersion, unknownVersionRefusal.getMessage());
            assertEquals(Optional.empty(), store.findActivation(scope, pack.packId()));

            PolicyIds.RuleId rule = pack.ruleIds().getFirst();
            EntityStateException inactive = assertThrows(EntityStateException.class,
                    () -> putOverrideDirect(store, scope, new OverrideRef(pack.packId(), pack.versionId(), rule), "put"));
            assertEquals("policy pack must be active before adding an override: " + pack.packId(), inactive.getMessage());
            assertEquals(Optional.empty(), store.findOverride(scope, pack.packId(), rule));
            assertEquals(audits, store.listAudit(pack.packId()).size());
        }
    }

    /** The encoded payload is the version; its key columns must name the same version, or a read is refused. */
    @Test
    void aVersionWhosePayloadNamesAnotherVersionIsRefusedOnRead() throws SQLException {
        Path database = tempDir.resolve("tampered.db");
        PackRef target;
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(database)) {
            PolicyPackService service = new PolicyPackService(store, CLOCK);
            target = createPack(service, "target", 1);
            createPack(service, "source", 1);
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
                PreparedStatement tamper = connection.prepareStatement("""
                        UPDATE policy_pack_versions
                        SET encoded_version = (SELECT encoded_version FROM policy_pack_versions WHERE pack_id <> ?)
                        WHERE pack_id = ?
                        """)) {
            tamper.setString(1, target.packId().toString());
            tamper.setString(2, target.packId().toString());
            assertEquals(1, tamper.executeUpdate());
        }

        try (SqlitePolicyPackStore reopened = new SqlitePolicyPackStore(database)) {
            for (Executable read : List.<Executable>of(
                    () -> reopened.findVersion(target.packId(), target.versionId()),
                    () -> reopened.listVersions(target.packId()))) {
                KnowledgeStoreException refusal = assertThrows(KnowledgeStoreException.class, read);
                assertEquals("policy version columns disagree with encoded payload", refusal.getMessage());
            }
        }
    }

    @Test
    void definitionsAreListedWithTheirContentByIdentity() {
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(tempDir.resolve("definitions.db"))) {
            PolicyPackService service = new PolicyPackService(store, CLOCK);
            PolicyPack.Definition first = service.create("first", List.of(rule("first")), ACTOR, "create first");
            PolicyPack.Definition second = service.create("second", List.of(rule("second")), ACTOR, "create second");

            assertEquals(Stream.of(first, second).sorted().toList(), store.listDefinitions());
        }
    }

    private static void assertRefused(String message, Executable write) {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, write);
        assertEquals(message, refusal.getMessage());
    }

    private PolicyPack.Definition definition(PolicyIds.PackId packId, long revision) {
        return new PolicyPack.Definition(packId, "pack", revision, revision, CLOCK.instant(), CLOCK.instant());
    }

    private PolicyPack.Version version(PolicyIds.PackId packId, PolicyIds.VersionId versionId, long number) {
        return new PolicyPack.Version(packId, versionId, number, "pack", List.of(rule("version")), CLOCK.instant());
    }

    private PolicyConfiguration.Override override(
            PolicyScope scope, PolicyIds.PackId packId, PolicyIds.RuleId ruleId, long revision) {
        return new PolicyConfiguration.Override(scope, packId, ruleId, PolicyConfiguration.OverrideMode.FORCE_WARN,
                "override", ACTOR, revision, CLOCK.instant());
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

    private void activateDirect(SqlitePolicyPackStore store, PolicyScope scope, PackRef pack, String reason) {
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

    private void putOverrideDirect(SqlitePolicyPackStore store, PolicyScope scope, OverrideRef ref, String reason) {
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
