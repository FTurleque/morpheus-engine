package com.morpheus.application.store;

import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.Optional;

/**
 * Technology-neutral storage port.
 *
 * <p>M1 intentionally implements only the project/snapshot metadata subset required to prove
 * identity, idempotence and atomic activation. Entity/query capabilities are added by later phases.
 */
public interface SpecificationKnowledgeStore {
    void putProject(ProjectStoreEntry project);

    Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId);

    void putSnapshot(KnowledgeSnapshotMetadata snapshot);

    Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId);

    Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId);

    KnowledgeSnapshotMetadata activateSnapshot(
            KnowledgeSnapshotId snapshotId,
            Optional<KnowledgeSnapshotId> expectedActiveSnapshotId);
}
