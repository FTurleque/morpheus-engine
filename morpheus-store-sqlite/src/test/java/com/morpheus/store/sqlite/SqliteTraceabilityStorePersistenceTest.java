package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.traceability.TraceabilityConfidence;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snapshot-scoped traceability: what a link keeps, and what a query is allowed to see.
 *
 * <p>Traceability answers "what does this affect", so the two ways it can be wrong are both silent: a link that
 * leaks across snapshots would answer with a superseded truth, and a relation filter that ignores its argument
 * would answer with too much. Both are asserted against a real database, together with the parts of a link that
 * a column can quietly drop -- its confidence and its evidence set.</p>
 */
class SqliteTraceabilityStorePersistenceTest {
    private static final Instant OBSERVED = Instant.parse("2026-09-04T09:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void anExplicitLinkIsReadableFromBothOfItsEndsAfterAReopen() {
        Path database = tempDir.resolve("traceability.db");
        KnowledgeSnapshotId snapshotId = snapshot(database);
        TraceabilityEntityRef source = ref(TraceabilityEntityKind.REQUIREMENT);
        TraceabilityEntityRef target = ref(TraceabilityEntityKind.IMPLEMENTATION_TASK);
        TraceabilityLink link = explicitLink(source, TraceabilityRelationType.SATISFIES, target);

        try (SqliteTraceabilityStore store = new SqliteTraceabilityStore(database)) {
            store.putLink(snapshotId, link);
        }

        try (SqliteTraceabilityStore reopened = new SqliteTraceabilityStore(database)) {
            assertEquals(link, reopened.findLink(snapshotId, link.id()).orElseThrow());
            assertEquals(List.of(link), reopened.outgoing(snapshotId, source, Set.of()));
            assertEquals(List.of(link), reopened.incoming(snapshotId, target, Set.of()));
            assertEquals(List.of(), reopened.outgoing(snapshotId, target, Set.of()));
            assertEquals(List.of(), reopened.incoming(snapshotId, source, Set.of()));
        }
    }

    @Test
    void aHeuristicLinkKeepsItsConfidenceAndItsEvidence() {
        Path database = tempDir.resolve("heuristic.db");
        KnowledgeSnapshotId snapshotId = snapshot(database);
        EvidenceId first = EvidenceId.generate();
        EvidenceId second = EvidenceId.generate();
        TraceabilityLink link = new TraceabilityLink(
                TraceabilityLinkId.generate(),
                ref(TraceabilityEntityKind.REQUIREMENT),
                TraceabilityRelationType.RELATED_TO,
                ref(TraceabilityEntityKind.DESIGN_DECISION),
                TraceabilityLinkOrigin.HEURISTIC,
                TraceabilityResolutionState.HEURISTIC,
                Optional.of(new TraceabilityConfidence(0.75d)),
                Set.of(first, second),
                OBSERVED);

        try (SqliteTraceabilityStore store = new SqliteTraceabilityStore(database)) {
            store.putLink(snapshotId, link);

            TraceabilityLink stored = store.findLink(snapshotId, link.id()).orElseThrow();
            assertEquals(Optional.of(new TraceabilityConfidence(0.75d)), stored.confidence());
            assertEquals(Set.of(first, second), stored.evidenceIds());
            assertEquals(TraceabilityLinkOrigin.HEURISTIC, stored.origin());
            assertEquals(TraceabilityResolutionState.HEURISTIC, stored.resolution());
        }
    }

