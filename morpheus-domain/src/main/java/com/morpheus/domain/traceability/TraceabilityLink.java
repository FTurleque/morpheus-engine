package com.morpheus.domain.traceability;

import com.morpheus.domain.evidence.EvidenceId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable provider-neutral traceability observation between two typed MORPHEUS entity references. */
public record TraceabilityLink(
        TraceabilityLinkId id,
        TraceabilityEntityRef source,
        TraceabilityRelationType relationType,
        TraceabilityEntityRef target,
        TraceabilityLinkOrigin origin,
        TraceabilityResolutionState resolution,
        Optional<TraceabilityConfidence> confidence,
        Set<EvidenceId> evidenceIds,
        Instant observedAt) {

    public TraceabilityLink {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(relationType, "relationType");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(resolution, "resolution");
        confidence = Objects.requireNonNull(confidence, "confidence");
        evidenceIds = Set.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds"));
        Objects.requireNonNull(observedAt, "observedAt");

        if (evidenceIds.isEmpty()) {
            throw new IllegalArgumentException("traceability link must contain at least one evidence id");
        }

        if ((origin == TraceabilityLinkOrigin.HEURISTIC
                || resolution == TraceabilityResolutionState.HEURISTIC)
                && confidence.isEmpty()) {
            throw new IllegalArgumentException("heuristic traceability requires an explicit confidence");
        }
    }
}
