package com.morpheus.application.sync;

import com.morpheus.application.store.SyncStateStore;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Conservative M7 coordinator: plan first, execute externally, then explicitly complete or fail. */
public final class IncrementalSyncService {
    private final SyncStateStore store;
    private final SourceInventoryDiffer differ;
    private final WatchSignalPolicy watchPolicy;

    public IncrementalSyncService(SyncStateStore store) {
        this(store, new SourceInventoryDiffer(), new WatchSignalPolicy());
    }

    public IncrementalSyncService(
            SyncStateStore store,
            SourceInventoryDiffer differ,
            WatchSignalPolicy watchPolicy) {
        this.store = Objects.requireNonNull(store, "store");
        this.differ = Objects.requireNonNull(differ, "differ");
        this.watchPolicy = Objects.requireNonNull(watchPolicy, "watchPolicy");
    }

    public SyncPlan prepare(
            SourceInventoryScanResult scan,
            SyncPlan.Trigger trigger,
            Instant attemptedAt) {
        Objects.requireNonNull(scan, "scan");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(attemptedAt, "attemptedAt");

        ProjectSpecificationId projectId = scan.projectId();
        Optional<SourceInventory> previous = store.findCurrentInventory(projectId);
        Optional<ProjectSyncState> state = store.findSyncState(projectId);
        Optional<SourceInventory> current = scan.inventory();
        Optional<SourceInventoryDiff> diff = previous.isPresent() && current.isPresent()
                ? Optional.of(differ.diff(previous.orElseThrow(), current.orElseThrow()))
                : Optional.empty();

        Optional<SyncPlan.FullRebuildReason> reason = decideFullRebuildReason(
                scan, trigger, previous, current, diff, state);
        SyncPlan.SyncMode mode = reason.isPresent()
                ? SyncPlan.SyncMode.FULL_REBUILD
                : SyncPlan.SyncMode.INCREMENTAL;

        SyncPlan.InvalidationSet invalidation = invalidation(previous, current, diff, mode);
        List<SyncPlan.ArchiveAction> archives = archiveActions(diff);
        SyncPlan plan = new SyncPlan(
                projectId,
                attemptedAt,
                mode,
                reason,
                trigger,
                previous,
                current,
                diff,
                invalidation,
                archives);

        store.recordAttempt(projectId, attemptedAt, reason);
        return plan;
    }

    public void complete(SyncPlan plan, Instant completedAt) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(completedAt, "completedAt");
        if (completedAt.isBefore(plan.attemptedAt())) {
            throw new IllegalArgumentException("completedAt must not be before attemptedAt");
        }
        SourceInventory current = plan.currentInventory()
                .orElseThrow(() -> new IllegalArgumentException("cannot complete a plan without a complete current inventory"));

        Optional<Instant> previousObservedChange = store.findSyncState(plan.projectId())
                .flatMap(ProjectSyncState::lastObservedChangeAt);
        Optional<Instant> observedChangeAt = plan.hasSourceChanges()
                ? Optional.of(completedAt)
                : previousObservedChange;
        Optional<String> archivedRevision = plan.previousInventory().flatMap(SourceInventory::sourceRevision);

        List<SourceArchiveRecord> archives = plan.archiveActions().stream()
                .map(action -> new SourceArchiveRecord(
                        plan.projectId(),
                        action.source(),
                        completedAt,
                        action.reason(),
                        action.movedTo(),
                        archivedRevision))
                .toList();

