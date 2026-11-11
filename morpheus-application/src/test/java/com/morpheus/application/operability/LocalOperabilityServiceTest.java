package com.morpheus.application.operability;

import com.morpheus.application.store.InMemorySnapshotStores;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Liveness claims only the process; readiness asks the store a real question and says why it is not ready. */
class LocalOperabilityServiceTest {
    private final OperationalMetrics metrics = new OperationalMetrics();
    private final List<OperationalEvent> events = new ArrayList<>();

    @Test
    void aStoreThatAnswersMakesTheServiceReadyAndTheAnswerIsEmitted() {
        LocalOperabilityService service = new LocalOperabilityService(() -> 3, metrics, events::add);

        LocalOperabilityService.Readiness readiness = service.readiness();

        assertEquals("READY", readiness.status());
        assertEquals(Optional.empty(), readiness.diagnosticCode());
        assertEquals(OperationalEventCode.DATABASE_READY, events.getFirst().code());
        assertEquals(OperationalEventLevel.INFO, events.getFirst().level());
        assertEquals("3", events.getFirst().attributes().get("projectCount"));
    }

    @Test
    void aStoreThatFailsMakesTheServiceNotReadyWithANamedCodeAndNoMessage() {
        LocalOperabilityService service = new LocalOperabilityService(() -> {
            throw new IllegalStateException("database locked at C:/secret/path");
        }, metrics, events::add);

        LocalOperabilityService.Readiness readiness = service.readiness();

        assertEquals("NOT_READY", readiness.status());
        assertEquals(Optional.of("DATABASE_NOT_READY"), readiness.diagnosticCode());
        OperationalEvent event = events.getFirst();
        assertEquals(OperationalEventCode.DATABASE_NOT_READY, event.code());
        assertEquals(OperationalEventLevel.ERROR, event.level());
        assertEquals("IllegalStateException", event.attributes().get("errorType"));
        assertEquals(1, event.attributes().size(), "the failure message may name a server location");
    }

    @Test
    void theSnapshotJoinsHealthReadinessAndTheCountersTheServiceWasHanded() {
        LocalOperabilityService service = new LocalOperabilityService(() -> 0, metrics, events::add);
        service.recorder().begin("probe", OperationalEventCode.DATABASE_READY, java.util.Map.of())
                .success(OperationalEventCode.DATABASE_READY, java.util.Map.of());

        LocalOperabilityService.Snapshot snapshot = service.snapshot();

        assertEquals("UP", snapshot.health().status());
        assertEquals("READY", snapshot.readiness().status());
        assertEquals(1L, snapshot.metrics().counters().get("probe.success"));
        assertEquals(service.metrics(), metrics.snapshot());
    }

    @Test
    void aStoreBackedServiceCountsTheProjectsOfTheStore() {
        InMemorySnapshotStores store = new InMemorySnapshotStores();
        store.putProject(new ProjectStoreEntry(ProjectSpecificationId.generate(), SourceLocator.file("one")));
        store.putProject(new ProjectStoreEntry(ProjectSpecificationId.generate(), SourceLocator.file("two")));
        LocalOperabilityService service = new LocalOperabilityService(store, metrics, events::add);

        service.readiness();

        assertEquals("2", events.getFirst().attributes().get("projectCount"));
    }

    @Test
    void theStatusRecordsRefuseAContradictoryShape() {
        Instant now = Instant.parse("2026-10-30T08:00:00Z");

        IllegalArgumentException down = assertThrows(IllegalArgumentException.class,
                () -> new LocalOperabilityService.Health("DOWN", now));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> new LocalOperabilityService.Readiness("MAYBE", now, Optional.empty()));
        IllegalArgumentException readyWithCode = assertThrows(IllegalArgumentException.class,
                () -> new LocalOperabilityService.Readiness("READY", now, Optional.of("X")));
        IllegalArgumentException notReadyWithout = assertThrows(IllegalArgumentException.class,
                () -> new LocalOperabilityService.Readiness("NOT_READY", now, Optional.empty()));

        assertEquals("health status must be UP", down.getMessage());
        assertEquals("readiness status must be READY or NOT_READY", unknown.getMessage());
        assertEquals("READY must not contain a diagnostic code", readyWithCode.getMessage());
        assertEquals("NOT_READY requires a diagnostic code", notReadyWithout.getMessage());
    }
}
