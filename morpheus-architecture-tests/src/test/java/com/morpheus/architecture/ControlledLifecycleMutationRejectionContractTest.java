package com.morpheus.architecture;

import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationCommand;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPolicy;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationResultState;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.lifecycle.mutation.ControlledChangeLifecycleMutationService;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleRevision;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
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
import com.morpheus.store.memory.MemoryChangeLifecycleMutationStore;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlledLifecycleMutationRejectionContractTest {
    private static final Instant T0 = Instant.parse("2026-07-26T16:30:00Z");
    private static final ProviderId PROVIDER = new ProviderId("m17-write-fixture");

    @Test
    void structurallyBlockedTransitionIsRejectedWithoutStateOrAudit() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        ChangeId changeId = ChangeId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        Evidence evidence = new Evidence(
                EvidenceId.generate(), SourceLocator.file("m17/rejected.md"), Optional.empty(), Optional.empty());
        Provenance provenance = new Provenance(
                PROVIDER, Optional.of("1"), SourceLocator.file("m17/rejected.md"),
                Optional.of("change:rejected"), Optional.empty(), evidence.id());
        ChangeProposal change = new ChangeProposal(
                changeId, projectId, Optional.of("rejected"), "Rejected mutation",
                "A DRAFT change must not jump directly to COMPLETED",
                List.of(), List.of(), List.of(), provenance);

        MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);
        MemoryTraceabilityStore traceability = new MemoryTraceabilityStore(core);
        core.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-m17-rejected")));
        core.putSpecificationVersion(new SpecificationVersion(
                versionId, projectId, Optional.of(1L), Optional.of("fixture"), Optional.empty(), T0, Optional.empty()));
        core.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.READY, Optional.empty(), T0));
        core.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
        content.putSnapshotContent(new SnapshotBusinessContent(
                snapshotId, versionId, List.of(), List.of(), List.of(change), List.of(),
                List.of(), List.of(), List.of(evidence)));
        core.activateSnapshot(snapshotId, Optional.empty());

        MemoryChangeLifecycleMutationStore mutations = new MemoryChangeLifecycleMutationStore();
        ControlledChangeLifecycleMutationService service = new ControlledChangeLifecycleMutationService(
                new ChangeTransitionEvaluationService(core, content, core, traceability),
                mutations,
                ignored -> ChangeWriteCapabilityObservation.allowed(PROVIDER, "explicit WRITE_CHANGE fixture"));

        var result = service.apply(
                new ChangeLifecycleMutationCommand(
                        ChangeLifecycleMutationId.generate(),
                        new ChangeLifecycleIdempotencyKey("m17-rejected"),
                        projectId,
                        changeId,
                        ChangeLifecycleRevision.initial(),
                        ChangeLifecycleState.COMPLETED,
                        Optional.empty(),
                        true,
                        "m17-test",
                        T0),
                ChangeLifecycleMutationPolicy.strict());

        assertEquals(ChangeLifecycleMutationResultState.REJECTED, result.state());
        assertTrue(result.reason().contains("BLOCKED"), result.reason());
        assertTrue(mutations.findState(projectId, changeId).isEmpty());
        assertTrue(mutations.listAudit(projectId, changeId).isEmpty());
    }
}
