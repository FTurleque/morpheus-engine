package com.morpheus.application.quality;

import com.morpheus.application.quality.compact.CompactQualityReportService;
import com.morpheus.application.quality.compact.CompactQualityReportView;
import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The quality report of a published snapshot and its compact projection, read through hand-written ports. */
class QualityReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final EvidenceId EVIDENCE = EvidenceId.generate();

    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
    private final SpecificationId specificationId = SpecificationId.generate();
    private final Snapshot snapshot = new Snapshot();
    private final QualityReportService service;

    QualityReportServiceTest() {
        Content content = new Content();
        OneRequirement requirements = new OneRequirement();
        NoLinks links = new NoLinks();
        service = new QualityReportService(
                snapshot,
                new RequirementQualityService(snapshot, requirements, links),
                new TaskQualityService(snapshot, content, requirements, links),
                new AcceptanceQualityService(snapshot, content),
                new ChangeCompletenessService(snapshot, content, requirements, links),
                new DecisionReferenceQualityService(snapshot, content, requirements, links, new NoReferences()));
    }

    @Test
    void anOrphanRequirementIsAFindingCountedInTheMetricsAndTheCompactView() {
        snapshot.state = KnowledgeSnapshotState.ACTIVE;

        QualityReport report = service.assessActive(projectId).orElseThrow();

        assertEquals(1, report.metrics().totalRequirements());
        assertEquals(1, report.metrics().orphanRequirements());
        assertEquals(1, report.metrics().totalChanges());
        assertEquals(report.findings().size(), report.metrics().totalFindings());
        assertTrue(report.metrics().totalFindings() > 0, "an orphan requirement is a finding");
        assertEquals(LifecycleQualityAggregationStatus.REQUIRES_EXPLICIT_LIFECYCLE_INPUT,
                report.lifecycleAggregationStatus());

        CompactQualityReportService compact = new CompactQualityReportService();
        CompactQualityReportView view = compact.view(report);
        assertEquals(report.findings().size(), view.findings().size());
        assertEquals(compact.toJson(report), new String(compact.toUtf8(report), StandardCharsets.UTF_8),
                "the JSON text and its UTF-8 bytes are the same canonical document");
        assertTrue(compact.toJson(report).contains("\"get_quality_report\""), "the view names its operation");
    }

    @Test
    void aSnapshotThatIsNotPublishedIsRefusedByNameAndAnUnknownOneToo() {
        assertTrue(service.assessActive(projectId).isEmpty(), "no active snapshot, no report");
        KnowledgeStoreException unknown = assertThrows(KnowledgeStoreException.class,
                () -> service.assessSnapshot(snapshotId));
        assertTrue(unknown.getMessage().startsWith("unknown knowledge snapshot"), unknown::getMessage);

        snapshot.state = KnowledgeSnapshotState.BUILDING;
        KnowledgeStoreException building = assertThrows(KnowledgeStoreException.class,
                () -> service.assessSnapshot(snapshotId));
        assertTrue(building.getMessage().contains("quality report requires an ACTIVE or RETIRED snapshot"),
                building::getMessage);

        snapshot.state = KnowledgeSnapshotState.RETIRED;
        assertEquals(snapshotId, service.assessSnapshot(snapshotId).snapshot().id());
    }

    private static Provenance provenance() {
        return new Provenance(new ProviderId("openspec"), Optional.of("1"), SourceLocator.file("specs/r.md"),
                Optional.of("R-1"), Optional.of("rev"), EVIDENCE);
    }

    private final class Snapshot implements SpecificationKnowledgeStore {
        private KnowledgeSnapshotState state;

        private Optional<KnowledgeSnapshotMetadata> current() {
            return Optional.ofNullable(state).map(value -> new KnowledgeSnapshotMetadata(snapshotId, projectId,
                    Optional.empty(), value, Optional.of("baseline"), NOW));
        }

        @Override public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId id) {
            return current().filter(value -> value.state() == KnowledgeSnapshotState.ACTIVE);
        }

        @Override public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId id) {
            return current().filter(value -> value.id().equals(id));
        }

        @Override public void putProject(ProjectStoreEntry project) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId id) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) { throw new UnsupportedOperationException(); }
        @Override public List<ProjectStoreEntry> listProjects() { throw new UnsupportedOperationException(); }
        @Override public void putSnapshot(KnowledgeSnapshotMetadata metadata) { throw new UnsupportedOperationException(); }
        @Override public KnowledgeSnapshotMetadata transitionSnapshotState(
                KnowledgeSnapshotId id, KnowledgeSnapshotState from, KnowledgeSnapshotState to) {
            throw new UnsupportedOperationException();
        }
        @Override public KnowledgeSnapshotMetadata activateSnapshot(
                KnowledgeSnapshotId id, Optional<KnowledgeSnapshotId> expected) {
            throw new UnsupportedOperationException();
        }
    }

    private final class Content implements SnapshotBusinessContentStore {
        @Override public Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId id) {
            Specification specification = new Specification(specificationId, projectId, "spec", "Spec",
                    Optional.empty(), provenance());
            ChangeProposal change = new ChangeProposal(ChangeId.generate(), projectId, Optional.of("change"), "Change",
                    "Intent", List.of(), List.of(), List.of(), provenance());
            return Optional.of(new SnapshotBusinessContent(id, SpecificationVersionId.generate(),
                    List.of(specification), List.of(), List.of(change), List.of(), List.of(), List.of(), List.of(),
                    List.of(new Evidence(EVIDENCE, SourceLocator.file("specs/r.md"), Optional.empty(),
                            Optional.empty()))));
        }

        @Override public void putSnapshotContent(SnapshotBusinessContent content) {
            throw new UnsupportedOperationException();
        }
    }

    private final class OneRequirement implements VersionedRequirementStore {
        private final Requirement requirement = new Requirement(RequirementId.generate(), specificationId,
                Optional.of("R-1"), "Orphan", "Statement", provenance());

        @Override public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId id) {
            return List.of(new RequirementVersionRecord(id, new EntityVersion<>(EntityVersionId.generate(),
                    requirement.id().value(), SpecificationVersionId.generate(), TemporalState.CURRENT, requirement)));
        }

        @Override public Optional<RequirementVersionRecord> currentRequirement(KnowledgeSnapshotId id, DomainIdentity identity) {
            return listRequirementVersions(id).stream()
                    .filter(record -> record.entityVersion().entityIdentity().equals(identity)).findFirst();
        }

        @Override public void putSpecificationVersion(SpecificationVersion version) { throw new UnsupportedOperationException(); }
        @Override public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) { throw new UnsupportedOperationException(); }
        @Override public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) { throw new UnsupportedOperationException(); }
        @Override public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId id) { throw new UnsupportedOperationException(); }
        @Override public void putRequirementVersion(RequirementVersionRecord record) { throw new UnsupportedOperationException(); }
        @Override public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) { throw new UnsupportedOperationException(); }
    }

    private static final class NoLinks implements TraceabilityStore {
        @Override public List<TraceabilityLink> outgoing(KnowledgeSnapshotId id, TraceabilityEntityRef source,
                Set<TraceabilityRelationType> relationTypes) {
            return List.of();
        }

        @Override public List<TraceabilityLink> incoming(KnowledgeSnapshotId id, TraceabilityEntityRef target,
                Set<TraceabilityRelationType> relationTypes) {
            return List.of();
        }

        @Override public void putLink(KnowledgeSnapshotId id, TraceabilityLink link) { throw new UnsupportedOperationException(); }
        @Override public void putLinks(KnowledgeSnapshotId id, List<TraceabilityLink> batch) { throw new UnsupportedOperationException(); }
        @Override public Optional<TraceabilityLink> findLink(KnowledgeSnapshotId id, TraceabilityLinkId linkId) { throw new UnsupportedOperationException(); }
    }

    private static final class NoReferences implements ExternalReferenceStore {
        @Override public List<ExternalReference> findByOwner(KnowledgeSnapshotId id, DomainIdentity ownerId) { return List.of(); }
        @Override public void putReference(KnowledgeSnapshotId id, ExternalReference reference) { throw new UnsupportedOperationException(); }
        @Override public Optional<ExternalReference> findReference(KnowledgeSnapshotId id, ExternalReferenceId referenceId) { throw new UnsupportedOperationException(); }
    }
}
