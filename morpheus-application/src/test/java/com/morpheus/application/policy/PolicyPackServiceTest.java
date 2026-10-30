package com.morpheus.application.policy;

import com.morpheus.application.query.PageRequest;
import com.morpheus.application.store.EntityNotFoundException;
import com.morpheus.application.store.EntityStateException;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The policy registry: every write is compare-and-set, bounded and audited, and every read is ordered. */
class PolicyPackServiceTest {
    private static final Instant AT = Instant.parse("2026-10-30T08:00:00Z");

    private final HandWrittenPolicyPackStore store = new HandWrittenPolicyPackStore();
    private final PolicyPackService service = new PolicyPackService(store, Clock.fixed(AT, ZoneOffset.UTC));
    private final PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
    private final PolicyRule rule = rule("guard");

    @Test
    void aCreatedPackHasItsFirstVersionAndAnAuditedCreation() {
        PolicyPack.Definition created = service.create("  pack  ", List.of(rule), "actor", "reason");

        assertEquals("pack", created.name());
        assertEquals(1, created.revision());
        assertEquals(1, created.latestVersionNumber());
        assertEquals(AT, created.createdAt());
        assertEquals(created, service.get(created.id()));
        List<PolicyPack.Version> versions = service.versions(created.id());
        assertEquals(1, versions.size());
        assertEquals(List.of(rule), versions.getFirst().rules());
        assertEquals(versions.getFirst(), service.version(created.id(), versions.getFirst().versionId()));
        assertEquals(PolicyConfiguration.AuditAction.CREATE, store.audit.getFirst().action());
        assertEquals(Optional.of(versions.getFirst().versionId()), store.audit.getFirst().versionId());
    }

    @Test
    void theListOfPacksIsOrderedByIdentityWhateverTheStoreReturns() {
        PolicyPack.Definition first = service.create("first", List.of(rule), "actor", "reason");
        PolicyPack.Definition second = service.create("second", List.of(rule), "actor", "reason");

        List<PolicyPack.Definition> listed = service.list();

        assertEquals(List.of(first, second).stream().sorted().toList(), listed);
    }

    @Test
    void anUnknownPackOrVersionIsRefusedByName() {
        PolicyIds.PackId unknown = PolicyIds.PackId.generate();
        PolicyIds.VersionId version = PolicyIds.VersionId.generate();

        EntityNotFoundException pack = assertThrows(EntityNotFoundException.class, () -> service.versions(unknown));
        EntityNotFoundException missingVersion = assertThrows(EntityNotFoundException.class,
                () -> service.version(unknown, version));

        assertEquals("unknown policy pack: " + unknown, pack.getMessage());
        assertEquals("unknown policy pack version: " + unknown + "/" + version, missingVersion.getMessage());
    }

    @Test
    void anUpdateAddsAVersionAndKeepsTheCreationInstant() {
        PolicyPack.Definition created = service.create("pack", List.of(rule), "actor", "reason");
        PolicyRule other = rule("other");

        PolicyPack.Definition updated = service.update(created.id(), 1, "renamed", List.of(rule, other), "actor", "why");

        assertEquals("renamed", updated.name());
        assertEquals(2, updated.revision());
        assertEquals(2, updated.latestVersionNumber());
        assertEquals(created.createdAt(), updated.createdAt());
        assertEquals(List.of(1L, 2L), service.versions(created.id()).stream().map(PolicyPack.Version::versionNumber).toList());
        assertEquals(PolicyConfiguration.AuditAction.UPDATE, store.audit.getLast().action());
    }

    @Test
    void aClockThatSteppedBackwardsDoesNotStampARevisionBeforeTheOneItReplaces() {
        Instant later = AT.plusSeconds(3_600);
        PolicyIds.PackId packId = PolicyIds.PackId.generate();
        PolicyIds.VersionId versionId = PolicyIds.VersionId.generate();
        store.create(
                new PolicyPack.Definition(packId, "pack", 1, 1, AT, later),
                new PolicyPack.Version(packId, versionId, 1, "pack", List.of(rule), AT),
                audit(packId));

        PolicyPack.Definition updated = service.update(packId, 1, "pack", List.of(rule), "actor", "reason");

        assertEquals(later, updated.updatedAt());
    }

