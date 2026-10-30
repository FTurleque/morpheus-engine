package com.morpheus.application.reference;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.query.PublishedSnapshotWorld;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
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

class LiveExternalReferenceResolutionServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");

    private final PublishedSnapshotWorld world = new PublishedSnapshotWorld();

    @Test
    void theReferencesOfAnOwnerAreListedFromTheActiveSnapshotOnly() {
        DomainIdentity owner = world.next();
        ExternalReference first = world.reference(owner);
        ExternalReference second = world.reference(owner);
        world.reference(world.next());
        LiveExternalReferenceResolutionService service = service(List.of());

        assertEquals(Optional.of(List.of(first, second)), service.listActive(world.projectId(), owner));
        assertEquals(Optional.of(List.of()), service.listActive(world.projectId(), world.next()));
        assertTrue(service.listActive(new ProjectSpecificationId(world.next()), owner).isEmpty());
    }

    @Test
    void aLiveResolutionObservesTheTargetWithoutRewritingTheStoredReference() {
        ExternalReference stored = world.reference(world.next());
        LiveExternalReferenceResolutionService service = service(List.of(new FoundResolver()));

        LiveExternalReferenceResolutionResult result =
                service.resolveActive(world.projectId(), stored.id()).orElseThrow();

        assertEquals(world.active(), result.snapshot());
        assertEquals(stored, result.storedReference());
        assertEquals(ExternalReferenceResolutionState.RESOLVED, result.observedReference().resolutionState());
        assertEquals(NOW, result.observedReference().history().getFirst().occurredAt());
        assertEquals(stored, world.referenceStore().findReference(world.active().id(), stored.id()).orElseThrow());
    }

    @Test
    void aReferenceWithoutResolverIsObservedUnresolvedAndAnUnknownOneIsRefusedByName() {
        ExternalReference stored = world.reference(world.next());
        ExternalReferenceId unknown = new ExternalReferenceId(world.next());
        LiveExternalReferenceResolutionService service = service(List.of());

        LiveExternalReferenceResolutionResult result =
                service.resolveActive(world.projectId(), stored.id()).orElseThrow();
        KnowledgeStoreException refusal = assertThrows(KnowledgeStoreException.class,
                () -> service.resolveActive(world.projectId(), unknown));

        assertEquals(ExternalReferenceResolutionReason.NO_RESOLVER, result.observedReference().resolutionReason());
        assertEquals("external reference not found in snapshot " + world.active().id() + ": " + unknown,
                refusal.getMessage());
        assertTrue(service.resolveActive(new ProjectSpecificationId(world.next()), stored.id()).isEmpty());
    }

    @Test
    void anObservationMustKeepTheIdentityTheOwnerAndTheTargetOfTheStoredReference() {
        ExternalReference stored = world.reference(world.next());
        ExternalReference otherIdentity = world.reference(stored.ownerId());
        ExternalReference otherOwner = new ExternalReference(stored.id(), world.next(), stored.target(),
                stored.resolutionState(), stored.resolutionReason(), stored.resolvedTarget(), stored.provenance(),
                stored.history());
        ExternalReference otherTarget = new ExternalReference(stored.id(), stored.ownerId(),
                new ExternalReferenceTarget("NEXUS", Optional.empty(), "FILE", "file:other", Optional.empty()),
                stored.resolutionState(), stored.resolutionReason(), stored.resolvedTarget(), stored.provenance(),
                stored.history());

        Map<String, ExternalReference> refusals = Map.of(
                "live resolution must preserve ExternalReferenceId", otherIdentity,
                "live resolution must preserve reference owner", otherOwner,
                "live resolution must preserve external target coordinates", otherTarget);
        for (Map.Entry<String, ExternalReference> refusal : refusals.entrySet()) {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> new LiveExternalReferenceResolutionResult(world.active(), stored, refusal.getValue()));
            assertEquals(refusal.getKey(), thrown.getMessage());
        }
    }

    private LiveExternalReferenceResolutionService service(List<ExternalReferenceResolver> resolvers) {
        return new LiveExternalReferenceResolutionService(world.snapshots(), world.referenceStore(),
                new ExternalReferenceResolverRegistry(resolvers), Clock.fixed(NOW, ZoneOffset.UTC),
                OperationalRecorder.noop());
    }

    private static final class FoundResolver implements ExternalReferenceResolver {
        @Override
        public String system() {
            return "minos";
        }

        @Override
        public ExternalReferenceResolverResult resolve(ExternalReferenceTarget target) {
            return ExternalReferenceResolverResult.found(new ResolvedExternalTarget(target, Map.of("name", "Owner")));
        }
    }
}
