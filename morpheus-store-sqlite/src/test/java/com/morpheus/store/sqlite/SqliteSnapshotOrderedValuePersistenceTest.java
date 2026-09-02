package com.morpheus.store.sqlite;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Round-trips the snapshot collections whose rows carry an explicit ordinal.
 *
 * <p>Change scope, out-of-scope, risks and scenario preconditions are ordered lists, and the store persists each
 * through a statement whose table and column names are built into the SQL text rather than bound. This exercises
 * that path where the adapter lives: the values must come back in the order they were written, not in whatever
 * order the database returns rows.</p>
 */
class SqliteSnapshotOrderedValuePersistenceTest {
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void orderedChangeAndScenarioListsSurviveARoundTripInWrittenOrder() {
        Path database = tempDir.resolve("ordered-values.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();

        Evidence evidence = new Evidence(
                EvidenceId.generate(),
                SourceLocator.file("specs/billing.md"),
                Optional.empty(),
                Optional.of("sha256:billing"));
        Provenance provenance = new Provenance(
                new ProviderId("openspec"),
                Optional.of("1.0.0"),
                SourceLocator.file("specs/billing.md"),
                Optional.of("SOURCE-1"),
                Optional.of("revision-1"),
                evidence.id());

        // Deliberately not alphabetical: a store that lost the ordinal would return these sorted or arbitrary.
        List<String> scope = List.of("billing", "api", "audit");
        List<String> outOfScope = List.of("reporting", "mobile");
        List<String> risks = List.of("migration", "compatibility", "rollback");
        List<String> preconditions = List.of("user is authenticated", "account exists", "invoice is open");

        ChangeProposal change = new ChangeProposal(
                ChangeId.generate(),
                projectId,
                Optional.of("CHG-BILLING"),
                "Harden billing flow",
                "Make billing deterministic",
                scope,
                outOfScope,
                risks,
                provenance);
        Scenario scenario = new Scenario(
                ScenarioId.generate(),
                Optional.empty(),
                "Pay invoice",
                preconditions,
                "user pays invoice",
                "invoice becomes paid",
                provenance);

        SnapshotBusinessContent content = new SnapshotBusinessContent(
                snapshotId,
                versionId,
                List.of(),
                List.of(scenario),
                List.of(change),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(evidence));

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var store = new SqliteSnapshotBusinessContentStore(database)) {
            snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
            snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                    snapshotId,
                    projectId,
                    Optional.empty(),
                    KnowledgeSnapshotState.READY,
                    Optional.of("revision-1"),
                    T0));
            versions.putSpecificationVersion(new SpecificationVersion(
                    versionId,
                    projectId,
                    Optional.of(1L),
                    Optional.of("provider-v1"),
                    Optional.of("revision-1"),
                    T0,
                    Optional.empty()));
            versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));

            store.putSnapshotContent(content);
            SnapshotBusinessContent reloaded = store.findSnapshotContent(snapshotId).orElseThrow();

            ChangeProposal reloadedChange = reloaded.changes().getFirst();
            assertEquals(scope, reloadedChange.scope());
            assertEquals(outOfScope, reloadedChange.outOfScope());
            assertEquals(risks, reloadedChange.risks());
            assertEquals(preconditions, reloaded.scenarios().getFirst().preconditions());
        }
    }
}
