package com.morpheus.application.delta;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.requirement.RequirementDeltaId;
import com.morpheus.domain.requirement.RequirementDeltaKind;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.version.EntityVersionId;

import java.util.Objects;
import java.util.Optional;

/** Evidence-bearing receipt for one normalized delta applied to a candidate projection. */
public record AppliedRequirementDelta(
        RequirementDeltaId deltaId,
        ChangeId changeId,
        RequirementDeltaKind kind,
        RequirementId requirementId,
        EvidenceId sourceEvidenceId,
        Optional<EntityVersionId> resultingEntityVersionId) {

    public AppliedRequirementDelta {
        Objects.requireNonNull(deltaId, "deltaId");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(sourceEvidenceId, "sourceEvidenceId");
        resultingEntityVersionId = Objects.requireNonNull(resultingEntityVersionId, "resultingEntityVersionId");

        if (kind == RequirementDeltaKind.REMOVED && resultingEntityVersionId.isPresent()) {
            throw new IllegalArgumentException("REMOVED delta must not expose a resulting entity version");
        }
        if (kind != RequirementDeltaKind.REMOVED && resultingEntityVersionId.isEmpty()) {
            throw new IllegalArgumentException(kind + " delta must expose its resulting entity version");
        }
    }
}