        try {
            commitSuccessfulSync(plan, current, completedAt, observedChangeAt, archives);
            return;
        } catch (RuntimeException primary) {
            if (successfulCommitVisible(plan, current)) {
                return;
            }

            // The SQLite implementation is idempotent (UPSERT + inventory replacement + INSERT OR IGNORE archives).
            // A single bounded retry closes the gap where snapshot publication succeeded but sync-state persistence
            // encountered a transient failure. Never loop indefinitely on a persistent storage problem.
            try {
                commitSuccessfulSync(plan, current, completedAt, observedChangeAt, archives);
                return;
            } catch (RuntimeException retryFailure) {
                if (successfulCommitVisible(plan, current)) {
                    return;
                }
                if (primary != retryFailure) primary.addSuppressed(retryFailure);
            }

            if (publicationExpected(plan)) {
                // Publication is the authoritative business mutation. If its follow-up inventory commit cannot be
                // persisted after a bounded retry, keep the operation truthful by marking the baseline inconsistent
                // rather than falsely recording EXECUTION_FAILED after a new ACTIVE snapshot may already exist.
                try {
                    store.recordAttempt(
                            plan.projectId(),
                            plan.attemptedAt(),
                            Optional.of(SyncPlan.FullRebuildReason.BASELINE_INCONSISTENT));
                } catch (RuntimeException markerFailure) {
                    if (baselineInconsistentVisible(plan)) {
                        throw inconsistentOutcome(primary);
                    }
                    if (primary != markerFailure) primary.addSuppressed(markerFailure);
                    throw primary;
                }
                throw inconsistentOutcome(primary);
            }
            throw primary;
        }
    }

    public void fail(SyncPlan plan, Instant failedAt) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(failedAt, "failedAt");
        if (failedAt.isBefore(plan.attemptedAt())) {
            throw new IllegalArgumentException("failedAt must not be before attemptedAt");
        }
        SourceInventory current = plan.currentInventory().orElse(null);
        if (current != null && successfulCommitVisible(plan, current)) {
            return;
        }
        if (baselineInconsistentVisible(plan)) {
            return;
        }
        SyncPlan.FullRebuildReason failureReason = plan.fullRebuildReason()
                .filter(reason -> reason == SyncPlan.FullRebuildReason.SCAN_INCOMPLETE)
                .orElse(SyncPlan.FullRebuildReason.EXECUTION_FAILED);
        store.recordAttempt(
                plan.projectId(),
                plan.attemptedAt(),
                Optional.of(failureReason));
    }

    private void commitSuccessfulSync(
            SyncPlan plan,
            SourceInventory current,
            Instant completedAt,
            Optional<Instant> observedChangeAt,
            List<SourceArchiveRecord> archives) {
        store.commitSuccessfulSync(
                current,
                plan.mode(),
                plan.attemptedAt(),
                completedAt,
                observedChangeAt,
                archives);
    }

    private boolean successfulCommitVisible(SyncPlan plan, SourceInventory expectedInventory) {
        try {
            Optional<ProjectSyncState> state = store.findSyncState(plan.projectId());
            Optional<SourceInventory> inventory = store.findCurrentInventory(plan.projectId());
            if (state.isEmpty() || inventory.isEmpty()) return false;
            ProjectSyncState persisted = state.orElseThrow();
            return persisted.lastSuccessfulSyncAt()
                    .filter(value -> !value.isBefore(plan.attemptedAt()))
                    .isPresent()
                    && persisted.lastSuccessfulMode().equals(Optional.of(plan.mode()))
                    && persisted.pendingFullRebuildReason().isEmpty()
                    && persisted.sourceRevision().equals(expectedInventory.sourceRevision())
                    && persisted.currentSourceCount() == expectedInventory.entries().size()
                    && inventory.orElseThrow().equals(expectedInventory);
        } catch (RuntimeException recoveryReadFailure) {
            return false;
        }
    }

    private boolean baselineInconsistentVisible(SyncPlan plan) {
        try {
            return store.findSyncState(plan.projectId())
                    .filter(state -> state.lastAttemptAt()
                            .filter(value -> !value.isBefore(plan.attemptedAt()))
                            .isPresent())
                    .flatMap(ProjectSyncState::pendingFullRebuildReason)
                    .filter(reason -> reason == SyncPlan.FullRebuildReason.BASELINE_INCONSISTENT)
                    .isPresent();
        } catch (RuntimeException recoveryReadFailure) {
            return false;
        }
    }

    private SyncBaselineInconsistentException inconsistentOutcome(RuntimeException cause) {
        return new SyncBaselineInconsistentException(
                "snapshot publication may already have succeeded but the sync baseline is inconsistent; "
                        + "inspect current published and sync state before retrying",
                cause);
    }

    private boolean publicationExpected(SyncPlan plan) {
        return plan.mode() == SyncPlan.SyncMode.FULL_REBUILD || plan.hasSourceChanges();
    }

    private Optional<SyncPlan.FullRebuildReason> decideFullRebuildReason(
            SourceInventoryScanResult scan,
            SyncPlan.Trigger trigger,
            Optional<SourceInventory> previous,
            Optional<SourceInventory> current,
            Optional<SourceInventoryDiff> diff,
            Optional<ProjectSyncState> state) {
        if (!scan.complete()) {
            return Optional.of(SyncPlan.FullRebuildReason.SCAN_INCOMPLETE);
        }
        if (trigger.forceFullRebuild()) {
            return Optional.of(SyncPlan.FullRebuildReason.FORCED);
        }
        if (trigger.kind() == SyncPlan.TriggerKind.WATCHER
                && watchPolicy.requiresFullRebuild(trigger.watchSignals())) {
            return Optional.of(SyncPlan.FullRebuildReason.WATCH_OVERFLOW);
        }
        if (!baselineConsistent(state, previous)) {
            return Optional.of(SyncPlan.FullRebuildReason.BASELINE_INCONSISTENT);
        }
        if (state.flatMap(ProjectSyncState::pendingFullRebuildReason).isPresent()) {
            return Optional.of(SyncPlan.FullRebuildReason.PREVIOUS_REBUILD_PENDING);
        }
        if (previous.isEmpty()) {
            return Optional.of(SyncPlan.FullRebuildReason.NO_BASELINE);
        }

        SourceInventory oldInventory = previous.orElseThrow();
        SourceInventory newInventory = current.orElseThrow();
        SourceInventoryDiff inventoryDiff = diff.orElseThrow();

        if (oldInventory.sourceRevision().isPresent() && newInventory.sourceRevision().isEmpty()) {
            return Optional.of(SyncPlan.FullRebuildReason.REVISION_SIGNAL_LOST);
        }
        if (oldInventory.sourceRevision().isPresent()
                && oldInventory.sourceRevision().equals(newInventory.sourceRevision())
                && inventoryDiff.changed()) {
            return Optional.of(SyncPlan.FullRebuildReason.REVISION_INCONSISTENCY);
        }
        if (inventoryDiff.ambiguousMoves()) {
            return Optional.of(SyncPlan.FullRebuildReason.AMBIGUOUS_MOVE);
        }
        return Optional.empty();
    }

    private boolean baselineConsistent(
            Optional<ProjectSyncState> state,
            Optional<SourceInventory> inventory) {
        boolean successfulState = state.flatMap(ProjectSyncState::lastSuccessfulSyncAt).isPresent();
        if (successfulState != inventory.isPresent()) {
            return false;
        }
        if (inventory.isEmpty()) {
            return true;
        }
        ProjectSyncState syncState = state.orElseThrow();
        SourceInventory baseline = inventory.orElseThrow();
        return syncState.currentSourceCount() == baseline.entries().size()
                && syncState.sourceRevision().equals(baseline.sourceRevision());
    }

    private SyncPlan.InvalidationSet invalidation(
            Optional<SourceInventory> previous,
            Optional<SourceInventory> current,
            Optional<SourceInventoryDiff> diff,
            SyncPlan.SyncMode mode) {
        if (mode == SyncPlan.SyncMode.FULL_REBUILD) {
            List<SourcePath> invalidated = previous.stream()
                    .flatMap(inventory -> inventory.entries().stream())
                    .map(SourceInventory.Entry::path)
                    .distinct()
                    .sorted()
                    .toList();
            List<SourcePath> refresh = current.stream()
                    .flatMap(inventory -> inventory.entries().stream())
                    .map(SourceInventory.Entry::path)
                    .distinct()
                    .sorted()
                    .toList();
            return new SyncPlan.InvalidationSet(invalidated, refresh);
        }

        List<SourcePath> invalidated = new ArrayList<>();
        List<SourcePath> refresh = new ArrayList<>();
        diff.orElseThrow().effectiveChanges().forEach(change -> {
            switch (change.kind()) {
                case ADDED -> refresh.add(change.after().orElseThrow().path());
                case MODIFIED -> {
                    SourcePath path = change.after().orElseThrow().path();
                    invalidated.add(path);
                    refresh.add(path);
                }
                case DELETED -> invalidated.add(change.before().orElseThrow().path());
                case MOVED -> {
                    invalidated.add(change.before().orElseThrow().path());
                    refresh.add(change.after().orElseThrow().path());
                }
                case UNCHANGED -> {
                    // No invalidation.
                }
            }
        });
        return new SyncPlan.InvalidationSet(invalidated, refresh);
    }

    private List<SyncPlan.ArchiveAction> archiveActions(Optional<SourceInventoryDiff> diff) {
        if (diff.isEmpty()) {
            return List.of();
        }
        return diff.orElseThrow().effectiveChanges().stream()
                .flatMap(change -> switch (change.kind()) {
                    case DELETED -> java.util.stream.Stream.of(new SyncPlan.ArchiveAction(
                            change.before().orElseThrow(),
                            SourceArchiveRecord.ArchiveReason.DELETED,
                            Optional.empty()));
                    case MOVED -> java.util.stream.Stream.of(new SyncPlan.ArchiveAction(
                            change.before().orElseThrow(),
                            SourceArchiveRecord.ArchiveReason.MOVED,
                            Optional.of(change.after().orElseThrow().path())));
                    default -> java.util.stream.Stream.empty();
                })
                .sorted()
                .toList();
    }
}
