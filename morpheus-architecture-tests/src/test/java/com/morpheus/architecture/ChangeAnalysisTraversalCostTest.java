package com.morpheus.architecture;

import com.morpheus.application.analysis.ChangeAnalysisResult;
import com.morpheus.application.analysis.ChangeAnalysisService;
import com.morpheus.application.analysis.ProposedChangeSet;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.requirement.RequirementDeltaId;
import com.morpheus.domain.requirement.RequirementDeltaKind;
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
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explaining the dependencies of a change costs what finding them costs: the store reads of an analysis grow with the
 * requirements reached, not with their square (AUD-PRF-02). The proof is a count over the traceability port, so it
 * needs neither a clock nor SQLite.
 */
class ChangeAnalysisTraversalCostTest {
    private static final Instant T0 = Instant.parse("2026-10-17T08:00:00Z");
    private static final int SHORT_CHAIN = 12;
    private static final int LONG_CHAIN = 2 * SHORT_CHAIN;
    private static final int DEPTH_THAT_REACHES_EVERY_NODE = LONG_CHAIN + 1;

    @Test
    void doublingTheDependencyChainAtMostDoublesTheStoreReadsOfTheAnalysis() {
        CountedAnalysis shortChain = analyseChainOf(SHORT_CHAIN);
        CountedAnalysis longChain = analyseChainOf(LONG_CHAIN);

        assertEquals(SHORT_CHAIN, shortChain.dependencies(), "every requirement of the short chain is reported");
        assertEquals(LONG_CHAIN, longChain.dependencies(), "every requirement of the long chain is reported");
        assertTrue(longChain.storeReads() <= 2 * shortChain.storeReads(),
                "store reads must grow with the nodes found, not with their square: " + shortChain.storeReads()
                        + " reads for " + SHORT_CHAIN + " requirements, " + longChain.storeReads()
                        + " reads for " + LONG_CHAIN);
    }

    private CountedAnalysis analyseChainOf(int length) {
        var snapshots = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(snapshots, snapshots);
        var traceability = new MemoryTraceabilityStore(snapshots);
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        SpecificationId specificationId = SpecificationId.generate();
        ChangeId changeId = ChangeId.generate();
        EvidenceId evidenceId = EvidenceId.generate();
        Provenance provenance = new Provenance(
                new ProviderId("prf-02-test"), Optional.of("1"), SourceLocator.file("specs/core.md"),
                Optional.of("PRF-02"), Optional.of("revision-1"), evidenceId);

        snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-prf-02")));
        snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.READY, Optional.of("revision-1"), T0));
        snapshots.putSpecificationVersion(new SpecificationVersion(
                versionId, projectId, Optional.of(1L), Optional.of("provider-v1"), Optional.of("revision-1"), T0,
                Optional.empty()));
        snapshots.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
        content.putSnapshotContent(new SnapshotBusinessContent(
                snapshotId, versionId, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of()));

        RequirementId rootId = RequirementId.generate();
        Requirement root = new Requirement(
                rootId, specificationId, Optional.of("REQ-ROOT"), "Root", "Root statement", provenance);
        snapshots.putRequirementVersion(new RequirementVersionRecord(
                snapshotId,
                new EntityVersion<>(EntityVersionId.generate(), rootId.value(), versionId, TemporalState.CURRENT, root)));

        TraceabilityEntityRef previous = ref(rootId);
        for (int index = 0; index < length; index++) {
            TraceabilityEntityRef next = ref(RequirementId.generate());
            traceability.putLink(snapshotId, dependsOn(previous, next, evidenceId, index));
            previous = next;
        }
        snapshots.activateSnapshot(snapshotId, Optional.empty());

        RequirementDelta modified = new RequirementDelta(
                RequirementDeltaId.generate(), changeId, RequirementDeltaKind.MODIFIED, "core", rootId,
                Optional.of("REQ-ROOT"), "Root, proposed", Optional.of("Proposed statement"), List.of(), provenance);
        ChangeProposal change = new ChangeProposal(
                changeId, projectId, Optional.of("CHG-PRF-02"), "Analyse a long chain", "Count the store reads",
                List.of("requirements"), List.of(), List.of(), provenance);
        ProposedChangeSet proposal = new ProposedChangeSet(
                change, List.of(modified), List.of(), List.of(), List.of());

        var counting = new CountingTraceabilityStore(traceability);
        ChangeAnalysisResult result = new ChangeAnalysisService(snapshots, content, snapshots, counting)
                .analyzeActive(proposal, DEPTH_THAT_REACHES_EVERY_NODE)
                .orElseThrow();
        return new CountedAnalysis(result.summary().dependencies(), counting.reads());
    }

    private TraceabilityEntityRef ref(RequirementId requirementId) {
        return new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, requirementId.value());
    }

    private TraceabilityLink dependsOn(
            TraceabilityEntityRef source, TraceabilityEntityRef target, EvidenceId evidenceId, int index) {
        return new TraceabilityLink(
                TraceabilityLinkId.generate(), source, TraceabilityRelationType.DEPENDS_ON, target,
                TraceabilityLinkOrigin.EXPLICIT, TraceabilityResolutionState.RESOLVED, Optional.empty(),
                Set.of(evidenceId), T0.plusSeconds(index));
    }

    private record CountedAnalysis(int dependencies, int storeReads) {
    }

    private static final class CountingTraceabilityStore implements TraceabilityStore {
        private final TraceabilityStore delegate;
        private final AtomicInteger reads = new AtomicInteger();

        private CountingTraceabilityStore(TraceabilityStore delegate) {
            this.delegate = delegate;
        }

        int reads() {
            return reads.get();
        }

        @Override
        public void putLink(KnowledgeSnapshotId snapshotId, TraceabilityLink link) {
            delegate.putLink(snapshotId, link);
        }

        @Override
        public void putLinks(KnowledgeSnapshotId snapshotId, List<TraceabilityLink> links) {
            delegate.putLinks(snapshotId, links);
        }

        @Override
        public Optional<TraceabilityLink> findLink(KnowledgeSnapshotId snapshotId, TraceabilityLinkId linkId) {
            reads.incrementAndGet();
            return delegate.findLink(snapshotId, linkId);
        }

        @Override
        public List<TraceabilityLink> outgoing(
                KnowledgeSnapshotId snapshotId, TraceabilityEntityRef source, Set<TraceabilityRelationType> types) {
            reads.incrementAndGet();
            return delegate.outgoing(snapshotId, source, types);
        }

        @Override
        public List<TraceabilityLink> incoming(
                KnowledgeSnapshotId snapshotId, TraceabilityEntityRef target, Set<TraceabilityRelationType> types) {
            reads.incrementAndGet();
            return delegate.incoming(snapshotId, target, types);
        }
    }
}
