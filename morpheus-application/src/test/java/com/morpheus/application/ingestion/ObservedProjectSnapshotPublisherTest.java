package com.morpheus.application.ingestion;

import com.morpheus.application.operability.OperationalEvent;
import com.morpheus.application.operability.OperationalEventCode;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.read.NormalizedProjectContent;
import com.morpheus.application.store.InMemorySnapshotStores;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Observing a publication adds counts and a timed outcome, and changes neither its result nor its refusal. */
class ObservedProjectSnapshotPublisherTest {
    private static final Instant AT = Instant.parse("2026-10-30T08:00:00Z");

    private final InMemorySnapshotStores stores = new InMemorySnapshotStores();
    private final OperationalMetrics metrics = new OperationalMetrics();
    private final List<OperationalEvent> events = new ArrayList<>();
    private final ObservedProjectSnapshotPublisher publisher = new ObservedProjectSnapshotPublisher(
            new ProjectSnapshotImportService(stores, stores, stores, stores),
            new OperationalRecorder(events::add, metrics));
    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();

    @Test
    void aPublicationIsTimedAndCountedAndReturnsTheDelegatesReceipt() {
        ProjectSnapshotImportResult result = publisher.publishFull(content(List.of()), Optional.of("rev-1"), AT);

        assertEquals(KnowledgeSnapshotState.ACTIVE, result.snapshot().state());
        assertEquals(1, result.requirementCount());
        assertEquals(result.snapshot(), stores.activeSnapshot(projectId).orElseThrow());
        OperationalMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(1L, snapshot.counters().get("snapshot.publish.success"));
        assertEquals(1L, snapshot.counters().get("snapshot.publish.requirement_count"));
        assertEquals((long) result.traceabilityLinkCount(),
                snapshot.counters().getOrDefault("snapshot.publish.traceability_link_count", 0L));
        assertEquals(List.of(OperationalEventCode.SNAPSHOT_PUBLISH_STARTED, OperationalEventCode.SNAPSHOT_PUBLISH_COMPLETED),
                events.stream().map(OperationalEvent::code).toList());
        Map<String, String> completed = events.getLast().attributes();
        assertEquals(result.snapshot().id().toString(), completed.get("snapshotId"));
        assertEquals("1", completed.get("requirementCount"));
        assertEquals(projectId.toString(), completed.get("projectId"));
    }

    @Test
    void aRefusedPublicationIsRecordedAsAFailureAndPropagatedUnchanged() {
        Diagnostic blocking = Diagnostic.error(DiagnosticCode.INVALID_SOURCE, "broken heading", Map.of());
        NormalizedProjectContent content = content(List.of(blocking));
        Optional<String> revision = Optional.of("rev-2");

        KnowledgeStoreException refusal = assertThrows(KnowledgeStoreException.class,
                () -> publisher.publishFull(content, revision, AT));

        assertTrue(refusal.getMessage().startsWith("normalized content contains blocking diagnostics"),
                refusal.getMessage());
        assertEquals(1L, metrics.snapshot().counters().get("snapshot.publish.failure"));
        assertEquals(OperationalEventCode.SNAPSHOT_PUBLISH_FAILED, events.getLast().code());
        assertEquals("KnowledgeStoreException", events.getLast().attributes().get("errorType"));
        assertEquals(Optional.empty(), stores.activeSnapshot(projectId));
    }

    @Test
    void aReceiptRefusesNegativeCounts() {
        ProjectSnapshotImportResult published = publisher.publishFull(content(List.of()), Optional.empty(), AT);

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> new ProjectSnapshotImportResult(
                        published.snapshot(), published.specificationVersion(), -1, 0, List.of()));

        assertEquals("import counts must be non-negative", refusal.getMessage());
    }

    private NormalizedProjectContent content(List<Diagnostic> diagnostics) {
        Evidence evidence = new Evidence(
                EvidenceId.generate(), SourceLocator.file("specs/auth.md"), Optional.empty(), Optional.of("sha256:a"));
        Provenance provenance = new Provenance(new ProviderId("test-provider"), Optional.of("1"),
                SourceLocator.file("specs/auth.md"), Optional.of("auth"), Optional.empty(), evidence.id());
        Specification specification = new Specification(
                SpecificationId.generate(), projectId, "auth", "Authentication", Optional.empty(), provenance);
        Requirement requirement = new Requirement(RequirementId.generate(), specification.id(), Optional.of("R-1"),
                "Session expiry", "Sessions expire.", provenance);
        return NormalizedProjectContent.currentSpecificationsOnly(
                new ProjectSpecification(projectId, "observed", SourceLocator.file("workspace")),
                List.of(specification), List.of(requirement), List.of(), List.of(evidence), diagnostics);
    }
}
