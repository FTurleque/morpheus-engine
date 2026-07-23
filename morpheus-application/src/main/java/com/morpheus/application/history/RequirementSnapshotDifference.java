package com.morpheus.application.history;

import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;
import java.util.Optional;

/** One deterministic requirement difference between two published snapshots. */
public record RequirementSnapshotDifference(
        DomainIdentity entityIdentity,
        RequirementSnapshotChangeKind kind,
        Optional<RequirementVersionRecord> source,
        Optional<RequirementVersionRecord> target) {

    public RequirementSnapshotDifference {
        Objects.requireNonNull(entityIdentity, "entityIdentity");
        Objects.requireNonNull(kind, "kind");
        source = Objects.requireNonNull(source, "source");
        target = Objects.requireNonNull(target, "target");

        source.ifPresent(record -> requireIdentity(entityIdentity, record, "source"));
        target.ifPresent(record -> requireIdentity(entityIdentity, record, "target"));

        switch (kind) {
            case ADDED -> {
                if (source.isPresent() || target.isEmpty()) {
                    throw new IllegalArgumentException("ADDED requires absent source and present target");
                }
            }
            case REMOVED -> {
                if (source.isEmpty() || target.isPresent()) {
                    throw new IllegalArgumentException("REMOVED requires present source and absent target");
                }
            }
            case MODIFIED, UNCHANGED -> {
                if (source.isEmpty() || target.isEmpty()) {
                    throw new IllegalArgumentException(kind + " requires source and target occurrences");
                }
            }
        }
    }

    private static void requireIdentity(
            DomainIdentity expected,
            RequirementVersionRecord record,
            String side) {
        if (!record.entityVersion().entityIdentity().equals(expected)) {
            throw new IllegalArgumentException(side + " occurrence identity does not match difference identity");
        }
    }
}