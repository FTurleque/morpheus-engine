package com.morpheus.store.memory;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reference in-memory adapter for snapshot-owned non-Requirement business content. */
public final class MemorySnapshotBusinessContentStore implements SnapshotBusinessContentStore {
    private final SpecificationKnowledgeStore snapshotStore;
    private final VersionedRequirementStore versionStore;
    private final Map<KnowledgeSnapshotId, SnapshotBusinessContent> contents = new HashMap<>();

    public MemorySnapshotBusinessContentStore(
            SpecificationKnowledgeStore snapshotStore,
            VersionedRequirementStore versionStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.versionStore = Objects.requireNonNull(versionStore, "versionStore");
    }

    @Override
    public synchronized void putSnapshotContent(SnapshotBusinessContent content) {
        Objects.requireNonNull(content, "content");
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(content.snapshotId())
                .orElseThrow(() -> new KnowledgeStoreException("snapshot not found: " + content.snapshotId()));
        SnapshotSpecificationVersionBinding binding = versionStore.findSnapshotVersion(content.snapshotId())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "snapshot has no specification version binding: " + content.snapshotId()));
        if (!binding.specificationVersionId().equals(content.specificationVersionId())) {
            throw new KnowledgeStoreException("business content does not match snapshot specification version");
        }
        validateProjectOwnership(content, snapshot.projectId());

        SnapshotBusinessContent existing = contents.get(content.snapshotId());
        if (existing != null) {
            if (!existing.equals(content)) {
                throw new KnowledgeStoreException("snapshot business content collision: " + content.snapshotId());
            }
            return;
        }
        contents.put(content.snapshotId(), content);
    }

    @Override
    public synchronized Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        return Optional.ofNullable(contents.get(snapshotId));
    }

    private void validateProjectOwnership(SnapshotBusinessContent content, ProjectSpecificationId projectId) {
        content.specifications().forEach(specification -> {
            if (!specification.projectId().equals(projectId)) {
                throw new KnowledgeStoreException("specification belongs to another project: " + specification.id());
            }
        });
        content.changes().forEach(change -> {
            if (!change.projectId().equals(projectId)) {
                throw new KnowledgeStoreException("change belongs to another project: " + change.id());
            }
        });
    }
}
