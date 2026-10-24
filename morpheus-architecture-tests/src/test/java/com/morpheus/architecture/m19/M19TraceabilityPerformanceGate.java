package com.morpheus.architecture.m19;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.analysis.ChangeAnalysisResult;
import com.morpheus.application.analysis.ChangeAnalysisService;
import com.morpheus.application.analysis.ChangeAnalysisWarningCode;
import com.morpheus.application.analysis.ProposedChangeSet;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.requirement.RequirementDeltaId;
import com.morpheus.domain.requirement.RequirementDeltaKind;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import com.morpheus.store.sqlite.adapter.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.adapter.SqliteVersionedRequirementStore;
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
import com.morpheus.store.sqlite.adapter.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.adapter.SqliteTraceabilityStore;
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
    /*
     * A complete 5-ary tree of depth 4 -- 781 nodes, 625 of them leaves -- stored beside the mesh, in the same
     * database, and disconnected from it. It is the one shape where a traversal ends inside every budget and
     * therefore runs the depth-truncation check over its whole frontier: on the mesh, every depth under the node
     * budget ends in DEPTH_BUDGET_REACHED (measured: depth 3 reaches 332 nodes), and that check stops at the first
     * frontier node with an unrecorded link.
     *
     * Measured 24/09/2026 on the maintainer's Windows 10 workstation, JDK 21.0.12, three runs of five samples: p95
     * 258-326 ms for the tree against 145-190 ms for the truncated mesh traversal, the highest of each under the
     * validator's -Xmx768m -- the complete frontier check costs more than a traversal at the node budget. The budget
     * is declared, not derived from the mesh one, and leaves about 4.6 times the highest measurement.
     */
    private static final int TREE_BRANCHING = 5;
    private static final int TREE_NODE_COUNT = 781;
    private static final long FRONTIER_CHECK_BUDGET_NANOS = 1_500_000_000L;
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
            // Depth 4 from node 0 reaches 1 583 of the 5 000 nodes; the traversal node budget stops it first, and the
            // subgraph says so. Timing the truncated traversal still measures a traversal at its largest legal size.
            assertEquals(TraceabilityTraversalService.MAX_NODES, expected.nodes().size());
            assertEquals(Optional.of("NODE_BUDGET_REACHED:" + TraceabilityTraversalService.MAX_NODES),
                    expected.truncationReason());

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
            System.out.println("M19_METRIC trace_traversal_truncation=" + expected.truncationReason().orElse("none"));
            assertTrue(p95 <= TRAVERSAL_BUDGET_NANOS,
                    () -> "trace traversal p95 exceeded frozen 2000ms budget: " + p95 / 1_000_000L + " ms");

            List<TraceabilityEntityRef> tree = createTreeNodes();
            for (int child = 1; child < TREE_NODE_COUNT; child++) {
                traceability.putLink(snapshotId, link(tree.get((child - 1) / TREE_BRANCHING), tree.get(child),
                        M19LargeFixtureSupport.GATE_TRACEABILITY_LINKS + child));
            }
            TraceabilityEntityRef root = tree.get(0);
            TraceabilitySubgraph complete = service.traverse(
                    snapshotId, root, MAX_DEPTH, TraceabilityTraversalDirection.BIDIRECTIONAL, Set.of());
            // Not truncated first: a fixture that starts truncating would fall back into the short-circuit silently.
            assertTrue(complete.truncationReason().isEmpty(), complete.truncationReason()::toString);
            assertEquals(TREE_NODE_COUNT, complete.nodes().size());
            assertEquals(TREE_NODE_COUNT - 1, complete.links().size());

            for (int index = 0; index < WARMUP_ITERATIONS; index++) {
                assertEquals(complete, service.traverse(
                        snapshotId, root, MAX_DEPTH, TraceabilityTraversalDirection.BIDIRECTIONAL, Set.of()));
            }

            List<Long> frontierSamples = new ArrayList<>(MEASURED_ITERATIONS);
            for (int index = 0; index < MEASURED_ITERATIONS; index++) {
                long started = System.nanoTime();
                TraceabilitySubgraph actual = service.traverse(
                        snapshotId, root, MAX_DEPTH, TraceabilityTraversalDirection.BIDIRECTIONAL, Set.of());
                frontierSamples.add(System.nanoTime() - started);
                assertEquals(complete, actual);
            }

            long frontierP95 = M19LargeFixtureSupport.percentile95Nanos(frontierSamples);
            System.out.println("M19_METRIC trace_frontier_check_p95_ms=" + frontierP95 / 1_000_000L);
            System.out.println("M19_METRIC trace_frontier_check_nodes=" + complete.nodes().size());
            System.out.println("M19_METRIC trace_frontier_check_links=" + complete.links().size());
            System.out.println("M19_METRIC trace_frontier_check_truncation="
                    + complete.truncationReason().orElse("none"));
            assertTrue(frontierP95 <= FRONTIER_CHECK_BUDGET_NANOS,
                    () -> "untruncated trace traversal p95 exceeded frozen 1500ms budget: "
                            + frontierP95 / 1_000_000L + " ms");
        }
    }

    /*
     * Budget declared on 13/10/2026 (M19_PERFORMANCE_BUDGETS.md, section 4 bis) before the analysis was corrected: one
     * analysis is a handful of traversals and must cost a handful of them, so it is judged against the budget of one
     * traversal, 2,000 ms. Three deltas, each reaching the node budget in both directions along DEPENDS_ON links, the
     * only relation the analysis follows.
     */
    private static final int ANALYSIS_DELTAS = 3;
    private static final int ANALYSIS_DEPTH = 6;
    private static final long ANALYSIS_BUDGET_NANOS = 2_000_000_000L;

    @Test
    void changeAnalysisOverDependencyMeshesAtTheNodeBudgetStaysWithinBudget() {
        Path database = tempDir.resolve("change-analysis-large.db");
        ProjectSpecificationId projectId = new ProjectSpecificationId(
                M19LargeFixtureSupport.deterministicIdentity(1930, 1));
        KnowledgeSnapshotId snapshotId = new KnowledgeSnapshotId(
                M19LargeFixtureSupport.deterministicIdentity(1930, 2));
        SpecificationVersionId versionId = new SpecificationVersionId(
                M19LargeFixtureSupport.deterministicIdentity(1930, 3));
        SpecificationId specificationId = new SpecificationId(M19LargeFixtureSupport.deterministicIdentity(1930, 4));
        ChangeId changeId = new ChangeId(M19LargeFixtureSupport.deterministicIdentity(1930, 5));
        EvidenceId evidenceId = new EvidenceId(M19LargeFixtureSupport.deterministicIdentity(1930, 6));
        Provenance provenance = new Provenance(
                new ProviderId("m19-gate"), Optional.of("1"), SourceLocator.file("m19/change-analysis"),
                Optional.of("M19"), Optional.of("revision-analysis"), evidenceId);
        List<RequirementId> requirements = new ArrayList<>(NODE_COUNT);
        for (int index = 0; index < NODE_COUNT; index++) {
            requirements.add(new RequirementId(M19LargeFixtureSupport.deterministicIdentity(1931, index)));
        }

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirementStore = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("m19/change-analysis")));
            snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                    snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.READY,
                    Optional.of("revision-analysis"), T0));
            requirementStore.putSpecificationVersion(new SpecificationVersion(
                    versionId, projectId, Optional.of(1L), Optional.of("m19"), Optional.of("revision-analysis"), T0,
                    Optional.empty()));
            requirementStore.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
            content.putSnapshotContent(new SnapshotBusinessContent(
                    snapshotId, versionId, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of()));

            List<RequirementDelta> deltas = new ArrayList<>(ANALYSIS_DELTAS);
            for (int delta = 0; delta < ANALYSIS_DELTAS; delta++) {
                RequirementId id = requirements.get(delta * (NODE_COUNT / ANALYSIS_DELTAS));
                requirementStore.putRequirementVersion(new RequirementVersionRecord(
                        snapshotId,
                        new EntityVersion<>(
                                new EntityVersionId(M19LargeFixtureSupport.deterministicIdentity(1932, delta)),
                                id.value(), versionId, TemporalState.CURRENT,
                                new Requirement(id, specificationId, Optional.of("REQ-" + delta), "Current " + delta,
                                        "Current statement " + delta, provenance))));
                deltas.add(new RequirementDelta(
                        new RequirementDeltaId(M19LargeFixtureSupport.deterministicIdentity(1933, delta)),
                        changeId, RequirementDeltaKind.MODIFIED, "core", id, Optional.of("REQ-" + delta),
                        "Proposed " + delta, Optional.of("Proposed statement " + delta), List.of(), provenance));
            }

            List<TraceabilityLink> links = new ArrayList<>(M19LargeFixtureSupport.GATE_TRACEABILITY_LINKS);
            for (int source = 0; source < NODE_COUNT; source++) {
                for (int edge = 1; edge <= OUTGOING_PER_NODE; edge++) {
                    int target = Math.floorMod(source * OUTGOING_PER_NODE + edge, NODE_COUNT);
                    links.add(new TraceabilityLink(
                            new TraceabilityLinkId(
                                    M19LargeFixtureSupport.deterministicIdentity(1934, links.size())),
                            requirementRef(requirements.get(source)),
                            TraceabilityRelationType.DEPENDS_ON,
                            requirementRef(requirements.get(target)),
                            TraceabilityLinkOrigin.EXPLICIT,
                            TraceabilityResolutionState.RESOLVED,
                            Optional.empty(),
                            Set.of(evidenceId),
                            T0.plusNanos(links.size())));
                }
            }
            assertEquals(M19LargeFixtureSupport.GATE_TRACEABILITY_LINKS, links.size());
            traceability.putLinks(snapshotId, links);
            snapshots.activateSnapshot(snapshotId, Optional.empty());

            ChangeProposal change = new ChangeProposal(
                    changeId, projectId, Optional.of("CHG-M19"), "Analyse a wide change", "M19 analysis gate",
                    List.of("requirements"), List.of(), List.of(), provenance);
            ProposedChangeSet proposal = new ProposedChangeSet(change, deltas, List.of(), List.of(), List.of());
            ChangeAnalysisService service =
                    new ChangeAnalysisService(snapshots, content, requirementStore, traceability);

            ChangeAnalysisResult expected = service.analyzeActive(proposal, ANALYSIS_DEPTH).orElseThrow();
            // All six traversals must end at the node budget, or the gate measures less than it names.
            assertEquals(2 * ANALYSIS_DELTAS, expected.warnings().stream()
                    .filter(warning -> warning.code() == ChangeAnalysisWarningCode.TRACEABILITY_TRAVERSAL_TRUNCATED)
                    .count());
            assertEquals(2 * ANALYSIS_DELTAS * (TraceabilityTraversalService.MAX_NODES - 1),
                    expected.dependencyImpacts().size());

            for (int index = 0; index < WARMUP_ITERATIONS; index++) {
                assertEquals(expected, service.analyzeActive(proposal, ANALYSIS_DEPTH).orElseThrow());
            }
            List<Long> samples = new ArrayList<>(MEASURED_ITERATIONS);
            for (int index = 0; index < MEASURED_ITERATIONS; index++) {
                long started = System.nanoTime();
                ChangeAnalysisResult actual = service.analyzeActive(proposal, ANALYSIS_DEPTH).orElseThrow();
                samples.add(System.nanoTime() - started);
                assertEquals(expected, actual);
            }

            long p95 = M19LargeFixtureSupport.percentile95Nanos(samples);
            System.out.println("M19_METRIC change_analysis_p95_ms=" + p95 / 1_000_000L);
            System.out.println("M19_METRIC change_analysis_deltas=" + ANALYSIS_DELTAS);
            System.out.println("M19_METRIC change_analysis_dependency_impacts=" + expected.dependencyImpacts().size());
            assertTrue(p95 <= ANALYSIS_BUDGET_NANOS,
                    () -> "change analysis p95 exceeded declared 2000ms budget: " + p95 / 1_000_000L + " ms");
        }
    }

    private TraceabilityEntityRef requirementRef(RequirementId requirementId) {
        return new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, requirementId.value());
    }

    private List<TraceabilityEntityRef> createTreeNodes() {
        List<TraceabilityEntityRef> nodes = new ArrayList<>(TREE_NODE_COUNT);
        for (int index = 0; index < TREE_NODE_COUNT; index++) {
            nodes.add(new TraceabilityEntityRef(
                    TraceabilityEntityKind.REQUIREMENT,
                    M19LargeFixtureSupport.deterministicIdentity(1924, index)));
        }
        return List.copyOf(nodes);
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