    @Test
    void aRelationFilterNarrowsBothDirections() {
        Path database = tempDir.resolve("filter.db");
        KnowledgeSnapshotId snapshotId = snapshot(database);
        TraceabilityEntityRef source = ref(TraceabilityEntityKind.REQUIREMENT);
        TraceabilityLink satisfies = explicitLink(
                source, TraceabilityRelationType.SATISFIES, ref(TraceabilityEntityKind.IMPLEMENTATION_TASK));
        TraceabilityLink dependsOn = explicitLink(
                source, TraceabilityRelationType.DEPENDS_ON, ref(TraceabilityEntityKind.REQUIREMENT));

        try (SqliteTraceabilityStore store = new SqliteTraceabilityStore(database)) {
            store.putLinks(snapshotId, List.of(satisfies, dependsOn));

            assertEquals(2, store.outgoing(snapshotId, source, Set.of()).size(),
                    "an empty relation set must not be read as 'nothing'");
            assertEquals(List.of(satisfies),
                    store.outgoing(snapshotId, source, Set.of(TraceabilityRelationType.SATISFIES)));
            assertEquals(List.of(dependsOn),
                    store.outgoing(snapshotId, source, Set.of(TraceabilityRelationType.DEPENDS_ON)));
            assertEquals(List.of(),
                    store.outgoing(snapshotId, source, Set.of(TraceabilityRelationType.SUPERSEDES)));
            assertEquals(List.of(satisfies),
                    store.incoming(snapshotId, satisfies.target(), Set.of(TraceabilityRelationType.SATISFIES)));
        }
    }

