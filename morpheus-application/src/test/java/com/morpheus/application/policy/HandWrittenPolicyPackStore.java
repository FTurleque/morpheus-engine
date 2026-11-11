package com.morpheus.application.policy;

import com.morpheus.application.query.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A policy store held in ordered maps, with the compare-and-set semantics the service relies on: a write against a
 * revision the store does not hold is refused. Every write appends its audit record.
 */
final class HandWrittenPolicyPackStore implements PolicyPackStore {
    private final TreeMap<PolicyIds.PackId, PolicyPack.Definition> definitions = new TreeMap<>();
    private final List<PolicyPack.Version> versions = new ArrayList<>();
    private final TreeMap<String, PolicyConfiguration.Activation> activations = new TreeMap<>();
    private final TreeMap<String, PolicyConfiguration.Override> overrides = new TreeMap<>();
    final List<PolicyConfiguration.AuditRecord> audit = new ArrayList<>();

    @Override
    public void create(
            PolicyPack.Definition definition,
            PolicyPack.Version initialVersion,
            PolicyConfiguration.AuditRecord record) {
        definitions.put(definition.id(), definition);
        versions.add(initialVersion);
        audit.add(record);
    }

    @Override
    public Optional<PolicyPack.Definition> findDefinition(PolicyIds.PackId id) {
        return Optional.ofNullable(definitions.get(id));
    }

    @Override
    public List<PolicyPack.Definition> listDefinitions() {
        return List.copyOf(definitions.values()).reversed();
    }

    @Override
    public Optional<PolicyPack.Version> findVersion(PolicyIds.PackId packId, PolicyIds.VersionId versionId) {
        return versions.stream()
                .filter(version -> version.packId().equals(packId) && version.versionId().equals(versionId))
                .findFirst();
    }

    @Override
    public List<PolicyPack.Version> listVersions(PolicyIds.PackId packId) {
        return versions.stream().filter(version -> version.packId().equals(packId)).toList().reversed();
    }

    @Override
    public PolicyPack.Definition compareAndSetDefinition(
            PolicyIds.PackId packId,
            long expectedRevision,
            PolicyPack.Definition replacement,
            PolicyPack.Version newVersion,
            PolicyConfiguration.AuditRecord record) {
        requireRevision(definitions.get(packId).revision(), expectedRevision);
        definitions.put(packId, replacement);
        versions.add(newVersion);
        audit.add(record);
        return replacement;
    }

    @Override
    public Optional<PolicyConfiguration.Activation> findActivation(PolicyScope scope, PolicyIds.PackId packId) {
        return Optional.ofNullable(activations.get(key(scope, packId)));
    }

    @Override
    public List<PolicyConfiguration.Activation> listActivations(PolicyScope scope) {
        return activations.values().stream()
                .filter(activation -> activation.scope().equals(scope))
                .toList()
                .reversed();
    }

    @Override
    public PolicyConfiguration.Activation compareAndSetActivation(
            PolicyScope scope,
            PolicyIds.PackId packId,
            long expectedRevision,
            PolicyConfiguration.Activation replacement,
            PolicyConfiguration.AuditRecord record) {
        requireRevision(findActivation(scope, packId).map(PolicyConfiguration.Activation::revision).orElse(0L),
                expectedRevision);
        activations.put(key(scope, packId), replacement);
        audit.add(record);
        return replacement;
    }

    @Override
    public void removeActivation(
            PolicyScope scope,
            PolicyIds.PackId packId,
            long expectedRevision,
            PolicyConfiguration.AuditRecord record) {
        requireRevision(activations.get(key(scope, packId)).revision(), expectedRevision);
        activations.remove(key(scope, packId));
        audit.add(record);
    }

    @Override
    public Optional<PolicyConfiguration.Override> findOverride(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.RuleId ruleId) {
        return Optional.ofNullable(overrides.get(key(scope, packId) + "|" + ruleId));
    }

    @Override
    public List<PolicyConfiguration.Override> listOverrides(PolicyScope scope) {
        return overrides.values().stream()
                .filter(override -> override.scope().equals(scope))
                .toList()
                .reversed();
    }

    @Override
    public PolicyConfiguration.Override compareAndSetOverride(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.RuleId ruleId,
            long expectedRevision,
            PolicyConfiguration.Override replacement,
            PolicyConfiguration.AuditRecord record) {
        requireRevision(findOverride(scope, packId, ruleId).map(PolicyConfiguration.Override::revision).orElse(0L),
                expectedRevision);
        overrides.put(key(scope, packId) + "|" + ruleId, replacement);
        audit.add(record);
        return replacement;
    }

    @Override
    public void removeOverride(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.RuleId ruleId,
            long expectedRevision,
            PolicyConfiguration.AuditRecord record) {
        overrides.remove(key(scope, packId) + "|" + ruleId);
        audit.add(record);
    }

    @Override
    public PolicyAuditPage listAudit(PolicyIds.PackId packId, PageRequest pageRequest) {
        return PolicyAuditPage.of(
                audit.stream().filter(record -> record.packId().equals(packId)).sorted().toList(), pageRequest);
    }

    void putActivation(PolicyConfiguration.Activation activation) {
        activations.put(key(activation.scope(), activation.packId()), activation);
    }

    void putVersion(PolicyPack.Version version) {
        versions.add(version);
    }

    void putOverride(PolicyConfiguration.Override override) {
        overrides.put(key(override.scope(), override.packId()) + "|" + override.ruleId(), override);
    }

    private static String key(PolicyScope scope, PolicyIds.PackId packId) {
        return PolicyConfiguration.scopeKey(scope) + "|" + packId;
    }

    private static void requireRevision(long actual, long expected) {
        if (actual != expected) {
            throw new IllegalStateException("store revision " + actual + " is not " + expected);
        }
    }
}
