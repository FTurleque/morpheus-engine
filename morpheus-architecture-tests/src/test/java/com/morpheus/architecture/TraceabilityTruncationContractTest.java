package com.morpheus.architecture;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.query.compact.CompactQueryViewService;
import com.morpheus.application.query.compact.CompactTraceRequirementView;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.traceability.TraceRequirementResult;
import com.morpheus.application.traceability.TraceRequirementService;
import com.morpheus.application.traceability.TraceabilitySubgraph;
import com.morpheus.application.traceability.TraceabilityTraversalBudgetException;
import com.morpheus.application.traceability.TraceabilityTraversalDirection;
import com.morpheus.application.traceability.TraceabilityTraversalService;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.SpecificationId;
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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A traceability traversal is bounded in nodes and links as well as depth, and a traversal that stops before
 * observing everything within its depth says why, in the vocabulary of the portfolio traversal. The reason travels
 * with the subgraph up to the compact view that MCP, HTTP and the CLI all serialize.
 */
class TraceabilityTruncationContractTest {
    private static final Instant T0 = Instant.parse("2026-09-23T08:00:00Z");
    private static final int MAX_NODES = TraceabilityTraversalService.MAX_NODES;
    private static final int MAX_LINKS = TraceabilityTraversalService.MAX_LINKS;

    private final MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
    private final MemoryTraceabilityStore traceability = new MemoryTraceabilityStore(core);
    private final TraceabilityTraversalService service = new TraceabilityTraversalService(traceability);

    @Test
    void aTraversalPastItsNodeBudgetStopsAndSaysWhy() {
        KnowledgeSnapshotId snapshotId = readySnapshot(ProjectSpecificationId.generate());
        TraceabilityEntityRef start = ref(TraceabilityEntityKind.REQUIREMENT);
        star(snapshotId, start, MAX_NODES + 4);

        TraceabilitySubgraph graph = service.traverse(
                snapshotId, start, 1, TraceabilityTraversalDirection.OUTGOING, Set.of());

        assertEquals(Optional.of("NODE_BUDGET_REACHED:" + MAX_NODES), graph.truncationReason());
        assertTrue(graph.truncated());
        assertEquals(MAX_NODES, graph.nodes().size());
        Set<TraceabilityEntityRef> nodes = new HashSet<>(graph.nodes());
        assertTrue(graph.links().stream().allMatch(link -> nodes.contains(link.target())),
                "a truncated subgraph must not carry a link to a node it does not contain");
    }

    @Test
    void aTraversalPastItsLinkBudgetStopsAndSaysWhy() {
        KnowledgeSnapshotId snapshotId = readySnapshot(ProjectSpecificationId.generate());
        List<TraceabilityEntityRef> clique = new ArrayList<>();
        for (int index = 0; index < 80; index++) {
            clique.add(ref(TraceabilityEntityKind.REQUIREMENT));
        }
        int offset = 0;
        for (TraceabilityEntityRef source : clique) {
            for (TraceabilityEntityRef target : clique) {
                if (!source.equals(target)) {
                    traceability.putLink(snapshotId, link(source, target, offset++));
                }
            }
        }

        TraceabilitySubgraph graph = service.traverse(
                snapshotId, clique.getFirst(), 2, TraceabilityTraversalDirection.OUTGOING, Set.of());

        assertEquals(Optional.of("LINK_BUDGET_REACHED:" + MAX_LINKS), graph.truncationReason());
        assertEquals(MAX_LINKS, graph.links().size());
    }

    @Test
    void aTraversalWithinItsBudgetsReportsNoTruncation() {
        KnowledgeSnapshotId snapshotId = readySnapshot(ProjectSpecificationId.generate());
        TraceabilityEntityRef a = ref(TraceabilityEntityKind.REQUIREMENT);
        TraceabilityEntityRef b = ref(TraceabilityEntityKind.CHANGE);
        TraceabilityEntityRef c = ref(TraceabilityEntityKind.DESIGN_DECISION);
        traceability.putLink(snapshotId, link(a, b, 1));
        traceability.putLink(snapshotId, link(b, c, 2));

        TraceabilitySubgraph outgoing = service.traverse(
                snapshotId, a, 2, TraceabilityTraversalDirection.OUTGOING, Set.of());
        TraceabilitySubgraph bidirectional = service.traverse(
                snapshotId, a, 2, TraceabilityTraversalDirection.BIDIRECTIONAL, Set.of());

        assertEquals(Optional.empty(), outgoing.truncationReason());
        assertEquals(3, outgoing.nodes().size());
        assertEquals(Optional.empty(), bidirectional.truncationReason(),
                "the link back towards the start was observed; a graph ending at the depth is complete");
    }

