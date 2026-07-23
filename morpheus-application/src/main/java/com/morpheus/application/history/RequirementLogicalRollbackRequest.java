package com.morpheus.application.history;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementDeltaId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.specification.SpecificationId;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Explicit inputs required to derive a logical rollback as RequirementDelta values. */
public record RequirementLogicalRollbackRequest(
        ProjectSpecificationId projectId,
        KnowledgeSnapshotId targetSnapshotId,
        ChangeId changeId,
        Map<DomainIdentity, RequirementDeltaId> deltaIdsByIdentity,
        Map<SpecificationId, String> specificationKeysById) {

    public RequirementLogicalRollbackRequest {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(targetSnapshotId, "targetSnapshotId");
        Objects.requireNonNull(changeId, "changeId");
        deltaIdsByIdentity = Map.copyOf(Objects.requireNonNull(deltaIdsByIdentity, "deltaIdsByIdentity"));
        specificationKeysById = Map.copyOf(Objects.requireNonNull(specificationKeysById, "specificationKeysById"));

        Set<RequirementDeltaId> uniqueDeltaIds = new HashSet<>(deltaIdsByIdentity.values());
        if (uniqueDeltaIds.size() != deltaIdsByIdentity.size()) {
            throw new IllegalArgumentException("rollback RequirementDeltaId values must be unique");
        }

        Set<String> uniqueKeys = new HashSet<>();
        for (Map.Entry<SpecificationId, String> entry : specificationKeysById.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "specificationId");
            String key = requireNonBlank(entry.getValue(), "specificationKey");
            if (!uniqueKeys.add(key)) {
                throw new IllegalArgumentException("rollback specification keys must map to exactly one SpecificationId: " + key);
            }
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}