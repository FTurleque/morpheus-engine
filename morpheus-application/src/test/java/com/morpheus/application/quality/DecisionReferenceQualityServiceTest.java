package com.morpheus.application.quality;

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
import com.morpheus.application.traceability.ExternalTraceabilityAvailability;
import com.morpheus.application.traceability.ExternalTraceabilityView;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceResolutionReason;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.reference.ResolvedExternalTarget;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Decisions without a DECIDED_BY trace and external references that are not resolved are findings; nothing else. */
class DecisionReferenceQualityServiceTest {
    private static final Instant T0 = Instant.parse("2026-10-30T12:00:00Z");
    private static final EvidenceId EVIDENCE = EvidenceId.generate();

    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final Snapshots snapshots = new Snapshots();
    private final Contents contents = new Contents();
    private final Requirements requirements = new Requirements();
    private final Links links = new Links();
    private final References references = new References();
    private final DecisionReferenceQualityService service =
            new DecisionReferenceQualityService(snapshots, contents, requirements, links, references);

    private final KnowledgeSnapshotMetadata active = snapshots.add(KnowledgeSnapshotState.ACTIVE);
    private final ChangeProposal change = new ChangeProposal(ChangeId.generate(), projectId, Optional.of("change"),
            "Change", "Intent", List.of(), List.of(), List.of(), provenance());
    private final DesignDecision traced = decision("Traced");
    private final DesignDecision untraced = decision("Untraced");
    private final Requirement requirement = new Requirement(RequirementId.generate(), SpecificationId.generate(),
            Optional.of("R-1"), "Requirement", "Statement", provenance());
    private final TraceabilityEntityRef changeRef =
            new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, change.id().value());

    @Test
    void everyDecisionIsAssessedAndOnlyAnUntracedOneLacksItsTrace() {
        publish();
        links.add(changeRef, TraceabilityRelationType.DECIDED_BY,
                new TraceabilityEntityRef(TraceabilityEntityKind.DESIGN_DECISION, traced.id().value()));

        DecisionReferenceQualityReport report = service.assessActive(projectId).orElseThrow();

        List<DesignDecision> expectedOrder = new ArrayList<>(List.of(traced, untraced));
        expectedOrder.sort(Comparator.comparing(item -> item.id().toString()));
        assertEquals(expectedOrder, report.decisions().stream().map(DesignDecisionQualityAssessment::decision).toList());

        DesignDecisionQualityAssessment tracedAssessment = assessment(report, traced);
        assertTrue(tracedAssessment.tracedByOwningChange());
        assertEquals(DecisionJustificationStatus.UNAVAILABLE_IN_NORMALIZED_MODEL, tracedAssessment.justificationStatus());
        assertEquals(List.of(QualityFindingCode.DECISION_JUSTIFICATION_UNAVAILABLE), codes(tracedAssessment.findings()));
        assertEquals(DiagnosticSeverity.INFO, tracedAssessment.findings().getFirst().severity());
        assertEquals(DecisionJustificationStatus.UNAVAILABLE_IN_NORMALIZED_MODEL.name(),
                tracedAssessment.findings().getFirst().details().get("status"));

        DesignDecisionQualityAssessment untracedAssessment = assessment(report, untraced);
        assertFalse(untracedAssessment.tracedByOwningChange());
        assertEquals(Set.of(QualityFindingCode.DESIGN_DECISION_WITHOUT_TRACE,
                        QualityFindingCode.DECISION_JUSTIFICATION_UNAVAILABLE),
                Set.copyOf(codes(untracedAssessment.findings())));
        QualityFinding missingTrace = untracedAssessment.findings().stream()
                .filter(finding -> finding.code() == QualityFindingCode.DESIGN_DECISION_WITHOUT_TRACE)
                .findFirst().orElseThrow();
        assertEquals(DiagnosticSeverity.WARNING, missingTrace.severity());
        assertEquals(change.id().toString(), missingTrace.details().get("changeId"));
        assertEquals(List.of(EVIDENCE), missingTrace.evidenceIds());

        assertTrue(report.externalReferences().isEmpty());
        assertEquals(3, report.findings().size(), "the report gathers the findings of every assessment");
    }

    @Test
    void aDecidedByLinkToAnotherDecisionDoesNotTraceThisOne() {
        publish();
        links.add(changeRef, TraceabilityRelationType.DECIDED_BY,
                new TraceabilityEntityRef(TraceabilityEntityKind.DESIGN_DECISION, DomainIdentity.generate()));
        links.add(changeRef, TraceabilityRelationType.DECIDED_BY,
                new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, traced.id().value()));

        DecisionReferenceQualityReport report = service.assessActive(projectId).orElseThrow();

        assertFalse(assessment(report, traced).tracedByOwningChange(),
                "only a DESIGN_DECISION target with this identity traces the decision");
    }

    @Test
    void eachExternalAvailabilityButResolvedIsAWarningNamedForItAndBrokenLinksAreKept() {
        publish();
        ExternalReference resolved = references.add(active.id(), state(ExternalReferenceResolutionState.RESOLVED));
        ExternalReference unresolved = references.add(active.id(), state(ExternalReferenceResolutionState.UNRESOLVED));
        ExternalReference stale = references.add(active.id(), state(ExternalReferenceResolutionState.STALE));
        ExternalReference unvalidated = references.add(active.id(), state(ExternalReferenceResolutionState.UNVALIDATED));
        TraceabilityLink toResolved = links.add(changeRef, TraceabilityRelationType.LINKS_TO_CODE, external(resolved.id().value()));
        TraceabilityLink toUnresolved = links.add(changeRef, TraceabilityRelationType.LINKS_TO_CODE, external(unresolved.id().value()));
        TraceabilityLink toStale = links.add(changeRef, TraceabilityRelationType.LINKS_TO_TEST, external(stale.id().value()));
        TraceabilityLink fromRequirement = links.add(
                new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, requirement.id().value()),
                TraceabilityRelationType.LINKS_TO_TEST, external(unvalidated.id().value()));
        TraceabilityLink broken = links.add(
                new TraceabilityEntityRef(TraceabilityEntityKind.DESIGN_DECISION, untraced.id().value()),
                TraceabilityRelationType.LINKS_TO_CODE, external(DomainIdentity.generate()));
        links.add(changeRef, TraceabilityRelationType.REFINES,
                new TraceabilityEntityRef(TraceabilityEntityKind.SPECIFICATION, requirement.specificationId().value()));

        DecisionReferenceQualityReport report = service.assessActive(projectId).orElseThrow();

        List<TraceabilityLink> expectedOrder = new ArrayList<>(
                List.of(toResolved, toUnresolved, toStale, fromRequirement, broken));
        expectedOrder.sort(Comparator.comparing(link -> link.id().toString()));
        assertEquals(expectedOrder, report.externalReferences().stream()
                .map(ExternalReferenceQualityAssessment::view).map(ExternalTraceabilityView::link).toList(),
                "only external targets are assessed, in link-identity order");

        assertTrue(external(report, toResolved).findings().isEmpty(), "a resolved reference is not a finding");
        assertFinding(external(report, toUnresolved), QualityFindingCode.EXTERNAL_REFERENCE_UNRESOLVED,
                "External reference is unresolved");
        assertFinding(external(report, toStale), QualityFindingCode.EXTERNAL_REFERENCE_STALE,
                "External reference is stale");
        assertFinding(external(report, fromRequirement), QualityFindingCode.EXTERNAL_REFERENCE_UNVALIDATED,
                "External reference has not been validated");
        ExternalReferenceQualityAssessment brokenAssessment = external(report, broken);
        assertEquals(ExternalTraceabilityAvailability.BROKEN_REFERENCE, brokenAssessment.view().availability());
        assertFinding(brokenAssessment, QualityFindingCode.EXTERNAL_REFERENCE_BROKEN,
                "External traceability link targets a missing reference");
        QualityFinding brokenFinding = brokenAssessment.findings().getFirst();
        assertEquals(broken.id().toString(), brokenFinding.details().get("traceabilityLinkId"));
        assertEquals(TraceabilityEntityKind.DESIGN_DECISION.name(), brokenFinding.details().get("sourceKind"));
        assertEquals(broken.target(), brokenFinding.subject());
    }

    @Test
    void aSnapshotAssessmentRequiresAKnownPublishedSnapshotWithContent() {
        assertTrue(service.assessActive(ProjectSpecificationId.generate()).isEmpty());

        KnowledgeSnapshotId unknown = KnowledgeSnapshotId.generate();
        KnowledgeStoreException missing = assertThrows(KnowledgeStoreException.class,
                () -> service.assessSnapshot(unknown));
        assertEquals("unknown knowledge snapshot: " + unknown, missing.getMessage());

        KnowledgeSnapshotMetadata ready = snapshots.add(KnowledgeSnapshotState.READY);
        KnowledgeStoreException unpublished = assertThrows(KnowledgeStoreException.class,
                () -> service.assessSnapshot(ready.id()));
        assertEquals("quality analysis requires an ACTIVE or RETIRED snapshot: " + ready.id() + " is READY",
                unpublished.getMessage());

        KnowledgeSnapshotMetadata retired = snapshots.add(KnowledgeSnapshotState.RETIRED);
        KnowledgeStoreException noContent = assertThrows(KnowledgeStoreException.class,
                () -> service.assessSnapshot(retired.id()));
        assertEquals("published snapshot has no business-content projection: " + retired.id(), noContent.getMessage());

        publish();
        assertEquals(active, service.assessSnapshot(active.id()).snapshot());
    }

    @Test
    void anAssessmentSortsItsFindingsAndRefusesAMissingOne() {
        TraceabilityEntityRef first = new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, DomainIdentity.generate());
        TraceabilityEntityRef second = new TraceabilityEntityRef(TraceabilityEntityKind.DESIGN_DECISION, traced.id().value());
        QualityFinding later = finding(second);
        QualityFinding earlier = finding(first);

        DesignDecisionQualityAssessment decision = new DesignDecisionQualityAssessment(
                traced, true, DecisionJustificationStatus.UNAVAILABLE_IN_NORMALIZED_MODEL, List.of(later, earlier));
        assertEquals(List.of(earlier, later), decision.findings());

        NullPointerException missingFinding = assertThrows(NullPointerException.class,
                () -> new DesignDecisionQualityAssessment(traced, true,
                        DecisionJustificationStatus.UNAVAILABLE_IN_NORMALIZED_MODEL, Arrays.asList(later, null)));
        assertEquals("findings item", missingFinding.getMessage());
        NullPointerException missingStatus = assertThrows(NullPointerException.class,
                () -> new DesignDecisionQualityAssessment(traced, true, null, List.of()));
        assertEquals("justificationStatus", missingStatus.getMessage());

        TraceabilityLink link = new Links().add(first, TraceabilityRelationType.LINKS_TO_CODE,
                external(DomainIdentity.generate()));
        ExternalTraceabilityView view = new ExternalTraceabilityView(
                link, Optional.empty(), ExternalTraceabilityAvailability.BROKEN_REFERENCE);
        assertEquals(List.of(earlier, later),
                new ExternalReferenceQualityAssessment(view, List.of(later, earlier)).findings());
        NullPointerException missingView = assertThrows(NullPointerException.class,
                () -> new ExternalReferenceQualityAssessment(null, List.of()));
        assertEquals("view", missingView.getMessage());
        NullPointerException missingExternalFinding = assertThrows(NullPointerException.class,
                () -> new ExternalReferenceQualityAssessment(view, Arrays.asList(earlier, null)));
        assertEquals("findings item", missingExternalFinding.getMessage());
    }

    private void publish() {
        contents.put(new SnapshotBusinessContent(active.id(), SpecificationVersionId.generate(), List.of(),
                List.of(), List.of(change), List.of(), List.of(traced, untraced), List.of(), List.of(),
                List.of(new Evidence(EVIDENCE, SourceLocator.file("specs/r.md"), Optional.empty(), Optional.empty()))));
        requirements.add(active.id(), requirement);
    }

    private DesignDecision decision(String title) {
        return new DesignDecision(DesignDecisionId.generate(), change.id(), title, "Decided " + title, provenance());
    }

    private static QualityFinding finding(TraceabilityEntityRef subject) {
        return new QualityFinding(QualityFindingCode.DECISION_JUSTIFICATION_UNAVAILABLE, DiagnosticSeverity.INFO,
                QualityEvidenceKind.DETERMINISTIC, subject, "unavailable", Map.of(), Optional.empty(), List.of());
    }

    private static TraceabilityEntityRef external(DomainIdentity identity) {
        return new TraceabilityEntityRef(TraceabilityEntityKind.EXTERNAL_REFERENCE, identity);
    }

    private static ExternalReference state(ExternalReferenceResolutionState state) {
        ExternalReferenceTarget target = new ExternalReferenceTarget(
                "MINOS", Optional.of("morpheus-engine"), "CODE_SYMBOL", "com.morpheus.Sample", Optional.of("rev-1"));
        ExternalReference unvalidated = ExternalReference.unvalidated(
                ExternalReferenceId.generate(), DomainIdentity.generate(), target, Optional.empty());
        return switch (state) {
            case UNVALIDATED -> unvalidated;
            case RESOLVED -> unvalidated.transition(state, ExternalReferenceResolutionReason.RESOLVED,
                    Optional.of(new ResolvedExternalTarget(target, Map.of("kind", "class"))), T0);
            case UNRESOLVED -> unvalidated.transition(state, ExternalReferenceResolutionReason.TARGET_NOT_FOUND,
                    Optional.empty(), T0);
            case STALE -> unvalidated.transition(state, ExternalReferenceResolutionReason.TARGET_UNAVAILABLE,
                    Optional.empty(), T0);
        };
    }

    private static DesignDecisionQualityAssessment assessment(DecisionReferenceQualityReport report, DesignDecision decision) {
        return report.decisions().stream().filter(item -> item.decision().equals(decision)).findFirst().orElseThrow();
    }

    private static ExternalReferenceQualityAssessment external(DecisionReferenceQualityReport report, TraceabilityLink link) {
        return report.externalReferences().stream()
                .filter(item -> item.view().link().equals(link)).findFirst().orElseThrow();
    }

    private static void assertFinding(ExternalReferenceQualityAssessment assessment, QualityFindingCode code, String message) {
        assertEquals(1, assessment.findings().size());
        QualityFinding finding = assessment.findings().getFirst();
        assertEquals(code, finding.code());
        assertEquals(message, finding.message());
        assertEquals(DiagnosticSeverity.WARNING, finding.severity());
        assertEquals(assessment.view().availability().name(), finding.details().get("availability"));
        assertEquals(List.of(EVIDENCE), finding.evidenceIds());
    }

    private static List<QualityFindingCode> codes(List<QualityFinding> findings) {
        return findings.stream().map(QualityFinding::code).toList();
    }

    private static Provenance provenance() {
        return new Provenance(new ProviderId("openspec"), Optional.of("1"), SourceLocator.file("specs/r.md"),
                Optional.of("R-1"), Optional.of("rev"), EVIDENCE);
    }

    private final class Snapshots implements SpecificationKnowledgeStore {
        private final Map<KnowledgeSnapshotId, KnowledgeSnapshotMetadata> byId = new LinkedHashMap<>();

        KnowledgeSnapshotMetadata add(KnowledgeSnapshotState state) {
            KnowledgeSnapshotMetadata snapshot = new KnowledgeSnapshotMetadata(KnowledgeSnapshotId.generate(),
                    projectId, Optional.empty(), state, Optional.of("rev"), T0);
            byId.put(snapshot.id(), snapshot);
            return snapshot;
        }

        @Override public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId id) {
            return byId.values().stream()
                    .filter(snapshot -> snapshot.projectId().equals(id) && snapshot.state() == KnowledgeSnapshotState.ACTIVE)
                    .findFirst();
        }

        @Override public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId id) {
            return Optional.ofNullable(byId.get(id));
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

    private static final class Contents implements SnapshotBusinessContentStore {
        private final Map<KnowledgeSnapshotId, SnapshotBusinessContent> byId = new LinkedHashMap<>();

        void put(SnapshotBusinessContent content) {
            byId.put(content.snapshotId(), content);
        }

        @Override public Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override public void putSnapshotContent(SnapshotBusinessContent content) { throw new UnsupportedOperationException(); }
    }

    private static final class Requirements implements VersionedRequirementStore {
        private final List<RequirementVersionRecord> records = new ArrayList<>();

        void add(KnowledgeSnapshotId snapshotId, Requirement requirement) {
            records.add(new RequirementVersionRecord(snapshotId, new EntityVersion<>(EntityVersionId.generate(),
                    requirement.id().value(), SpecificationVersionId.generate(), TemporalState.CURRENT, requirement)));
        }

        @Override public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId id) {
            return records.stream().filter(record -> record.snapshotId().equals(id)).toList();
        }

        @Override public Optional<RequirementVersionRecord> currentRequirement(KnowledgeSnapshotId id, DomainIdentity identity) { throw new UnsupportedOperationException(); }
        @Override public void putSpecificationVersion(SpecificationVersion version) { throw new UnsupportedOperationException(); }
        @Override public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) { throw new UnsupportedOperationException(); }
        @Override public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) { throw new UnsupportedOperationException(); }
        @Override public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId id) { throw new UnsupportedOperationException(); }
        @Override public void putRequirementVersion(RequirementVersionRecord record) { throw new UnsupportedOperationException(); }
        @Override public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) { throw new UnsupportedOperationException(); }
    }

    private static final class Links implements TraceabilityStore {
        private final List<TraceabilityLink> links = new ArrayList<>();

        TraceabilityLink add(TraceabilityEntityRef source, TraceabilityRelationType relation, TraceabilityEntityRef target) {
            TraceabilityLink link = new TraceabilityLink(TraceabilityLinkId.generate(), source, relation, target,
                    TraceabilityLinkOrigin.EXPLICIT, TraceabilityResolutionState.RESOLVED, Optional.empty(),
                    Set.of(EVIDENCE), T0);
            links.add(link);
            return link;
        }

        @Override public List<TraceabilityLink> outgoing(KnowledgeSnapshotId id, TraceabilityEntityRef source,
                Set<TraceabilityRelationType> relationTypes) {
            return links.stream()
                    .filter(link -> link.source().equals(source) && relationTypes.contains(link.relationType()))
                    .toList();
        }

        @Override public List<TraceabilityLink> incoming(KnowledgeSnapshotId id, TraceabilityEntityRef target,
                Set<TraceabilityRelationType> relationTypes) {
            throw new UnsupportedOperationException();
        }

        @Override public void putLink(KnowledgeSnapshotId id, TraceabilityLink link) { throw new UnsupportedOperationException(); }
        @Override public void putLinks(KnowledgeSnapshotId id, List<TraceabilityLink> batch) { throw new UnsupportedOperationException(); }
        @Override public Optional<TraceabilityLink> findLink(KnowledgeSnapshotId id, TraceabilityLinkId linkId) { throw new UnsupportedOperationException(); }
    }

    private static final class References implements ExternalReferenceStore {
        private final Map<String, ExternalReference> byKey = new LinkedHashMap<>();

        ExternalReference add(KnowledgeSnapshotId snapshotId, ExternalReference reference) {
            byKey.put(snapshotId + "/" + reference.id(), reference);
            return reference;
        }

        @Override public Optional<ExternalReference> findReference(KnowledgeSnapshotId id, ExternalReferenceId referenceId) {
            return Optional.ofNullable(byKey.get(id + "/" + referenceId));
        }

        @Override public void putReference(KnowledgeSnapshotId id, ExternalReference reference) { throw new UnsupportedOperationException(); }
        @Override public List<ExternalReference> findByOwner(KnowledgeSnapshotId id, DomainIdentity ownerId) { throw new UnsupportedOperationException(); }
    }
}
