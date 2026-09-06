package com.morpheus.store.memory;

import com.morpheus.application.policy.PolicyBudgets;
import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyConflictException;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPack;
import com.morpheus.application.policy.PolicyScope;
import com.morpheus.application.store.PolicyPackStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Thread-safe deterministic in-memory M25 policy adapter with immutable version/audit history. */
public final class MemoryPolicyPackStore implements PolicyPackStore {
    private final Map<PolicyIds.PackId, PolicyPack.Definition> definitions = new TreeMap<>();
    private final Map<PolicyIds.PackId, List<PolicyPack.Version>> versions = new TreeMap<>();
    private final Map<String, PolicyConfiguration.Activation> activations = new TreeMap<>();
    private final Map<String, PolicyConfiguration.Override> overrides = new TreeMap<>();
    private final Map<PolicyIds.PackId, List<PolicyConfiguration.AuditRecord>> audits = new TreeMap<>();

    @Override
    public synchronized void create(
            PolicyPack.Definition definition,
            PolicyPack.Version initialVersion,
            PolicyConfiguration.AuditRecord audit) {
        if (!definition.id().equals(initialVersion.packId())) {
            throw new IllegalArgumentException("policy create identity mismatch");
        }
        requireAudit(
                audit,
                PolicyConfiguration.AuditAction.CREATE,
                definition.id(),
                Optional.of(initialVersion.versionId()),
                Optional.empty(),
                Optional.empty());
        if (definitions.containsKey(definition.id())) {
            throw new PolicyConflictException("policy pack already exists: " + definition.id());
        }
        definitions.put(definition.id(), definition);
        versions.put(definition.id(), new ArrayList<>(List.of(initialVersion)));
        audits.put(definition.id(), new ArrayList<>(List.of(audit)));
    }

    @Override
    public synchronized Optional<PolicyPack.Definition> findDefinition(PolicyIds.PackId id) {
        return Optional.ofNullable(definitions.get(id));
    }

    @Override
    public synchronized List<PolicyPack.Definition> listDefinitions() {
        return List.copyOf(definitions.values());
    }

    @Override
    public synchronized Optional<PolicyPack.Version> findVersion(
            PolicyIds.PackId packId,
            PolicyIds.VersionId versionId) {
        return versions.getOrDefault(packId, List.of()).stream()
                .filter(version -> version.versionId().equals(versionId))
                .findFirst();
    }

    @Override
    public synchronized List<PolicyPack.Version> listVersions(PolicyIds.PackId packId) {
        return List.copyOf(versions.getOrDefault(packId, List.of()));
    }

    @Override
    public synchronized PolicyPack.Definition compareAndSetDefinition(
            PolicyIds.PackId packId,
            long expectedRevision,
            PolicyPack.Definition replacement,
            PolicyPack.Version newVersion,
            PolicyConfiguration.AuditRecord audit) {
        PolicyPack.Definition current = requireDefinition(packId);
        if (current.revision() != expectedRevision) {
            throw conflict("policy pack", expectedRevision, current.revision());
        }
        if (!replacement.id().equals(packId) || !newVersion.packId().equals(packId)) {
            throw new IllegalArgumentException("policy update identity mismatch");
        }
        requireAudit(
                audit,
                PolicyConfiguration.AuditAction.UPDATE,
                packId,
                Optional.of(newVersion.versionId()),
                Optional.empty(),
                Optional.empty());
        if (replacement.revision() != expectedRevision + 1
                || replacement.latestVersionNumber() != current.latestVersionNumber() + 1
                || newVersion.versionNumber() != replacement.latestVersionNumber()) {
            throw new IllegalArgumentException("policy update must advance revision and version by exactly one");
        }
        definitions.put(packId, replacement);
        versions.computeIfAbsent(packId, ignored -> new ArrayList<>()).add(newVersion);
        appendAudit(audit);
        return replacement;
    }