    @Test
    void activationIsCompareAndSetAndAudited() {
        PolicyPack.Definition created = service.create("pack", List.of(rule), "actor", "reason");
        PolicyIds.VersionId versionId = service.versions(created.id()).getFirst().versionId();

        PolicyConfiguration.Activation activation = service.activate(scope, created.id(), versionId, 0, "actor", "on");

        assertEquals(1, activation.revision());
        assertEquals(Optional.of(activation), service.activation(scope, created.id()));
        assertEquals(List.of(activation), service.activations(scope));
        assertEquals(Optional.of(scope), store.audit.getLast().scope());
        PolicyConflictException stale = assertThrows(PolicyConflictException.class,
                () -> service.activate(scope, created.id(), versionId, 0, "actor", "again"));
        assertEquals("stale policy activation revision: expected 0 but current is 1", stale.getMessage());
    }

    @Test
    void aScopeRefusesOneActivePackMoreThanItsBudget() {
        for (int index = 0; index < PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE; index++) {
            store.putActivation(new PolicyConfiguration.Activation(
                    scope, PolicyIds.PackId.generate(), PolicyIds.VersionId.generate(), 1, "actor", AT));
        }
        PolicyPack.Definition created = service.create("pack", List.of(rule), "actor", "reason");
        PolicyIds.VersionId versionId = service.versions(created.id()).getFirst().versionId();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> service.activate(scope, created.id(), versionId, 0, "actor", "on"));

