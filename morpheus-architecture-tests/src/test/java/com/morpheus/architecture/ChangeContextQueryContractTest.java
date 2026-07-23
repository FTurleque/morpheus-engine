package com.morpheus.architecture;

import com.morpheus.application.query.ChangeContextQueryService;
import com.morpheus.application.query.ChangeContextResult;
import com.morpheus.application.query.TraceRequirementQueryService;
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
import com.morpheus.application.traceability.ExternalTraceabilityLinkFactory;
import com.morpheus.application.traceability.TraceRequirementResult;
import com.morpheus.application.traceability.TraceRequirementService;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
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
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;
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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeContextQueryContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T17:00:00Z");
    private final ExternalTraceabilityLinkFactory externalLinkFactory = new ExternalTraceabilityLinkFactory();

    @TempDir
    Path tempDir;

    @Test
    void traceRequirementQueryFacadePreservesExactM4Contract() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(snapshots, snapshots);
        var traceability = new MemoryTraceabilityStore(snapshots);
        var references = new MemoryExternalReferenceStore(snapshots);
        populate(snapshots, snapshots, content, traceability, references, fixture, true);

        TraceRequirementResult direct = new TraceRequirementService(snapshots, snapshots, traceability, references)
                .traceActive(fixture.projectId(), fixture.requirementOneId(), 2, Set.of())
                .orElseThrow();
        TraceRequirementResult queryView = new TraceRequirementQueryService(snapshots, snapshots, traceability, references)
                .active(fixture.projectId(), fixture.requirementOneId(), 2, Set.of())
                .orElseThrow();

        assertEquals(direct, queryView);
        assertEquals(
                direct,
                new TraceRequirementQueryService(snapshots, snapshots, traceability, references)
                        .snapshot(fixture.snapshotId(), fixture.requirementOneId(), 2, Set.of())
                        .orElseThrow());
    }

    @Test
    void memoryAndSqliteProduceExactlySameActiveChangeContext() {
        Fixture fixture = Fixture.create();

        var memorySnapshots = new MemorySpecificationKnowledgeStore();
        var memoryContent = new MemorySnapshotBusinessContentStore(memorySnapshots, memorySnapshots);
        var memoryTraceability = new MemoryTraceabilityStore(memorySnapshots);
        var memoryReferences = new MemoryExternalReferenceStore(memorySnapshots);
        populate(memorySnapshots, memorySnapshots, memoryContent, memoryTraceability, memoryReferences, fixture, true);
        ChangeContextResult memory = service(
                memorySnapshots, memoryContent, memorySnapshots, memoryTraceability, memoryReferences)
                .active(fixture.projectId(), fixture.changeId(), 2, Set.of())
                .orElseThrow();

        Path database = tempDir.resolve("change-context-equivalence.db");
        ChangeContextResult sqlite;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            populate(snapshots, requirements, content, traceability, references, fixture, true);
            sqlite = service(snapshots, content, requirements, traceability, references)
                    .active(fixture.projectId(), fixture.changeId(), 2, Set.of())
                    .orElseThrow();
        }

        assertEquals(memory, sqlite);
        assertActiveShape(memory, fixture);
    }

    @Test
    void affectedRequirementsUseDirectAffectsCurrentOnlyAndKeepBrokenTargetVisible() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(snapshots, snapshots);
        var traceability = new MemoryTraceabilityStore(snapshots);
        var references = new MemoryExternalReferenceStore(snapshots);
        populate(snapshots, snapshots, content, traceability, references, fixture, true);

        ChangeContextResult result = service(snapshots, content, snapshots, traceability, references)
                .active(fixture.projectId(), fixture.changeId(), 1, Set.of())
                .orElseThrow();

        assertEquals(3, result.affectedRequirementLinks().size());
        assertEquals(
                List.of(fixture.requirementOneId(), fixture.requirementTwoId()),
                result.affectedRequirements().stream()
                        .map(record -> record.entityVersion().content().id())
                        .toList());
        assertTrue(result.affectedRequirementLinks().stream().anyMatch(link ->
                link.target().identity().equals(fixture.missingRequirementId().value())));
        assertTrue(result.affectedRequirements().stream().allMatch(record ->
                record.entityVersion().temporalState() == TemporalState.CURRENT));
        assertFalse(result.affectedRequirements().stream().anyMatch(record ->
                record.entityVersion().content().statement().contains("PROPOSED")));
    }

    @Test
    void activeAndRetiredQueriesAreSnapshotCoherentWhileReadyIsRejected() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(snapshots, snapshots);
        var traceability = new MemoryTraceabilityStore(snapshots);
        var references = new MemoryExternalReferenceStore(snapshots);
        populate(snapshots, snapshots, content, traceability, references, fixture, true);

        KnowledgeSnapshotId successorId = KnowledgeSnapshotId.generate();
        SpecificationVersionId successorVersionId = SpecificationVersionId.generate();
        SnapshotBusinessContent successorContent = copyContent(
                fixture.content(), successorId, successorVersionId, "Active successor");
        snapshots.putSnapshot(readySnapshot(
                successorId, fixture.projectId(), Optional.of(fixture.snapshotId()), "revision-2", T0.plusSeconds(50)));
        snapshots.putSpecificationVersion(new SpecificationVersion(
                successorVersionId,
                fixture.projectId(),
                Optional.of(2L),
                Optional.of("provider-v1"),
                Optional.of("revision-2"),
                T0.plusSeconds(50),
                Optional.of(fixture.versionId())));
        snapshots.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(successorId, successorVersionId));
        content.putSnapshotContent(successorContent);
        putCurrentRequirements(snapshots, successorId, successorVersionId, fixture, "successor");
        traceability.putLink(successorId, internalLink(
                TraceabilityLinkId.generate(), fixture.changeRef(), TraceabilityRelationType.AFFECTS,
                fixture.requirementOneRef(), fixture.evidenceId(), T0.plusSeconds(51)));
        snapshots.activateSnapshot(successorId, Optional.of(fixture.snapshotId()));

        ChangeContextQueryService query = service(snapshots, content, snapshots, traceability, references);
        ChangeContextResult active = query.active(fixture.projectId(), fixture.changeId(), 2, Set.of()).orElseThrow();
        ChangeContextResult retired = query.snapshot(fixture.snapshotId(), fixture.changeId(), 2, Set.of());

        assertEquals(successorId, active.snapshot().id());
        assertEquals(KnowledgeSnapshotState.ACTIVE, active.snapshot().state());
        assertEquals(fixture.snapshotId(), retired.snapshot().id());
        assertEquals(KnowledgeSnapshotState.RETIRED, retired.snapshot().state());

        KnowledgeSnapshotId readyId = KnowledgeSnapshotId.generate();
        snapshots.putSnapshot(readySnapshot(
                readyId, fixture.projectId(), Optional.of(successorId), "revision-ready", T0.plusSeconds(60)));
        assertThrows(KnowledgeStoreException.class, () ->
                query.snapshot(readyId, fixture.changeId(), 2, Set.of()));
    }

    @Test
    void noActiveSnapshotAndMissingChangeRemainDistinct() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(snapshots, snapshots);
        var traceability = new MemoryTraceabilityStore(snapshots);
        var references = new MemoryExternalReferenceStore(snapshots);
        populate(snapshots, snapshots, content, traceability, references, fixture, false);

        ChangeContextQueryService query = service(snapshots, content, snapshots, traceability, references);
        assertTrue(query.active(fixture.projectId(), fixture.changeId(), 2, Set.of()).isEmpty());

        snapshots.activateSnapshot(fixture.snapshotId(), Optional.empty());
        ChangeContextResult missing = query
                .active(fixture.projectId(), ChangeId.generate(), 2, Set.of())
                .orElseThrow();
        assertTrue(missing.change().isEmpty());
        assertTrue(missing.constraints().isEmpty());
        assertTrue(missing.designDecisions().isEmpty());
        assertTrue(missing.implementationTasks().isEmpty());
    }

    @Test
    void relationFilterShapesOnlyTraceViewAndDepthRemainsBounded() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(snapshots, snapshots);
        var traceability = new MemoryTraceabilityStore(snapshots);
        var references = new MemoryExternalReferenceStore(snapshots);
        populate(snapshots, snapshots, content, traceability, references, fixture, true);

        ChangeContextResult filtered = service(snapshots, content, snapshots, traceability, references)
                .active(
                        fixture.projectId(),
                        fixture.changeId(),
                        1,
                        Set.of(TraceabilityRelationType.CONSTRAINS))
                .orElseThrow();

        assertEquals(1, filtered.subgraph().links().size());
        assertEquals(TraceabilityRelationType.CONSTRAINS, filtered.subgraph().links().getFirst().relationType());
        assertEquals(3, filtered.affectedRequirementLinks().size());
        assertEquals(2, filtered.affectedRequirements().size());
        assertEquals(1, filtered.constraints().size());
        assertEquals(1, filtered.designDecisions().size());
        assertEquals(1, filtered.implementationTasks().size());
        assertTrue(filtered.externalLinks().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service(
                snapshots, content, snapshots, traceability, references)
                .active(fixture.projectId(), fixture.changeId(), 0, Set.of()));
    }

    @Test
    void sqliteReopenPreservesContextIncludingUnresolvedAndBrokenExternalViews() {
        Fixture fixture = Fixture.create();
        Path database = tempDir.resolve("change-context-reopen.db");
        ChangeContextResult before;

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            populate(snapshots, requirements, content, traceability, references, fixture, true);
            before = service(snapshots, content, requirements, traceability, references)
                    .active(fixture.projectId(), fixture.changeId(), 2, Set.of())
                    .orElseThrow();
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            ChangeContextResult after = service(snapshots, content, requirements, traceability, references)
                    .active(fixture.projectId(), fixture.changeId(), 2, Set.of())
                    .orElseThrow();
            assertEquals(before, after);
            assertEquals(
                    Set.of(
                            ExternalTraceabilityAvailability.REFERENCE_UNRESOLVED,
                            ExternalTraceabilityAvailability.BROKEN_REFERENCE),
                    after.externalLinks().stream()
                            .map(view -> view.availability())
                            .collect(java.util.stream.Collectors.toSet()));
        }
    }

    private ChangeContextQueryService service(
            SpecificationKnowledgeStore snapshots,
            SnapshotBusinessContentStore content,
            VersionedRequirementStore requirements,
            TraceabilityStore traceability,
            ExternalReferenceStore references) {
        return new ChangeContextQueryService(snapshots, content, requirements, traceability, references);
    }

    private void populate(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            SnapshotBusinessContentStore content,
            TraceabilityStore traceability,
            ExternalReferenceStore references,
            Fixture fixture,
            boolean activate) {
        snapshots.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace-m5-s4")));
        snapshots.putSnapshot(readySnapshot(
                fixture.snapshotId(), fixture.projectId(), Optional.empty(), "revision-1", T0));
        requirements.putSpecificationVersion(new SpecificationVersion(
                fixture.versionId(),
                fixture.projectId(),
                Optional.of(1L),
                Optional.of("provider-v1"),
                Optional.of("revision-1"),
                T0,
                Optional.empty()));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                fixture.snapshotId(), fixture.versionId()));
        content.putSnapshotContent(fixture.content());
        putCurrentRequirements(requirements, fixture.snapshotId(), fixture.versionId(), fixture, "active");
        requirements.putRequirementVersion(requirementVersion(
                fixture.snapshotId(),
                fixture.versionId(),
                EntityVersionId.generate(),
                fixture.requirementOneId(),
                "PROPOSED requirement one",
                TemporalState.PROPOSED,
                fixture.evidenceId()));

        traceability.putLink(fixture.snapshotId(), internalLink(
                fixture.affectsOneLinkId(), fixture.changeRef(), TraceabilityRelationType.AFFECTS,
                fixture.requirementOneRef(), fixture.evidenceId(), T0.plusSeconds(1)));
        traceability.putLink(fixture.snapshotId(), internalLink(
                fixture.affectsTwoLinkId(), fixture.changeRef(), TraceabilityRelationType.AFFECTS,
                fixture.requirementTwoRef(), fixture.evidenceId(), T0.plusSeconds(2)));
        traceability.putLink(fixture.snapshotId(), internalLink(
                fixture.affectsMissingLinkId(), fixture.changeRef(), TraceabilityRelationType.AFFECTS,
                fixture.missingRequirementRef(), fixture.evidenceId(), T0.plusSeconds(3)));
        traceability.putLink(fixture.snapshotId(), internalLink(
                fixture.constraintLinkId(), fixture.constraintRef(), TraceabilityRelationType.CONSTRAINS,
                fixture.changeRef(), fixture.evidenceId(), T0.plusSeconds(4)));
        traceability.putLink(fixture.snapshotId(), internalLink(
                fixture.decisionLinkId(), fixture.changeRef(), TraceabilityRelationType.DECIDED_BY,
                fixture.decisionRef(), fixture.evidenceId(), T0.plusSeconds(5)));
        traceability.putLink(fixture.snapshotId(), internalLink(
                fixture.cycleLinkId(), fixture.decisionRef(), TraceabilityRelationType.RELATED_TO,
                fixture.changeRef(), fixture.evidenceId(), T0.plusSeconds(6)));

        ExternalReference unresolved = unresolvedReference(
                fixture.unresolvedReferenceId(), fixture.changeId(), fixture.evidenceId());
        ExternalReference broken = ExternalReference.unvalidated(
                fixture.brokenReferenceId(),
                fixture.changeId().value(),
                externalTarget("com.morpheus.BrokenChangeTarget"),
                Optional.empty());
        references.putReference(fixture.snapshotId(), unresolved);
        traceability.putLink(fixture.snapshotId(), externalLinkFactory.create(
                fixture.unresolvedLinkId(),
                fixture.changeRef(),
                TraceabilityRelationType.LINKS_TO_CODE,
                unresolved,
                TraceabilityLinkOrigin.EXPLICIT,
                Optional.empty(),
                Set.of(fixture.evidenceId()),
                T0.plusSeconds(7)));
        traceability.putLink(fixture.snapshotId(), externalLinkFactory.create(
                fixture.brokenLinkId(),
                fixture.changeRef(),
                TraceabilityRelationType.LINKS_TO_TEST,
                broken,
                TraceabilityLinkOrigin.EXPLICIT,
                Optional.empty(),
                Set.of(fixture.evidenceId()),
                T0.plusSeconds(8)));

        if (activate) {
            snapshots.activateSnapshot(fixture.snapshotId(), Optional.empty());
        }
    }

    private void putCurrentRequirements(
            VersionedRequirementStore requirements,
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            Fixture fixture,
            String suffix) {
        requirements.putRequirementVersion(requirementVersion(
                snapshotId,
                versionId,
                EntityVersionId.generate(),
                fixture.requirementOneId(),
                "CURRENT requirement one " + suffix,
                TemporalState.CURRENT,
                fixture.evidenceId()));
        requirements.putRequirementVersion(requirementVersion(
                snapshotId,
                versionId,
                EntityVersionId.generate(),
                fixture.requirementTwoId(),
                "CURRENT requirement two " + suffix,
                TemporalState.CURRENT,
                fixture.evidenceId()));
    }

    private RequirementVersionRecord requirementVersion(
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            EntityVersionId entityVersionId,
            RequirementId requirementId,
            String statement,
            TemporalState temporalState,
            EvidenceId evidenceId) {
        Requirement requirement = new Requirement(
                requirementId,
                SpecificationId.parse(requirementId.value().toString()),
                Optional.of("REQ-" + requirementId),
                "Requirement " + requirementId,
                statement,
                provenance(evidenceId));
        return new RequirementVersionRecord(
                snapshotId,
                new EntityVersion<>(
                        entityVersionId,
                        requirementId.value(),
                        versionId,
                        temporalState,
                        requirement));
    }

    private SnapshotBusinessContent copyContent(
            SnapshotBusinessContent source,
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            String changeTitle) {
        ChangeProposal original = source.changes().getFirst();
        ChangeProposal change = new ChangeProposal(
                original.id(),
                original.projectId(),
                original.key(),
                changeTitle,
                original.intent(),
                original.scope(),
                original.outOfScope(),
                original.risks(),
                original.provenance());
        return new SnapshotBusinessContent(
                snapshotId,
                versionId,
                source.specifications(),
                source.scenarios(),
                List.of(change),
                source.constraints(),
                source.designDecisions(),
                source.tasks(),
                source.evidence());
    }

    private void assertActiveShape(ChangeContextResult result, Fixture fixture) {
        assertEquals(fixture.snapshotId(), result.snapshot().id());
        assertEquals(KnowledgeSnapshotState.ACTIVE, result.snapshot().state());
        assertEquals(fixture.changeId(), result.changeId());
        assertEquals(fixture.change(), result.change().orElseThrow());
        assertEquals(3, result.affectedRequirementLinks().size());
        assertEquals(2, result.affectedRequirements().size());
        assertEquals(List.of(fixture.constraint()), result.constraints());
        assertEquals(List.of(fixture.decision()), result.designDecisions());
        assertEquals(List.of(fixture.task()), result.implementationTasks());
        assertEquals(fixture.changeRef(), result.subgraph().start());
        assertTrue(result.subgraph().links().stream().anyMatch(link -> link.id().equals(fixture.cycleLinkId())));
        assertEquals(2, result.externalLinks().size());
        assertEquals(
                Set.of(
                        ExternalTraceabilityAvailability.REFERENCE_UNRESOLVED,
                        ExternalTraceabilityAvailability.BROKEN_REFERENCE),
                result.externalLinks().stream()
                        .map(view -> view.availability())
                        .collect(java.util.stream.Collectors.toSet()));
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
            ChangeId owner,
            EvidenceId evidenceId) {
        return ExternalReference.unvalidated(
                        id,
                        owner.value(),
                        externalTarget("com.morpheus.ChangeService"),
                        Optional.of(provenance(evidenceId)))
                .transition(
                        ExternalReferenceResolutionState.UNRESOLVED,
                        ExternalReferenceResolutionReason.TARGET_UNAVAILABLE,
                        Optional.empty(),
                        T0.plusSeconds(7));
    }

    private ExternalReferenceTarget externalTarget(String externalId) {
        return new ExternalReferenceTarget(
                "MINOS",
                Optional.of("morpheus-engine"),
                "CODE_SYMBOL",
                externalId,
                Optional.of("rev-m5-s4"));
    }

    private Provenance provenance(EvidenceId evidenceId) {
        return new Provenance(
                new ProviderId("m5-s4-test"),
                Optional.of("1"),
                SourceLocator.file("specs/m5-s4.md"),
                Optional.of("M5-S4"),
                Optional.of("source-revision-s4"),
                evidenceId);
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            SpecificationId specificationId,
            ChangeId changeId,
            RequirementId requirementOneId,
            RequirementId requirementTwoId,
            RequirementId missingRequirementId,
            EvidenceId evidenceId,
            ChangeProposal change,
            Constraint constraint,
            DesignDecision decision,
            ImplementationTask task,
            SnapshotBusinessContent content,
            TraceabilityEntityRef changeRef,
            TraceabilityEntityRef requirementOneRef,
            TraceabilityEntityRef requirementTwoRef,
            TraceabilityEntityRef missingRequirementRef,
            TraceabilityEntityRef constraintRef,
            TraceabilityEntityRef decisionRef,
            ExternalReferenceId unresolvedReferenceId,
            ExternalReferenceId brokenReferenceId,
            TraceabilityLinkId affectsOneLinkId,
            TraceabilityLinkId affectsTwoLinkId,
            TraceabilityLinkId affectsMissingLinkId,
            TraceabilityLinkId constraintLinkId,
            TraceabilityLinkId decisionLinkId,
            TraceabilityLinkId cycleLinkId,
            TraceabilityLinkId unresolvedLinkId,
            TraceabilityLinkId brokenLinkId) {

        private static Fixture create() {
            ProjectSpecificationId projectId = ProjectSpecificationId.generate();
            KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
            SpecificationVersionId versionId = SpecificationVersionId.generate();
            SpecificationId specificationId = SpecificationId.generate();
            ChangeId changeId = ChangeId.generate();
            RequirementId requirementOneId = RequirementId.generate();
            RequirementId requirementTwoId = RequirementId.generate();
            RequirementId missingRequirementId = RequirementId.generate();
            EvidenceId evidenceId = EvidenceId.generate();
            Provenance provenance = new Provenance(
                    new ProviderId("m5-s4-test"),
                    Optional.of("1"),
                    SourceLocator.file("specs/m5-s4.md"),
                    Optional.of("M5-S4"),
                    Optional.of("source-revision-s4"),
                    evidenceId);
            Evidence evidence = new Evidence(
                    evidenceId,
                    SourceLocator.file("specs/m5-s4.md"),
                    Optional.empty(),
                    Optional.of("sha256:m5-s4"));
            Specification specification = new Specification(
                    specificationId,
                    projectId,
                    "m5-s4",
                    "M5 S4",
                    Optional.of("Change context"),
                    provenance);
            ChangeProposal change = new ChangeProposal(
                    changeId,
                    projectId,
                    Optional.of("CHG-M5-S4"),
                    "Build deterministic change context",
                    "Aggregate only MORPHEUS facts",
                    List.of("query"),
                    List.of("ranking"),
                    List.of("broken references"),
                    provenance);
            Constraint constraint = new Constraint(
                    ConstraintId.generate(), changeId, "must remain snapshot coherent", provenance);
            DesignDecision decision = new DesignDecision(
                    DesignDecisionId.generate(), changeId, "Use published trace facts", "Avoid inferred impact", provenance);
            ImplementationTask task = new ImplementationTask(
                    TaskId.generate(), changeId, Optional.of("TASK-M5-S4"), "Implement change context", false, provenance);
            SnapshotBusinessContent content = new SnapshotBusinessContent(
                    snapshotId,
                    versionId,
                    List.of(specification),
                    List.of(),
                    List.of(change),
                    List.of(constraint),
                    List.of(decision),
                    List.of(task),
                    List.of(evidence));

            return new Fixture(
                    projectId,
                    snapshotId,
                    versionId,
                    specificationId,
                    changeId,
                    requirementOneId,
                    requirementTwoId,
                    missingRequirementId,
                    evidenceId,
                    change,
                    constraint,
                    decision,
                    task,
                    content,
                    ref(TraceabilityEntityKind.CHANGE, changeId.value()),
                    ref(TraceabilityEntityKind.REQUIREMENT, requirementOneId.value()),
                    ref(TraceabilityEntityKind.REQUIREMENT, requirementTwoId.value()),
                    ref(TraceabilityEntityKind.REQUIREMENT, missingRequirementId.value()),
                    ref(TraceabilityEntityKind.CONSTRAINT, constraint.id().value()),
                    ref(TraceabilityEntityKind.DESIGN_DECISION, decision.id().value()),
                    ExternalReferenceId.generate(),
                    ExternalReferenceId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate(),
                    TraceabilityLinkId.generate());
        }

        private static TraceabilityEntityRef ref(TraceabilityEntityKind kind, DomainIdentity identity) {
            return new TraceabilityEntityRef(kind, identity);
        }
    }
}
