package com.morpheus.architecture;

import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.application.traceability.ExternalTraceabilityAvailability;
import com.morpheus.application.traceability.ExternalTraceabilityLinkFactory;
import com.morpheus.application.traceability.TraceRequirementResult;
import com.morpheus.application.traceability.TraceRequirementService;
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
import com.morpheus.store.memory.MemoryExternalReferenceStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import com.morpheus.store.sqlite.SqliteExternalReferenceStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceRequirementFinalValidationTest {
    private static final Instant T0 = Instant.parse("2026-07-23T12:00:00Z");
    private final ExternalTraceabilityLinkFactory externalLinkFactory = new ExternalTraceabilityLinkFactory();

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqliteProduceTheSameFinalActiveTrace() {
        Fixture fixture = Fixture.create();

        var memorySnapshots = new MemorySpecificationKnowledgeStore();
        var memoryTraceability = new MemoryTraceabilityStore(memorySnapshots);
        var memoryReferences = new MemoryExternalReferenceStore(memorySnapshots);
        populate(memorySnapshots, memorySnapshots, memoryTraceability, memoryReferences, fixture);
        TraceRequirementResult memory = service(
                memorySnapshots, memorySnapshots, memoryTraceability, memoryReferences)
                .traceActive(fixture.projectId(), fixture.requirementId(), 3, Set.of())
                .orElseThrow();

        Path database = tempDir.resolve("final-equivalence.db");
        TraceRequirementResult sqlite;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            populate(snapshots, requirements, traceability, references, fixture);
            sqlite = service(snapshots, requirements, traceability, references)
                    .traceActive(fixture.projectId(), fixture.requirementId(), 3, Set.of())
                    .orElseThrow();
        }

        assertEquals(memory, sqlite);
        assertFinalActiveShape(memory, fixture);
    }

    @Test
    void activeTraceIsIsolatedFromRetiredAndReadySnapshots() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var traceability = new MemoryTraceabilityStore(snapshots);
        var references = new MemoryExternalReferenceStore(snapshots);
        populate(snapshots, snapshots, traceability, references, fixture);
        TraceRequirementService service = service(snapshots, snapshots, traceability, references);

        TraceRequirementResult active = service
                .traceActive(fixture.projectId(), fixture.requirementId(), 3, Set.of())
                .orElseThrow();
        TraceRequirementResult historical = service
                .traceSnapshot(fixture.retiredSnapshotId(), fixture.requirementId(), 3, Set.of())
                .orElseThrow();

        assertEquals(KnowledgeSnapshotState.ACTIVE, active.snapshot().state());
        assertEquals(fixture.activeSnapshotId(), active.snapshot().id());
        assertEquals(KnowledgeSnapshotState.RETIRED, historical.snapshot().state());
        assertEquals(fixture.retiredSnapshotId(), historical.snapshot().id());
        assertEquals(Set.of(fixture.retiredLinkId()), historical.subgraph().links().stream()
                .map(TraceabilityLink::id)
                .collect(java.util.stream.Collectors.toSet()));
        assertFalse(active.subgraph().nodes().contains(fixture.retiredScenario()));
        assertFalse(active.subgraph().nodes().contains(fixture.candidateScenario()));
        assertThrows(
                KnowledgeStoreException.class,
                () -> service.traceSnapshot(
                        fixture.candidateSnapshotId(), fixture.requirementId(), 3, Set.of()));
    }

    @Test
    void finalTraceSupportsRelationFiltersAndMissingRequirementWithoutLeakingTechnicalSnapshots() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var traceability = new MemoryTraceabilityStore(snapshots);
        var references = new MemoryExternalReferenceStore(snapshots);
        populate(snapshots, snapshots, traceability, references, fixture);
        TraceRequirementService service = service(snapshots, snapshots, traceability, references);

        TraceRequirementResult filtered = service
                .traceActive(
                        fixture.projectId(),
                        fixture.requirementId(),
                        3,
                        Set.of(TraceabilityRelationType.REFINES))
                .orElseThrow();

        assertEquals(1, filtered.subgraph().links().size());
        assertEquals(TraceabilityRelationType.REFINES, filtered.subgraph().links().getFirst().relationType());
        assertTrue(filtered.externalLinks().isEmpty());
        assertTrue(service.traceActive(
                fixture.projectId(), RequirementId.generate(), 3, Set.of()).isEmpty());
    }

    @Test
    void sqliteReopenPreservesTheFinalTraceIncludingUnresolvedAndBrokenExternalViews() {
        Fixture fixture = Fixture.create();
        Path database = tempDir.resolve("final-reopen.db");
        TraceRequirementResult before;

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            populate(snapshots, requirements, traceability, references, fixture);
            before = service(snapshots, requirements, traceability, references)
                    .traceActive(fixture.projectId(), fixture.requirementId(), 3, Set.of())
                    .orElseThrow();
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            TraceRequirementResult after = service(snapshots, requirements, traceability, references)
                    .traceActive(fixture.projectId(), fixture.requirementId(), 3, Set.of())
                    .orElseThrow();
            assertEquals(before, after);
            assertFinalActiveShape(after, fixture);
        }
    }

    @Test
    void explicitSnapshotQueryRejectsAllNonPublishedStates() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var traceability = new MemoryTraceabilityStore(snapshots);
        var references = new MemoryExternalReferenceStore(snapshots);
        populate(snapshots, snapshots, traceability, references, fixture);

        KnowledgeSnapshotId buildingId = KnowledgeSnapshotId.generate();
        snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                buildingId,
                fixture.projectId(),
                Optional.of(fixture.activeSnapshotId()),
                KnowledgeSnapshotState.BUILDING,
                Optional.of("building"),
                T0.plusSeconds(30)));

        TraceRequirementService service = service(snapshots, snapshots, traceability, references);
        assertThrows(
                KnowledgeStoreException.class,
                () -> service.traceSnapshot(buildingId, fixture.requirementId(), 3, Set.of()));
        assertThrows(
                KnowledgeStoreException.class,
                () -> service.traceSnapshot(fixture.candidateSnapshotId(), fixture.requirementId(), 3, Set.of()));
    }

    private TraceRequirementService service(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            TraceabilityStore traceability,
            ExternalReferenceStore references) {
        return new TraceRequirementService(snapshots, requirements, traceability, references);
    }

    private void populate(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            TraceabilityStore traceability,
            ExternalReferenceStore references,
            Fixture fixture) {
        snapshots.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace-final-m4")));

        snapshots.putSnapshot(readySnapshot(
                fixture.retiredSnapshotId(), fixture.projectId(), Optional.empty(), "revision-1", T0));
        requirements.putSpecificationVersion(new SpecificationVersion(
                fixture.retiredVersionId(),
                fixture.projectId(),
                Optional.of(1L),
                Optional.of("provider-v1"),
                Optional.of("revision-1"),
                T0,
                Optional.empty()));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.retiredSnapshotId(), fixture.retiredVersionId()));
        requirements.putRequirementVersion(requirementVersion(
                fixture.retiredSnapshotId(), fixture.retiredVersionId(), fixture.retiredEntityVersionId(),
                fixture.requirementId(), fixture.specificationId(), "historical requirement", fixture.evidenceId()));
        traceability.putLink(fixture.retiredSnapshotId(), internalLink(
                fixture.retiredLinkId(), fixture.retiredScenario(), TraceabilityRelationType.REFINES,
                fixture.requirement(), fixture.evidenceId(), T0.plusSeconds(1)));
        snapshots.activateSnapshot(fixture.retiredSnapshotId(), Optional.empty());

        snapshots.putSnapshot(readySnapshot(
                fixture.activeSnapshotId(), fixture.projectId(), Optional.of(fixture.retiredSnapshotId()),
                "revision-2", T0.plusSeconds(10)));
        requirements.putSpecificationVersion(new SpecificationVersion(
                fixture.activeVersionId(),
                fixture.projectId(),
                Optional.of(2L),
                Optional.of("provider-v1"),
                Optional.of("revision-2"),
                T0.plusSeconds(10),
                Optional.of(fixture.retiredVersionId())));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.activeSnapshotId(), fixture.activeVersionId()));
        requirements.putRequirementVersion(requirementVersion(
                fixture.activeSnapshotId(), fixture.activeVersionId(), fixture.activeEntityVersionId(),
                fixture.requirementId(), fixture.specificationId(), "active requirement", fixture.evidenceId()));

        traceability.putLink(fixture.activeSnapshotId(), internalLink(
                fixture.scenarioLinkId(), fixture.scenario(), TraceabilityRelationType.REFINES,
                fixture.requirement(), fixture.evidenceId(), T0.plusSeconds(11)));
        traceability.putLink(fixture.activeSnapshotId(), internalLink(
                fixture.changeLinkId(), fixture.change(), TraceabilityRelationType.AFFECTS,
                fixture.requirement(), fixture.evidenceId(), T0.plusSeconds(12)));
        traceability.putLink(fixture.activeSnapshotId(), internalLink(
                fixture.constraintLinkId(), fixture.constraint(), TraceabilityRelationType.CONSTRAINS,
                fixture.change(), fixture.evidenceId(), T0.plusSeconds(13)));
        traceability.putLink(fixture.activeSnapshotId(), internalLink(
                fixture.decisionLinkId(), fixture.change(), TraceabilityRelationType.DECIDED_BY,
                fixture.decision(), fixture.evidenceId(), T0.plusSeconds(14)));
        traceability.putLink(fixture.activeSnapshotId(), internalLink(
                fixture.depthThreeLinkId(), fixture.decision(), TraceabilityRelationType.RELATED_TO,
                fixture.graphSpecification(), fixture.evidenceId(), T0.plusSeconds(15)));
        traceability.putLink(fixture.activeSnapshotId(), internalLink(
                fixture.cycleLinkId(), fixture.decision(), TraceabilityRelationType.RELATED_TO,
                fixture.change(), fixture.evidenceId(), T0.plusSeconds(16)));

        ExternalReference unresolved = unresolvedReference(fixture.unresolvedReferenceId(), fixture.requirementId(), fixture.evidenceId());
        ExternalReference broken = ExternalReference.unvalidated(
                fixture.brokenReferenceId(),
                fixture.requirementId().value(),
                externalTarget("com.morpheus.BrokenTarget"),
                Optional.empty());
        references.putReference(fixture.activeSnapshotId(), unresolved);
        traceability.putLink(fixture.activeSnapshotId(), externalLinkFactory.create(
                fixture.unresolvedLinkId(),
                fixture.requirement(),
                TraceabilityRelationType.LINKS_TO_CODE,
                unresolved,
                TraceabilityLinkOrigin.EXPLICIT,
                Optional.empty(),
                Set.of(fixture.evidenceId()),
                T0.plusSeconds(17)));
        traceability.putLink(fixture.activeSnapshotId(), externalLinkFactory.create(
                fixture.brokenLinkId(),
                fixture.requirement(),
                TraceabilityRelationType.LINKS_TO_TEST,
                broken,
                TraceabilityLinkOrigin.EXPLICIT,
                Optional.empty(),
                Set.of(fixture.evidenceId()),
                T0.plusSeconds(18)));
        snapshots.activateSnapshot(fixture.activeSnapshotId(), Optional.of(fixture.retiredSnapshotId()));

        snapshots.putSnapshot(readySnapshot(
                fixture.candidateSnapshotId(), fixture.projectId(), Optional.of(fixture.activeSnapshotId()),
                "revision-3", T0.plusSeconds(20)));
        requirements.putSpecificationVersion(new SpecificationVersion(
                fixture.candidateVersionId(),
                fixture.projectId(),
                Optional.of(3L),
                Optional.of("provider-v1"),
                Optional.of("revision-3"),
                T0.plusSeconds(20),
                Optional.of(fixture.activeVersionId())));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.candidateSnapshotId(), fixture.candidateVersionId()));
        requirements.putRequirementVersion(requirementVersion(
                fixture.candidateSnapshotId(), fixture.candidateVersionId(), fixture.candidateEntityVersionId(),
                fixture.requirementId(), fixture.specificationId(), "candidate requirement", fixture.evidenceId()));
        traceability.putLink(fixture.candidateSnapshotId(), internalLink(
                fixture.candidateLinkId(), fixture.candidateScenario(), TraceabilityRelationType.REFINES,
                fixture.requirement(), fixture.evidenceId(), T0.plusSeconds(21)));
    }

    private void assertFinalActiveShape(TraceRequirementResult result, Fixture fixture) {
        assertEquals(fixture.activeSnapshotId(), result.snapshot().id());
        assertEquals(KnowledgeSnapshotState.ACTIVE, result.snapshot().state());
        assertEquals(fixture.requirementId(), result.requirement().entityVersion().content().id());
        assertEquals(fixture.requirement(), result.subgraph().start());
        assertTrue(result.subgraph().nodes().contains(fixture.scenario()));
        assertTrue(result.subgraph().nodes().contains(fixture.change()));
        assertTrue(result.subgraph().nodes().contains(fixture.constraint()));
        assertTrue(result.subgraph().nodes().contains(fixture.decision()));
        assertTrue(result.subgraph().nodes().contains(fixture.graphSpecification()));
        assertTrue(result.subgraph().links().stream().anyMatch(link -> link.id().equals(fixture.cycleLinkId())));
        assertTrue(result.subgraph().links().stream().allMatch(link -> link.evidenceIds().contains(fixture.evidenceId())));
        assertEquals(2, result.externalLinks().size());
        assertEquals(
                Set.of(
                        ExternalTraceabilityAvailability.REFERENCE_UNRESOLVED,
                        ExternalTraceabilityAvailability.BROKEN_REFERENCE),
                result.externalLinks().stream()
                        .map(view -> view.availability())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    private RequirementVersionRecord requirementVersion(
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            EntityVersionId entityVersionId,
            RequirementId requirementId,
            SpecificationId specificationId,
            String statement,
            EvidenceId evidenceId) {
        Requirement requirement = new Requirement(
                requirementId,
                specificationId,
                Optional.of("FINAL-M4"),
                "Final M4 requirement",
                statement,
                provenance(evidenceId));
        return new RequirementVersionRecord(
                snapshotId,
                new EntityVersion<>(
                        entityVersionId,
                        requirementId.value(),
                        versionId,
                        TemporalState.CURRENT,
                        requirement));
    }

    private KnowledgeSnapshotMetadata readySnapshot(
            KnowledgeSnapshotId id,
            ProjectSpecificationId projectId,
            Optional<KnowledgeSnapshotId> predecessor,
            String revision,
            Instant builtAt) {
        return new KnowledgeSnapshotMetadata(
                id,
                projectId,
                predecessor,
                KnowledgeSnapshotState.READY,
                Optional.of(revision),
                builtAt);
    }

    private TraceabilityLink internalLink(
            TraceabilityLinkId id,
            TraceabilityEntityRef source,
            TraceabilityRelationType relation,
            TraceabilityEntityRef target,
            EvidenceId evidenceId,
            Instant observedAt) {
        return new TraceabilityLink(
                id,
                source,
                relation,
                target,
                TraceabilityLinkOrigin.DERIVED,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of(evidenceId),
                observedAt);
    }

    private ExternalReference unresolvedReference(
            ExternalReferenceId id,
            RequirementId owner,
            EvidenceId evidenceId) {
        return ExternalReference.unvalidated(
                        id,
                        owner.value(),
                        externalTarget("com.morpheus.PaymentService"),
                        Optional.of(provenance(evidenceId)))
                .transition(
                        ExternalReferenceResolutionState.UNRESOLVED,
                        ExternalReferenceResolutionReason.TARGET_UNAVAILABLE,
                        Optional.empty(),
                        T0.plusSeconds(16));
    }

    private ExternalReferenceTarget externalTarget(String externalId) {
        return new ExternalReferenceTarget(
                "MINOS",
                Optional.of("morpheus-engine"),
                "CODE_SYMBOL",
                externalId,
                Optional.of("rev-final"));
    }

    private Provenance provenance(EvidenceId evidenceId) {
        return new Provenance(
                new ProviderId("final-m4-test"),
                Optional.of("1"),
                SourceLocator.file("specs/final-m4.md"),
                Optional.of("REQ-FINAL-M4"),
                Optional.of("source-revision-final"),
                evidenceId);
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            RequirementId requirementId,
            SpecificationId specificationId,
            KnowledgeSnapshotId retiredSnapshotId,
            KnowledgeSnapshotId activeSnapshotId,
            KnowledgeSnapshotId candidateSnapshotId,
            SpecificationVersionId retiredVersionId,
            SpecificationVersionId activeVersionId,
            SpecificationVersionId candidateVersionId,
            EntityVersionId retiredEntityVersionId,
            EntityVersionId activeEntityVersionId,
            EntityVersionId candidateEntityVersionId,
            TraceabilityEntityRef requirement,
            TraceabilityEntityRef retiredScenario,
            TraceabilityEntityRef scenario,
            TraceabilityEntityRef change,
            TraceabilityEntityRef constraint,
            TraceabilityEntityRef decision,
            TraceabilityEntityRef graphSpecification,
            TraceabilityEntityRef candidateScenario,
            ExternalReferenceId unresolvedReferenceId,
            ExternalReferenceId brokenReferenceId,
            EvidenceId evidenceId,
            TraceabilityLinkId retiredLinkId,
            TraceabilityLinkId scenarioLinkId,
            TraceabilityLinkId changeLinkId,
            TraceabilityLinkId constraintLinkId,
            TraceabilityLinkId decisionLinkId,
            TraceabilityLinkId depthThreeLinkId,
            TraceabilityLinkId cycleLinkId,
            TraceabilityLinkId unresolvedLinkId,
            TraceabilityLinkId brokenLinkId,
            TraceabilityLinkId candidateLinkId) {

        private static Fixture create() {
            RequirementId requirementId = RequirementId.generate();
            return new Fixture(
                    ProjectSpecificationId.generate(),
                    requirementId,
                    SpecificationId.generate(),
                    KnowledgeSnapshotId.generate(),
                    KnowledgeSnapshotId.generate(),
                    KnowledgeSnapshotId.generate(),
                    SpecificationVersionId.generate(),
                    SpecificationVersionId.generate(),
                    SpecificationVersionId.generate(),
                    EntityVersionId.generate(),
                    EntityVersionId.generate(),
                    EntityVersionId.generate(),
                    new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, requirementId.value()),
                    ref(TraceabilityEntityKind.SCENARIO),
                    ref(TraceabilityEntityKind.SCENARIO),
                    ref(TraceabilityEntityKind.CHANGE),
                    ref(TraceabilityEntityKind.CONSTRAINT),
                    ref(TraceabilityEntityKind.DESIGN_DECISION),
                    ref(TraceabilityEntityKind.SPECIFICATION),
                    ref(TraceabilityEntityKind.SCENARIO),
                    ExternalReferenceId.generate(),
                    ExternalReferenceId.generate(),
                    EvidenceId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate());
        }

        private static TraceabilityEntityRef ref(TraceabilityEntityKind kind) {
            return new TraceabilityEntityRef(kind, DomainIdentity.generate());
        }
    }
}
