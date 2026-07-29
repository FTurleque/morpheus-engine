package com.morpheus.application.policy;

import com.morpheus.domain.identity.DomainIdentity;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Mutable governance configuration is explicit, CAS-versioned and separately audited. */
public final class PolicyConfiguration {
    private PolicyConfiguration() {
    }

    public record Activation(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.VersionId versionId,
            long revision,
            String actor,
            Instant updatedAt) implements Comparable<Activation> {
        public Activation {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(versionId, "versionId");
            if (revision <= 0) {
                throw new IllegalArgumentException("activation revision must be positive");
            }
            actor = nonBlank(actor, "actor");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }

        @java.lang.Override
        public int compareTo(Activation other) {
            int scopeCompare = scopeKey(scope).compareTo(scopeKey(other.scope));
            return scopeCompare != 0 ? scopeCompare : packId.compareTo(other.packId);
        }
    }

    public record Override(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.RuleId ruleId,
            OverrideMode mode,
            String reason,
            String actor,
            long revision,
            Instant updatedAt) implements Comparable<Override> {
        public Override {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(mode, "mode");
            reason = nonBlank(reason, "reason");
            actor = nonBlank(actor, "actor");
            if (revision <= 0) {
                throw new IllegalArgumentException("override revision must be positive");
            }
            Objects.requireNonNull(updatedAt, "updatedAt");
        }

        @java.lang.Override
        public int compareTo(Override other) {
            int scopeCompare = scopeKey(scope).compareTo(scopeKey(other.scope));
            if (scopeCompare != 0) {
                return scopeCompare;
            }
            int packCompare = packId.compareTo(other.packId);
            return packCompare != 0 ? packCompare : ruleId.compareTo(other.ruleId);
        }
    }

    public enum OverrideMode {
        DISABLE,
        FORCE_WARN,
        FORCE_BLOCK
    }

    public enum AuditAction {
        CREATE,
        UPDATE,
        ACTIVATE,
        DEACTIVATE,
        PUT_OVERRIDE,
        REMOVE_OVERRIDE
    }

    public record AuditRecord(
            DomainIdentity id,
            AuditAction action,
            PolicyIds.PackId packId,
            Optional<PolicyIds.VersionId> versionId,
            Optional<PolicyIds.RuleId> ruleId,
            Optional<PolicyScope> scope,
            String actor,
            String reason,
            Instant at) implements Comparable<AuditRecord> {
        public AuditRecord {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(versionId, "versionId");
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(scope, "scope");
            actor = nonBlank(actor, "actor");
            reason = nonBlank(reason, "reason");
            Objects.requireNonNull(at, "at");
        }

        @java.lang.Override
        public int compareTo(AuditRecord other) {
            int atCompare = at.compareTo(other.at);
            return atCompare != 0 ? atCompare : id.compareTo(other.id);
        }
    }

    static String scopeKey(PolicyScope scope) {
        return scope.type() + ":" + scope.identity();
    }

    private static String nonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