    @Test
    void aTraversalThatLeavesALinkBeyondItsDepthSaysSo() {
        KnowledgeSnapshotId snapshotId = readySnapshot(ProjectSpecificationId.generate());
        TraceabilityEntityRef a = ref(TraceabilityEntityKind.REQUIREMENT);
        TraceabilityEntityRef b = ref(TraceabilityEntityKind.CHANGE);
        TraceabilityEntityRef c = ref(TraceabilityEntityKind.DESIGN_DECISION);
        traceability.putLink(snapshotId, link(a, b, 1));
        traceability.putLink(snapshotId, link(b, c, 2));

        TraceabilitySubgraph graph = service.traverse(
                snapshotId, a, 1, TraceabilityTraversalDirection.BIDIRECTIONAL, Set.of());

        assertEquals(Optional.of("DEPTH_BUDGET_REACHED:1"), graph.truncationReason());
        assertEquals(2, graph.nodes().size());
    }

    @Test
    void aPathSearchPastItsNodeBudgetFailsInsteadOfAnsweringNoPath() {
        KnowledgeSnapshotId snapshotId = readySnapshot(ProjectSpecificationId.generate());
        TraceabilityEntityRef start = ref(TraceabilityEntityKind.REQUIREMENT);
        star(snapshotId, start, MAX_NODES + 4);
        TraceabilityEntityRef unreachable = ref(TraceabilityEntityKind.CHANGE);

        TraceabilityTraversalBudgetException failure = assertThrows(TraceabilityTraversalBudgetException.class,
                () -> service.findPath(
                        snapshotId, start, unreachable, 1, TraceabilityTraversalDirection.OUTGOING, Set.of()));

        assertEquals("NODE_BUDGET_REACHED:" + MAX_NODES, failure.getMessage());
    }

    @Test
    void theTruncationReasonReachesTheTraceRequirementResult() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = readySnapshot(projectId);
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        core.putSpecificationVersion(new SpecificationVersion(
                versionId, projectId, Optional.of(1L), Optional.of("trace"), Optional.of("rev-1"), T0, Optional.empty()));
        core.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
        MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);
        content.putSnapshotContent(new SnapshotBusinessContent(
                snapshotId, versionId, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of()));
        RequirementId requirementId = RequirementId.generate();
        core.putRequirementVersion(requirement(snapshotId, versionId, requirementId));
        star(snapshotId, new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, requirementId.value()),
                MAX_NODES + 4);
        core.activateSnapshot(snapshotId, Optional.empty());

        TraceRequirementResult result = new TraceRequirementService(
                core, core, traceability, new MemoryExternalReferenceStore(core))
                .traceActive(projectId, requirementId, 1, Set.of())
                .orElseThrow();
        CompactTraceRequirementView view = new CompactQueryViewService(content).traceRequirement(result);
        String json = new CanonicalJsonSerializer().toJson(view);

        assertEquals(Optional.of("NODE_BUDGET_REACHED:" + MAX_NODES), result.subgraph().truncationReason());
        assertEquals(Optional.of("NODE_BUDGET_REACHED:" + MAX_NODES), view.truncationReason());
        assertTrue(view.truncated());
        assertTrue(json.contains("\"truncationReason\":\"NODE_BUDGET_REACHED:" + MAX_NODES + "\""), json.substring(0, 200));
        assertTrue(json.contains("\"truncated\":true"));
        assertFalse(json.contains("\"truncated\":false"));
    }

    private void star(KnowledgeSnapshotId snapshotId, TraceabilityEntityRef start, int leaves) {
        for (int index = 0; index < leaves; index++) {
            traceability.putLink(snapshotId, link(start, ref(TraceabilityEntityKind.IMPLEMENTATION_TASK), index));
        }
    }

    private KnowledgeSnapshotId readySnapshot(ProjectSpecificationId projectId) {
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        core.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-" + projectId)));
        core.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.READY, Optional.of("rev-1"), T0));
        return snapshotId;
    }

    private static TraceabilityEntityRef ref(TraceabilityEntityKind kind) {
        return new TraceabilityEntityRef(kind, DomainIdentity.generate());
    }

    private static TraceabilityLink link(TraceabilityEntityRef source, TraceabilityEntityRef target, long offset) {
        return new TraceabilityLink(
                TraceabilityLinkId.generate(),
                source,
                TraceabilityRelationType.RELATED_TO,
                target,
                TraceabilityLinkOrigin.DERIVED,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of(EvidenceId.generate()),
                T0.plusSeconds(offset));
    }

    private static RequirementVersionRecord requirement(
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            RequirementId requirementId) {
        Requirement requirement = new Requirement(
                requirementId,
                SpecificationId.generate(),
                Optional.of("WIDE"),
                "Widely linked requirement",
                "The requirement is linked to more entities than one traversal may observe",
                new Provenance(
                        new ProviderId("test-provider"),
                        Optional.of("1"),
                        SourceLocator.file("specs/wide.md"),
                        Optional.of("REQ-WIDE"),
                        Optional.of("rev-1"),
                        EvidenceId.generate()));
        return new RequirementVersionRecord(
                snapshotId,
                new EntityVersion<>(
                        EntityVersionId.generate(),
                        requirementId.value(),
                        versionId,
                        TemporalState.CURRENT,
                        requirement));
    }
}
