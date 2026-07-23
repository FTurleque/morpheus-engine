package com.morpheus.application.traceability;

import com.morpheus.application.reference.ExternalReferenceResolutionService;
import com.morpheus.application.reference.ExternalReferenceResolver;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.reference.ExternalReferenceResolverResult;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceResolutionReason;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.reference.ResolvedExternalTarget;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalTraceabilityLinkFactoryTest {
    private static final Instant T0 = Instant.parse("2026-07-23T12:00:00Z");
    private final ExternalTraceabilityLinkFactory factory = new ExternalTraceabilityLinkFactory();

    @Test
    void allowedExternalRelationsTargetTheExplicitExternalReferenceIdentity() {
        DomainIdentity owner = DomainIdentity.generate();
        TraceabilityEntityRef source = new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, owner);
        ExternalReference reference = reference(owner, ExternalReferenceResolutionState.UNVALIDATED);

        for (TraceabilityRelationType relationType : Set.of(
                TraceabilityRelationType.LINKS_TO_CODE,
                TraceabilityRelationType.LINKS_TO_TEST,
                TraceabilityRelationType.VERIFIED_BY,
                TraceabilityRelationType.SATISFIES)) {
            TraceabilityLinkId linkId = TraceabilityLinkId.generate();
            var link = factory.create(
                    linkId,
                    source,
                    relationType,
                    reference,
                    TraceabilityLinkOrigin.EXPLICIT,
                    Optional.empty(),
                    Set.of(EvidenceId.generate()),
                    T0);

            assertEquals(linkId, link.id());
            assertEquals(source, link.source());
            assertEquals(relationType, link.relationType());
            assertEquals(TraceabilityEntityKind.EXTERNAL_REFERENCE, link.target().kind());
            assertEquals(reference.id().value(), link.target().identity());
            assertEquals(TraceabilityResolutionState.UNRESOLVED, link.resolution());
        }
    }

    @Test
    void externalReferenceStateMapsToLinkResolutionWithoutSynthesizingHeuristic() {
        assertEquals(
                TraceabilityResolutionState.UNRESOLVED,
                factory.resolutionAtObservation(ExternalReferenceResolutionState.UNVALIDATED));
        assertEquals(
                TraceabilityResolutionState.UNRESOLVED,
                factory.resolutionAtObservation(ExternalReferenceResolutionState.UNRESOLVED));
        assertEquals(
                TraceabilityResolutionState.RESOLVED,
                factory.resolutionAtObservation(ExternalReferenceResolutionState.RESOLVED));
        assertEquals(
                TraceabilityResolutionState.PARTIALLY_RESOLVED,
                factory.resolutionAtObservation(ExternalReferenceResolutionState.STALE));
    }

    @Test
    void ownerMismatchAndNonExternalRelationAreRejected() {
        DomainIdentity owner = DomainIdentity.generate();
        TraceabilityEntityRef wrongSource = new TraceabilityEntityRef(
                TraceabilityEntityKind.REQUIREMENT,
                DomainIdentity.generate());
        ExternalReference reference = reference(owner, ExternalReferenceResolutionState.UNVALIDATED);

        assertThrows(IllegalArgumentException.class, () -> factory.create(
                TraceabilityLinkId.generate(),
                wrongSource,
                TraceabilityRelationType.LINKS_TO_CODE,
                reference,
                TraceabilityLinkOrigin.EXPLICIT,
                Optional.empty(),
                Set.of(EvidenceId.generate()),
                T0));

        TraceabilityEntityRef correctSource = new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, owner);
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                TraceabilityLinkId.generate(),
                correctSource,
                TraceabilityRelationType.RELATED_TO,
                reference,
                TraceabilityLinkOrigin.EXPLICIT,
                Optional.empty(),
                Set.of(EvidenceId.generate()),
                T0));
    }

    @Test
    void unavailableResolverMakesReferenceStaleWithoutMutatingCanonicalLink() {
        DomainIdentity owner = DomainIdentity.generate();
        TraceabilityEntityRef source = new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, owner);
        ExternalReference resolved = reference(owner, ExternalReferenceResolutionState.RESOLVED);
        var link = factory.create(
                TraceabilityLinkId.generate(),
                source,
                TraceabilityRelationType.LINKS_TO_CODE,
                resolved,
                TraceabilityLinkOrigin.EXPLICIT,
                Optional.empty(),
                Set.of(EvidenceId.generate()),
                T0);

        ExternalReferenceResolver unavailable = new ExternalReferenceResolver() {
            @Override
            public String system() {
                return "MINOS";
            }

            @Override
            public ExternalReferenceResolverResult resolve(ExternalReferenceTarget target) {
                return ExternalReferenceResolverResult.unavailable();
            }
        };
        var resolutionService = new ExternalReferenceResolutionService(
                new ExternalReferenceResolverRegistry(List.of(unavailable)),
                Clock.fixed(T0.plusSeconds(60), ZoneOffset.UTC));

        ExternalReference stale = resolutionService.resolve(resolved);

        assertEquals(ExternalReferenceResolutionState.STALE, stale.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.TARGET_UNAVAILABLE, stale.resolutionReason());
        assertEquals(TraceabilityResolutionState.RESOLVED, link.resolution());
        assertEquals(resolved.id().value(), link.target().identity());
    }

    @Test
    void missingResolverLeavesReferenceUnresolvedWithoutMutatingCanonicalLink() {
        DomainIdentity owner = DomainIdentity.generate();
        TraceabilityEntityRef source = new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, owner);
        ExternalReference unvalidated = reference(owner, ExternalReferenceResolutionState.UNVALIDATED);
        var link = factory.create(
                TraceabilityLinkId.generate(),
                source,
                TraceabilityRelationType.LINKS_TO_TEST,
                unvalidated,
                TraceabilityLinkOrigin.EXPLICIT,
                Optional.empty(),
                Set.of(EvidenceId.generate()),
                T0);
        var resolutionService = new ExternalReferenceResolutionService(
                new ExternalReferenceResolverRegistry(List.of()),
                Clock.fixed(T0.plusSeconds(30), ZoneOffset.UTC));

        ExternalReference unresolved = resolutionService.resolve(unvalidated);

        assertEquals(ExternalReferenceResolutionState.UNRESOLVED, unresolved.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.NO_RESOLVER, unresolved.resolutionReason());
        assertEquals(TraceabilityResolutionState.UNRESOLVED, link.resolution());
        assertEquals(unvalidated.id().value(), link.target().identity());
    }

    private ExternalReference reference(DomainIdentity owner, ExternalReferenceResolutionState state) {
        ExternalReferenceTarget target = new ExternalReferenceTarget(
                "MINOS",
                Optional.of("morpheus-engine"),
                "CODE_SYMBOL",
                "com.morpheus.Sample",
                Optional.of("rev-1"));
        return switch (state) {
            case UNVALIDATED -> ExternalReference.unvalidated(
                    ExternalReferenceId.generate(), owner, target, Optional.empty());
            case UNRESOLVED -> new ExternalReference(
                    ExternalReferenceId.generate(),
                    owner,
                    target,
                    ExternalReferenceResolutionState.UNRESOLVED,
                    ExternalReferenceResolutionReason.TARGET_NOT_FOUND,
                    Optional.empty(),
                    Optional.empty(),
                    List.of());
            case RESOLVED -> new ExternalReference(
                    ExternalReferenceId.generate(),
                    owner,
                    target,
                    ExternalReferenceResolutionState.RESOLVED,
                    ExternalReferenceResolutionReason.RESOLVED,
                    Optional.of(new ResolvedExternalTarget(target, Map.of("kind", "class"))),
                    Optional.empty(),
                    List.of());
            case STALE -> new ExternalReference(
                    ExternalReferenceId.generate(),
                    owner,
                    target,
                    ExternalReferenceResolutionState.STALE,
                    ExternalReferenceResolutionReason.TARGET_UNAVAILABLE,
                    Optional.empty(),
                    Optional.empty(),
                    List.of());
        };
    }
}
