package com.morpheus.application.query;

import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.application.traceability.TraceRequirementResult;
import com.morpheus.application.traceability.TraceRequirementService;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.traceability.TraceabilityRelationType;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** M5 query facade exposing the M4 trace(requirement) contract without changing its semantics. */
public final class TraceRequirementQueryService {
    private final TraceRequirementService delegate;

    public TraceRequirementQueryService(
            SpecificationKnowledgeStore snapshotStore,
            VersionedRequirementStore requirementStore,
            TraceabilityStore traceabilityStore,
            ExternalReferenceStore externalReferenceStore) {
        this.delegate = new TraceRequirementService(
                Objects.requireNonNull(snapshotStore, "snapshotStore"),
                Objects.requireNonNull(requirementStore, "requirementStore"),
                Objects.requireNonNull(traceabilityStore, "traceabilityStore"),
                Objects.requireNonNull(externalReferenceStore, "externalReferenceStore"));
    }

    public Optional<TraceRequirementResult> active(
            ProjectSpecificationId projectId,
            RequirementId requirementId,
            int maxDepth,
            Set<TraceabilityRelationType> relationTypes) {
        return delegate.traceActive(projectId, requirementId, maxDepth, relationTypes);
    }

    public Optional<TraceRequirementResult> snapshot(
            KnowledgeSnapshotId snapshotId,
            RequirementId requirementId,
            int maxDepth,
            Set<TraceabilityRelationType> relationTypes) {
        return delegate.traceSnapshot(snapshotId, requirementId, maxDepth, relationTypes);
    }
}
