package com.morpheus.application.delta;

import com.morpheus.domain.evidence.EvidenceId;

import java.time.Instant;
import java.util.Objects;

/** Explicit evidence that a caller decided to promote one candidate projection to READY eligibility. */
public record RequirementPromotionEvidence(
        EvidenceId evidenceId,
        String rationale,
        Instant decidedAt) {

    public RequirementPromotionEvidence {
        Objects.requireNonNull(evidenceId, "evidenceId");
        Objects.requireNonNull(rationale, "rationale");
        rationale = rationale.trim();
        if (rationale.isEmpty()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
        Objects.requireNonNull(decidedAt, "decidedAt");
    }
}
