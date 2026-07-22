package com.morpheus.application.store;

import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;

import java.util.List;
import java.util.Optional;

/**
 * Technology-neutral storage port.
 *
 * <p>The port owns project registration and the observable lifecycle of knowledge-snapshot metadata.
 * Business entity persistence is introduced separately once version/snapshot membership is explicit.</p>
 */
public interface SpecificationKnowledgeStore {
    void putProject(ProjectStoreEntry project);

    Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId);

    Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator);

    List<ProjectStoreEntry> listProjects();

    void putSnapshot(KnowledgeSnapshotMetadata snapshot);

    Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId);

    Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId);

    /**
     * Atomically moves one non-published snapshot from an expected technical state to another.
     * ACTIVE and RETIRED remain exclusively owned by {@link #activateSnapshot}.
     */
    KnowledgeSnapshotMetadata transitionSnapshotState(
            KnowledgeSnapshotId snapshotId,
            KnowledgeSnapshotState expectedState,
            KnowledgeSnapshotState targetState);

    KnowledgeSnapshotMetadata activateSnapshot(
            KnowledgeSnapshotId snapshotId,
            Optional<KnowledgeSnapshotId> expectedActiveSnapshotId);
}
