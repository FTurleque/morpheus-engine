package com.morpheus.store.memory;

import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reference in-memory adapter for snapshot-scoped external references. */
public final class MemoryExternalReferenceStore implements ExternalReferenceStore {
    private final SpecificationKnowledgeStore snapshots;
    private final Map<KnowledgeSnapshotId, Map<ExternalReferenceId, ExternalReference>> references = new HashMap<>();

    public MemoryExternalReferenceStore(SpecificationKnowledgeStore snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }

    @Override
    public synchronized void putReference(KnowledgeSnapshotId snapshotId, ExternalReference reference) {
        requireSnapshot(snapshotId);
        Objects.requireNonNull(reference, "reference");
        Map<ExternalReferenceId, ExternalReference> byId =
                references.computeIfAbsent(snapshotId, ignored -> new HashMap<>());
        ExternalReference existing = byId.get(reference.id());
        if (existing != null && !existing.equals(reference)) {
            throw new KnowledgeStoreException("external reference identity collision in snapshot: " + reference.id());
        }
        byId.putIfAbsent(reference.id(), reference);
    }

    @Override
    public synchronized Optional<ExternalReference> findReference(
            KnowledgeSnapshotId snapshotId,
            ExternalReferenceId referenceId) {
        requireSnapshot(snapshotId);
        Objects.requireNonNull(referenceId, "referenceId");
        return Optional.ofNullable(references.getOrDefault(snapshotId, Map.of()).get(referenceId));
    }

    @Override
    public synchronized List<ExternalReference> findByOwner(
            KnowledgeSnapshotId snapshotId,
            DomainIdentity ownerId) {
        requireSnapshot(snapshotId);
        Objects.requireNonNull(ownerId, "ownerId");
        return references.getOrDefault(snapshotId, Map.of()).values().stream()
                .filter(reference -> reference.ownerId().equals(ownerId))
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
    }

    private void requireSnapshot(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        if (snapshots.findSnapshot(snapshotId).isEmpty()) {
            throw new KnowledgeStoreException("snapshot not found for external reference: " + snapshotId);
        }
    }
}
