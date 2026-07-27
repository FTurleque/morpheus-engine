package com.morpheus.architecture.m19;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.traceability.TraceabilitySubgraph;
import com.morpheus.application.traceability.TraceabilityTraversalDirection;
import com.morpheus.application.traceability.TraceabilityTraversalService;
import com.morpheus.domain.evidence.EvidenceId;
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
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit M19 large-graph traversal gate; invoked only by validate-m19. */
class M19TraceabilityPerformanceGate {
    private static final int NODE_COUNT = 5_000;
    private static final int OUTGOING_PER_NODE = 5;
    private static final int MAX_DEPTH = 4;
    private static final long TRAVERSAL_BUDGET_NANOS = 2_000_000_000L;
    private static final int WARMUP_ITERATIONS = 1;
    private static final int MEASURED_ITERATIONS = 5;
    private static final Instant T0 = Instant.parse("2026-07-26T18:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void twentyFiveThousandLinkSqliteGraphTraversalStaysBoundedDeterministicAndWithinBudget() {
        Path database = tempDir.resolve("traceability-large.db");
        ProjectSpecificationId projectId = new ProjectSpecificationId(
                M19LargeFixtureSupport.deterministicIdentity(1920, 1));
        KnowledgeSnapshotId snapshotId = new KnowledgeSnapshotId(
                M19LargeFixtureSupport.deterministicIdentity(1920, 2));
        List<TraceabilityEntityRef> nodes = createNodes();

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("m19/trace-large")));
            snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                    snapshotId,
                    projectId,
                    Optional.empty(),
                    KnowledgeSnapshotState.READY,
                    Optional.of("revision-trace-large"),
                    T0));
            snapshots.activateSnapshot(snapshotId, Optional.empty());

            int inserted = 0;
            for (int sourceIndex = 0; sourceIndex < NODE_COUNT; sourceIndex++) {
                for (int edge = 1; edge <= OUTGOING_PER_NODE; edge++) {
                    int targetIndex = Math.floorMod(sourceIndex * OUTGOING_PER_NODE + edge, NODE_COUNT);
                    traceability.putLink(snapshotId, link(
                            nodes.get(sourceIndex),
                            nodes.get(targetIndex),
                            inserted));
                    inserted++;
                }
            }
            assertEquals(M19LargeFixtureSupport.GATE_TRACEABILITY_LINKS, inserted);

            TraceabilityTraversalService service = new TraceabilityTraversalService(traceability);
            TraceabilityEntityRef start = nodes.get(0);
            TraceabilitySubgraph expected = service.traverse(
                    snapshotId,
                    start,
                    MAX_DEPTH,
                    TraceabilityTraversalDirection.BIDIRECTIONAL,
                    Set.of());
            assertFalse(expected.links().isEmpty());
            assertTrue(expected.nodes().size() <= NODE_COUNT);

            for (int index = 0; index < WARMUP_ITERATIONS; index++) {
                assertEquals(expected, service.traverse(
                        snapshotId, start, MAX_DEPTH, TraceabilityTraversalDirection.BIDIRECTIONAL, Set.of()));
            }

            List<Long> samples = new ArrayList<>(MEASURED_ITERATIONS);
            for (int index = 0; index < MEASURED_ITERATIONS; index++) {
                long started = System.nanoTime();
                TraceabilitySubgraph actual = service.traverse(
                        snapshotId,
                        start,
                        MAX_DEPTH,
                        TraceabilityTraversalDirection.BIDIRECTIONAL,
                        Set.of());
                samples.add(System.nanoTime() - started);
                assertEquals(expected, actual);
            }

            long p95 = M19LargeFixtureSupport.percentile95Nanos(samples);
            System.out.println("M19_METRIC trace_traversal_p95_ms=" + p95 / 1_000_000L);
            System.out.println("M19_METRIC trace_traversal_nodes=" + expected.nodes().size());
            System.out.println("M19_METRIC trace_traversal_links=" + expected.links().size());
            assertTrue(p95 <= TRAVERSAL_BUDGET_NANOS,
                    () -> "trace traversal p95 exceeded frozen 2000ms budget: " + p95 / 1_000_000L + " ms");
        }
    }

    private List<TraceabilityEntityRef> createNodes() {
        List<TraceabilityEntityRef> nodes = new ArrayList<>(NODE_COUNT);
        for (int index = 0; index < NODE_COUNT; index++) {
            nodes.add(new TraceabilityEntityRef(
                    TraceabilityEntityKind.REQUIREMENT,
                    M19LargeFixtureSupport.deterministicIdentity(1921, index)));
        }
        return List.copyOf(nodes);
    }

    private TraceabilityLink link(TraceabilityEntityRef source, TraceabilityEntityRef target, int index) {
        return new TraceabilityLink(
                new TraceabilityLinkId(M19LargeFixtureSupport.deterministicIdentity(1922, index)),
                source,
                TraceabilityRelationType.RELATED_TO,
                target,
                TraceabilityLinkOrigin.DERIVED,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of(new EvidenceId(M19LargeFixtureSupport.deterministicIdentity(1923, index))),
                T0.plusNanos(index));
    }
}
