package com.morpheus.architecture;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintApplicability;
import com.morpheus.domain.constraint.ConstraintBlockingPolicy;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.constraint.ConstraintSatisfaction;
import com.morpheus.domain.constraint.ConstraintSeverity;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
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

class ConstraintSemanticsPersistenceContractTest {
    private static final Instant T0 = Instant.parse("2026-07-26T09:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqlitePreserveExplicitConstraintSemantics() {
        Fixture fixture = fixture();

        MemorySpecificationKnowledgeStore memoryCore = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore memoryContent = new MemorySnapshotBusinessContentStore(memoryCore, memoryCore);
        seed(memoryCore, memoryCore, fixture);
        memoryContent.putSnapshotContent(fixture.content());

        SnapshotBusinessContent sqliteResult;
        Path database = tempDir.resolve("constraint-semantics.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database)) {
            seed(snapshots, versions, fixture);
            content.putSnapshotContent(fixture.content());
            sqliteResult = content.findSnapshotContent(fixture.snapshotId()).orElseThrow();
        }

        assertEquals(fixture.content(), memoryContent.findSnapshotContent(fixture.snapshotId()).orElseThrow());
        assertEquals(fixture.content(), sqliteResult);
        Constraint persisted = sqliteResult.constraints().getFirst();
        assertEquals(ConstraintApplicability.APPLICABLE, persisted.applicability());
        assertEquals(ConstraintSeverity.CRITICAL, persisted.severity());
        assertEquals(ConstraintSatisfaction.VIOLATED, persisted.satisfaction());
        assertEquals(List.of(ChangeLifecycleState.VERIFYING), persisted.blockingPolicy().targetStates());
        assertEquals(List.of(fixture.supportingEvidenceId()), persisted.supportingEvidenceIds());
    }

    @Test
    void sqliteReopenPreservesConstraintSemanticsExactly() {
        Fixture fixture = fixture();
        Path database = tempDir.resolve("constraint-reopen.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database)) {
            seed(snapshots, versions, fixture);
            content.putSnapshotContent(fixture.content());
        }

        try (var reopened = new SqliteSnapshotBusinessContentStore(database)) {
            assertEquals(fixture.content(), reopened.findSnapshotContent(fixture.snapshotId()).orElseThrow());
        }
    }

    @Test
    void projectionRejectsUnknownConstraintSupportingEvidence() {
        Fixture fixture = fixture();
        Constraint source = fixture.content().constraints().getFirst();
        Constraint broken = new Constraint(
                source.id(),
                source.changeId(),
                source.statement(),
                source.applicability(),
                source.severity(),
                source.satisfaction(),
                source.blockingPolicy(),
                List.of(EvidenceId.generate()),
                source.provenance());

        assertThrows(IllegalArgumentException.class, () -> new SnapshotBusinessContent(
                fixture.snapshotId(),
                fixture.versionId(),
                List.of(),
                List.of(),
                fixture.content().changes(),
                List.of(broken),
                List.of(),
                List.of(),
                List.of(),
                fixture.content().evidence()));
    }

    private Fixture fixture() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        Evidence definition = new Evidence(
                EvidenceId.generate(), SourceLocator.file("specs/change.md"), Optional.empty(), Optional.of("sha256:def"));
        Evidence support = new Evidence(
                EvidenceId.generate(), SourceLocator.file("reviews/security.txt"), Optional.empty(), Optional.of("sha256:support"));
        Provenance provenance = new Provenance(
                new ProviderId("synthetic-json"),
                Optional.of("m16"),
                SourceLocator.file("specs/change.md"),
                Optional.of("constraint:security-review"),
                Optional.of("rev-1"),
                definition.id());
        ChangeProposal change = new ChangeProposal(
                ChangeId.generate(), projectId, Optional.of("secure-change"), "Secure change", "Require explicit review",
                List.of(), List.of(), List.of(), provenance);
        Constraint constraint = new Constraint(
                ConstraintId.generate(),
                change.id(),
                "Security review must pass before verification",
                ConstraintApplicability.APPLICABLE,
                ConstraintSeverity.CRITICAL,
                ConstraintSatisfaction.VIOLATED,
                ConstraintBlockingPolicy.blockWhenViolated(List.of(ChangeLifecycleState.VERIFYING)),
                List.of(support.id()),
                provenance);
        SnapshotBusinessContent content = new SnapshotBusinessContent(
                snapshotId,
                versionId,
                List.of(),
                List.of(),
                List.of(change),
                List.of(constraint),
                List.of(),
                List.of(),
                List.of(),
                List.of(definition, support));
        return new Fixture(projectId, snapshotId, versionId, support.id(), content);
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
                Optional.of("rev-1"),
                T0));
        versions.putSpecificationVersion(new SpecificationVersion(
                fixture.versionId(),
                fixture.projectId(),
                Optional.of(1L),
                Optional.of("synthetic-m16"),
                Optional.of("rev-1"),
                T0,
                Optional.empty()));
        versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(fixture.snapshotId(), fixture.versionId()));
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            EvidenceId supportingEvidenceId,
            SnapshotBusinessContent content) {
    }
}
