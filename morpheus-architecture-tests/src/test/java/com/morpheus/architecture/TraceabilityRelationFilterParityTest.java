package com.morpheus.architecture;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import com.morpheus.store.sqlite.adapter.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.adapter.SqliteTraceabilityStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The endpoint reads of the two traceability stores agree for every shape of relation filter, in both directions: the
 * empty set means every type, a named set keeps only its types, and a link of another snapshot is never returned.
 * The SQLite store applies the filter in its statement; this test is what says the statement keeps the same links as
 * the memory store's stream does.
 */
class TraceabilityRelationFilterParityTest {
    private static final Instant T0 = Instant.parse("2026-10-17T10:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void memoryStoreAppliesEveryShapeOfFilterInBothDirections() {
        var snapshots = new MemorySpecificationKnowledgeStore();
        verifyEveryShapeOfFilter(snapshots, new MemoryTraceabilityStore(snapshots));
    }

    @Test
    void sqliteStoreAppliesEveryShapeOfFilterInBothDirections() {
        Path database = tempDir.resolve("relation-filter.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            verifyEveryShapeOfFilter(snapshots, traceability);
        }
    }

    private void verifyEveryShapeOfFilter(SpecificationKnowledgeStore snapshots, TraceabilityStore traceability) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId otherSnapshotId = KnowledgeSnapshotId.generate();
        snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        snapshots.putSnapshot(snapshot(snapshotId, projectId, "revision-1"));
        snapshots.putSnapshot(snapshot(otherSnapshotId, projectId, "revision-2"));

        TraceabilityEntityRef hub = new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, DomainIdentity.generate());
        List<TraceabilityRelationType> used = List.of(
                TraceabilityRelationType.AFFECTS, TraceabilityRelationType.DECIDED_BY, TraceabilityRelationType.SATISFIES);
        for (TraceabilityRelationType relation : used) {
            traceability.putLink(snapshotId, link(hub, relation, ref(TraceabilityEntityKind.REQUIREMENT)));
            traceability.putLink(snapshotId, link(ref(TraceabilityEntityKind.REQUIREMENT), relation, hub));
        }
        TraceabilityRelationType elsewhereOnly = TraceabilityRelationType.IMPLEMENTS;
        traceability.putLink(otherSnapshotId,
                link(hub, elsewhereOnly, ref(TraceabilityEntityKind.IMPLEMENTATION_TASK)));
        TraceabilityRelationType absent = TraceabilityRelationType.SUPERSEDES;

        List<Set<TraceabilityRelationType>> filters = List.of(
                Set.of(),
                Set.of(used.get(0)),
                Set.of(used.get(1)),
                Set.of(used.get(0), used.get(2)),
                EnumSet.copyOf(used),
                EnumSet.allOf(TraceabilityRelationType.class),
                Set.of(absent),
                Set.of(elsewhereOnly),
                Set.of(absent, used.get(1)));
        for (Set<TraceabilityRelationType> filter : filters) {
            assertEquals(expected(traceability.outgoing(snapshotId, hub, Set.of()), filter),
                    traceability.outgoing(snapshotId, hub, filter), "outgoing " + filter);
            assertEquals(expected(traceability.incoming(snapshotId, hub, Set.of()), filter),
                    traceability.incoming(snapshotId, hub, filter), "incoming " + filter);
        }

        assertEquals(used.size(), traceability.outgoing(snapshotId, hub, Set.of()).size());
        assertEquals(used.size(), traceability.incoming(snapshotId, hub, Set.of()).size());
        assertEquals(List.of(), traceability.outgoing(snapshotId, hub, Set.of(elsewhereOnly)),
                "a link of another snapshot must not be returned");
        assertTrue(traceability.outgoing(snapshotId, hub, Set.of(absent)).isEmpty());
        assertEquals(1, traceability.outgoing(otherSnapshotId, hub, Set.of(elsewhereOnly)).size());
    }

    /** The filter the reads promise, applied to what the unfiltered read returned: same links, ordered by link id. */
    private static List<TraceabilityLink> expected(List<TraceabilityLink> every, Set<TraceabilityRelationType> filter) {
        return every.stream()
                .filter(link -> filter.isEmpty() || filter.contains(link.relationType()))
                .sorted(Comparator.comparing(TraceabilityLink::id))
                .toList();
    }

    private static TraceabilityEntityRef ref(TraceabilityEntityKind kind) {
        return new TraceabilityEntityRef(kind, DomainIdentity.generate());
    }

    private static KnowledgeSnapshotMetadata snapshot(
            KnowledgeSnapshotId id, ProjectSpecificationId projectId, String revision) {
        return new KnowledgeSnapshotMetadata(
                id, projectId, Optional.empty(), KnowledgeSnapshotState.READY, Optional.of(revision), T0);
    }

    private static TraceabilityLink link(
            TraceabilityEntityRef source, TraceabilityRelationType relation, TraceabilityEntityRef target) {
        return new TraceabilityLink(
                TraceabilityLinkId.generate(),
                source,
                relation,
                target,
                TraceabilityLinkOrigin.DERIVED,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of(EvidenceId.generate()),
                T0);
    }
}
