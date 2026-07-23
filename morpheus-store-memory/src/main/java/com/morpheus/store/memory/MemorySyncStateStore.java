package com.morpheus.store.memory;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.SyncStateStore;
import com.morpheus.application.sync.ProjectSyncState;
import com.morpheus.application.sync.SourceArchiveRecord;
import com.morpheus.application.sync.SourceInventory;
import com.morpheus.application.sync.SyncPlan;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reference in-memory adapter for M7 synchronization state. */
public final class MemorySyncStateStore implements SyncStateStore {
    private final SpecificationKnowledgeStore projectStore;
    private final Map<ProjectSpecificationId, ProjectSyncState> states = new HashMap<>();
    private final Map<ProjectSpecificationId, SourceInventory> inventories = new HashMap<>();
    private final Map<ProjectSpecificationId, List<SourceArchiveRecord>> archives = new HashMap<>();

    public MemorySyncStateStore(SpecificationKnowledgeStore projectStore) {
        this.projectStore = Objects.requireNonNull(projectStore, "projectStore");
    }

    @Override
    public synchronized Optional<ProjectSyncState> findSyncState(ProjectSpecificationId projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return Optional.ofNullable(states.get(projectId));
    }

    @Override
    public synchronized Optional<SourceInventory> findCurrentInventory(ProjectSpecificationId projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return Optional.ofNullable(inventories.get(projectId));
    }

    @Override
    public synchronized List<SourceArchiveRecord> listArchives(ProjectSpecificationId projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return archives.getOrDefault(projectId, List.of()).stream().sorted().toList();
    }

    @Override
    public synchronized void recordAttempt(
            ProjectSpecificationId projectId,
            Instant attemptedAt,
            Optional<SyncPlan.FullRebuildReason> pendingFullRebuildReason) {
        requireProject(projectId);
        Objects.requireNonNull(attemptedAt, "attemptedAt");
        Objects.requireNonNull(pendingFullRebuildReason, "pendingFullRebuildReason");
        ProjectSyncState previous = states.getOrDefault(projectId, ProjectSyncState.empty(projectId));
        states.put(projectId, new ProjectSyncState(
                projectId,
                Optional.of(attemptedAt),
                previous.lastSuccessfulSyncAt(),
                previous.lastObservedChangeAt(),
                previous.sourceRevision(),
                previous.lastSuccessfulMode(),
                pendingFullRebuildReason,
                previous.currentSourceCount()));
    }

    @Override
    public synchronized void commitSuccessfulSync(
            SourceInventory inventory,
            SyncPlan.SyncMode mode,
            Instant attemptedAt,
            Instant completedAt,
            Optional<Instant> lastObservedChangeAt,
            List<SourceArchiveRecord> newArchives) {
        Objects.requireNonNull(inventory, "inventory");
        requireProject(inventory.projectId());
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(attemptedAt, "attemptedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        Objects.requireNonNull(lastObservedChangeAt, "lastObservedChangeAt");
        Objects.requireNonNull(newArchives, "newArchives");
        if (completedAt.isBefore(attemptedAt)) {
            throw new IllegalArgumentException("completedAt must not be before attemptedAt");
        }
        lastObservedChangeAt.ifPresent(value -> {
            if (value.isAfter(completedAt)) {
                throw new IllegalArgumentException("lastObservedChangeAt must not be after completedAt");
            }
        });
        newArchives.forEach(record -> {
            if (!record.projectId().equals(inventory.projectId())) {
                throw new IllegalArgumentException("archive belongs to another project");
            }
        });

        inventories.put(inventory.projectId(), inventory);
        states.put(inventory.projectId(), new ProjectSyncState(
                inventory.projectId(),
                Optional.of(attemptedAt),
                Optional.of(completedAt),
                lastObservedChangeAt,
                inventory.sourceRevision(),
                Optional.of(mode),
                Optional.empty(),
                inventory.entries().size()));

        List<SourceArchiveRecord> projectArchives = archives.computeIfAbsent(
                inventory.projectId(), ignored -> new ArrayList<>());
        for (SourceArchiveRecord record : newArchives) {
            if (!projectArchives.contains(record)) {
                projectArchives.add(record);
            }
        }
        projectArchives.sort(SourceArchiveRecord::compareTo);
    }

    private void requireProject(ProjectSpecificationId projectId) {
        if (projectStore.findProject(projectId).isEmpty()) {
            throw new KnowledgeStoreException("project not found for synchronization state: " + projectId);
        }
    }
}
