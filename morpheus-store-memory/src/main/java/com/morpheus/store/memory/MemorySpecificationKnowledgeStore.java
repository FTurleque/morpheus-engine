package com.morpheus.store.memory;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotConflictException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Reference in-memory implementation of the M1 storage contract. */
public final class MemorySpecificationKnowledgeStore implements SpecificationKnowledgeStore {
    private final Map<ProjectSpecificationId, ProjectStoreEntry> projects = new HashMap<>();
    private final Map<KnowledgeSnapshotId, KnowledgeSnapshotMetadata> snapshots = new HashMap<>();

    @Override
    public synchronized void putProject(ProjectStoreEntry project) {
        ProjectStoreEntry existing = projects.get(project.id());
        if (existing == null) {
            projects.put(project.id(), project);
            return;
        }
        if (!existing.equals(project)) {
            throw new KnowledgeStoreException("project identity collision: " + project.id());
        }
    }

    @Override
    public synchronized Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId) {
        return Optional.ofNullable(projects.get(projectId));
    }

    @Override
    public synchronized void putSnapshot(KnowledgeSnapshotMetadata snapshot) {
        if (snapshot.state() == KnowledgeSnapshotState.ACTIVE
                || snapshot.state() == KnowledgeSnapshotState.RETIRED) {
            throw new KnowledgeStoreException("ACTIVE/RETIRED snapshots must be produced by activation lifecycle");
        }

        KnowledgeSnapshotMetadata existing = snapshots.get(snapshot.id());
        if (existing != null) {
            if (!existing.sameDefinitionAs(snapshot)) {
                throw new KnowledgeStoreException("snapshot identity collision: " + snapshot.id());
            }
            return;
        }

        validateSnapshotReferences(snapshot);
        snapshots.put(snapshot.id(), snapshot);
    }

    @Override
    public synchronized Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId) {
        return Optional.ofNullable(snapshots.get(snapshotId));
    }

    @Override
    public synchronized Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId) {
        return snapshots.values().stream()
                .filter(snapshot -> snapshot.projectId().equals(projectId))
                .filter(snapshot -> snapshot.state() == KnowledgeSnapshotState.ACTIVE)
                .findFirst();
    }

    @Override
    public synchronized KnowledgeSnapshotMetadata activateSnapshot(
            KnowledgeSnapshotId snapshotId,
            Optional<KnowledgeSnapshotId> expectedActiveSnapshotId) {
        KnowledgeSnapshotMetadata target = snapshots.get(snapshotId);
        if (target == null) {
            throw new KnowledgeStoreException("snapshot not found: " + snapshotId);
        }

        Optional<KnowledgeSnapshotMetadata> active = activeSnapshot(target.projectId());
        if (target.state() == KnowledgeSnapshotState.ACTIVE) {
            if (active.map(KnowledgeSnapshotMetadata::id).equals(Optional.of(snapshotId))) {
                return target;
            }
            throw new SnapshotConflictException("active snapshot state is inconsistent for project " + target.projectId());
        }

        if (target.state() != KnowledgeSnapshotState.READY) {
            throw new SnapshotConflictException("only READY snapshots can be activated: " + snapshotId);
        }

        if (!target.predecessorId().equals(expectedActiveSnapshotId)) {
            throw new SnapshotConflictException("snapshot predecessor does not match expected active snapshot");
        }

        Optional<KnowledgeSnapshotId> currentActiveId = active.map(KnowledgeSnapshotMetadata::id);
        if (!currentActiveId.equals(expectedActiveSnapshotId)) {
            throw new SnapshotConflictException("active snapshot changed before activation");
        }

        active.ifPresent(current -> snapshots.put(
                current.id(), current.withState(KnowledgeSnapshotState.RETIRED)));

        KnowledgeSnapshotMetadata activated = target.withState(KnowledgeSnapshotState.ACTIVE);
        snapshots.put(snapshotId, activated);
        return activated;
    }

    private void validateSnapshotReferences(KnowledgeSnapshotMetadata snapshot) {
        if (!projects.containsKey(snapshot.projectId())) {
            throw new KnowledgeStoreException("project not found: " + snapshot.projectId());
        }

        snapshot.predecessorId().ifPresent(predecessorId -> {
            KnowledgeSnapshotMetadata predecessor = snapshots.get(predecessorId);
            if (predecessor == null) {
                throw new KnowledgeStoreException("snapshot predecessor not found: " + predecessorId);
            }
            if (!predecessor.projectId().equals(snapshot.projectId())) {
                throw new KnowledgeStoreException("snapshot predecessor belongs to another project");
            }
        });
    }
}
