package com.morpheus.application.traceability;

import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;
import com.morpheus.domain.traceability.TraceabilityConfidence;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Materializes explicit traceability links toward MORPHEUS-owned external-reference identities. */
public final class ExternalTraceabilityLinkFactory {
    private static final Set<TraceabilityRelationType> EXTERNAL_RELATIONS = Set.copyOf(EnumSet.of(
            TraceabilityRelationType.LINKS_TO_CODE,
            TraceabilityRelationType.LINKS_TO_TEST,
            TraceabilityRelationType.VERIFIED_BY,
            TraceabilityRelationType.SATISFIES));

    public TraceabilityLink create(
            TraceabilityLinkId linkId,
            TraceabilityEntityRef source,
            TraceabilityRelationType relationType,
            ExternalReference reference,
            TraceabilityLinkOrigin origin,
            Optional<TraceabilityConfidence> confidence,
            Set<EvidenceId> evidenceIds,
            Instant observedAt) {
        Objects.requireNonNull(linkId, "linkId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(relationType, "relationType");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(evidenceIds, "evidenceIds");
        Objects.requireNonNull(observedAt, "observedAt");

        if (!EXTERNAL_RELATIONS.contains(relationType)) {
            throw new IllegalArgumentException("relation is not an external traceability relation: " + relationType);
        }
        if (!reference.ownerId().equals(source.identity())) {
            throw new IllegalArgumentException("external reference owner must match traceability source identity");
        }

        return new TraceabilityLink(
                linkId,
                source,
                relationType,
                new TraceabilityEntityRef(TraceabilityEntityKind.EXTERNAL_REFERENCE, reference.id().value()),
                origin,
                resolutionAtObservation(reference.resolutionState()),
                confidence,
                evidenceIds,
                observedAt);
    }

    public TraceabilityResolutionState resolutionAtObservation(ExternalReferenceResolutionState state) {
        return switch (Objects.requireNonNull(state, "state")) {
            case UNVALIDATED, UNRESOLVED -> TraceabilityResolutionState.UNRESOLVED;
            case RESOLVED -> TraceabilityResolutionState.RESOLVED;
            case STALE -> TraceabilityResolutionState.PARTIALLY_RESOLVED;
        };
    }
}
