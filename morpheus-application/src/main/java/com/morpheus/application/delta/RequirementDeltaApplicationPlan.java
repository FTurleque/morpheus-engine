package com.morpheus.application.delta;

import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Fully explicit inputs required to construct one candidate requirement baseline. */
public record RequirementDeltaApplicationPlan(
        ProjectSpecificationId projectId,
        SpecificationVersion specificationVersion,
        KnowledgeSnapshotMetadata candidateSnapshot,
        List<RequirementDelta> deltas,
        Map<String, SpecificationId> specificationIdsByKey,
        Map<DomainIdentity, EntityVersionId> entityVersionIds,
        EvidenceId applicationEvidenceId) {

    public RequirementDeltaApplicationPlan {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(specificationVersion, "specificationVersion");
        Objects.requireNonNull(candidateSnapshot, "candidateSnapshot");
        deltas = List.copyOf(Objects.requireNonNull(deltas, "deltas"));
        specificationIdsByKey = Map.copyOf(Objects.requireNonNull(specificationIdsByKey, "specificationIdsByKey"));
        entityVersionIds = Map.copyOf(Objects.requireNonNull(entityVersionIds, "entityVersionIds"));
        Objects.requireNonNull(applicationEvidenceId, "applicationEvidenceId");

        specificationIdsByKey.keySet().forEach(key -> {
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalArgumentException("specificationIdsByKey must not contain blank keys");
            }
        });
    }
}
