package com.morpheus.architecture;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.traceability.TraceabilityPath;
import com.morpheus.application.traceability.TraceabilitySubgraph;
import com.morpheus.application.traceability.TraceabilityTraversalDirection;
import com.morpheus.application.traceability.TraceabilityTraversalService;
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
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceabilityTraversalContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T11:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void directAndInverseViewsPreserveTheCanonicalPersistedEdge() {
        withBothBackends("direct-inverse.db", (snapshots, traceability) -> {
            KnowledgeSnapshotId snapshotId = prepareSnapshot(snapshots);
            TraceabilityEntityRef source = ref(TraceabilityEntityKind.CHANGE);
            TraceabilityEntityRef target = ref(TraceabilityEntityKind.REQUIREMENT);
            TraceabilityLink link = link(source, TraceabilityRelationType.AFFECTS, target, 1);
            traceability.putLink(snapshotId, link);

            TraceabilityTraversalService service = new TraceabilityTraversalService(traceability);
            assertEquals(List.of(link), service.direct(
                    snapshotId, source, TraceabilityTraversalDirection.OUTGOING, Set.of()));
            assertEquals(List.of(link), service.direct(
                    snapshotId, target, TraceabilityTraversalDirection.INCOMING, Set.of()));
            assertEquals(List.of(link), service.direct(
                    snapshotId, target, TraceabilityTraversalDirection.BIDIRECTIONAL, Set.of()));

            TraceabilityPath inversePath = service.findPath(
                    snapshotId,
                    target,
                    source,
                    1,
                    TraceabilityTraversalDirection.INCOMING,
                    Set.of()).orElseThrow();
            assertEquals(1, inversePath.steps().size());
            assertEquals(link, inversePath.steps().getFirst().link());
            assertTrue(inversePath.steps().getFirst().reversed());
            assertEquals(link.source(), inversePath.steps().getFirst().into());
        });
    }

    @Test
    void boundedTraversalIsCycleSafeAndNeverCreatesATransitiveEdge() {
        withBothBackends("cycle.db", (snapshots, traceability) -> {
            KnowledgeSnapshotId snapshotId = prepareSnapshot(snapshots);
            TraceabilityEntityRef a = ref(TraceabilityEntityKind.REQUIREMENT);
            TraceabilityEntityRef b = ref(TraceabilityEntityKind.CHANGE);
            TraceabilityEntityRef c = ref(TraceabilityEntityKind.DESIGN_DECISION);
            TraceabilityEntityRef d = ref(TraceabilityEntityKind.CONSTRAINT);

            TraceabilityLink ab = link(a, TraceabilityRelationType.RELATED_TO, b, 1);
            TraceabilityLink bc = link(b, TraceabilityRelationType.RELATED_TO, c, 2);
            TraceabilityLink ca = link(c, TraceabilityRelationType.RELATED_TO, a, 3);
            TraceabilityLink cd = link(c, TraceabilityRelationType.RELATED_TO, d, 4);
            putAll(traceability, snapshotId, ab, bc, ca, cd);

            TraceabilityTraversalService service = new TraceabilityTraversalService(traceability);
            TraceabilitySubgraph graph = service.traverse(
                    snapshotId, a, 3, TraceabilityTraversalDirection.OUTGOING, Set.of());

            assertEquals(sortedNodes(a, b, c, d), graph.nodes());
            assertEquals(sortedLinks(ab, bc, ca, cd), graph.links());
            assertFalse(graph.links().stream().anyMatch(link -> link.source().equals(a) && link.target().equals(c)));

            TraceabilityPath path = service.findPath(
                    snapshotId, a, c, 2, TraceabilityTraversalDirection.OUTGOING, Set.of()).orElseThrow();
            assertEquals(List.of(ab, bc), path.steps().stream().map(step -> step.link()).toList());
        });
    }

    @Test
    void findPathChoosesADeterministicShortestPath() {
        withBothBackends("deterministic-path.db", (snapshots, traceability) -> {
            KnowledgeSnapshotId snapshotId = prepareSnapshot(snapshots);
            TraceabilityEntityRef start = ref(TraceabilityEntityKind.REQUIREMENT);
            TraceabilityEntityRef left = ref(TraceabilityEntityKind.CHANGE);
            TraceabilityEntityRef right = ref(TraceabilityEntityKind.CHANGE);
            TraceabilityEntityRef target = ref(TraceabilityEntityKind.DESIGN_DECISION);

            TraceabilityLink startLeft = link(start, TraceabilityRelationType.RELATED_TO, left, 1);
            TraceabilityLink startRight = link(start, TraceabilityRelationType.RELATED_TO, right, 2);
            TraceabilityLink leftTarget = link(left, TraceabilityRelationType.RELATED_TO, target, 3);
            TraceabilityLink rightTarget = link(right, TraceabilityRelationType.RELATED_TO, target, 4);
            putAll(traceability, snapshotId, startRight, rightTarget, startLeft, leftTarget);

            TraceabilityTraversalService service = new TraceabilityTraversalService(traceability);
            TraceabilityPath path = service.findPath(
                    snapshotId, start, target, 2, TraceabilityTraversalDirection.OUTGOING, Set.of()).orElseThrow();

            TraceabilityEntityRef expectedIntermediate = left.compareTo(right) <= 0 ? left : right;
            TraceabilityLink expectedFirst = expectedIntermediate.equals(left) ? startLeft : startRight;
            TraceabilityLink expectedSecond = expectedIntermediate.equals(left) ? leftTarget : rightTarget;
            assertEquals(List.of(expectedFirst, expectedSecond), path.steps().stream().map(step -> step.link()).toList());
            assertTrue(service.findPath(
                    snapshotId, start, target, 1, TraceabilityTraversalDirection.OUTGOING, Set.of()).isEmpty());
        });
    }

    @Test
    void relationFiltersApplyToTraversalAndPath() {
        withBothBackends("filters.db", (snapshots, traceability) -> {
            KnowledgeSnapshotId snapshotId = prepareSnapshot(snapshots);
            TraceabilityEntityRef start = ref(TraceabilityEntityKind.CHANGE);
            TraceabilityEntityRef refined = ref(TraceabilityEntityKind.REQUIREMENT);
            TraceabilityEntityRef affected = ref(TraceabilityEntityKind.REQUIREMENT);
            TraceabilityLink refines = link(start, TraceabilityRelationType.REFINES, refined, 1);
            TraceabilityLink affects = link(start, TraceabilityRelationType.AFFECTS, affected, 2);
            putAll(traceability, snapshotId, refines, affects);

            TraceabilityTraversalService service = new TraceabilityTraversalService(traceability);
            TraceabilitySubgraph filtered = service.traverse(
                    snapshotId,
                    start,
                    1,
                    TraceabilityTraversalDirection.OUTGOING,
                    Set.of(TraceabilityRelationType.REFINES));
            assertEquals(sortedNodes(start, refined), filtered.nodes());
            assertEquals(List.of(refines), filtered.links());
            assertTrue(service.findPath(
                    snapshotId,
                    start,
                    affected,
                    1,
                    TraceabilityTraversalDirection.OUTGOING,
                    Set.of(TraceabilityRelationType.REFINES)).isEmpty());
            assertTrue(service.findPath(
                    snapshotId,
                    start,
                    affected,
                    1,
                    TraceabilityTraversalDirection.OUTGOING,
                    Set.of()).isPresent());
        });
    }

    @Test
    void maxDepthMustBeStrictlyPositive() {
        var snapshots = new MemorySpecificationKnowledgeStore();
        var service = new TraceabilityTraversalService(new MemoryTraceabilityStore(snapshots));
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        TraceabilityEntityRef start = ref(TraceabilityEntityKind.REQUIREMENT);

        assertThrows(IllegalArgumentException.class, () -> service.traverse(
                snapshotId, start, 0, TraceabilityTraversalDirection.OUTGOING, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> service.findPath(
                snapshotId, start, start, -1, TraceabilityTraversalDirection.BIDIRECTIONAL, Set.of()));
    }

    @Test
    void traversalNeverLeaksLinksAcrossSnapshots() {
        withBothBackends("snapshot-isolation.db", (snapshots, traceability) -> {
            ProjectSpecificationId projectId = ProjectSpecificationId.generate();
            KnowledgeSnapshotId first = KnowledgeSnapshotId.generate();
            KnowledgeSnapshotId second = KnowledgeSnapshotId.generate();
            createProjectAndSnapshots(snapshots, projectId, first, second);

            TraceabilityEntityRef start = ref(TraceabilityEntityKind.REQUIREMENT);
            TraceabilityEntityRef firstTarget = ref(TraceabilityEntityKind.SCENARIO);
            TraceabilityEntityRef secondTarget = ref(TraceabilityEntityKind.CHANGE);
            TraceabilityLink firstLink = link(start, TraceabilityRelationType.RELATED_TO, firstTarget, 1);
            TraceabilityLink secondLink = link(start, TraceabilityRelationType.RELATED_TO, secondTarget, 2);
            traceability.putLink(first, firstLink);
            traceability.putLink(second, secondLink);

            TraceabilityTraversalService service = new TraceabilityTraversalService(traceability);
            TraceabilitySubgraph firstGraph = service.traverse(
                    first, start, 1, TraceabilityTraversalDirection.OUTGOING, Set.of());
            TraceabilitySubgraph secondGraph = service.traverse(
                    second, start, 1, TraceabilityTraversalDirection.OUTGOING, Set.of());

            assertEquals(List.of(firstLink), firstGraph.links());
            assertEquals(List.of(secondLink), secondGraph.links());
            assertFalse(firstGraph.nodes().contains(secondTarget));
            assertFalse(secondGraph.nodes().contains(firstTarget));
        });
    }

    @Test
    void memoryAndSqliteExposeIdenticalTraversalAndPathForTheSameGraph() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        TraceabilityEntityRef requirement = ref(TraceabilityEntityKind.REQUIREMENT);
        TraceabilityEntityRef change = ref(TraceabilityEntityKind.CHANGE);
        TraceabilityEntityRef decision = ref(TraceabilityEntityKind.DESIGN_DECISION);
        TraceabilityEntityRef scenario = ref(TraceabilityEntityKind.SCENARIO);
        TraceabilityLink affects = link(change, TraceabilityRelationType.AFFECTS, requirement, 1);
        TraceabilityLink decidedBy = link(change, TraceabilityRelationType.DECIDED_BY, decision, 2);
        TraceabilityLink refines = link(scenario, TraceabilityRelationType.REFINES, requirement, 3);

        var memorySnapshots = new MemorySpecificationKnowledgeStore();
        var memoryTraceability = new MemoryTraceabilityStore(memorySnapshots);
        createProjectAndSnapshot(memorySnapshots, projectId, snapshotId);
        putAll(memoryTraceability, snapshotId, affects, decidedBy, refines);
        TraceabilityTraversalService memoryService = new TraceabilityTraversalService(memoryTraceability);

        Path database = tempDir.resolve("cross-backend.db");
        try (var sqliteSnapshots = new SqliteSpecificationKnowledgeStore(database);
             var sqliteTraceability = new SqliteTraceabilityStore(database)) {
            createProjectAndSnapshot(sqliteSnapshots, projectId, snapshotId);
            putAll(sqliteTraceability, snapshotId, affects, decidedBy, refines);
            TraceabilityTraversalService sqliteService = new TraceabilityTraversalService(sqliteTraceability);

            TraceabilitySubgraph memoryGraph = memoryService.traverse(
                    snapshotId, requirement, 2, TraceabilityTraversalDirection.BIDIRECTIONAL, Set.of());
            TraceabilitySubgraph sqliteGraph = sqliteService.traverse(
                    snapshotId, requirement, 2, TraceabilityTraversalDirection.BIDIRECTIONAL, Set.of());
            assertEquals(memoryGraph, sqliteGraph);

            Optional<TraceabilityPath> memoryPath = memoryService.findPath(
                    snapshotId, requirement, decision, 2, TraceabilityTraversalDirection.BIDIRECTIONAL, Set.of());
            Optional<TraceabilityPath> sqlitePath = sqliteService.findPath(
                    snapshotId, requirement, decision, 2, TraceabilityTraversalDirection.BIDIRECTIONAL, Set.of());
            assertEquals(memoryPath, sqlitePath);
            assertEquals(List.of(affects, decidedBy), memoryPath.orElseThrow().steps().stream()
                    .map(step -> step.link()).toList());
            assertTrue(memoryPath.orElseThrow().steps().getFirst().reversed());
            assertFalse(memoryPath.orElseThrow().steps().getLast().reversed());
        }
    }

    private void withBothBackends(
            String databaseName,
            BiConsumer<SpecificationKnowledgeStore, TraceabilityStore> contract) {
        var memorySnapshots = new MemorySpecificationKnowledgeStore();
        contract.accept(memorySnapshots, new MemoryTraceabilityStore(memorySnapshots));

        Path database = tempDir.resolve(databaseName);
        try (var sqliteSnapshots = new SqliteSpecificationKnowledgeStore(database);
             var sqliteTraceability = new SqliteTraceabilityStore(database)) {
            contract.accept(sqliteSnapshots, sqliteTraceability);
        }
    }

    private KnowledgeSnapshotId prepareSnapshot(SpecificationKnowledgeStore snapshots) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        createProjectAndSnapshot(snapshots, projectId, snapshotId);
        return snapshotId;
    }

    private void createProjectAndSnapshot(
            SpecificationKnowledgeStore snapshots,
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId) {
        snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-" + projectId)));
        snapshots.putSnapshot(snapshot(snapshotId, projectId, "revision-1"));
    }

    private void createProjectAndSnapshots(
            SpecificationKnowledgeStore snapshots,
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId first,
            KnowledgeSnapshotId second) {
        snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-" + projectId)));
        snapshots.putSnapshot(snapshot(first, projectId, "revision-1"));
        snapshots.putSnapshot(snapshot(second, projectId, "revision-2"));
    }

    private KnowledgeSnapshotMetadata snapshot(
            KnowledgeSnapshotId snapshotId,
            ProjectSpecificationId projectId,
            String revision) {
        return new KnowledgeSnapshotMetadata(
                snapshotId,
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of(revision),
                T0);
    }

    private TraceabilityEntityRef ref(TraceabilityEntityKind kind) {
        return new TraceabilityEntityRef(kind, DomainIdentity.generate());
    }

    private TraceabilityLink link(
            TraceabilityEntityRef source,
            TraceabilityRelationType relationType,
            TraceabilityEntityRef target,
            long offsetSeconds) {
        return new TraceabilityLink(
                TraceabilityLinkId.generate(),
                source,
                relationType,
                target,
                TraceabilityLinkOrigin.DERIVED,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of(EvidenceId.generate()),
                T0.plusSeconds(offsetSeconds));
    }

    private void putAll(
            TraceabilityStore store,
            KnowledgeSnapshotId snapshotId,
            TraceabilityLink... links) {
        for (TraceabilityLink link : links) {
            store.putLink(snapshotId, link);
        }
    }

    private List<TraceabilityEntityRef> sortedNodes(TraceabilityEntityRef... nodes) {
        return List.of(nodes).stream().sorted().toList();
    }

    private List<TraceabilityLink> sortedLinks(TraceabilityLink... links) {
        return List.of(links).stream().sorted(Comparator.comparing(TraceabilityLink::id)).toList();
    }
}
