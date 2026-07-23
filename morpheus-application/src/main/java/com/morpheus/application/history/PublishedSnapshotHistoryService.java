package com.morpheus.application.history;

import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reconstructs the published lineage of one project from RETIRED predecessors to the ACTIVE head. */
public final class PublishedSnapshotHistoryService {
    private final SpecificationKnowledgeStore snapshotStore;

    public PublishedSnapshotHistoryService(SpecificationKnowledgeStore snapshotStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
    }

    public List<KnowledgeSnapshotMetadata> lineage(ProjectSpecificationId projectId) {
        Objects.requireNonNull(projectId, "projectId");
        KnowledgeSnapshotMetadata active = snapshotStore.activeSnapshot(projectId).orElse(null);
        if (active == null) {
            return List.of();
        }
        if (active.state() != KnowledgeSnapshotState.ACTIVE) {
            throw new PublishedHistoryException("published lineage head must be ACTIVE: " + active.id());
        }

        List<KnowledgeSnapshotMetadata> newestFirst = new ArrayList<>();
        Set<Object> visited = new HashSet<>();
        KnowledgeSnapshotMetadata current = active;
        boolean head = true;

        while (true) {
            if (!current.projectId().equals(projectId)) {
                throw new PublishedHistoryException("published lineage crosses project boundary at snapshot " + current.id());
            }
            if (!visited.add(current.id())) {
                throw new PublishedHistoryException("cycle detected in published snapshot lineage at " + current.id());
            }
            if (head) {
                if (current.state() != KnowledgeSnapshotState.ACTIVE) {
                    throw new PublishedHistoryException("published lineage head must be ACTIVE: " + current.id());
                }
            } else if (current.state() != KnowledgeSnapshotState.RETIRED) {
                throw new PublishedHistoryException(
                        "published predecessor must be RETIRED but was " + current.state() + ": " + current.id());
            }

            newestFirst.add(current);
            if (current.predecessorId().isEmpty()) {
                break;
            }

            current = snapshotStore.findSnapshot(current.predecessorId().orElseThrow())
                    .orElseThrow(() -> new PublishedHistoryException(
                            "published predecessor not found: " + newestFirst.get(newestFirst.size() - 1).predecessorId().orElseThrow()));
            head = false;
        }

        Collections.reverse(newestFirst);
        return List.copyOf(newestFirst);
    }
}