    @Override
    public synchronized Optional<PolicyConfiguration.Activation> findActivation(
            PolicyScope scope,
            PolicyIds.PackId packId) {
        return Optional.ofNullable(activations.get(activationKey(scope, packId)));
    }

    @Override
    public synchronized List<PolicyConfiguration.Activation> listActivations(PolicyScope scope) {
        String prefix = scopeKey(scope) + "|";
        return activations.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .sorted()
                .toList();
    }

    @Override
    public synchronized PolicyConfiguration.Activation compareAndSetActivation(
            PolicyScope scope,
            PolicyIds.PackId packId,
            long expectedRevision,
            PolicyConfiguration.Activation replacement,
            PolicyConfiguration.AuditRecord audit) {
        String key = activationKey(scope, packId);
        long actual = Optional.ofNullable(activations.get(key))
                .map(PolicyConfiguration.Activation::revision)
                .orElse(0L);
        if (actual != expectedRevision) {
            throw conflict("policy activation", expectedRevision, actual);
        }
        if (!replacement.scope().equals(scope) || !replacement.packId().equals(packId)
                || replacement.revision() != expectedRevision + 1) {
            throw new IllegalArgumentException("policy activation replacement mismatch");
        }
        requireAudit(
                audit,
                PolicyConfiguration.AuditAction.ACTIVATE,
                packId,
                Optional.of(replacement.versionId()),
                Optional.empty(),
                Optional.of(scope));
        requireVersion(packId, replacement.versionId());
        if (expectedRevision == 0 && countActivations(scope) >= PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE) {
            throw new IllegalArgumentException(
                    "policy scope exceeds active pack budget: " + PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE);
        }
        activations.put(key, replacement);
        appendAudit(audit);
        return replacement;
    }

    @Override
    public synchronized void removeActivation(
            PolicyScope scope,
            PolicyIds.PackId packId,
            long expectedRevision,
            PolicyConfiguration.AuditRecord audit) {
        String key = activationKey(scope, packId);
        PolicyConfiguration.Activation current = activations.get(key);
        if (current == null) {
            throw new IllegalArgumentException("policy activation does not exist: " + packId);
        }
        if (current.revision() != expectedRevision) {
            throw conflict("policy activation", expectedRevision, current.revision());
        }
        requireAudit(
                audit,
                PolicyConfiguration.AuditAction.DEACTIVATE,
                packId,
                Optional.of(current.versionId()),
                Optional.empty(),
                Optional.of(scope));
        activations.remove(key);
        appendAudit(audit);
    }

    @Override
    public synchronized Optional<PolicyConfiguration.Override> findOverride(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.RuleId ruleId) {
        return Optional.ofNullable(overrides.get(overrideKey(scope, packId, ruleId)));
    }

    @Override
    public synchronized List<PolicyConfiguration.Override> listOverrides(PolicyScope scope) {
        String prefix = scopeKey(scope) + "|";
        return overrides.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .sorted()
                .toList();
    }

    @Override
    public synchronized PolicyConfiguration.Override compareAndSetOverride(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.RuleId ruleId,
            long expectedRevision,
            PolicyConfiguration.Override replacement,
            PolicyConfiguration.AuditRecord audit) {
        String key = overrideKey(scope, packId, ruleId);
        long actual = Optional.ofNullable(overrides.get(key))
                .map(PolicyConfiguration.Override::revision)
                .orElse(0L);
        if (actual != expectedRevision) {
            throw conflict("policy override", expectedRevision, actual);
        }
        if (!replacement.scope().equals(scope) || !replacement.packId().equals(packId)
                || !replacement.ruleId().equals(ruleId) || replacement.revision() != expectedRevision + 1) {
            throw new IllegalArgumentException("policy override replacement mismatch");
        }
        PolicyConfiguration.Activation active = activations.get(activationKey(scope, packId));
        if (active == null) {
            throw new IllegalArgumentException("policy pack must be active before adding an override: " + packId);
        }
        requireAudit(
                audit,
                PolicyConfiguration.AuditAction.PUT_OVERRIDE,
                packId,
                Optional.of(active.versionId()),
                Optional.of(ruleId),
                Optional.of(scope));
        if (expectedRevision == 0 && countOverrides(scope) >= PolicyBudgets.MAX_OVERRIDES_PER_SCOPE) {
            throw new IllegalArgumentException(
                    "policy scope exceeds override budget: " + PolicyBudgets.MAX_OVERRIDES_PER_SCOPE);
        }
        overrides.put(key, replacement);
        appendAudit(audit);
        return replacement;
    }

