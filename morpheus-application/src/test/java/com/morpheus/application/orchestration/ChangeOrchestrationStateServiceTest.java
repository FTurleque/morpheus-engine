package com.morpheus.application.orchestration;

import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
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
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
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
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The orchestration view of one change, read from an ACTIVE snapshot through hand-written ports. */
class ChangeOrchestrationStateServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final EvidenceId EVIDENCE = EvidenceId.generate();

    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
    private final ChangeId changeId = ChangeId.generate();
    private final ActiveSnapshot snapshots = new ActiveSnapshot();
    private final Content content = new Content();
    private final Links links = new Links();
    private final ChangeOrchestrationStateService service = new ChangeOrchestrationStateService(
            snapshots, content, new NoRequirements(), links, new NoExternalReferences());

    @Test
    void withoutAnActiveSnapshotThereIsNoStateAndAnUnknownChangeIsRefusedByName() {
        assertTrue(service.active(projectId, changeId, ChangeLifecycleObservation.unavailable()).isEmpty());

        snapshots.active = true;
        KnowledgeStoreException unknown = assertThrows(KnowledgeStoreException.class,
                () -> service.active(projectId, ChangeId.generate(), ChangeLifecycleObservation.unavailable()));
        assertTrue(unknown.getMessage().startsWith("change not found in ACTIVE snapshot"), unknown::getMessage);
    }

    @Test
    void aChangeWithoutConstraintsHasNothingBlockingAndNoTransitionWithoutAnObservedState() {
        snapshots.active = true;

        ChangeOrchestrationState state = service.active(projectId, changeId, ChangeLifecycleObservation.unavailable())
                .orElseThrow();

        assertEquals(changeId.toString(), state.change().id());
        assertEquals("AVAILABLE", state.blockingConstraints().status());
        assertEquals("No constraint is attached to this change", state.blockingConstraints().reason());
        assertEquals(0, state.acceptanceCriteria().observedCount());
        assertTrue(state.transitionEvaluations().isEmpty(), "no observed state, no transition to evaluate");
        assertFalse(state.persisted(), "the orchestration view is computed, never persisted");
        assertFalse(state.observableFacts().isEmpty());
    }

    @Test
    void explicitConstraintsCountTheViolatedBlockingOnesAndUnknownSemanticsAreNeverBlocking() {
        snapshots.active = true;
        content.constraints.add(constraint(ConstraintApplicability.APPLICABLE, ConstraintSatisfaction.VIOLATED,
                ConstraintBlockingPolicy.blockWhenViolated(List.of(ChangeLifecycleState.COMPLETED))));
        content.constraints.add(constraint(ConstraintApplicability.APPLICABLE, ConstraintSatisfaction.SATISFIED,
                ConstraintBlockingPolicy.nonBlocking()));
        content.constraints.add(constraint(ConstraintApplicability.NOT_APPLICABLE, ConstraintSatisfaction.UNKNOWN,
                ConstraintBlockingPolicy.unknown()));

        ChangeOrchestrationState explicit = service.active(projectId, changeId,
                ChangeLifecycleObservation.callerSupplied(ChangeLifecycleState.PROPOSED, Optional.empty())).orElseThrow();
        assertEquals("AVAILABLE", explicit.blockingConstraints().status());
        assertEquals(1, explicit.blockingConstraints().observedCount(), "one violated explicit blocking policy");
        assertEquals(3, explicit.applicableConstraints().size());
        assertFalse(explicit.transitionEvaluations().isEmpty(), "an observed state is evaluated against every other");
        assertTrue(explicit.transitionEvaluations().stream()
                        .noneMatch(evaluation -> evaluation.targetState() == ChangeLifecycleState.PROPOSED),
                "the current state is not a transition target");

        content.constraints.add(Constraint.withUnknownSemantics(ConstraintId.generate(), changeId, "Unknown",
                provenance()));
        ChangeOrchestrationState partial = service.active(projectId, changeId, ChangeLifecycleObservation.unavailable())
                .orElseThrow();
        assertEquals("PARTIALLY_AVAILABLE", partial.blockingConstraints().status());

        content.constraints.clear();
        content.constraints.add(constraint(ConstraintApplicability.UNKNOWN, ConstraintSatisfaction.VIOLATED,
                ConstraintBlockingPolicy.blockWhenViolated(List.of(ChangeLifecycleState.COMPLETED))));
        content.constraints.add(constraint(ConstraintApplicability.APPLICABLE, ConstraintSatisfaction.UNKNOWN,
                ConstraintBlockingPolicy.blockWhenViolated(List.of(ChangeLifecycleState.COMPLETED))));
        content.constraints.add(constraint(ConstraintApplicability.APPLICABLE, ConstraintSatisfaction.VIOLATED,
                ConstraintBlockingPolicy.unknown()));
        ChangeOrchestrationState unknown = service.active(projectId, changeId, ChangeLifecycleObservation.unavailable())
                .orElseThrow();
        assertEquals("UNKNOWN", unknown.blockingConstraints().status());
        assertEquals(0, unknown.blockingConstraints().observedCount(), "UNKNOWN is never counted as blocking");
    }

    @Test
    void anUnresolvedLinkOfTheChangeIsListedAndAResolvedOneIsNot() {
        snapshots.active = true;
        links.links.add(link(TraceabilityResolutionState.UNRESOLVED));
        links.links.add(link(TraceabilityResolutionState.RESOLVED));

        ChangeOrchestrationState state = service.active(projectId, changeId, ChangeLifecycleObservation.unavailable())
                .orElseThrow();

        assertEquals(1, state.unresolvedLinks().size());
        assertEquals("UNRESOLVED", state.unresolvedLinks().getFirst().resolution());
    }

    private Constraint constraint(ConstraintApplicability applicability, ConstraintSatisfaction satisfaction,
            ConstraintBlockingPolicy policy) {
        return new Constraint(ConstraintId.generate(), changeId, "Statement", applicability, ConstraintSeverity.ERROR,
                satisfaction, policy, List.of(EVIDENCE), provenance());
    }

    private TraceabilityLink link(TraceabilityResolutionState resolution) {
        return new TraceabilityLink(TraceabilityLinkId.generate(),
                new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, changeId.value()),
                TraceabilityRelationType.AFFECTS,
                new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, DomainIdentity.generate()),
                TraceabilityLinkOrigin.EXPLICIT, resolution, Optional.empty(), Set.of(EVIDENCE), NOW);
    }

    private static Provenance provenance() {
        return new Provenance(new ProviderId("openspec"), Optional.of("1"), SourceLocator.file("changes/c.md"),
                Optional.of("c"), Optional.of("rev"), EVIDENCE);
    }

    private final class ActiveSnapshot implements SpecificationKnowledgeStore {
        private boolean active;

        private KnowledgeSnapshotMetadata snapshot() {
            return new KnowledgeSnapshotMetadata(snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.ACTIVE,
                    Optional.of("baseline"), NOW);
        }

        @Override public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId id) {
            return active && id.equals(projectId) ? Optional.of(snapshot()) : Optional.empty();
        }

        @Override public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId id) {
            return active && id.equals(snapshotId) ? Optional.of(snapshot()) : Optional.empty();
        }

        @Override public void putProject(ProjectStoreEntry project) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId id) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) { throw new UnsupportedOperationException(); }
        @Override public List<ProjectStoreEntry> listProjects() { throw new UnsupportedOperationException(); }
        @Override public void putSnapshot(KnowledgeSnapshotMetadata snapshot) { throw new UnsupportedOperationException(); }
        @Override public KnowledgeSnapshotMetadata transitionSnapshotState(
                KnowledgeSnapshotId id, KnowledgeSnapshotState from, KnowledgeSnapshotState to) {
            throw new UnsupportedOperationException();
        }
        @Override public KnowledgeSnapshotMetadata activateSnapshot(
                KnowledgeSnapshotId id, Optional<KnowledgeSnapshotId> expected) {
            throw new UnsupportedOperationException();
        }
    }

    private final class Content implements SnapshotBusinessContentStore {
        private final List<Constraint> constraints = new ArrayList<>();

        @Override public Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId id) {
            ChangeProposal change = new ChangeProposal(changeId, projectId, Optional.of("change"), "Change", "Intent",
                    List.of(), List.of(), List.of(), provenance());
            return Optional.of(new SnapshotBusinessContent(id, SpecificationVersionId.generate(), List.of(), List.of(),
                    List.of(change), constraints, List.of(), List.of(), List.of(),
                    List.of(new Evidence(EVIDENCE, SourceLocator.file("changes/c.md"), Optional.empty(),
                            Optional.empty()))));
        }

        @Override public void putSnapshotContent(SnapshotBusinessContent snapshotContent) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class NoRequirements implements VersionedRequirementStore {
        @Override public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId id) { return List.of(); }
        @Override public Optional<RequirementVersionRecord> currentRequirement(KnowledgeSnapshotId id, DomainIdentity identity) { return Optional.empty(); }
        @Override public void putSpecificationVersion(SpecificationVersion version) { throw new UnsupportedOperationException(); }
        @Override public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) { throw new UnsupportedOperationException(); }
        @Override public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) { throw new UnsupportedOperationException(); }
        @Override public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId id) { throw new UnsupportedOperationException(); }
        @Override public void putRequirementVersion(RequirementVersionRecord record) { throw new UnsupportedOperationException(); }
        @Override public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) { throw new UnsupportedOperationException(); }
    }

    private static final class Links implements TraceabilityStore {
        private final List<TraceabilityLink> links = new ArrayList<>();

        @Override public List<TraceabilityLink> outgoing(KnowledgeSnapshotId id, TraceabilityEntityRef source,
                Set<TraceabilityRelationType> relationTypes) {
            return links.stream().filter(link -> link.source().equals(source))
                    .filter(link -> relationTypes.isEmpty() || relationTypes.contains(link.relationType())).toList();
        }

        @Override public List<TraceabilityLink> incoming(KnowledgeSnapshotId id, TraceabilityEntityRef target,
                Set<TraceabilityRelationType> relationTypes) {
            return links.stream().filter(link -> link.target().equals(target))
                    .filter(link -> relationTypes.isEmpty() || relationTypes.contains(link.relationType())).toList();
        }

        @Override public void putLink(KnowledgeSnapshotId id, TraceabilityLink link) { throw new UnsupportedOperationException(); }
        @Override public void putLinks(KnowledgeSnapshotId id, List<TraceabilityLink> batch) { throw new UnsupportedOperationException(); }
        @Override public Optional<TraceabilityLink> findLink(KnowledgeSnapshotId id, TraceabilityLinkId linkId) { throw new UnsupportedOperationException(); }
    }

    private static final class NoExternalReferences implements ExternalReferenceStore {
        @Override public List<ExternalReference> findByOwner(KnowledgeSnapshotId id, DomainIdentity ownerId) { return List.of(); }
        @Override public void putReference(KnowledgeSnapshotId id, ExternalReference reference) { throw new UnsupportedOperationException(); }
        @Override public Optional<ExternalReference> findReference(KnowledgeSnapshotId id, ExternalReferenceId referenceId) { throw new UnsupportedOperationException(); }
    }
}
