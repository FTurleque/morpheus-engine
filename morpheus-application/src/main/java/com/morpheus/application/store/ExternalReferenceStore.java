package com.morpheus.application.store;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;

import java.util.List;
import java.util.Optional;

/** Technology-neutral snapshot-scoped persistence port for external references. */
public interface ExternalReferenceStore {
    void putReference(KnowledgeSnapshotId snapshotId, ExternalReference reference);

    Optional<ExternalReference> findReference(KnowledgeSnapshotId snapshotId, ExternalReferenceId referenceId);

    List<ExternalReference> findByOwner(KnowledgeSnapshotId snapshotId, DomainIdentity ownerId);
}
