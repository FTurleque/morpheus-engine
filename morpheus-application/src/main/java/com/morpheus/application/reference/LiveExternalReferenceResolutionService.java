package com.morpheus.application.reference;

import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves persisted external references as live observations without rewriting a published snapshot.
 */
public final class LiveExternalReferenceResolutionService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final ExternalReferenceStore referenceStore;
    private final ExternalReferenceResolutionService resolutionService;

    public LiveExternalReferenceResolutionService(
            SpecificationKnowledgeStore snapshotStore,
            ExternalReferenceStore referenceStore,
            ExternalReferenceResolverRegistry resolverRegistry,
            Clock clock) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.referenceStore = Objects.requireNonNull(referenceStore, "referenceStore");
        this.resolutionService = new ExternalReferenceResolutionService(
                Objects.requireNonNull(resolverRegistry, "resolverRegistry"),
                Objects.requireNonNull(clock, "clock"));
    }

    public Optional<List<ExternalReference>> listActive(
            ProjectSpecificationId projectId,
            DomainIdentity ownerId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(ownerId, "ownerId");
        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> referenceStore.findByOwner(snapshot.id(), ownerId));
    }

    public Optional<LiveExternalReferenceResolutionResult> resolveActive(
            ProjectSpecificationId projectId,
            ExternalReferenceId referenceId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(referenceId, "referenceId");
        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> resolve(snapshot, referenceId));
    }

    public LiveExternalReferenceResolutionResult resolveSnapshot(
            KnowledgeSnapshotId snapshotId,
            ExternalReferenceId referenceId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(referenceId, "referenceId");
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("unknown knowledge snapshot: " + snapshotId));
        if (snapshot.state() != KnowledgeSnapshotState.ACTIVE
                && snapshot.state() != KnowledgeSnapshotState.RETIRED) {
            throw new KnowledgeStoreException(
                    "live external resolution requires an ACTIVE or RETIRED snapshot: "
                            + snapshot.id() + " is " + snapshot.state());
        }
        return resolve(snapshot, referenceId);
    }

    private LiveExternalReferenceResolutionResult resolve(
            KnowledgeSnapshotMetadata snapshot,
            ExternalReferenceId referenceId) {
        ExternalReference stored = referenceStore.findReference(snapshot.id(), referenceId)
                .orElseThrow(() -> new KnowledgeStoreException(
                        "external reference not found in snapshot " + snapshot.id() + ": " + referenceId));
        ExternalReference observed = resolutionService.resolve(stored);
        return new LiveExternalReferenceResolutionResult(snapshot, stored, observed);
    }
}
