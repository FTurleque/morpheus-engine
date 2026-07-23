package com.morpheus.architecture;

import com.morpheus.application.quality.DecisionJustificationStatus;
import com.morpheus.application.quality.DecisionReferenceQualityReport;
import com.morpheus.application.quality.DecisionReferenceQualityService;
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
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
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
    void memoryAndSqliteProduceTheSameReport() {
        Fixture fixture = Fixture.create();
        DecisionReferenceQualityReport memory;
        var memoryCore = new MemorySpecificationKnowledgeStore();
        var memoryContent = new MemorySnapshotBusinessContentStore(memoryCore, memoryCore);
        var memoryTrace = new MemoryTraceabilityStore(memoryCore);
        var memoryReferences = new MemoryExternalReferenceStore(memoryCore);
        seed(memoryCore, memoryCore, memoryContent, memoryTrace, memoryReferences, fixture);
        memory = service(memoryCore, memoryContent, memoryCore, memoryTrace, memoryReferences)
                .assessActive(fixture.projectId()).orElseThrow();

        Path database = tempDir.resolve("s4-parity.db");
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
        assertEquals(memory.findings().stream().sorted().toList(), memory.findings());
    }

    @Test
    void decisionTraceIsStructuralAndJustificationRemainsUnavailable() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var trace = new MemoryTraceabilityStore(core);
        var references = new MemoryExternalReferenceStore(core);
        seed(core, core, content, trace, references, fixture);
        var report = service(core, content, core, trace, references)
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
        assertTrue(untraced.findings().stream().anyMatch(f -> f.code() == QualityFindingCode.DESIGN_DECISION_WITHOUT_TRACE));
        assertTrue(report.decisions().stream().allMatch(item -> item.findings().stream().anyMatch(f ->
                f.code() == QualityFindingCode.DECISION_JUSTIFICATION_UNAVAILABLE)));
    }

    @Test
    void externalStatesMapExactlyAndResolvedIsSilent() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var trace = new MemoryTraceabilityStore(core);
        var references = new MemoryExternalReferenceStore(core);
        seed(core, core, content, trace, references, fixture);
        var report = service(core, content, core, trace, references)
                .assessActive(fixture.projectId()).orElseThrow();

        assertCode(report, ExternalTraceabilityAvailability.REFERENCE_UNVALIDATED,
                QualityFindingCode.EXTERNAL_REFERENCE_UNVALIDATED);
        assertCode(report, ExternalTraceabilityAvailability.REFERENCE_UNRESOLVED,
                QualityFindingCode.EXTERNAL_REFERENCE_UNRESOLVED);
        assertCode(report, ExternalTraceabilityAvailability.REFERENCE_STALE,
                QualityFindingCode.EXTERNAL_REFERENCE_STALE);
        assertCode(report, ExternalTraceabilityAvailability.BROKEN_REFERENCE,
                QualityFindingCode.EXTERNAL_REFERENCE_BROKEN);
        assertTrue(report.externalReferences().stream()
                .filter(item -> item.view().availability() == ExternalTraceabilityAvailability.REFERENCE_RESOLVED)
                .allMatch(item -> item.findings().isEmpty()));
    }

    @Test
    void brokenExternalReferenceKeepsItsPersistedLinkAndEvidence() {
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
        assertEquals(fixture.brokenLink().evidenceIds().stream().sorted().toList(),
                broken.findings().getFirst().evidenceIds());
    }

    @Test
    void publishedSnapshotPolicyRemainsActiveOrRetiredOnly() {
        Fixture fixture = Fixture.create();
        var core = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(core, core);
        var trace = new MemoryTraceabilityStore(core);
        var references = new MemoryExternalReferenceStore(core);
        seed(core, core, content, trace, references, fixture);
        var service = service(core, content, core, trace, references);

        assertEquals(fixture.activeSnapshotId(), service.assessActive(fixture.projectId()).orElseThrow().snapshot().id());
        assertEquals(KnowledgeSnapshotState.RETIRED, service.assessSnapshot(fixture.retiredSnapshotId()).snapshot().state());
        assertThrows(KnowledgeStoreException.class, () -> service.assessSnapshot(fixture.readySnapshotId()));
        assertThrows(KnowledgeStoreException.class, () -> service.assessSnapshot(KnowledgeSnapshotId.generate()));
    }

    @Test
    void sqliteReopenPreservesTheExactReport() {
        Fixture fixture = Fixture.create();
        Path database = tempDir.resolve("s4-reopen.db");
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
            assertEquals(before, service(snapshots, content, versions, traceability, references)
                    .assessActive(fixture.projectId()).orElseThrow());
        }
    }

    private void assertCode(
            DecisionReferenceQualityReport report,
            ExternalTraceabilityAvailability availability,
            QualityFindingCode code) {
        var item = report.externalReferences().stream()
                .filter(value -> value.view().availability() == availability)
                .findFirst().orElseThrow();
        assertEquals(List.of(code), item.findings().stream().map(f -> f.code()).toList());
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
        putCandidate(snapshots, versions, content,
                fixture.retiredSnapshotId(), fixture.retiredVersionId(), Optional.empty(), fixture.retiredContent(), "r1");
        snapshots.activateSnapshot(fixture.retiredSnapshotId(), Optional.empty());

        putCandidate(snapshots, versions, content,
                fixture.activeSnapshotId(), fixture.activeVersionId(), Optional.of(fixture.retiredSnapshotId()), fixture.activeContent(), "r2");
        fixture.references().forEach(reference -> references.putReference(fixture.activeSnapshotId(), reference));
        fixture.links().forEach(link -> traceability.putLink(fixture.activeSnapshotId(), link));
        snapshots.activateSnapshot(fixture.activeSnapshotId(), Optional.of(fixture.retiredSnapshotId()));

        putCandidate(snapshots, versions, content,
                fixture.readySnapshotId(), fixture.readyVersionId(), Optional.of(fixture.activeSnapshotId()), fixture.readyContent(), "r3");
    }

    private void putCandidate(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore versions,
            SnapshotBusinessContentStore content,
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            Optional<KnowledgeSnapshotId> predecessor,
            SnapshotBusinessContent businessContent,
            String revision) {
        ProjectSpecificationId projectId = businessContent.specifications().getFirst().projectId();
        versions.putSpecificationVersion(new SpecificationVersion(
                versionId, projectId, Optional.empty(), Optional.of("provider-v1"), Optional.of(revision), T0, Optional.empty()));
        snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId, projectId, predecessor, KnowledgeSnapshotState.READY, Optional.of(revision), T0));
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
            List<ExternalReference> references,
            List<TraceabilityLink> links,
            TraceabilityLink brokenLink) {

        static Fixture create() {
            ProjectSpecificationId projectId = ProjectSpecificationId.generate();
            KnowledgeSnapshotId retiredId = KnowledgeSnapshotId.generate();
            KnowledgeSnapshotId activeId = KnowledgeSnapshotId.generate();
            KnowledgeSnapshotId readyId = KnowledgeSnapshotId.generate();
            SpecificationVersionId retiredVersion = SpecificationVersionId.generate();
            SpecificationVersionId activeVersion = SpecificationVersionId.generate();
            SpecificationVersionId readyVersion = SpecificationVersionId.generate();

            Evidence retiredEvidence = evidence("retired");
            Evidence activeEvidence = evidence("active");
            Evidence readyEvidence = evidence("ready");
            Provenance retiredProvenance = provenance(retiredEvidence.id(), "retired");
            Provenance activeProvenance = provenance(activeEvidence.id(), "active");
            Provenance readyProvenance = provenance(readyEvidence.id(), "ready");

            SnapshotBusinessContent retiredContent = content(
                    retiredId, retiredVersion, specification(projectId, "retired", retiredProvenance),
                    List.of(), List.of(), retiredEvidence);

            ChangeProposal change = new ChangeProposal(
                    ChangeId.generate(), projectId, Optional.empty(), "Quality change", "Quality intent",
                    List.of(), List.of(), List.of(), activeProvenance);
            DesignDecision traced = new DesignDecision(
                    DesignDecisionId.generate(), change.id(), "Traced", "Use explicit trace", activeProvenance);
            DesignDecision untraced = new DesignDecision(
                    DesignDecisionId.generate(), change.id(), "Untraced", "Keep separate concern", activeProvenance);
            SnapshotBusinessContent activeContent = content(
                    activeId, activeVersion, specification(projectId, "active", activeProvenance),
                    List.of(change), List.of(untraced, traced), activeEvidence);

            TraceabilityEntityRef changeRef = new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, change.id().value());
            TraceabilityLink decisionLink = new TraceabilityLink(
                    TraceabilityLinkId.generate(), changeRef, TraceabilityRelationType.DECIDED_BY,
                    new TraceabilityEntityRef(TraceabilityEntityKind.DESIGN_DECISION, traced.id().value()),
                    TraceabilityLinkOrigin.DERIVED, TraceabilityResolutionState.RESOLVED, Optional.empty(),
                    Set.of(activeEvidence.id()), T0.plusSeconds(1));

            ExternalReference unvalidated = unvalidated(change, activeProvenance, "unvalidated");
            ExternalReference unresolved = unvalidated(change, activeProvenance, "unresolved").transition(
                    ExternalReferenceResolutionState.UNRESOLVED, ExternalReferenceResolutionReason.TARGET_NOT_FOUND,
                    Optional.empty(), T0.plusSeconds(2));
            ExternalReference resolved = unvalidated(change, activeProvenance, "resolved")
                    .transition(ExternalReferenceResolutionState.UNRESOLVED, ExternalReferenceResolutionReason.TARGET_NOT_FOUND,
                            Optional.empty(), T0.plusSeconds(3))
                    .transition(ExternalReferenceResolutionState.RESOLVED, ExternalReferenceResolutionReason.RESOLVED,
                            Optional.of(new ResolvedExternalTarget(target("resolved"), Map.of("kind", "symbol"))), T0.plusSeconds(4));
            ExternalReference stale = unvalidated(change, activeProvenance, "stale")
                    .transition(ExternalReferenceResolutionState.UNRESOLVED, ExternalReferenceResolutionReason.TARGET_NOT_FOUND,
                            Optional.empty(), T0.plusSeconds(5))
                    .transition(ExternalReferenceResolutionState.RESOLVED, ExternalReferenceResolutionReason.RESOLVED,
                            Optional.of(new ResolvedExternalTarget(target("stale"), Map.of("kind", "symbol"))), T0.plusSeconds(6))
                    .transition(ExternalReferenceResolutionState.STALE, ExternalReferenceResolutionReason.TARGET_REMOVED,
                            Optional.empty(), T0.plusSeconds(7));
            ExternalReference brokenReference = unvalidated(change, activeProvenance, "broken");

            TraceabilityLink unvalidatedLink = external(changeRef, TraceabilityRelationType.LINKS_TO_CODE, unvalidated, activeEvidence.id(), 10);
            TraceabilityLink unresolvedLink = external(changeRef, TraceabilityRelationType.LINKS_TO_TEST, unresolved, activeEvidence.id(), 11);
            TraceabilityLink resolvedLink = external(changeRef, TraceabilityRelationType.SATISFIES, resolved, activeEvidence.id(), 12);
            TraceabilityLink staleLink = external(changeRef, TraceabilityRelationType.VERIFIED_BY, stale, activeEvidence.id(), 13);
            TraceabilityLink brokenLink = external(changeRef, TraceabilityRelationType.LINKS_TO_TEST, brokenReference, activeEvidence.id(), 14);

            SnapshotBusinessContent readyContent = content(
                    readyId, readyVersion, specification(projectId, "ready", readyProvenance),
                    List.of(), List.of(), readyEvidence);

            return new Fixture(
                    projectId, retiredId, activeId, readyId, retiredVersion, activeVersion, readyVersion,
                    retiredContent, activeContent, readyContent, traced, untraced,
                    List.of(unvalidated, unresolved, resolved, stale),
                    List.of(decisionLink, staleLink, resolvedLink, brokenLink, unvalidatedLink, unresolvedLink),
                    brokenLink);
        }

        private static ExternalReference unvalidated(ChangeProposal change, Provenance provenance, String suffix) {
            return ExternalReference.unvalidated(
                    ExternalReferenceId.generate(), change.id().value(), target(suffix), Optional.of(provenance));
        }

        private static TraceabilityLink external(
                TraceabilityEntityRef source,
                TraceabilityRelationType relation,
                ExternalReference reference,
                EvidenceId evidenceId,
                long seconds) {
            return LINK_FACTORY.create(
                    TraceabilityLinkId.generate(), source, relation, reference, TraceabilityLinkOrigin.EXPLICIT,
                    Optional.empty(), Set.of(evidenceId), T0.plusSeconds(seconds));
        }

        private static ExternalReferenceTarget target(String suffix) {
            return new ExternalReferenceTarget(
                    "MINOS", Optional.of("morpheus-engine"), "CODE_SYMBOL",
                    "com.morpheus." + suffix, Optional.of("rev-" + suffix));
        }

        private static SnapshotBusinessContent content(
                KnowledgeSnapshotId snapshotId,
                SpecificationVersionId versionId,
                Specification specification,
                List<ChangeProposal> changes,
                List<DesignDecision> decisions,
                Evidence evidence) {
            return new SnapshotBusinessContent(
                    snapshotId, versionId, List.of(specification), List.of(), changes,
                    List.of(), decisions, List.of(), List.of(evidence));
        }

        private static Specification specification(
                ProjectSpecificationId projectId,
                String name,
                Provenance provenance) {
            return new Specification(
                    SpecificationId.generate(), projectId, name, name + " specification", Optional.empty(), provenance);
        }

        private static Evidence evidence(String name) {
            return new Evidence(
                    EvidenceId.generate(), SourceLocator.file("specs/" + name + ".md"),
                    Optional.empty(), Optional.of("sha256:" + name));
        }

        private static Provenance provenance(EvidenceId evidenceId, String name) {
            return new Provenance(
                    new ProviderId("m6-s4-fixture"), Optional.of("1"), SourceLocator.file("specs/" + name + ".md"),
                    Optional.of(name), Optional.of("revision-" + name), evidenceId);
        }
    }
}