    @Override
    public synchronized void removeOverride(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.RuleId ruleId,
            long expectedRevision,
            PolicyConfiguration.AuditRecord audit) {
        String key = overrideKey(scope, packId, ruleId);
        PolicyConfiguration.Override current = overrides.get(key);
        if (current == null) {
            throw new IllegalArgumentException("policy override does not exist: " + ruleId);
        }
        if (current.revision() != expectedRevision) {
            throw conflict("policy override", expectedRevision, current.revision());
        }
        requireAudit(
                audit,
                PolicyConfiguration.AuditAction.REMOVE_OVERRIDE,
                packId,
                Optional.empty(),
                Optional.of(ruleId),
                Optional.of(scope));
        overrides.remove(key);
        appendAudit(audit);
    }

    @Override
    public synchronized List<PolicyConfiguration.AuditRecord> listAudit(PolicyIds.PackId packId) {
        return List.copyOf(audits.getOrDefault(packId, List.of()));
    }

    private PolicyPack.Definition requireDefinition(PolicyIds.PackId packId) {
        PolicyPack.Definition definition = definitions.get(packId);
        if (definition == null) {
            throw new IllegalArgumentException("unknown policy pack: " + packId);
        }
        return definition;
    }

    private PolicyPack.Version requireVersion(PolicyIds.PackId packId, PolicyIds.VersionId versionId) {
        return findVersion(packId, versionId)
                .orElseThrow(() -> new IllegalArgumentException("unknown policy version: " + versionId));
    }

    private void appendAudit(PolicyConfiguration.AuditRecord audit) {
        requireDefinition(audit.packId());
        audits.computeIfAbsent(audit.packId(), ignored -> new ArrayList<>()).add(audit);
    }

    private void requireAudit(
            PolicyConfiguration.AuditRecord audit,
            PolicyConfiguration.AuditAction action,
            PolicyIds.PackId packId,
            Optional<PolicyIds.VersionId> versionId,
            Optional<PolicyIds.RuleId> ruleId,
            Optional<PolicyScope> scope) {
        Objects.requireNonNull(audit, "audit");
        if (audit.action() != action
                || !audit.packId().equals(packId)
                || !audit.versionId().equals(versionId)
                || !audit.ruleId().equals(ruleId)
                || !audit.scope().equals(scope)) {
            throw new IllegalArgumentException("policy audit target mismatch for " + action);
        }
    }

    private long countActivations(PolicyScope scope) {
        String prefix = scopeKey(scope) + "|";
        return activations.keySet().stream().filter(key -> key.startsWith(prefix)).count();
    }

    private long countOverrides(PolicyScope scope) {
        String prefix = scopeKey(scope) + "|";
        return overrides.keySet().stream().filter(key -> key.startsWith(prefix)).count();
    }

    private PolicyConflictException conflict(String target, long expected, long actual) {
        return new PolicyConflictException("stale " + target + " revision: expected " + expected + " but current is " + actual);
    }

    private String activationKey(PolicyScope scope, PolicyIds.PackId packId) {
        return scopeKey(scope) + "|" + packId;
    }

    private String overrideKey(PolicyScope scope, PolicyIds.PackId packId, PolicyIds.RuleId ruleId) {
        return scopeKey(scope) + "|" + packId + "|" + ruleId;
    }

    private String scopeKey(PolicyScope scope) {
        return scope.type() + ":" + scope.identity();
    }
}
