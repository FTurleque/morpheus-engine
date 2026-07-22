package com.morpheus.application.reference;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceResolutionReason;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.reference.ResolvedExternalTarget;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalReferenceResolutionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-22T20:45:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void referenceExistsWithoutAnyTargetSystem() {
        ExternalReference reference = reference();

        assertEquals(ExternalReferenceResolutionState.UNVALIDATED, reference.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.NOT_ATTEMPTED, reference.resolutionReason());
        assertTrue(reference.resolvedTarget().isEmpty());
        assertTrue(reference.history().isEmpty());
    }

    @Test
    void missingResolverProducesExplicitUnresolvedStateWithoutFailure() {
        ExternalReferenceResolutionService service = service(List.of());

        ExternalReference resolved = service.resolve(reference());

        assertEquals(ExternalReferenceResolutionState.UNRESOLVED, resolved.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.NO_RESOLVER, resolved.resolutionReason());
        assertEquals(1, resolved.history().size());
        assertEquals(NOW, resolved.history().getFirst().occurredAt());
    }

    @Test
    void optionalResolverCanResolveTargetWithoutLeakingTargetSystemTypes() {
        MutableResolver resolver = new MutableResolver(ExternalReferenceResolverResult.found(resolvedTarget()));

        ExternalReference resolved = service(List.of(resolver)).resolve(reference());

        assertEquals(ExternalReferenceResolutionState.RESOLVED, resolved.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.RESOLVED, resolved.resolutionReason());
        assertEquals("RequirementService", resolved.resolvedTarget().orElseThrow().attributes().get("name"));
        assertEquals(1, resolved.history().size());
    }

    @Test
    void previouslyResolvedTargetBecomesStaleWhenRemoved() {
        MutableResolver resolver = new MutableResolver(ExternalReferenceResolverResult.found(resolvedTarget()));
        ExternalReferenceResolutionService service = service(List.of(resolver));
        ExternalReference resolved = service.resolve(reference());
        resolver.result = ExternalReferenceResolverResult.notFound();

        ExternalReference stale = service.resolve(resolved);

        assertEquals(ExternalReferenceResolutionState.STALE, stale.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.TARGET_REMOVED, stale.resolutionReason());
        assertTrue(stale.resolvedTarget().isEmpty());
        assertEquals(2, stale.history().size());
    }

    @Test
    void unresolvedReferenceCanResolveLater() {
        MutableResolver resolver = new MutableResolver(ExternalReferenceResolverResult.notFound());
        ExternalReferenceResolutionService service = service(List.of(resolver));
        ExternalReference unresolved = service.resolve(reference());
        resolver.result = ExternalReferenceResolverResult.found(resolvedTarget());

        ExternalReference resolved = service.resolve(unresolved);

        assertEquals(ExternalReferenceResolutionState.RESOLVED, resolved.resolutionState());
        assertEquals(2, resolved.history().size());
        assertEquals(ExternalReferenceResolutionState.UNRESOLVED, resolved.history().getFirst().newState());
        assertEquals(ExternalReferenceResolutionState.RESOLVED, resolved.history().get(1).newState());
    }

    @Test
    void unavailableSystemDoesNotBreakMorpheusAndDuplicateResolverIsRejected() {
        MutableResolver unavailable = new MutableResolver(ExternalReferenceResolverResult.unavailable());
        ExternalReference unresolved = service(List.of(unavailable)).resolve(reference());

        assertEquals(ExternalReferenceResolutionState.UNRESOLVED, unresolved.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.TARGET_UNAVAILABLE, unresolved.resolutionReason());

        assertThrows(IllegalArgumentException.class, () -> new ExternalReferenceResolverRegistry(List.of(
                unavailable,
                new MutableResolver(ExternalReferenceResolverResult.notFound()))));
    }

    private ExternalReferenceResolutionService service(List<? extends ExternalReferenceResolver> resolvers) {
        return new ExternalReferenceResolutionService(new ExternalReferenceResolverRegistry(resolvers), CLOCK);
    }

    private ExternalReference reference() {
        return ExternalReference.unvalidated(
                ExternalReferenceId.generate(),
                DomainIdentity.generate(),
                target(),
                Optional.empty());
    }

    private ExternalReferenceTarget target() {
        return new ExternalReferenceTarget(
                "MINOS",
                Optional.of("morpheus-engine"),
                "SYMBOL",
                "symbol:RequirementService",
                Optional.empty());
    }

    private ResolvedExternalTarget resolvedTarget() {
        return new ResolvedExternalTarget(target(), Map.of("name", "RequirementService"));
    }

    private static final class MutableResolver implements ExternalReferenceResolver {
        private ExternalReferenceResolverResult result;

        private MutableResolver(ExternalReferenceResolverResult result) {
            this.result = result;
        }

        @Override
        public String system() {
            return "minos";
        }

        @Override
        public ExternalReferenceResolverResult resolve(ExternalReferenceTarget target) {
            return result;
        }
    }
}
