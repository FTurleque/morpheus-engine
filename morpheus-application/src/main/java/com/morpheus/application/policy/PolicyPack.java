package com.morpheus.application.policy;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Policy pack metadata plus immutable version snapshots. */
public final class PolicyPack {
    private PolicyPack() {
    }

    public record Definition(
            PolicyIds.PackId id,
            String name,
            long revision,
            long latestVersionNumber,
            Instant createdAt,
            Instant updatedAt) implements Comparable<Definition> {
        public Definition {
            Objects.requireNonNull(id, "id");
            name = boundedName(name);
            if (revision <= 0 || latestVersionNumber <= 0) {
                throw new IllegalArgumentException("policy pack revision/version must be positive");
            }
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (updatedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("updatedAt must not precede createdAt");
            }
        }

        @Override
        public int compareTo(Definition other) {
            return id.compareTo(other.id);
        }
    }

    public record Version(
            PolicyIds.PackId packId,
            PolicyIds.VersionId versionId,
            long versionNumber,
            String name,
            List<PolicyRule> rules,
            Instant createdAt) implements Comparable<Version> {
        public Version {
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(versionId, "versionId");
            if (versionNumber <= 0) {
                throw new IllegalArgumentException("policy versionNumber must be positive");
            }
            name = boundedName(name);
            Objects.requireNonNull(rules, "rules");
            if (rules.isEmpty()) {
                throw new IllegalArgumentException("policy pack version requires at least one rule");
            }
            if (rules.size() > PolicyBudgets.MAX_RULES_PER_PACK) {
                throw new IllegalArgumentException("policy pack exceeds max rules: " + PolicyBudgets.MAX_RULES_PER_PACK);
            }
            List<PolicyRule> ordered = rules.stream()
                    .peek(rule -> Objects.requireNonNull(rule, "rules item"))
                    .sorted()
                    .toList();
            Set<PolicyIds.RuleId> ids = new HashSet<>();
            for (PolicyRule rule : ordered) {
                if (!ids.add(rule.id())) {
                    throw new IllegalArgumentException("duplicate policy rule identity: " + rule.id());
                }
            }
            rules = List.copyOf(ordered);
            Objects.requireNonNull(createdAt, "createdAt");
        }

        @Override
        public int compareTo(Version other) {
            int pack = packId.compareTo(other.packId);
            return pack != 0 ? pack : Long.compare(versionNumber, other.versionNumber);
        }
    }

    private static String boundedName(String value) {
        Objects.requireNonNull(value, "name");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("policy pack name must not be blank");
        }
        if (normalized.length() > PolicyBudgets.MAX_PACK_NAME) {
            throw new IllegalArgumentException("policy pack name exceeds " + PolicyBudgets.MAX_PACK_NAME + " characters");
        }
        return normalized;
    }
}