package com.morpheus.application.policy;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** Stable provider-neutral identities used by M25 policy packs. */
public final class PolicyIds {
    private PolicyIds() {
    }

    public record PackId(DomainIdentity value) implements Comparable<PackId> {
        public PackId {
            Objects.requireNonNull(value, "value");
        }

        public static PackId generate() {
            return new PackId(DomainIdentity.generate());
        }

        public static PackId parse(String value) {
            return new PackId(DomainIdentity.parse(value));
        }

        @Override
        public int compareTo(PackId other) {
            return value.compareTo(other.value);
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    public record VersionId(DomainIdentity value) implements Comparable<VersionId> {
        public VersionId {
            Objects.requireNonNull(value, "value");
        }

        public static VersionId generate() {
            return new VersionId(DomainIdentity.generate());
        }

        public static VersionId parse(String value) {
            return new VersionId(DomainIdentity.parse(value));
        }

        @Override
        public int compareTo(VersionId other) {
            return value.compareTo(other.value);
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    public record RuleId(DomainIdentity value) implements Comparable<RuleId> {
        public RuleId {
            Objects.requireNonNull(value, "value");
        }

        public static RuleId generate() {
            return new RuleId(DomainIdentity.generate());
        }

        public static RuleId parse(String value) {
            return new RuleId(DomainIdentity.parse(value));
        }

        @Override
        public int compareTo(RuleId other) {
            return value.compareTo(other.value);
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }
}