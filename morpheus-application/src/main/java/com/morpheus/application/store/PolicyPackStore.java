package com.morpheus.application.store;

import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPack;
import com.morpheus.application.policy.PolicyScope;

import java.util.List;
import java.util.Optional;

/** Technology-neutral M25 persistence boundary for policy registry, activation, overrides and audit. */
public interface PolicyPackStore {
    void create(
            PolicyPack.Definition definition,
            PolicyPack.Version initialVersion,
            PolicyConfiguration.AuditRecord audit);

    Optional<PolicyPack.Definition> findDefinition(PolicyIds.PackId id);

    List<PolicyPack.Definition> listDefinitions();

    Optional<PolicyPack.Version> findVersion(PolicyIds.PackId packId, PolicyIds.VersionId versionId);

    List<PolicyPack.Version> listVersions(PolicyIds.PackId packId);

    PolicyPack.Definition compareAndSetDefinition(
            PolicyIds.PackId packId,
            long expectedRevision,
            PolicyPack.Definition replacement,
            PolicyPack.Version newVersion,
            PolicyConfiguration.AuditRecord audit);

    Optional<PolicyConfiguration.Activation> findActivation(PolicyScope scope, PolicyIds.PackId packId);

    List<PolicyConfiguration.Activation> listActivations(PolicyScope scope);

    PolicyConfiguration.Activation compareAndSetActivation(
            PolicyScope scope,
            PolicyIds.PackId packId,
            long expectedRevision,
            PolicyConfiguration.Activation replacement,
            PolicyConfiguration.AuditRecord audit);

    void removeActivation(
            PolicyScope scope,
            PolicyIds.PackId packId,
            long expectedRevision,
            PolicyConfiguration.AuditRecord audit);

    Optional<PolicyConfiguration.Override> findOverride(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.RuleId ruleId);

    List<PolicyConfiguration.Override> listOverrides(PolicyScope scope);

    PolicyConfiguration.Override compareAndSetOverride(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.RuleId ruleId,
            long expectedRevision,
            PolicyConfiguration.Override replacement,
            PolicyConfiguration.AuditRecord audit);

    void removeOverride(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.RuleId ruleId,
            long expectedRevision,
            PolicyConfiguration.AuditRecord audit);

    List<PolicyConfiguration.AuditRecord> listAudit(PolicyIds.PackId packId);
}