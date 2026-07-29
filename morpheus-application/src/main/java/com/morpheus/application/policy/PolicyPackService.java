package com.morpheus.application.policy;

import com.morpheus.application.store.PolicyPackStore;
import com.morpheus.domain.identity.DomainIdentity;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Versioned M25 policy registry. Configuration writes are explicit, CAS-protected and audited. */
public final class PolicyPackService {
    private final PolicyPackStore store;
    private final Clock clock;

    public PolicyPackService(PolicyPackStore store) {
        this(store, Clock.systemUTC());
    }

    public PolicyPackService(PolicyPackStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PolicyPack.Definition create(
            String name,
            List<PolicyRule> rules,
            String actor,
            String reason) {
        Instant now = clock.instant();
        PolicyIds.PackId packId = PolicyIds.PackId.generate();
        PolicyIds.VersionId versionId = PolicyIds.VersionId.generate();
        PolicyPack.Version version = new PolicyPack.Version(packId, versionId, 1, name, rules, now);
        PolicyPack.Definition definition = new PolicyPack.Definition(packId, name, 1, 1, now, now);
        store.create(definition, version, audit(
                PolicyConfiguration.AuditAction.CREATE,
                packId,
                Optional.of(versionId),
                Optional.empty(),
                Optional.empty(),
                actor,
                reason,
                now));
        return definition;
    }

    public PolicyPack.Definition get(PolicyIds.PackId packId) {
        return store.findDefinition(Objects.requireNonNull(packId, "packId"))
                .orElseThrow(() -> new IllegalArgumentException("unknown policy pack: " + packId));
    }

    public List<PolicyPack.Definition> list() {
        return store.listDefinitions().stream().sorted().toList();
    }

    public PolicyPack.Version version(PolicyIds.PackId packId, PolicyIds.VersionId versionId) {
        return store.findVersion(packId, versionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown policy pack version: " + packId + "/" + versionId));
    }

    public List<PolicyPack.Version> versions(PolicyIds.PackId packId) {
        get(packId);
        return store.listVersions(packId).stream().sorted().toList();
    }

    public PolicyPack.Definition update(
            PolicyIds.PackId packId,
            long expectedRevision,
            String name,
            List<PolicyRule> rules,
            String actor,
            String reason) {
        PolicyPack.Definition current = get(packId);
        if (current.revision() != expectedRevision) {
            throw new PolicyConflictException(
                    "stale policy pack revision: expected " + expectedRevision + " but current is " + current.revision());
        }
        Instant now = clock.instant();
        long nextVersionNumber = current.latestVersionNumber() + 1;
        PolicyIds.VersionId versionId = PolicyIds.VersionId.generate();
        PolicyPack.Version version = new PolicyPack.Version(
                packId, versionId, nextVersionNumber, name, rules, now);
        PolicyPack.Definition replacement = new PolicyPack.Definition(
                packId, name, expectedRevision + 1, nextVersionNumber, current.createdAt(), now);
        return store.compareAndSetDefinition(packId, expectedRevision, replacement, version, audit(
                PolicyConfiguration.AuditAction.UPDATE,
                packId,
                Optional.of(versionId),
                Optional.empty(),
                Optional.empty(),
                actor,
                reason,
                now));
    }

    public Optional<PolicyConfiguration.Activation> activation(PolicyScope scope, PolicyIds.PackId packId) {
        return store.findActivation(scope, packId);
    }

    public List<PolicyConfiguration.Activation> activations(PolicyScope scope) {
        return store.listActivations(scope).stream().sorted().toList();
    }

    public PolicyConfiguration.Activation activate(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.VersionId versionId,
            long expectedRevision,
            String actor,
            String reason) {
        version(packId, versionId);
        Optional<PolicyConfiguration.Activation> current = store.findActivation(scope, packId);
        long actualRevision = current.map(PolicyConfiguration.Activation::revision).orElse(0L);
        if (actualRevision != expectedRevision) {
            throw new PolicyConflictException(
                    "stale policy activation revision: expected " + expectedRevision + " but current is " + actualRevision);
        }
        if (current.isEmpty() && store.listActivations(scope).size() >= PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE) {
            throw new IllegalArgumentException(
                    "policy scope exceeds active pack budget: " + PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE);
        }
        Instant now = clock.instant();
        PolicyConfiguration.Activation replacement = new PolicyConfiguration.Activation(
                scope, packId, versionId, expectedRevision + 1, actor, now);
        return store.compareAndSetActivation(scope, packId, expectedRevision, replacement, audit(
                PolicyConfiguration.AuditAction.ACTIVATE,
                packId,
                Optional.of(versionId),
                Optional.empty(),
                Optional.of(scope),
                actor,
                reason,
                now));
    }

    public void deactivate(
            PolicyScope scope,
            PolicyIds.PackId packId,
            long expectedRevision,
            String actor,
            String reason) {
        PolicyConfiguration.Activation current = store.findActivation(scope, packId)
                .orElseThrow(() -> new IllegalArgumentException("policy pack is not active in scope: " + packId));
        if (current.revision() != expectedRevision) {
            throw new PolicyConflictException(
                    "stale policy activation revision: expected " + expectedRevision + " but current is " + current.revision());
        }
        Instant now = clock.instant();
        store.removeActivation(scope, packId, expectedRevision, audit(
                PolicyConfiguration.AuditAction.DEACTIVATE,
                packId,
                Optional.of(current.versionId()),
                Optional.empty(),
                Optional.of(scope),
                actor,
                reason,
                now));
    }

    public PolicyConfiguration.Override putOverride(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.RuleId ruleId,
            PolicyConfiguration.OverrideMode mode,
            long expectedRevision,
            String actor,
            String reason) {
        PolicyConfiguration.Activation active = store.findActivation(scope, packId)
                .orElseThrow(() -> new IllegalArgumentException("policy pack must be active before adding an override: " + packId));
        PolicyPack.Version version = version(packId, active.versionId());
        if (version.rules().stream().noneMatch(rule -> rule.id().equals(ruleId))) {
            throw new IllegalArgumentException("rule is not present in active policy pack version: " + ruleId);
        }
        long actualRevision = store.findOverride(scope, packId, ruleId)
                .map(PolicyConfiguration.Override::revision)
                .orElse(0L);
        if (actualRevision != expectedRevision) {
            throw new PolicyConflictException(
                    "stale policy override revision: expected " + expectedRevision + " but current is " + actualRevision);
        }
        if (actualRevision == 0 && store.listOverrides(scope).size() >= PolicyBudgets.MAX_OVERRIDES_PER_SCOPE) {
            throw new IllegalArgumentException(
                    "policy scope exceeds override budget: " + PolicyBudgets.MAX_OVERRIDES_PER_SCOPE);
        }
        Instant now = clock.instant();
        PolicyConfiguration.Override replacement = new PolicyConfiguration.Override(
                scope, packId, ruleId, mode, expectedRevision + 1, actor, expectedRevision + 1, now);
        // Rebuild to keep constructor parameter order explicit and avoid reason/revision ambiguity.
        replacement = new PolicyConfiguration.Override(
                scope, packId, ruleId, mode, reason, actor, expectedRevision + 1, now);
        return store.compareAndSetOverride(scope, packId, ruleId, expectedRevision, replacement, audit(
                PolicyConfiguration.AuditAction.PUT_OVERRIDE,
                packId,
                Optional.of(active.versionId()),
                Optional.of(ruleId),
                Optional.of(scope),
                actor,
                reason,
                now));
    }

    public List<PolicyConfiguration.Override> overrides(PolicyScope scope) {
        return store.listOverrides(scope).stream().sorted().toList();
    }

    public void removeOverride(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.RuleId ruleId,
            long expectedRevision,
            String actor,
            String reason) {
        PolicyConfiguration.Override current = store.findOverride(scope, packId, ruleId)
                .orElseThrow(() -> new IllegalArgumentException("policy override does not exist: " + ruleId));
        if (current.revision() != expectedRevision) {
            throw new PolicyConflictException(
                    "stale policy override revision: expected " + expectedRevision + " but current is " + current.revision());
        }
        Instant now = clock.instant();
        store.removeOverride(scope, packId, ruleId, expectedRevision, audit(
                PolicyConfiguration.AuditAction.REMOVE_OVERRIDE,
                packId,
                Optional.empty(),
                Optional.of(ruleId),
                Optional.of(scope),
                actor,
                reason,
                now));
    }

    public List<PolicyConfiguration.AuditRecord> audit(PolicyIds.PackId packId) {
        get(packId);
        return store.listAudit(packId).stream().sorted().toList();
    }

    private PolicyConfiguration.AuditRecord audit(
            PolicyConfiguration.AuditAction action,
            PolicyIds.PackId packId,
            Optional<PolicyIds.VersionId> versionId,
            Optional<PolicyIds.RuleId> ruleId,
            Optional<PolicyScope> scope,
            String actor,
            String reason,
            Instant at) {
        return new PolicyConfiguration.AuditRecord(
                DomainIdentity.generate(), action, packId, versionId, ruleId, scope, actor, reason, at);
    }
}