        assertEquals("policy scope exceeds active pack budget: " + PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE,
                refused.getMessage());
    }

    @Test
    void deactivationNeedsAnActivePackAndItsCurrentRevision() {
        PolicyPack.Definition created = service.create("pack", List.of(rule), "actor", "reason");
        PolicyIds.VersionId versionId = service.versions(created.id()).getFirst().versionId();

        EntityStateException inactive = assertThrows(EntityStateException.class,
                () -> service.deactivate(scope, created.id(), 1, "actor", "off"));
        service.activate(scope, created.id(), versionId, 0, "actor", "on");
        PolicyConflictException stale = assertThrows(PolicyConflictException.class,
                () -> service.deactivate(scope, created.id(), 2, "actor", "off"));
        service.deactivate(scope, created.id(), 1, "actor", "off");

        assertEquals("policy pack is not active in scope: " + created.id(), inactive.getMessage());
        assertEquals("stale policy activation revision: expected 2 but current is 1", stale.getMessage());
        assertEquals(List.of(), service.activations(scope));
        assertEquals(PolicyConfiguration.AuditAction.DEACTIVATE, store.audit.getLast().action());
        assertEquals(Optional.of(versionId), store.audit.getLast().versionId());
    }

    @Test
    void anOverrideTargetsARuleOfTheActiveVersionAndIsRevisioned() {
        PolicyPack.Definition created = service.create("pack", List.of(rule), "actor", "reason");
        PolicyIds.VersionId versionId = service.versions(created.id()).getFirst().versionId();

        EntityStateException inactive = assertThrows(EntityStateException.class, () -> service.putOverride(
                scope, created.id(), rule.id(), PolicyConfiguration.OverrideMode.DISABLE, 0, "actor", "why"));
        service.activate(scope, created.id(), versionId, 0, "actor", "on");
        PolicyIds.RuleId absent = PolicyIds.RuleId.generate();
        EntityNotFoundException unknownRule = assertThrows(EntityNotFoundException.class, () -> service.putOverride(
                scope, created.id(), absent, PolicyConfiguration.OverrideMode.DISABLE, 0, "actor", "why"));
        PolicyConfiguration.Override first = service.putOverride(
                scope, created.id(), rule.id(), PolicyConfiguration.OverrideMode.DISABLE, 0, "actor", "why");
        PolicyConflictException stale = assertThrows(PolicyConflictException.class, () -> service.putOverride(
                scope, created.id(), rule.id(), PolicyConfiguration.OverrideMode.FORCE_WARN, 0, "actor", "why"));
        PolicyConfiguration.Override second = service.putOverride(
                scope, created.id(), rule.id(), PolicyConfiguration.OverrideMode.FORCE_WARN, 1, "actor", "why");

        assertEquals("policy pack must be active before adding an override: " + created.id(), inactive.getMessage());
        assertEquals("rule is not present in active policy pack version: " + absent, unknownRule.getMessage());
        assertEquals("stale policy override revision: expected 0 but current is 1", stale.getMessage());
        assertEquals(1, first.revision());
        assertEquals(2, second.revision());
        assertEquals(List.of(second), service.overrides(scope));
        assertEquals(Optional.of(rule.id()), store.audit.getLast().ruleId());
    }

    @Test
    void aScopeRefusesOneOverrideMoreThanItsBudgetButStillRevisesAnExistingOne() {
        PolicyPack.Definition created = service.create("pack", List.of(rule), "actor", "reason");
        PolicyIds.VersionId versionId = service.versions(created.id()).getFirst().versionId();
        service.activate(scope, created.id(), versionId, 0, "actor", "on");
        service.putOverride(scope, created.id(), rule.id(), PolicyConfiguration.OverrideMode.DISABLE, 0, "actor", "why");
        for (int index = 1; index < PolicyBudgets.MAX_OVERRIDES_PER_SCOPE; index++) {
            store.putOverride(new PolicyConfiguration.Override(scope, PolicyIds.PackId.generate(),
                    PolicyIds.RuleId.generate(), PolicyConfiguration.OverrideMode.DISABLE, "why", "actor", 1, AT));
        }
        PolicyRule other = rule("other");
        PolicyPack.Definition second = service.create("second", List.of(other), "actor", "reason");
        service.activate(scope, second.id(), service.versions(second.id()).getFirst().versionId(), 0, "actor", "on");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> service.putOverride(
                scope, second.id(), other.id(), PolicyConfiguration.OverrideMode.DISABLE, 0, "actor", "why"));
        PolicyConfiguration.Override revised = service.putOverride(
                scope, created.id(), rule.id(), PolicyConfiguration.OverrideMode.FORCE_BLOCK, 1, "actor", "why");

        assertEquals("policy scope exceeds override budget: " + PolicyBudgets.MAX_OVERRIDES_PER_SCOPE,
                refused.getMessage());
        assertEquals(PolicyConfiguration.OverrideMode.FORCE_BLOCK, revised.mode());
    }

    @Test
    void removingAnOverrideNeedsItsCurrentRevision() {
        PolicyPack.Definition created = service.create("pack", List.of(rule), "actor", "reason");
        service.activate(scope, created.id(), service.versions(created.id()).getFirst().versionId(), 0, "actor", "on");

        EntityNotFoundException missing = assertThrows(EntityNotFoundException.class,
                () -> service.removeOverride(scope, created.id(), rule.id(), 1, "actor", "why"));
        service.putOverride(scope, created.id(), rule.id(), PolicyConfiguration.OverrideMode.DISABLE, 0, "actor", "why");
        PolicyConflictException stale = assertThrows(PolicyConflictException.class,
                () -> service.removeOverride(scope, created.id(), rule.id(), 3, "actor", "why"));
        service.removeOverride(scope, created.id(), rule.id(), 1, "actor", "why");

        assertEquals("policy override does not exist: " + rule.id(), missing.getMessage());
        assertEquals("stale policy override revision: expected 3 but current is 1", stale.getMessage());
        assertEquals(List.of(), service.overrides(scope));
        assertEquals(PolicyConfiguration.AuditAction.REMOVE_OVERRIDE, store.audit.getLast().action());
    }

    @Test
    void theAuditOfAPackListsEveryWriteInOrder() {
        PolicyPack.Definition created = service.create("pack", List.of(rule), "actor", "reason");
        new PolicyPackService(store, Clock.fixed(AT.plusSeconds(1), ZoneOffset.UTC))
                .update(created.id(), 1, "pack", List.of(rule), "actor", "reason");

        PolicyAuditPage page = service.audit(created.id(), new PageRequest(0, 10));

        assertEquals(List.of(PolicyConfiguration.AuditAction.CREATE, PolicyConfiguration.AuditAction.UPDATE),
                page.items().stream().map(PolicyConfiguration.AuditRecord::action).toList());
    }

    @Test
    void aPortfolioScopeIsADistinctScope() {
        PolicyScope portfolio = new PolicyScope.Portfolio(PortfolioId.generate());
        PolicyPack.Definition created = service.create("pack", List.of(rule), "actor", "reason");
        service.activate(portfolio, created.id(), service.versions(created.id()).getFirst().versionId(), 0, "a", "on");

        assertEquals("PORTFOLIO", portfolio.type());
        assertTrue(service.activations(scope).isEmpty());
        assertEquals(1, service.activations(portfolio).size());
    }

    static PolicyRule rule(String description) {
        return new PolicyRule(PolicyIds.RuleId.generate(), description, PolicyRule.Kind.LIFECYCLE_GUARD,
                PolicyRule.Severity.BLOCKER, new PolicyRule.LifecycleGuard(ChangeId.generate(),
                ChangeLifecycleState.values()[0], ChangeLifecycleState.values()[1]));
    }

    private static PolicyConfiguration.AuditRecord audit(PolicyIds.PackId packId) {
        return new PolicyConfiguration.AuditRecord(DomainIdentity.generate(),
                PolicyConfiguration.AuditAction.CREATE, packId, Optional.empty(), Optional.empty(), Optional.empty(),
                "actor", "reason", AT);
    }
}
