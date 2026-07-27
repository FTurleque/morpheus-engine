package com.morpheus.application.store;

import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;

import java.util.List;
import java.util.Optional;

/** Technology-neutral storage port for projects and knowledge snapshot lifecycle metadata. */
public interface SpecificationKnowledgeStore {
    void putProject(ProjectStoreEntry project);

    Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId);

    Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator);

    List<ProjectStoreEntry> listProjects();

    void putSnapshot(KnowledgeSnapshotMetadata snapshot);

    Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId);

    /**
     * Lists all technical and published snapshots for one project. Adapters used for recovery must override this
     * method and return a deterministic order.
     */
    default List<KnowledgeSnapshotMetadata> listSnapshots(ProjectSpecificationId projectId) {
        throw new UnsupportedOperationException("snapshot listing is not supported by this store adapter");
    }

    Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId);

    KnowledgeSnapshotMetadata transitionSnapshotState(
            KnowledgeSnapshotId snapshotId,
            KnowledgeSnapshotState expectedState,
            KnowledgeSnapshotState targetState);

    KnowledgeSnapshotMetadata activateSnapshot(
            KnowledgeSnapshotId snapshotId,
            Optional<KnowledgeSnapshotId> expectedActiveSnapshotId);
}
