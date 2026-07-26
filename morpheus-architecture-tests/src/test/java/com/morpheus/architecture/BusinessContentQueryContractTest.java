package com.morpheus.architecture;

import com.morpheus.application.query.BusinessContentQueryService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.SnapshotItemResult;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.evidence.SourceRange;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessContentQueryContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T16:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqliteExposeSameActiveSingleItemQueriesAndExplicitNotFound() {
        Fixture fixture = fixture();

        MemorySpecificationKnowledgeStore memoryCore = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore memoryContent = new MemorySnapshotBusinessContentStore(memoryCore, memoryCore);
        seedPublished(memoryCore, memoryCore, memoryContent, fixture);
        BusinessContentQueryService memoryQuery = new BusinessContentQueryService(memoryCore, memoryContent);

        Path database = tempDir.resolve("active-single.db");
        SnapshotItemResult<Specification> sqliteSpecification;
        SnapshotItemResult<ChangeProposal> sqliteChange;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database)) {
            seedPublished(snapshots, versions, content, fixture);
            BusinessContentQueryService sqliteQuery = new BusinessContentQueryService(snapshots, content);
            sqliteSpecification = sqliteQuery.activeSpecification(fixture.projectId(), fixture.specificationId())
                    .orElseThrow();
            sqliteChange = sqliteQuery.activeChange(fixture.projectId(), fixture.primaryChangeId())
                    .orElseThrow();
        }

        SnapshotItemResult<Specification> memorySpecification = memoryQuery
                .activeSpecification(fixture.projectId(), fixture.specificationId())
                .orElseThrow();
        SnapshotItemResult<ChangeProposal> memoryChange = memoryQuery
                .activeChange(fixture.projectId(), fixture.primaryChangeId())
                .orElseThrow();

        assertEquals(memorySpecification, sqliteSpecification);
        assertEquals(memoryChange, sqliteChange);
        assertEquals(fixture.specificationId(), memorySpecification.item().orElseThrow().id());
        assertEquals(fixture.primaryChangeId(), memoryChange.item().orElseThrow().id());

        SnapshotItemResult<Specification> missing = memoryQuery
                .activeSpecification(fixture.projectId(), SpecificationId.generate())
                .orElseThrow();
        assertTrue(missing.item().isEmpty());
        assertEquals(fixture.snapshotId(), missing.snapshot().id());
    }

    @Test
    void changesAndRelatedListsAreSortedFilteredAndPaginatedAfterOrdering() {
        Fixture fixture = fixture();
        MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);
        seedPublished(core, core, content, fixture);
        BusinessContentQueryService query = new BusinessContentQueryService(core, content);

        SnapshotPage<ChangeProposal> changes = query
                .listActiveChanges(fixture.projectId(), new PageRequest(1, 2))
                .orElseThrow();
        assertEquals(fixture.content().changes().subList(1, 3), changes.items());
        assertEquals(3, changes.totalMatches());
        assertFalse(changes.hasMore());

        List<Constraint> expectedConstraints = fixture.content().constraints().stream()
                .filter(item -> item.changeId().equals(fixture.primaryChangeId()))
                .toList();
        SnapshotPage<Constraint> constraints = query
                .activeConstraints(fixture.projectId(), fixture.primaryChangeId(), new PageRequest(0, 1))
                .orElseThrow();
        assertEquals(expectedConstraints.subList(0, 1), constraints.items());
        assertEquals(expectedConstraints.size(), constraints.totalMatches());
        assertEquals(expectedConstraints.size() > 1, constraints.hasMore());

        List<DesignDecision> expectedDecisions = fixture.content().designDecisions().stream()
                .filter(item -> item.changeId().equals(fixture.primaryChangeId()))
                .toList();
        SnapshotPage<DesignDecision> decisions = query
                .activeDesignDecisions(fixture.projectId(), fixture.primaryChangeId(), new PageRequest(0, 100))
                .orElseThrow();
        assertEquals(expectedDecisions, decisions.items());

        List<ImplementationTask> expectedTasks = fixture.content().tasks().stream()
                .filter(item -> item.changeId().equals(fixture.primaryChangeId()))
                .toList();
        SnapshotPage<ImplementationTask> tasks = query
                .activeImplementationTasks(fixture.projectId(), fixture.primaryChangeId(), new PageRequest(0, 100))
                .orElseThrow();
        assertEquals(expectedTasks, tasks.items());
    }

    @Test
    void explicitRetiredSnapshotIsReadableWhileReadySnapshotIsRejected() {
        HistoricalFixture history = historicalFixture();
        MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);

        core.putProject(new ProjectStoreEntry(history.projectId(), SourceLocator.file("workspace")));
        putVersionAndSnapshot(core, core, history.retiredSnapshot(), history.retiredVersion(), Optional.empty());
        content.putSnapshotContent(history.retiredContent());
        core.activateSnapshot(history.retiredSnapshot().id(), Optional.empty());

        putVersionAndSnapshot(
                core,
                core,
                history.activeSnapshot(),
                history.activeVersion(),
                Optional.of(history.retiredVersion().id()));
        content.putSnapshotContent(history.activeContent());
        core.activateSnapshot(history.activeSnapshot().id(), Optional.of(history.retiredSnapshot().id()));

        core.putSnapshot(history.readySnapshot());
        core.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                history.readySnapshot().id(), history.activeVersion().id()));

        BusinessContentQueryService query = new BusinessContentQueryService(core, content);

        SnapshotItemResult<Specification> retired = query.snapshotSpecification(
                history.retiredSnapshot().id(), history.retiredContent().specifications().get(0).id());
        assertEquals(KnowledgeSnapshotState.RETIRED, retired.snapshot().state());
        assertTrue(retired.item().isPresent());

        SnapshotItemResult<Specification> active = query.snapshotSpecification(
                history.activeSnapshot().id(), history.activeContent().specifications().get(0).id());
        assertEquals(KnowledgeSnapshotState.ACTIVE, active.snapshot().state());

        assertThrows(
                KnowledgeStoreException.class,
                () -> query.snapshotSpecification(
                        history.readySnapshot().id(), history.activeContent().specifications().get(0).id()));
    }

    @Test
    void noActiveSnapshotIsDistinctFromPublishedSnapshotWithoutBusinessProjection() {
        Fixture fixture = fixture();
        MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);

        core.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace")));
        core.putSnapshot(readySnapshot(fixture.snapshotId(), fixture.projectId(), Optional.empty(), "revision-1"));
        core.putSpecificationVersion(specificationVersion(fixture.versionId(), fixture.projectId(), 1L, Optional.empty()));
        core.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(fixture.snapshotId(), fixture.versionId()));

        BusinessContentQueryService query = new BusinessContentQueryService(core, content);
        assertTrue(query.activeSpecification(fixture.projectId(), fixture.specificationId()).isEmpty());

        core.activateSnapshot(fixture.snapshotId(), Optional.empty());
        assertThrows(
                KnowledgeStoreException.class,
                () -> query.activeSpecification(fixture.projectId(), fixture.specificationId()));
    }

    @Test
    void unknownChangeProducesEmptyBoundedRelatedPagesWithoutSyntheticObjects() {
        Fixture fixture = fixture();
        MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);
        seedPublished(core, core, content, fixture);
        BusinessContentQueryService query = new BusinessContentQueryService(core, content);
        ChangeId unknown = ChangeId.generate();
        PageRequest pageRequest = new PageRequest(0, 10);

        SnapshotPage<Constraint> constraints = query.activeConstraints(fixture.projectId(), unknown, pageRequest).orElseThrow();
        SnapshotPage<DesignDecision> decisions = query.activeDesignDecisions(fixture.projectId(), unknown, pageRequest).orElseThrow();
        SnapshotPage<ImplementationTask> tasks = query.activeImplementationTasks(fixture.projectId(), unknown, pageRequest).orElseThrow();

        assertEquals(List.of(), constraints.items());
        assertEquals(0, constraints.totalMatches());
        assertFalse(constraints.hasMore());
        assertEquals(List.of(), decisions.items());
        assertEquals(List.of(), tasks.items());
    }

    @Test
    void sqliteReopenPreservesDeterministicBusinessQueries() {
        Fixture fixture = fixture();
        Path database = tempDir.resolve("query-reopen.db");

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database)) {
            seedPublished(snapshots, versions, content, fixture);
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database)) {
            BusinessContentQueryService query = new BusinessContentQueryService(snapshots, content);
            assertEquals(
                    fixture.content().changes().subList(0, 2),
                    query.listActiveChanges(fixture.projectId(), new PageRequest(0, 2)).orElseThrow().items());
            assertEquals(
                    fixture.content().specifications().get(0),
                    query.activeSpecification(fixture.projectId(), fixture.specificationId())
                            .orElseThrow().item().orElseThrow());
            assertEquals(
                    fixture.content().tasks().stream()
                            .filter(item -> item.changeId().equals(fixture.primaryChangeId()))
                            .toList(),
                    query.activeImplementationTasks(
                                    fixture.projectId(), fixture.primaryChangeId(), new PageRequest(0, 100))
                            .orElseThrow().items());
        }
    }

    @Test
    void acceptanceCriteriaAreNotSynthesizedFromScenarios() {
        Fixture fixture = fixture();
        assertFalse(fixture.content().scenarios().isEmpty());
        assertTrue(fixture.content().acceptanceCriteria().isEmpty());

        MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);
        seedPublished(core, core, content, fixture);

        BusinessContentQueryService query = new BusinessContentQueryService(core, content);
        var allAcceptance = query.activeAcceptanceCriteria(fixture.projectId(), new PageRequest(0, 100)).orElseThrow();
        var changeAcceptance = query.activeAcceptanceCriteriaForChange(
                fixture.projectId(), fixture.primaryChangeId(), new PageRequest(0, 100)).orElseThrow();

        assertTrue(allAcceptance.items().isEmpty());
        assertEquals(0, allAcceptance.totalMatches());
        assertTrue(changeAcceptance.items().isEmpty());
        assertEquals(0, changeAcceptance.totalMatches());
    }

    private void seedPublished(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore versions,
            SnapshotBusinessContentStore contentStore,
            Fixture fixture) {
        snapshots.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace")));
        snapshots.putSnapshot(readySnapshot(fixture.snapshotId(), fixture.projectId(), Optional.empty(), "revision-1"));
        versions.putSpecificationVersion(specificationVersion(fixture.versionId(), fixture.projectId(), 1L, Optional.empty()));
        versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(fixture.snapshotId(), fixture.versionId()));
        contentStore.putSnapshotContent(fixture.content());
        snapshots.activateSnapshot(fixture.snapshotId(), Optional.empty());
    }

    private void putVersionAndSnapshot(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore versions,
            KnowledgeSnapshotMetadata snapshot,
            SpecificationVersion version,
            Optional<SpecificationVersionId> predecessorVersion) {
        snapshots.putSnapshot(snapshot);
        versions.putSpecificationVersion(new SpecificationVersion(
                version.id(),
                version.projectId(),
                version.sequence(),
                version.providerVersion(),
                version.sourceRevision(),
                version.createdAt(),
                predecessorVersion));
        versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshot.id(), version.id()));
    }

    private Fixture fixture() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        RequirementId requirementId = RequirementId.generate();
        Evidence evidence = new Evidence(
                EvidenceId.generate(),
                SourceLocator.file("specs/business.md"),
                Optional.of(new SourceRange(1, 30)),
                Optional.of("sha256:business"));
        Provenance provenance = provenance(evidence.id(), "specs/business.md", "SOURCE-BUSINESS");

        Specification specification = new Specification(
                SpecificationId.generate(), projectId, "business", "Business", Optional.of("Business rules"), provenance);

        ChangeProposal primary = change(projectId, provenance, "CHG-A", "Primary change");
        ChangeProposal secondary = change(projectId, provenance, "CHG-B", "Secondary change");
        ChangeProposal tertiary = change(projectId, provenance, "CHG-C", "Tertiary change");

        Constraint constraintOne = new Constraint(
                ConstraintId.generate(), primary.id(), "must remain deterministic", provenance);
        Constraint constraintTwo = new Constraint(
                ConstraintId.generate(), primary.id(), "must preserve auditability", provenance);
        Constraint otherConstraint = new Constraint(
                ConstraintId.generate(), secondary.id(), "must isolate changes", provenance);

        DesignDecision decisionOne = new DesignDecision(
                DesignDecisionId.generate(), primary.id(), "Explicit query", "Use explicit snapshot queries", provenance);
        DesignDecision decisionTwo = new DesignDecision(
                DesignDecisionId.generate(), primary.id(), "Stable order", "Order by domain identity", provenance);
        DesignDecision otherDecision = new DesignDecision(
                DesignDecisionId.generate(), secondary.id(), "Separate history", "Keep retired reads explicit", provenance);

        ImplementationTask taskOne = new ImplementationTask(
                TaskId.generate(), primary.id(), Optional.of("TASK-A"), "Implement active getter", false, provenance);
        ImplementationTask taskTwo = new ImplementationTask(
                TaskId.generate(), primary.id(), Optional.of("TASK-B"), "Implement snapshot getter", false, provenance);
        ImplementationTask otherTask = new ImplementationTask(
                TaskId.generate(), secondary.id(), Optional.of("TASK-C"), "Implement history query", true, provenance);

        Scenario scenario = new Scenario(
                ScenarioId.generate(),
                Optional.of(requirementId),
                "Read current state",
                List.of("project exists"),
                "consumer requests current content",
                "active snapshot content is returned",
                provenance);

        SnapshotBusinessContent content = new SnapshotBusinessContent(
                snapshotId,
                versionId,
                List.of(specification),
                List.of(scenario),
                List.of(tertiary, primary, secondary),
                List.of(constraintTwo, otherConstraint, constraintOne),
                List.of(decisionTwo, otherDecision, decisionOne),
                List.of(taskTwo, otherTask, taskOne),
                List.of(evidence));

        return new Fixture(projectId, snapshotId, versionId, specification.id(), primary.id(), content);
    }

    private HistoricalFixture historicalFixture() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId retiredId = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId activeId = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId readyId = KnowledgeSnapshotId.generate();
        SpecificationVersionId retiredVersionId = SpecificationVersionId.generate();
        SpecificationVersionId activeVersionId = SpecificationVersionId.generate();

        Evidence retiredEvidence = evidence("history-retired");
        Evidence activeEvidence = evidence("history-active");
        Provenance retiredProvenance = provenance(retiredEvidence.id(), "specs/history.md", "HISTORY-1");
        Provenance activeProvenance = provenance(activeEvidence.id(), "specs/history.md", "HISTORY-2");

        Specification retiredSpecification = new Specification(
                SpecificationId.generate(), projectId, "history", "History v1", Optional.empty(), retiredProvenance);
        Specification activeSpecification = new Specification(
                SpecificationId.generate(), projectId, "history", "History v2", Optional.empty(), activeProvenance);

        SnapshotBusinessContent retiredContent = new SnapshotBusinessContent(
                retiredId, retiredVersionId, List.of(retiredSpecification), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(retiredEvidence));
        SnapshotBusinessContent activeContent = new SnapshotBusinessContent(
                activeId, activeVersionId, List.of(activeSpecification), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(activeEvidence));

        KnowledgeSnapshotMetadata retiredSnapshot = readySnapshot(retiredId, projectId, Optional.empty(), "revision-1");
        KnowledgeSnapshotMetadata activeSnapshot = readySnapshot(activeId, projectId, Optional.of(retiredId), "revision-2");
        KnowledgeSnapshotMetadata readySnapshot = readySnapshot(readyId, projectId, Optional.of(activeId), "revision-3");

        SpecificationVersion retiredVersion = specificationVersion(retiredVersionId, projectId, 1L, Optional.empty());
        SpecificationVersion activeVersion = specificationVersion(activeVersionId, projectId, 2L, Optional.of(retiredVersionId));

        return new HistoricalFixture(
                projectId,
                retiredSnapshot,
                activeSnapshot,
                readySnapshot,
                retiredVersion,
                activeVersion,
                retiredContent,
                activeContent);
    }

    private ChangeProposal change(
            ProjectSpecificationId projectId,
            Provenance provenance,
            String key,
            String title) {
        return new ChangeProposal(
                ChangeId.generate(),
                projectId,
                Optional.of(key),
                title,
                "Deliver " + title,
                List.of("scope"),
                List.of(),
                List.of(),
                provenance);
    }

    private Evidence evidence(String key) {
        return new Evidence(
                EvidenceId.generate(),
                SourceLocator.file("specs/" + key + ".md"),
                Optional.empty(),
                Optional.of("sha256:" + key));
    }

    private Provenance provenance(EvidenceId evidenceId, String path, String externalId) {
        return new Provenance(
                new ProviderId("test-provider"),
                Optional.of("1"),
                SourceLocator.file(path),
                Optional.of(externalId),
                Optional.of("source-revision"),
                evidenceId);
    }

    private SpecificationVersion specificationVersion(
            SpecificationVersionId id,
            ProjectSpecificationId projectId,
            long sequence,
            Optional<SpecificationVersionId> predecessor) {
        return new SpecificationVersion(
                id,
                projectId,
                Optional.of(sequence),
                Optional.of("provider-v1"),
                Optional.of("revision-" + sequence),
                T0.plusSeconds(sequence),
                predecessor);
    }

    private KnowledgeSnapshotMetadata readySnapshot(
            KnowledgeSnapshotId id,
            ProjectSpecificationId projectId,
            Optional<KnowledgeSnapshotId> predecessor,
            String revision) {
        return new KnowledgeSnapshotMetadata(
                id,
                projectId,
                predecessor,
                KnowledgeSnapshotState.READY,
                Optional.of(revision),
                T0);
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            SpecificationId specificationId,
            ChangeId primaryChangeId,
            SnapshotBusinessContent content) {
    }

    private record HistoricalFixture(
            ProjectSpecificationId projectId,
            KnowledgeSnapshotMetadata retiredSnapshot,
            KnowledgeSnapshotMetadata activeSnapshot,
            KnowledgeSnapshotMetadata readySnapshot,
            SpecificationVersion retiredVersion,
            SpecificationVersion activeVersion,
            SnapshotBusinessContent retiredContent,
            SnapshotBusinessContent activeContent) {
    }
}
