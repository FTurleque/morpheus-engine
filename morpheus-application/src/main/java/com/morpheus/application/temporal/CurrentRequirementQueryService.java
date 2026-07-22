package com.morpheus.application.temporal;

import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.Objects;
import java.util.Optional;

/** Resolves CURRENT requirement content strictly through the project's ACTIVE knowledge snapshot. */
public final class CurrentRequirementQueryService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final VersionedRequirementStore requirementStore;

    public CurrentRequirementQueryService(
            SpecificationKnowledgeStore snapshotStore,
            VersionedRequirementStore requirementStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
    }

    public Optional<RequirementVersionRecord> current(
            ProjectSpecificationId projectId,
            DomainIdentity entityIdentity) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(entityIdentity, "entityIdentity");

        return snapshotStore.activeSnapshot(projectId)
                .flatMap(snapshot -> requirementStore.currentRequirement(snapshot.id(), entityIdentity));
    }
}
