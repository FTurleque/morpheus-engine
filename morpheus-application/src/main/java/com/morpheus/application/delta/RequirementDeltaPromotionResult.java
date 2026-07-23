package com.morpheus.application.delta;

import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.version.SpecificationVersionId;

import java.util.Objects;

/** Receipt proving that PROMOTE completed independently from ACTIVATE. */
public record RequirementDeltaPromotionResult(
        KnowledgeSnapshotMetadata readySnapshot,
        SpecificationVersionId specificationVersionId,
        EvidenceId applicationEvidenceId,
        RequirementPromotionEvidence promotionEvidence) {

    public RequirementDeltaPromotionResult {
        Objects.requireNonNull(readySnapshot, "readySnapshot");
        Objects.requireNonNull(specificationVersionId, "specificationVersionId");
        Objects.requireNonNull(applicationEvidenceId, "applicationEvidenceId");
        Objects.requireNonNull(promotionEvidence, "promotionEvidence");
    }
}
