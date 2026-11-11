package com.morpheus.domain.reference;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.traceability.TraceabilityResolutionState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalReferenceResolutionTest {
    private static final Instant AT = Instant.parse("2026-10-30T00:00:00Z");

    @Test
    void aReferenceWithoutResolverStaysUnresolvedEvenAfterAResolution() {
        ExternalReference resolved = unvalidated().resolvedAs(target(), AT);
        ExternalReference withoutResolver = resolved.withoutResolver(AT);

        assertEquals(ExternalReferenceResolutionState.UNRESOLVED, withoutResolver.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.NO_RESOLVER, withoutResolver.resolutionReason());
        assertTrue(withoutResolver.hasBeenResolved(), "the history keeps the earlier resolution");
        assertFalse(withoutResolver.isResolved());
    }

    @Test
    void aTargetNeverFoundIsUnresolvedWithTheUnresolvedReason() {
        ExternalReference notFound = unvalidated().notFound(
                ExternalReferenceResolutionReason.TARGET_NOT_FOUND, ExternalReferenceResolutionReason.TARGET_REMOVED, AT);

        assertEquals(ExternalReferenceResolutionState.UNRESOLVED, notFound.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.TARGET_NOT_FOUND, notFound.resolutionReason());
        assertFalse(notFound.hasBeenResolved());
        assertEquals(1, notFound.history().size());
    }

    @Test
    void aTargetFoundOnceAndLostIsStaleWithTheStaleReason() {
        ExternalReference resolved = unvalidated().resolvedAs(target(), AT);
        assertTrue(resolved.isResolved());
        assertEquals(Optional.of(target()), resolved.resolvedTarget());

        ExternalReference stale = resolved.notFound(
                ExternalReferenceResolutionReason.TARGET_NOT_FOUND, ExternalReferenceResolutionReason.TARGET_REMOVED, AT);
        assertEquals(ExternalReferenceResolutionState.STALE, stale.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.TARGET_REMOVED, stale.resolutionReason());
        assertTrue(stale.hasBeenResolved());

        ExternalReference stillStale = stale.notFound(
                ExternalReferenceResolutionReason.TARGET_AMBIGUOUS, ExternalReferenceResolutionReason.TARGET_UNAVAILABLE, AT);
        assertEquals(ExternalReferenceResolutionState.STALE, stillStale.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.TARGET_UNAVAILABLE, stillStale.resolutionReason());
    }

    @Test
    void aTraceabilityLinkRecordsAStaleReferenceAsPartiallyResolvedAndNeverAsHeuristic() {
        assertEquals(TraceabilityResolutionState.UNRESOLVED,
                ExternalReferenceResolutionState.UNVALIDATED.traceabilityResolution());
        assertEquals(TraceabilityResolutionState.UNRESOLVED,
                ExternalReferenceResolutionState.UNRESOLVED.traceabilityResolution());
        assertEquals(TraceabilityResolutionState.RESOLVED,
                ExternalReferenceResolutionState.RESOLVED.traceabilityResolution());
        assertEquals(TraceabilityResolutionState.PARTIALLY_RESOLVED,
                ExternalReferenceResolutionState.STALE.traceabilityResolution());
    }

    private static ExternalReference unvalidated() {
        return ExternalReference.unvalidated(ExternalReferenceId.generate(), DomainIdentity.generate(),
                new ExternalReferenceTarget("jira", Optional.empty(), "issue", "MOR-1", Optional.empty()),
                Optional.empty());
    }

    private static ResolvedExternalTarget target() {
        return new ResolvedExternalTarget(
                new ExternalReferenceTarget("jira", Optional.empty(), "issue", "MOR-1", Optional.empty()), Map.of());
    }
}
