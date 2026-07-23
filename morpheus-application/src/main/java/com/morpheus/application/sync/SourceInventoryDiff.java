package com.morpheus.application.sync;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic source-level delta between two complete inventories. */
public record SourceInventoryDiff(
        List<Change> changes,
        boolean ambiguousMoves) {

    public SourceInventoryDiff {
        changes = Objects.requireNonNull(changes, "changes").stream()
                .peek(change -> Objects.requireNonNull(change, "changes item"))
                .sorted()
                .toList();
    }

    public boolean changed() {
        return changes.stream().anyMatch(change -> change.kind() != ChangeKind.UNCHANGED);
    }

    public List<Change> effectiveChanges() {
        return changes.stream().filter(change -> change.kind() != ChangeKind.UNCHANGED).toList();
    }

    public enum ChangeKind {
        ADDED,
        MODIFIED,
        DELETED,
        MOVED,
        UNCHANGED
    }

    public record Change(
            ChangeKind kind,
            Optional<SourceInventory.Entry> before,
            Optional<SourceInventory.Entry> after) implements Comparable<Change> {

        public Change {
            Objects.requireNonNull(kind, "kind");
            before = Objects.requireNonNull(before, "before");
            after = Objects.requireNonNull(after, "after");
            validate(kind, before, after);
        }

        private static void validate(
                ChangeKind kind,
                Optional<SourceInventory.Entry> before,
                Optional<SourceInventory.Entry> after) {
            switch (kind) {
                case ADDED -> require(!before.isPresent() && after.isPresent(), "ADDED requires after only");
                case DELETED -> require(before.isPresent() && !after.isPresent(), "DELETED requires before only");
                case MODIFIED -> {
                    require(before.isPresent() && after.isPresent(), "MODIFIED requires before and after");
                    require(before.orElseThrow().path().equals(after.orElseThrow().path()), "MODIFIED path must be stable");
                    require(!before.orElseThrow().sameContentAs(after.orElseThrow()), "MODIFIED content must differ");
                }
                case MOVED -> {
                    require(before.isPresent() && after.isPresent(), "MOVED requires before and after");
                    require(!before.orElseThrow().path().equals(after.orElseThrow().path()), "MOVED path must change");
                    require(before.orElseThrow().sameContentAs(after.orElseThrow()), "MOVED content must be identical");
                }
                case UNCHANGED -> {
                    require(before.isPresent() && after.isPresent(), "UNCHANGED requires before and after");
                    require(before.orElseThrow().equals(after.orElseThrow()), "UNCHANGED entries must be identical");
                }
            }
        }

        private static void require(boolean condition, String message) {
            if (!condition) {
                throw new IllegalArgumentException(message);
            }
        }

        private String sortKey() {
            return before.map(entry -> entry.path().toString()).orElse("")
                    + "->"
                    + after.map(entry -> entry.path().toString()).orElse("")
                    + "#"
                    + kind.name();
        }

        @Override
        public int compareTo(Change other) {
            return sortKey().compareTo(other.sortKey());
        }
    }
}
