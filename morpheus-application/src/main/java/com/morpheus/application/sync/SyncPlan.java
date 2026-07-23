package com.morpheus.application.sync;

import com.morpheus.domain.project.ProjectSpecificationId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Explainable synchronization plan derived from one complete source scan and the persisted baseline. */
public record SyncPlan(
        ProjectSpecificationId projectId,
        Instant attemptedAt,
        SyncMode mode,
        Optional<FullRebuildReason> fullRebuildReason,
        Trigger trigger,
        Optional<SourceInventory> previousInventory,
        Optional<SourceInventory> currentInventory,
        Optional<SourceInventoryDiff> diff,
        InvalidationSet invalidation,
        List<ArchiveAction> archiveActions) {

    public SyncPlan {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(attemptedAt, "attemptedAt");
        Objects.requireNonNull(mode, "mode");
        fullRebuildReason = Objects.requireNonNull(fullRebuildReason, "fullRebuildReason");
        Objects.requireNonNull(trigger, "trigger");
        previousInventory = Objects.requireNonNull(previousInventory, "previousInventory");
        currentInventory = Objects.requireNonNull(currentInventory, "currentInventory");
        diff = Objects.requireNonNull(diff, "diff");
        Objects.requireNonNull(invalidation, "invalidation");
        archiveActions = Objects.requireNonNull(archiveActions, "archiveActions").stream()
                .peek(action -> Objects.requireNonNull(action, "archiveActions item"))
                .sorted()
                .toList();

        if ((mode == SyncMode.FULL_REBUILD) != fullRebuildReason.isPresent()) {
            throw new IllegalArgumentException("FULL_REBUILD requires a reason and INCREMENTAL must not have one");
        }
        currentInventory.ifPresent(inventory -> requireProject(projectId, inventory));
        previousInventory.ifPresent(inventory -> requireProject(projectId, inventory));
    }

    private static void requireProject(ProjectSpecificationId projectId, SourceInventory inventory) {
        if (!inventory.projectId().equals(projectId)) {
            throw new IllegalArgumentException("inventory belongs to another project");
        }
    }

    public boolean hasSourceChanges() {
        return diff.map(SourceInventoryDiff::changed).orElse(false);
    }

    public enum SyncMode {
        INCREMENTAL,
        FULL_REBUILD
    }

    public enum FullRebuildReason {
        NO_BASELINE,
        SCAN_INCOMPLETE,
        WATCH_OVERFLOW,
        AMBIGUOUS_MOVE,
        REVISION_INCONSISTENCY,
        REVISION_SIGNAL_LOST,
        PREVIOUS_REBUILD_PENDING,
        EXECUTION_FAILED,
        FORCED
    }

    public enum TriggerKind {
        MANUAL,
        WATCHER,
        STARTUP
    }

    public record Trigger(
            TriggerKind kind,
            List<SourceWatchSignal> watchSignals,
            boolean forceFullRebuild) {
        public Trigger {
            Objects.requireNonNull(kind, "kind");
            watchSignals = Objects.requireNonNull(watchSignals, "watchSignals").stream()
                    .peek(signal -> Objects.requireNonNull(signal, "watchSignals item"))
                    .sorted()
                    .toList();
            if (kind != TriggerKind.WATCHER && !watchSignals.isEmpty()) {
                throw new IllegalArgumentException("watch signals require WATCHER trigger");
            }
        }

        public static Trigger manual() {
            return new Trigger(TriggerKind.MANUAL, List.of(), false);
        }

        public static Trigger startup() {
            return new Trigger(TriggerKind.STARTUP, List.of(), false);
        }

        public static Trigger watcher(List<SourceWatchSignal> signals) {
            return new Trigger(TriggerKind.WATCHER, signals, false);
        }

        public Trigger forced() {
            return new Trigger(kind, watchSignals, true);
        }
    }

    public record InvalidationSet(
            List<SourcePath> invalidated,
            List<SourcePath> refreshRequired) {
        public InvalidationSet {
            invalidated = sortedDistinct(invalidated, "invalidated");
            refreshRequired = sortedDistinct(refreshRequired, "refreshRequired");
        }

        private static List<SourcePath> sortedDistinct(List<SourcePath> values, String name) {
            return Objects.requireNonNull(values, name).stream()
                    .peek(value -> Objects.requireNonNull(value, name + " item"))
                    .distinct()
                    .sorted()
                    .toList();
        }

        public static InvalidationSet empty() {
            return new InvalidationSet(List.of(), List.of());
        }
    }

    public record ArchiveAction(
            SourceInventory.Entry source,
            SourceArchiveRecord.ArchiveReason reason,
            Optional<SourcePath> movedTo) implements Comparable<ArchiveAction> {
        public ArchiveAction {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(reason, "reason");
            movedTo = Objects.requireNonNull(movedTo, "movedTo");
            if (reason == SourceArchiveRecord.ArchiveReason.MOVED && movedTo.isEmpty()) {
                throw new IllegalArgumentException("MOVED archive action requires movedTo");
            }
            if (reason == SourceArchiveRecord.ArchiveReason.DELETED && movedTo.isPresent()) {
                throw new IllegalArgumentException("DELETED archive action must not have movedTo");
            }
        }

        @Override
        public int compareTo(ArchiveAction other) {
            return source.path().compareTo(other.source.path());
        }
    }
}