    /** Two snapshots of one project are two truths; a link recorded in one must never answer for the other. */
    @Test
    void linksNeverLeakBetweenSnapshots() {
        Path database = tempDir.resolve("snapshots.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId first = snapshot(database, projectId, Optional.empty());
        KnowledgeSnapshotId second = snapshot(database, projectId, Optional.of(first));
        TraceabilityEntityRef source = ref(TraceabilityEntityKind.REQUIREMENT);
        TraceabilityLink link = explicitLink(
                source, TraceabilityRelationType.SATISFIES, ref(TraceabilityEntityKind.IMPLEMENTATION_TASK));

        try (SqliteTraceabilityStore store = new SqliteTraceabilityStore(database)) {
            store.putLink(first, link);

            assertEquals(Optional.of(link), store.findLink(first, link.id()));
            assertEquals(Optional.empty(), store.findLink(second, link.id()));
            assertEquals(List.of(), store.outgoing(second, source, Set.of()));
        }
    }

    @Test
    void anUnknownSnapshotCannotReceiveALink() {
        Path database = tempDir.resolve("orphan.db");
        KnowledgeSnapshotId unknown = KnowledgeSnapshotId.generate();
        TraceabilityLink link = explicitLink(
                ref(TraceabilityEntityKind.REQUIREMENT),
                TraceabilityRelationType.SATISFIES,
                ref(TraceabilityEntityKind.IMPLEMENTATION_TASK));

        try (SqliteTraceabilityStore store = new SqliteTraceabilityStore(database)) {
            assertTrue(assertThrows(KnowledgeStoreException.class, () -> store.putLink(unknown, link))
                    .getMessage().contains("snapshot not found for traceability link"));
            assertTrue(assertThrows(KnowledgeStoreException.class, () -> store.findLink(unknown, link.id()))
                    .getMessage().contains("snapshot not found for traceability link"),
                    "reading a snapshot that does not exist must fail closed, never look like an empty snapshot");
        }
    }

    /** One identity means one link. A second link claiming it is a collision, not an update. */
    @Test
    void reusingALinkIdentityForADifferentLinkIsRefused() {
        Path database = tempDir.resolve("collision.db");
        KnowledgeSnapshotId snapshotId = snapshot(database);
        TraceabilityLinkId reused = TraceabilityLinkId.generate();
        TraceabilityLink original = link(
                reused, ref(TraceabilityEntityKind.REQUIREMENT), TraceabilityRelationType.SATISFIES,
                ref(TraceabilityEntityKind.IMPLEMENTATION_TASK));
        TraceabilityLink impostor = link(
                reused, ref(TraceabilityEntityKind.REQUIREMENT), TraceabilityRelationType.DEPENDS_ON,
                ref(TraceabilityEntityKind.REQUIREMENT));

        try (SqliteTraceabilityStore store = new SqliteTraceabilityStore(database)) {
            store.putLink(snapshotId, original);

            assertTrue(assertThrows(KnowledgeStoreException.class, () -> store.putLink(snapshotId, impostor))
                    .getMessage().contains("traceability link identity collision"));
            assertEquals(original, store.findLink(snapshotId, reused).orElseThrow());
        }
    }

    /** Rewriting the same observation is how a re-sync behaves, and it must stay idempotent. */
    @Test
    void writingTheSameLinkTwiceIsIdempotent() {
        Path database = tempDir.resolve("idempotent.db");
        KnowledgeSnapshotId snapshotId = snapshot(database);
        TraceabilityEntityRef source = ref(TraceabilityEntityKind.REQUIREMENT);
        TraceabilityLink link = explicitLink(
                source, TraceabilityRelationType.SATISFIES, ref(TraceabilityEntityKind.IMPLEMENTATION_TASK));

        try (SqliteTraceabilityStore store = new SqliteTraceabilityStore(database)) {
            store.putLink(snapshotId, link);
            store.putLink(snapshotId, link);

            assertEquals(List.of(link), store.outgoing(snapshotId, source, Set.of()));
        }
    }

    @Test
    void anEmptyBatchWritesNothingAndFailsNothing() {
        Path database = tempDir.resolve("empty-batch.db");
        KnowledgeSnapshotId snapshotId = snapshot(database);
        TraceabilityEntityRef source = ref(TraceabilityEntityKind.REQUIREMENT);

        try (SqliteTraceabilityStore store = new SqliteTraceabilityStore(database)) {
            store.putLinks(snapshotId, List.of());

            assertEquals(List.of(), store.outgoing(snapshotId, source, Set.of()));
        }
    }

    @Test
    void aClosedStoreRefusesEveryOperationInsteadOfFailingLater() {
        Path database = tempDir.resolve("closed.db");
        KnowledgeSnapshotId snapshotId = snapshot(database);
        TraceabilityEntityRef source = ref(TraceabilityEntityKind.REQUIREMENT);
        TraceabilityLink link = explicitLink(
                source, TraceabilityRelationType.SATISFIES, ref(TraceabilityEntityKind.IMPLEMENTATION_TASK));
        SqliteTraceabilityStore store = new SqliteTraceabilityStore(database);
        store.close();
        store.close();

        assertThrows(KnowledgeStoreException.class, () -> store.putLink(snapshotId, link));
        assertThrows(KnowledgeStoreException.class, () -> store.findLink(snapshotId, link.id()));
        assertThrows(KnowledgeStoreException.class, () -> store.outgoing(snapshotId, source, Set.of()));
    }

    private static TraceabilityEntityRef ref(TraceabilityEntityKind kind) {
        return new TraceabilityEntityRef(kind, DomainIdentity.generate());
    }

    private static TraceabilityLink explicitLink(
            TraceabilityEntityRef source, TraceabilityRelationType relation, TraceabilityEntityRef target) {
        return link(TraceabilityLinkId.generate(), source, relation, target);
    }

    private static TraceabilityLink link(
            TraceabilityLinkId id,
            TraceabilityEntityRef source,
            TraceabilityRelationType relation,
            TraceabilityEntityRef target) {
        return new TraceabilityLink(
                id,
                source,
                relation,
                target,
                TraceabilityLinkOrigin.EXPLICIT,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of(EvidenceId.generate()),
                OBSERVED);
    }

    private KnowledgeSnapshotId snapshot(Path database) {
        return snapshot(database, ProjectSpecificationId.generate(), Optional.empty());
    }

    private KnowledgeSnapshotId snapshot(
            Path database, ProjectSpecificationId projectId, Optional<KnowledgeSnapshotId> predecessor) {
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        try (SqliteSpecificationKnowledgeStore store = new SqliteSpecificationKnowledgeStore(database)) {
            if (predecessor.isEmpty()) {
                store.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
            }
            store.putSnapshot(new KnowledgeSnapshotMetadata(
                    snapshotId,
                    projectId,
                    predecessor,
                    KnowledgeSnapshotState.BUILDING,
                    Optional.of("revision"),
                    OBSERVED));
        }
        return snapshotId;
    }
}
