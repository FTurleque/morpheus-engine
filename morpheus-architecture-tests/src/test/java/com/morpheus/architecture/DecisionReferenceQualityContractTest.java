package com.morpheus.architecture;

import com.morpheus.application.quality.DecisionJustificationStatus;
import com.morpheus.application.quality.DecisionReferenceQualityReport;
import com.morpheus.application.quality.DecisionReferenceQualityService;
import com.morpheus.application.quality.QualityEvidenceKind;
import com.morpheus.application.quality.QualityFindingCode;
import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.application.traceability.ExternalTraceabilityAvailability;
import com.morpheus.application.traceability.ExternalTraceabilityLinkFactory;
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
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import com.morpheus.store.memory.MemoryExternalReferenceStore;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import com.morpheus.store.sqlite.SqliteExternalReferenceStore;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionReferenceQualityContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T20:45:00Z");
    private static final ExternalTraceabilityLinkFactory LINK_FACTORY = new ExternalTraceabilityLinkFactory();

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqliteProduceSameDecisionAndReferenceQualityReport() {
        Fixture fixture = Fixture.create();

        var memoryCore = new MemorySpecificationKnowledgeStore();
        var memoryContent = new MemorySnapshotBusinessContentStore(memoryCore, memoryCore);
        var memoryTrace = new MemoryTraceabilityStore(memoryCore);
        var memoryReferences = new MemoryExternalReferenceStore(memoryCore);
        seed(memoryCore, memoryCore, memoryContent, memoryTrace, memoryReferences, fixture);
        DecisionReferenceQualityReport memory = service(
                memoryCore, memoryContent, memoryCore, memoryTrace, memoryReferences)
                .assessActive(fixture.projectId()).orElseThrow();

        Path database = tempDir.resolve("decision-reference-parity.db");
        DecisionReferenceQualityReport sqlite;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            seed(snapshots, versions, content, traceability, references, fixture);
            sqlite = service(snapshots, content, versions, traceability, references)
                    .assessActive(fixture.projectId()).orElseThrow();
        }

        assertEquals(memory, sqlite);
        assertEquals(2, memory.decisions().size());
        assertEquals(5, memory.externalReferences().size());
    }

    @Test
    void decisionTraceAndJustificationAreReportedWithoutInferringRationale() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var trace = new MemoryTraceabilityStore(core);
        var references = new MemoryExternalReferenceStore(core);
        seed(core, core, content, trace, references, fixture);

        DecisionReferenceQualityReport report = service(core, content, core, trace, references)
                .assessActive(fixture.projectId()).orElseThrow();

        var traced = report.decisions().stream()
                .filter(item -> item.decision().id().equals(fixture.tracedDecision().id()))
                .findFirst().orElseThrow();
        var untraced = report.decisions().stream()
                .filter(item -> item.decision().id().equals(fixture.untracedDecision().id()))
                .findFirst().orElseThrow();

        assertTrue(traced.tracedByOwningChange());
        assertFalse(untraced.tracedByOwningChange());
        assertEquals(DecisionJustificationStatus.UNAVAILABLE_IN_NORMALIZED_MODEL, traced.justificationStatus());
        assertEquals(DecisionJustificationStatus.UNAVAILABLE_IN_NORMALIZED_MODEL, untraced.justificationStatus());
        assertFalse(traced.findings().stream().anyMatch(f -> f.code() == QualityFindingCode.DESIGN_DECISION_WITHOUT_TRACE));
        assertTrue(untraced.findings().stream().anyMatch(f ->
                f.code() == QualityFindingCode.DESIGN_DECISION_WITHOUT_TRACE
                        && f.severity() == DiagnosticSeverity.WARNING
                        && f.evidenceKind() == QualityEvidenceKind.DETERMINISTIC));
        assertTrue(report.decisions().stream().allMatch(item -> item.findings().stream().anyMatch(f ->
                f.code() == QualityFindingCode.DECISION_JUSTIFICATION_UNAVAILABLE
                        && f.severity() == DiagnosticSeverity.INFO)));
    }

    @Test
    void externalAvailabilityMapsExactlyAndResolvedReferenceIsSilent() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var trace = new MemoryTraceabilityStore(core);
        var references = new MemoryExternalReferenceStore(core);
        seed(core, core, content, trace, references, fixture);

        DecisionReferenceQualityReport report = service(core, content, core, trace, references)
                .assessActive(fixture.projectId()).orElseThrow();

        assertEquals(5, report.externalReferences().size());
        assertFinding(report, ExternalTraceabilityAvailability.REFERENCE_UNVALIDATED,
                QualityFindingCode.EXTERNAL_REFERENCE_UNVALIDATED);
        assertFinding(report, ExternalTraceabilityAvailability.REFERENCE_UNRESOLVED,
                QualityFindingCode.EXTERNAL_REFERENCE_UNRESOLVED);
        assertFinding(report, ExternalTraceabilityAvailability.REFERENCE_STALE,
                QualityFindingCode.EXTERNAL_REFERENCE_STALE);
        assertFinding(report, ExternalTraceabilityAvailability.BROKEN_REFERENCE,
                QualityFindingCode.EXTERNAL_REFERENCE_BROKEN);
        assertTrue(report.externalReferences().stream()
                .filter(item -> item.view().availability() == ExternalTraceabilityAvailability.REFERENCE_RESOLVED)
                .allMatch(item -> item.findings().isEmpty()));
    }

    @Test
    void brokenExternalLinkRemainsAuditableWithoutStoredReference() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var trace = new MemoryTraceabilityStore(core);
        var references = new MemoryExternalReferenceStore(core);
        seed(core, core, content, trace, references, fixture);

        var broken = service(core, content, core, trace, references)
                .assessActive(fixture.projectId()).orElseThrow()
                .externalReferences().stream()
                .filter(item -> item.view().availability() == ExternalTraceabilityAvailability.BROKEN_REFERENCE)
                .findFirst().orElseThrow();

        assertTrue(broken.view().reference().isEmpty());
        assertEquals(fixture.brokenLink(), broken.view().link());
        assertEquals(List.of(QualityFindingCode.EXTERNAL_REFERENCE_BROKEN),
                broken.findings().stream().map(f -> f.code()).toList());
        assertEquals(fixture.brokenLink().evidenceIds().stream().sorted().toList(),
                broken.findings().getFirst().evidenceIds());
    }

    @Test
    void activeRetiredAndReadySnapshotPolicyIsExplicit() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var trace = new MemoryTraceabilityStore(core);
        var references = new MemoryExternalReferenceStore(core);
        seed(core, core, content, trace, references, fixture);
        DecisionReferenceQualityService service = service(core, content, core, trace, references);

        assertEquals(fixture.activeSnapshotId(), service.assessActive(fixture.projectId()).orElseThrow().snapshot().id());
        assertEquals(KnowledgeSnapshotState.RETIRED, service.assessSnapshot(fixture.retiredSnapshotId()).snapshot().state());
        assertThrows(KnowledgeStoreException.class, () -> service.assessSnapshot(fixture.readySnapshotId()));
        assertThrows(KnowledgeStoreException.class, () -> service.assessSnapshot(KnowledgeSnapshotId.generate()));

        var empty = new MemorySpecificationKnowledgeStore();
        var emptyContent = new MemorySnapshotBusinessContentStore(empty, empty);
        var emptyTrace = new MemoryTraceabilityStore(empty);
        var emptyRefs = new MemoryExternalReferenceStore(empty);
        assertTrue(service(empty, emptyContent, empty, emptyTrace, emptyRefs)
                .assessActive(ProjectSpecificationId.generate()).isEmpty());
    }

    @Test
    void sqliteReopenPreservesDecisionAndReferenceQualityReport() {
        Fixture fixture = Fixture.create();
        Path database = tempDir.resolve("decision-reference-reopen.db");
        DecisionReferenceQualityReport before;

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            seed(snapshots, versions, content, traceability, references, fixture);
            before = service(snapshots, content, versions, traceability, references)
                    .assessActive(fixture.projectId()).orElseThrow();
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            DecisionReferenceQualityReport after = service(snapshots, content, versions, traceability, references)
                    .assessActive(fixture.projectId()).orElseThrow();
            assertEquals(before, after);
        }
    }

    @Test
    void outputOrderingIsStableByDecisionAndTraceabilityIdentity() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var trace = new MemoryTraceabilityStore(core);
        var references = new MemoryExternalReferenceStore(core);
        seed(core, core, content, trace, references, fixture);

        DecisionReferenceQualityReport first = service(core, content, core, trace, references)
                .assessActive(fixture.projectId()).orElseThrow();
        DecisionReferenceQualityReport second = service(core, content, core, trace, references)
                .assessActive(fixture.projectId()).orElseThrow();

        assertEquals(first, second);
        assertEquals(first.decisions().stream().map(item -> item.decision().id().toString()).sorted().toList(),
                first.decisions().stream().map(item -> item.decision().id().toString()).toList());
        assertEquals(first.externalReferences().stream().map(item -> item.view().link().id().toString()).sorted().toList(),
                first.externalReferences().stream().map(item -> item.view().link().id().toString()).toList());
        assertEquals(first.findings().stream().sorted().toList(), first.findings());
    }

    private void assertFinding(
            DecisionReferenceQualityReport report,
            ExternalTraceabilityAvailability availability,
            QualityFindingCode code) {
        var assessment = report.externalReferences().stream()
                .filter(item -> item.view().availability() == availability)
                .findFirst().orElseThrow();
        assertEquals(List.of(code), assessment.findings().stream().map(f -> f.code()).toList());
        assertTrue(assessment.findings().stream().allMatch(f ->
                f.severity() == DiagnosticSeverity.WARNING
                        && f.evidenceKind() == QualityEvidenceKind.DETERMINISTIC
                        && f.confidence().isEmpty()));
    }

    private DecisionReferenceQualityService service(
            SpecificationKnowledgeStore snapshots,
            SnapshotBusinessContentStore content,
            VersionedRequirementStore versions,
            TraceabilityStore traceability,
            ExternalReferenceStore references) {
        return new DecisionReferenceQualityService(snapshots, content, versions, traceability, references);
    }

    private void seed(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore versions,
            SnapshotBusinessContentStore content,
            TraceabilityStore traceability,
            ExternalReferenceStore references,
            Fixture fixture) {
        snapshots.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace-m6-s4")));

        putPublishedCandidate(snapshots, versions, content,
                fixture.retiredSnapshotId(), fixture.retiredVersionId(), Optional.empty(), fixture.retiredContent(), "retired");
        snapshots.activateSnapshot(fixture.retiredSnapshotId(), Optional.empty());

        putPublishedCandidate(snapshots, versions, content,
                fixture.activeSnapshotId(), fixture.activeVersionId(), Optional.of(fixture.retiredSnapshotId()), fixture.activeContent(), "active");
        fixture.referencesToPersist().forEach(reference -> references.putReference(fixture.activeSnapshotId(), reference));
        fixture.activeLinks().forEach(link -> traceability.putLink(fixture.activeSnapshotId(), link));
        snapshots.activateSnapshot(fixture.activeSnapshotId(), Optional.of(fixture.retiredSnapshotId()));

        putPublishedCandidate(snapshots, versions, content,
                fixture.readySnapshotId(), fixture.readyVersionId(), Optional.of(fixture.activeSnapshotId()), fixture.readyContent(), "ready");
    }

    private void putPublishedCandidate(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore versions,
            SnapshotBusinessContentStore content,
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            Optional<KnowledgeSnapshotId> predecessor,
            SnapshotBusinessContent businessContent,
            String revision) {
        versions.putSpecificationVersion(new SpecificationVersion(
                versionId,
                businessContent.specifications().getFirst().projectId(),
                Optional.empty(),
                Optional.of("provider-v1"),
                Optional.of(revision),
                T0,
                Optional.empty()));
        snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId,
                businessContent.specifications().getFirst().projectId(),
                predecessor,
                KnowledgeSnapshotState.READY,
                Optional.of(revision),
                T0));
        versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
        content.putSnapshotContent(businessContent);
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId retiredSnapshotId,
            KnowledgeSnapshotId activeSnapshotId,
            KnowledgeSnapshotId readySnapshotId,
            SpecificationVersionId retiredVersionId,
            SpecificationVersionId activeVersionId,
            SpecificationVersionId readyVersionId,
            SnapshotBusinessContent retiredContent,
            SnapshotBusinessContent activeContent,
            SnapshotBusinessContent readyContent,
            DesignDecision tracedDecision,
            DesignDecision untracedDecision,
            List<ExternalReference> referencesToPersist,
            List<TraceabilityLink> activeLinks,
            TraceabilityLink brokenLink) {

        static Fixture create() {
            ProjectSpecificationId projectId = ProjectSpecificationId.generate();
            KnowledgeSnapshotId retiredSnapshotId = KnowledgeSnapshotId.generate();
            KnowledgeSnapshotId activeSnapshotId = KnowledgeSnapshotId.generate();
            KnowledgeSnapshotId readySnapshotId = KnowledgeSnapshotId.generate();
            SpecificationVersionId retiredVersionId = SpecificationVersionId.generate();
            SpecificationVersionId activeVersionId = SpecificationVersionId.generate();
            SpecificationVersionId readyVersionId = SpecificationVersionId.generate();

            Evidence retiredEvidence = evidence("retired");
            Evidence activeEvidence = evidence("active");
            Evidence readyEvidence = evidence("ready");
            Provenance retiredProvenance = provenance(retiredEvidence.id(), "retired");
            Provenance activeProvenance = provenance(activeEvidence.id(), "active");
            Provenance readyProvenance = provenance(readyEvidence.id(), "ready");

            Specification retiredSpecification = specification(projectId, "retired", retiredProvenance);
            SnapshotBusinessContent retiredContent = content(
                    retiredSnapshotId, retiredVersionId, retiredSpecification, List.of(), List.of(), retiredEvidence);

            Specification activeSpecification = specification(projectId, "active", activeProvenance);
            ChangeProposal change = change(projectId, "quality change", activeProvenance);
            DesignDecision tracedDecision = new DesignDecision(
                    DesignDecisionId.generate(), change.id(), "Traced decision", "Use explicit trace", activeProvenance);
            DesignDecision untracedDecision = new DesignDecision(
                    DesignDecisionId.generate(), change.id(), "Untraced decision", "Keep separate concern", activeProvenance);
            SnapshotBusinessContent activeContent = content(
                    activeSnapshotId,
                    activeVersionId,
                    activeSpecification,
                    List.of(change),
                    List.of(untracedDecision, tracedDecision),
                    activeEvidence);

            TraceabilityLink decisionTrace = new TraceabilityLink(
                    TraceabilityLinkId.generate(),
                    new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, change.id().value()),
                    TraceabilityRelationType.DECIDED_BY,
                    new TraceabilityEntityRef(TraceabilityEntityKind.DESIGN_DECISION, tracedDecision.id().value()),
                    TraceabilityLinkOrigin.DERIVED,
                    TraceabilityResolutionState.RESOLVED,
                    Optional.empty(),
                    Set.of(activeEvidence.id()),
                    T0.plusSeconds(1));

            ExternalReference unvalidated = ExternalReference.unvalidated(
                    ExternalReferenceId.generate(), change.id().value(), target("unvalidated"), Optional.of(activeProvenance));
            ExternalReference unresolved = ExternalReference.unvalidated(
                            ExternalReferenceId.generate(), change.id().value(), target("unresolved"), Optional.of(activeProvenance))
                    .transition(ExternalReferenceResolutionState.UNRESOLVED,
                            ExternalReferenceResolutionReason.TARGET_NOT_FOUND, Optional.empty(), T0.plusSeconds(2));
            ExternalReference resolved = ExternalReference.unvalidated(
                            ExternalReferenceId.generate(), change.id().value(), target("resolved"), Optional.of(activeProvenance))
                    .transition(ExternalReferenceResolutionState.UNRESOLVED,
                            ExternalReferenceResolutionReason.TARGET_NOT_FOUND, Optional.empty(), T0.plusSeconds(3))
                    .transition(ExternalReferenceResolutionState.RESOLVED,
                            ExternalReferenceResolutionReason.RESOLVED,
                            Optional.of(new ResolvedExternalTarget(target("resolved"), Map.of("kind", "symbol"))),
                            T0.plusSeconds(4));
            ExternalReference stale = ExternalReference.unvalidated(
                            ExternalReferenceId.generate(), change.id().value(), target("stale"), Optional.of(activeProvenance))
                    .transition(ExternalReferenceResolutionState.UNRESOLVED,
                            ExternalReferenceResolutionReason.TARGET_NOT_FOUND, Optional.empty(), T0.plusSeconds(5))
                    .transition(ExternalReferenceResolutionState.RESOLVED,
                            ExternalReferenceResolutionReason.RESOLVED,
                            Optional.of(new ResolvedExternalTarget(target("stale"), Map.of("kind", "symbol"))),
                            T0.plusSeconds(6))
                    .transition(ExternalReferenceResolutionState.STALE,
                            ExternalReferenceResolutionReason.TARGET_REMOVED, Optional.empty(), T0.plusSeconds(7));
            ExternalReference brokenReference = ExternalReference.unvalidated(
                    ExternalReferenceId.generate(), change.id().value(), target("broken"), Optional.of(activeProvenance));

            TraceabilityEntityRef source = new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, change.id().value());
            TraceabilityLink unvalidatedLink = externalLink(source, TraceabilityRelationType.LINKS_TO_CODE, unvalidated, activeEvidence.id(), 10);
            TraceabilityLink unresolvedLink = externalLink(source, TraceabilityRelationType.LINKS_TO_TEST, unresolved, activeEvidence.id(), 11);
            TraceabilityLink resolvedLink = externalLink(source, TraceabilityRelationType.RELATED_TO, resolved, activeEvidence.id(), 12);
            TraceabilityLink staleLink = externalLink(source, TraceabilityRelationType.LINKS_TO_CODE, stale, activeEvidence.id(), 13);
            TraceabilityLink brokenLink = externalLink(source, TraceabilityRelationType.LINKS_TO_TEST, brokenReference, activeEvidence.id(), 14);

            Specification readySpecification = specification(projectId, "ready", readyProvenance);
            SnapshotBusinessContent readyContent = content(
                    readySnapshotId, readyVersionId, readySpecification, List.of(), List.of(), readyEvidence);

            return new Fixture(
                    projectId,
                    retiredSnapshotId,
                    activeSnapshotId,
                    readySnapshotId,
                    retiredVersionId,
                    activeVersionId,
                    readyVersionId,
                    retiredContent,
                    activeContent,
                    readyContent,
                    tracedDecision,
                    untracedDecision,
                    List.of(unvalidated, unresolved, resolved, stale),
                    List.of(decisionTrace, staleLink, resolvedLink, brokenLink, unvalidatedLink, unresolvedLink),
                    brokenLink);
        }

        private static SnapshotBusinessContent content(
                KnowledgeSnapshotId snapshotId,
                SpecificationVersionId versionId,
                Specification specification,
                List<ChangeProposal> changes,
                List<DesignDecision> decisions,
                Evidence evidence) {
            return new SnapshotBusinessContent(
                    snapshotId,
                    versionId,
                    List.of(specification),
                    List.of(),
                    changes,
                    List.of(),
                    decisions,
                    List.of(),
                    List.of(evidence));
        }

        private static Specification specification(
                ProjectSpecificationId projectId,
                String name,
                Provenance provenance) {
            return new Specification(
                    SpecificationId.generate(), projectId, name, name + " specification", Optional.empty(), provenance);
        }

        private static ChangeProposal change(
                ProjectSpecificationId projectId,
                String title,
                Provenance provenance) {
            return new ChangeProposal(
                    ChangeId.generate(), projectId, Optional.empty(), title, title + " intent",
                    List.of(), List.of(), List.of(), provenance);
        }

        private static Evidence evidence(String name) {
            return new Evidence(
                    EvidenceId.generate(), SourceLocator.file("specs/" + name + ".md"), Optional.empty(), Optional.of("sha256:" + name));
        }

        private static Provenance provenance(EvidenceId evidenceId, String name) {
            return new Provenance(
                    new ProviderId("m6-s4-fixture"), Optional.of("1"), SourceLocator.file("specs/" + name + ".md"),
                    Optional.of(name), Optional.of("revision-" + name), evidenceId);
        }

        private static ExternalReferenceTarget target(String suffix) {
            return new ExternalReferenceTarget(
                    "MINOS", Optional.of("morpheus-engine"), "CODE_SYMBOL",
                    "com.morpheus." + suffix, Optional.of("rev-" + suffix));
        }

        private static TraceabilityLink externalLink(
                TraceabilityEntityRef source,
                TraceabilityRelationType relation,
                ExternalReference reference,
                EvidenceId evidenceId,
                long seconds) {
            return LINK_FACTORY.create(
                    TraceabilityLinkId.generate(), source, relation, reference, TraceabilityLinkOrigin.EXPLICIT,
                    Optional.empty(), Set.of(evidenceId), T0.plusSeconds(seconds));
        }
    }
}
