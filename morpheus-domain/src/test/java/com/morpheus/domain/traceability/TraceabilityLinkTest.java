package com.morpheus.domain.traceability;

import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceabilityLinkTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-23T12:00:00Z");

    @Test
    void controlledTaxonomyContainsExactlyTheMvpRelations() {
        assertEquals(Set.of(
                TraceabilityRelationType.REFINES,
                TraceabilityRelationType.DERIVES_FROM,
                TraceabilityRelationType.CONSTRAINS,
                TraceabilityRelationType.SATISFIES,
                TraceabilityRelationType.IMPLEMENTS,
                TraceabilityRelationType.VALIDATES,
                TraceabilityRelationType.VERIFIED_BY,
                TraceabilityRelationType.DEPENDS_ON,
                TraceabilityRelationType.AFFECTS,
                TraceabilityRelationType.DECIDED_BY,
                TraceabilityRelationType.SUPERSEDES,
                TraceabilityRelationType.LINKS_TO_CODE,
                TraceabilityRelationType.LINKS_TO_TEST,
                TraceabilityRelationType.RELATED_TO),
                EnumSet.allOf(TraceabilityRelationType.class));
    }

    @Test
    void sameDomainIdentityWithDifferentKindsRemainsTwoTypedReferences() {
        DomainIdentity identity = DomainIdentity.generate();

        TraceabilityEntityRef requirement = new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, identity);
        TraceabilityEntityRef change = new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, identity);

        assertNotEquals(requirement, change);
        assertEquals(identity, requirement.identity());
        assertEquals(identity, change.identity());
    }

    @Test
    void linkRetainsExplicitIdentityCanonicalDirectionEvidenceAndObservationTime() {
        TraceabilityLinkId linkId = TraceabilityLinkId.generate();
        TraceabilityEntityRef source = ref(TraceabilityEntityKind.SCENARIO);
        TraceabilityEntityRef target = ref(TraceabilityEntityKind.REQUIREMENT);
        EvidenceId evidenceId = EvidenceId.generate();

        TraceabilityLink link = new TraceabilityLink(
                linkId,
                source,
                TraceabilityRelationType.REFINES,
                target,
                TraceabilityLinkOrigin.EXPLICIT,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of(evidenceId),
                OBSERVED_AT);

        assertEquals(linkId, link.id());
        assertEquals(source, link.source());
        assertEquals(target, link.target());
        assertEquals(TraceabilityRelationType.REFINES, link.relationType());
        assertEquals(Set.of(evidenceId), link.evidenceIds());
        assertEquals(OBSERVED_AT, link.observedAt());
    }

    @Test
    void evidenceIsMandatoryImmutableAndDeduplicated() {
        EvidenceId evidenceId = EvidenceId.generate();
        TraceabilityLink link = link(
                TraceabilityLinkOrigin.DERIVED,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of(evidenceId));

        assertEquals(1, link.evidenceIds().size());
        assertThrows(UnsupportedOperationException.class, () -> link.evidenceIds().add(EvidenceId.generate()));
        assertThrows(IllegalArgumentException.class, () -> link(
                TraceabilityLinkOrigin.EXPLICIT,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of()));
    }

    @Test
    void heuristicOriginOrResolutionRequiresExplicitConfidence() {
        assertThrows(IllegalArgumentException.class, () -> link(
                TraceabilityLinkOrigin.HEURISTIC,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of(EvidenceId.generate())));
        assertThrows(IllegalArgumentException.class, () -> link(
                TraceabilityLinkOrigin.DERIVED,
                TraceabilityResolutionState.HEURISTIC,
                Optional.empty(),
                Set.of(EvidenceId.generate())));

        TraceabilityLink heuristic = link(
                TraceabilityLinkOrigin.HEURISTIC,
                TraceabilityResolutionState.HEURISTIC,
                Optional.of(TraceabilityConfidence.of(0.62d)),
                Set.of(EvidenceId.generate()));

        assertEquals(0.62d, heuristic.confidence().orElseThrow().value());
    }

    @Test
    void confidenceIsFiniteAndInclusivelyBounded() {
        assertEquals(0.0d, TraceabilityConfidence.of(0.0d).value());
        assertEquals(1.0d, TraceabilityConfidence.of(1.0d).value());
        assertThrows(IllegalArgumentException.class, () -> TraceabilityConfidence.of(-0.0001d));
        assertThrows(IllegalArgumentException.class, () -> TraceabilityConfidence.of(1.0001d));
        assertThrows(IllegalArgumentException.class, () -> TraceabilityConfidence.of(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> TraceabilityConfidence.of(Double.POSITIVE_INFINITY));
    }

    @Test
    void semanticallySimilarEdgesCanStillHaveDistinctExplicitLinkIdentities() {
        TraceabilityEntityRef source = ref(TraceabilityEntityKind.CHANGE);
        TraceabilityEntityRef target = ref(TraceabilityEntityKind.REQUIREMENT);
        EvidenceId evidenceId = EvidenceId.generate();

        TraceabilityLink left = new TraceabilityLink(
                TraceabilityLinkId.generate(),
                source,
                TraceabilityRelationType.AFFECTS,
                target,
                TraceabilityLinkOrigin.DERIVED,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of(evidenceId),
                OBSERVED_AT);
        TraceabilityLink right = new TraceabilityLink(
                TraceabilityLinkId.generate(),
                source,
                TraceabilityRelationType.AFFECTS,
                target,
                TraceabilityLinkOrigin.DERIVED,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of(evidenceId),
                OBSERVED_AT);

        assertNotEquals(left.id(), right.id());
        assertNotEquals(left, right);
    }

    @Test
    void relationMetadataIsIndependentFromOriginResolutionAndDoesNotPersistInverseEdges() {
        assertEquals(TraceabilitySemanticClass.REALIZATION, TraceabilityRelationType.IMPLEMENTS.semanticClass());
        assertEquals(
                TraceabilityTransitivityPolicy.NON_TRANSITIVE,
                TraceabilityRelationType.IMPLEMENTS.transitivityPolicy());
        assertEquals("IMPLEMENTED_BY", TraceabilityRelationType.IMPLEMENTS.inverseQueryName().orElseThrow());

        assertEquals(TraceabilitySemanticClass.DEPENDENCY, TraceabilityRelationType.DEPENDS_ON.semanticClass());
        assertEquals(
                TraceabilityTransitivityPolicy.CONTEXTUAL,
                TraceabilityRelationType.DEPENDS_ON.transitivityPolicy());

        assertTrue(TraceabilityRelationType.RELATED_TO.inverseQueryName().isPresent());
        assertFalse(TraceabilityRelationType.values().length == 0);
    }

    private static TraceabilityEntityRef ref(TraceabilityEntityKind kind) {
        return new TraceabilityEntityRef(kind, DomainIdentity.generate());
    }

    private static TraceabilityLink link(
            TraceabilityLinkOrigin origin,
            TraceabilityResolutionState resolution,
            Optional<TraceabilityConfidence> confidence,
            Set<EvidenceId> evidenceIds) {
        return new TraceabilityLink(
                TraceabilityLinkId.generate(),
                ref(TraceabilityEntityKind.REQUIREMENT),
                TraceabilityRelationType.RELATED_TO,
                ref(TraceabilityEntityKind.REQUIREMENT),
                origin,
                resolution,
                confidence,
                evidenceIds,
                OBSERVED_AT);
    }
}
