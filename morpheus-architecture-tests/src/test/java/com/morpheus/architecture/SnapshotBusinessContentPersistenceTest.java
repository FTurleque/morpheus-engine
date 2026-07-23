package com.morpheus.architecture;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotBusinessContentPersistenceTest {
    private static final Instant T0 = Instant.parse("2026-07-23T14:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqliteProduceSameCanonicalProjection() {
        Fixture fixture = fixture();

        MemorySpecificationKnowledgeStore memoryCore = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore memoryContent = new MemorySnapshotBusinessContentStore(memoryCore, memoryCore);
        seed(memoryCore, memoryCore, fixture);
        memoryContent.putSnapshotContent(fixture.content());
        SnapshotBusinessContent memoryResult = memoryContent.findSnapshotContent(fixture.snapshotId()).orElseThrow();

        Path database = tempDir.resolve("same-projection.db");
        SnapshotBusinessContent sqliteResult;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database)) {
            seed(snapshots, versions, fixture);
            content.putSnapshotContent(fixture.content());
            sqliteResult = content.findSnapshotContent(fixture.snapshotId()).orElseThrow();
        }

        assertEquals(fixture.content(), memoryResult);
        assertEquals(memoryResult, sqliteResult);
    }

    @Test
    void ownershipAndBindingRulesAreEquivalentAcrossBackends() {
        Fixture fixture = fixture();
        ProjectSpecificationId foreignProject = ProjectSpecificationId.generate();
        SpecificationVersionId foreignVersion = SpecificationVersionId.generate();

        MemorySpecificationKnowledgeStore memoryCore = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore memoryContent = new MemorySnapshotBusinessContentStore(memoryCore, memoryCore);
        seed(memoryCore, memoryCore, fixture);
        memoryCore.putSpecificationVersion(specificationVersion(foreignVersion, fixture.projectId(), 2L, Optional.of(fixture.versionId())));
        verifyOwnershipFailures(memoryContent, fixture, foreignProject, foreignVersion);

        Path database = tempDir.resolve("ownership.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database)) {
            seed(snapshots, versions, fixture);
            versions.putSpecificationVersion(specificationVersion(foreignVersion, fixture.projectId(), 2L, Optional.of(fixture.versionId())));
            verifyOwnershipFailures(content, fixture, foreignProject, foreignVersion);
        }
    }

    @Test
    void projectionRejectsDuplicateIdentitiesBrokenChangeLinksAndUnknownEvidence() {
        Fixture fixture = fixture();
        SnapshotBusinessContent content = fixture.content();

        assertThrows(IllegalArgumentException.class, () -> new SnapshotBusinessContent(
                fixture.snapshotId(), fixture.versionId(),
                List.of(content.specifications().get(0), content.specifications().get(0)),
                content.scenarios(), content.changes(), content.constraints(), content.designDecisions(), content.tasks(), content.evidence()));

        Constraint broken = new Constraint(
                ConstraintId.generate(), ChangeId.generate(), "must remain auditable", content.constraints().get(0).provenance());
        assertThrows(IllegalArgumentException.class, () -> new SnapshotBusinessContent(
                fixture.snapshotId(), fixture.versionId(), content.specifications(), content.scenarios(), content.changes(),
                List.of(broken), content.designDecisions(), content.tasks(), content.evidence()));

        Specification unknownEvidence = new Specification(
                SpecificationId.generate(), fixture.projectId(), "unknown-evidence", "Unknown evidence", Optional.empty(),
                provenance(EvidenceId.generate(), "specs/unknown.md", "SPEC-UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotBusinessContent(
                fixture.snapshotId(), fixture.versionId(), List.of(unknownEvidence), List.of(), List.of(), List.of(), List.of(), List.of(), content.evidence()));
    }

    @Test
    void repeatedProjectionIsIdempotentAndMutationCollidesAcrossBackends() {
        Fixture fixture = fixture();

        MemorySpecificationKnowledgeStore memoryCore = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore memoryContent = new MemorySnapshotBusinessContentStore(memoryCore, memoryCore);
        seed(memoryCore, memoryCore, fixture);
        verifyIdempotenceAndCollision(memoryContent, fixture);

        Path database = tempDir.resolve("collision.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database)) {
            seed(snapshots, versions, fixture);
            verifyIdempotenceAndCollision(content, fixture);
        }
    }

    @Test
    void orderedListsAndScenarioRequirementReferenceArePreserved() {
        Fixture fixture = fixture();
        Path database = tempDir.resolve("ordered.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database)) {
            seed(snapshots, versions, fixture);
            content.putSnapshotContent(fixture.content());
            SnapshotBusinessContent result = content.findSnapshotContent(fixture.snapshotId()).orElseThrow();

            Scenario scenario = result.scenarios().get(0);
            assertEquals(List.of("account exists", "invoice is open", "user is authenticated"), scenario.preconditions());
            assertEquals(Optional.of(fixture.requirementId()), scenario.requirementId());

            ChangeProposal change = result.changes().get(0);
            assertEquals(List.of("billing", "api"), change.scope());
            assertEquals(List.of("reporting", "mobile"), change.outOfScope());
            assertEquals(List.of("migration", "compatibility"), change.risks());
        }
    }

    @Test
    void sqliteReopenPreservesExactProjection() {
        Fixture fixture = fixture();
        Path database = tempDir.resolve("reopen-business-content.db");

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database)) {
            seed(snapshots, versions, fixture);
            content.putSnapshotContent(fixture.content());
        }

        try (var reopened = new SqliteSnapshotBusinessContentStore(database)) {
            assertEquals(fixture.content(), reopened.findSnapshotContent(fixture.snapshotId()).orElseThrow());
            assertTrue(reopened.findSnapshotContent(KnowledgeSnapshotId.generate()).isEmpty());
        }
    }

    private void verifyOwnershipFailures(
            SnapshotBusinessContentStore store,
            Fixture fixture,
            ProjectSpecificationId foreignProject,
            SpecificationVersionId foreignVersion) {
        SnapshotBusinessContent wrongVersion = copyWith(
                fixture.content(), fixture.snapshotId(), foreignVersion, fixture.content().specifications(), fixture.content().changes());
        assertThrows(KnowledgeStoreException.class, () -> store.putSnapshotContent(wrongVersion));

        Specification wrongSpecification = new Specification(
                SpecificationId.generate(), foreignProject, "foreign", "Foreign", Optional.empty(),
                fixture.content().specifications().get(0).provenance());
        SnapshotBusinessContent wrongProject = copyWith(
                fixture.content(), fixture.snapshotId(), fixture.versionId(), List.of(wrongSpecification), fixture.content().changes());
        assertThrows(KnowledgeStoreException.class, () -> store.putSnapshotContent(wrongProject));
    }

    private void verifyIdempotenceAndCollision(SnapshotBusinessContentStore store, Fixture fixture) {
        store.putSnapshotContent(fixture.content());
        store.putSnapshotContent(fixture.content());
        assertEquals(fixture.content(), store.findSnapshotContent(fixture.snapshotId()).orElseThrow());

        ChangeProposal changed = new ChangeProposal(
                fixture.content().changes().get(0).id(),
                fixture.projectId(),
                fixture.content().changes().get(0).key(),
                "Changed title",
                fixture.content().changes().get(0).intent(),
                fixture.content().changes().get(0).scope(),
                fixture.content().changes().get(0).outOfScope(),
                fixture.content().changes().get(0).risks(),
                fixture.content().changes().get(0).provenance());
        SnapshotBusinessContent collision = copyWith(
                fixture.content(), fixture.snapshotId(), fixture.versionId(), fixture.content().specifications(), List.of(changed));
        assertThrows(KnowledgeStoreException.class, () -> store.putSnapshotContent(collision));
    }

    private SnapshotBusinessContent copyWith(
            SnapshotBusinessContent base,
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            List<Specification> specifications,
            List<ChangeProposal> changes) {
        return new SnapshotBusinessContent(
                snapshotId,
                versionId,
                specifications,
                base.scenarios(),
                changes,
                base.constraints(),
                base.designDecisions(),
                base.tasks(),
                base.evidence());
    }

    private void seed(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore versions,
            Fixture fixture) {
        snapshots.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace")));
        snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                fixture.snapshotId(),
                fixture.projectId(),
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of("revision-1"),
                T0));
        versions.putSpecificationVersion(specificationVersion(fixture.versionId(), fixture.projectId(), 1L, Optional.empty()));
        versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(fixture.snapshotId(), fixture.versionId()));
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

    private Fixture fixture() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        RequirementId requirementId = RequirementId.generate();
        Evidence evidence = new Evidence(
                EvidenceId.generate(),
                SourceLocator.file("specs/billing.md"),
                Optional.of(new SourceRange(10, 18)),
                Optional.of("sha256:billing"));
        Provenance provenance = provenance(evidence.id(), "specs/billing.md", "SOURCE-1");
        Specification specification = new Specification(
                SpecificationId.generate(), projectId, "billing", "Billing", Optional.of("Billing rules"), provenance);
        Scenario scenario = new Scenario(
                ScenarioId.generate(),
                Optional.of(requirementId),
                "Pay invoice",
                List.of("account exists", "invoice is open", "user is authenticated"),
                "user pays invoice",
                "invoice becomes paid",
                provenance);
        ChangeProposal change = new ChangeProposal(
                ChangeId.generate(),
                projectId,
                Optional.of("CHG-BILLING"),
                "Harden billing flow",
                "Make billing deterministic",
                List.of("billing", "api"),
                List.of("reporting", "mobile"),
                List.of("migration", "compatibility"),
                provenance);
        Constraint constraint = new Constraint(
                ConstraintId.generate(), change.id(), "must preserve audit history", provenance);
        DesignDecision decision = new DesignDecision(
                DesignDecisionId.generate(), change.id(), "Use explicit state", "Persist explicit state transitions", provenance);
        ImplementationTask task = new ImplementationTask(
                TaskId.generate(), change.id(), Optional.of("TASK-BILLING"), "Implement billing state", false, provenance);

        SnapshotBusinessContent content = new SnapshotBusinessContent(
                snapshotId,
                versionId,
                List.of(specification),
                List.of(scenario),
                List.of(change),
                List.of(constraint),
                List.of(decision),
                List.of(task),
                List.of(evidence));
        return new Fixture(projectId, snapshotId, versionId, requirementId, content);
    }

    private static Provenance provenance(EvidenceId evidenceId, String path, String externalId) {
        return new Provenance(
                new ProviderId("test-provider"),
                Optional.of("1"),
                SourceLocator.file(path),
                Optional.of(externalId),
                Optional.of("source-revision"),
                evidenceId);
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            RequirementId requirementId,
            SnapshotBusinessContent content) {
    }
}
