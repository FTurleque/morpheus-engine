package com.morpheus.application.lifecycle.mutation;

import com.morpheus.application.lifecycle.ChangeLifecyclePolicy;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.query.PageRequest;
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
import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleRevision;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The side-effect boundary of a lifecycle mutation: idempotency, authorization, confirmation, CAS, evaluation. */
class ControlledChangeLifecycleMutationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");
    private static final ProviderId WRITER = new ProviderId("openspec");
    private static final EvidenceId EVIDENCE = EvidenceId.generate();

    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
    private final ChangeId changeId = ChangeId.generate();
    private final ActiveSnapshot snapshots = new ActiveSnapshot();
    private final MutationStore store = new MutationStore();
    private final ChangeTransitionEvaluationService evaluation = new ChangeTransitionEvaluationService(
            snapshots, new Content(), new NoRequirements(), new NoLinks());
    private ChangeWriteCapabilityResolver writeCapability =
            project -> ChangeWriteCapabilityObservation.allowed(WRITER, "Provider explicitly exposes WRITE_CHANGE");

    @Test
    void aConfirmedMutationAtTheCurrentRevisionIsAppliedWithItsEvidence() {
        ChangeLifecycleMutationResult result = service().apply(
                command("first", 0, ChangeLifecycleState.PROPOSED, Optional.empty(), true),
                ChangeLifecycleMutationPolicy.strict());

        assertEquals(ChangeLifecycleMutationResultState.APPLIED, result.state());
        assertEquals(ChangeLifecycleState.PROPOSED, result.lifecycleState().orElseThrow().lifecycle().state());
        assertEquals(1, result.lifecycleState().orElseThrow().revision().value());
        ChangeLifecycleMutationAttempt attempt = store.attempts.getFirst();
        assertEquals(ChangeLifecycleState.DRAFT, attempt.fromState());
        assertEquals(ChangeLifecycleState.PROPOSED, attempt.targetState());
        assertEquals(WRITER, attempt.providerId());
        assertEquals(NOW, attempt.appliedAt());
        assertEquals("maintainer", attempt.actor());
        assertTrue(attempt.commandFingerprint().matches("[0-9a-f]{64}"), attempt.commandFingerprint());
        assertEquals("Transition is allowed by MORPHEUS lifecycle and explicit constraint rules", attempt.reason());
    }

    @Test
    void replayingTheSameCommandUnderItsKeyReturnsTheEarlierAuditWithoutWritingAgain() {
        ChangeLifecycleMutationCommand command =
                command("replay", 0, ChangeLifecycleState.PROPOSED, Optional.empty(), true);
        ChangeLifecycleMutationResult first = service().apply(command, ChangeLifecycleMutationPolicy.strict());

        ChangeLifecycleMutationResult replay = service().apply(command, ChangeLifecycleMutationPolicy.strict());

        assertEquals(ChangeLifecycleMutationResultState.ALREADY_APPLIED, replay.state());
        assertEquals("Idempotent lifecycle mutation was already applied", replay.reason());
        assertEquals(first.audit(), replay.audit());
        assertEquals(first.lifecycleState(), replay.lifecycleState());
        assertEquals(1, store.attempts.size(), "a replay never reaches the store");
    }

    @Test
    void replayingAnAbandonmentRebuildsTheAbandonedLifecycleWithItsReason() {
        ChangeLifecycleMutationCommand command = command(
                "abandon", 0, ChangeLifecycleState.ABANDONED, Optional.of(ChangeAbandonmentReason.OBSOLETE), true);
        assertEquals(ChangeLifecycleMutationResultState.APPLIED,
                service().apply(command, ChangeLifecycleMutationPolicy.strict()).state());

        ChangeLifecycleMutationResult replay = service().apply(command, ChangeLifecycleMutationPolicy.strict());

        assertEquals(ChangeLifecycle.abandoned(changeId, ChangeAbandonmentReason.OBSOLETE),
                replay.lifecycleState().orElseThrow().lifecycle());
    }

    @Test
    void aKeyAlreadyUsedByAnotherCommandIsAConflictAndNeverAReplay() {
        service().apply(command("shared", 0, ChangeLifecycleState.PROPOSED, Optional.empty(), true),
                ChangeLifecycleMutationPolicy.strict());

        ChangeLifecycleMutationResult result = service().apply(
                command("shared", 1, ChangeLifecycleState.SPECIFIED, Optional.empty(), true),
                ChangeLifecycleMutationPolicy.strict());

        assertEquals(ChangeLifecycleMutationResultState.CONFLICT, result.state());
        assertEquals("Idempotency key was already used for a different lifecycle mutation command", result.reason());
        assertEquals(1, store.attempts.size());
    }

    @Test
    void withoutWriteCapabilityTheRefusalCarriesTheResolverReason() {
        writeCapability = project -> ChangeWriteCapabilityObservation.denied("Project is not registered");

        ChangeLifecycleMutationResult result = service().apply(
                command("denied", 0, ChangeLifecycleState.PROPOSED, Optional.empty(), true),
                ChangeLifecycleMutationPolicy.strict());

        assertEquals(ChangeLifecycleMutationResultState.NOT_AUTHORIZED, result.state());
        assertEquals("Project is not registered", result.reason());
        assertTrue(store.attempts.isEmpty());
    }

    @Test
    void anUnconfirmedCommandIsRefusedUnderTheStrictPolicyOnly() {
        ChangeLifecycleMutationCommand unconfirmed =
                command("unconfirmed", 0, ChangeLifecycleState.PROPOSED, Optional.empty(), false);

        ChangeLifecycleMutationResult strict = service().apply(unconfirmed, ChangeLifecycleMutationPolicy.strict());
        assertEquals(ChangeLifecycleMutationResultState.REQUIRES_CONFIRMATION, strict.state());
        assertEquals("Lifecycle mutation requires explicit confirmation", strict.reason());
        assertTrue(store.attempts.isEmpty());

        ChangeLifecycleMutationResult lenient = service().apply(unconfirmed, new ChangeLifecycleMutationPolicy(false));
        assertEquals(ChangeLifecycleMutationResultState.APPLIED, lenient.state());
    }

    @Test
    void aStaleExpectedRevisionIsAConflictBeforeAnyEvaluation() {
        ChangeLifecycleMutationResult result = service().apply(
                command("stale", 3, ChangeLifecycleState.PROPOSED, Optional.empty(), true),
                ChangeLifecycleMutationPolicy.strict());

        assertEquals(ChangeLifecycleMutationResultState.CONFLICT, result.state());
        assertEquals("Expected lifecycle revision 3 does not match current revision 0", result.reason());
        assertTrue(store.attempts.isEmpty());
    }

    @Test
    void withoutAnActiveSnapshotTheTransitionCannotBeEvaluatedAndIsRejected() {
        snapshots.active = false;

        ChangeLifecycleMutationResult result = service().apply(
                command("no-snapshot", 0, ChangeLifecycleState.PROPOSED, Optional.empty(), true),
                ChangeLifecycleMutationPolicy.strict());

        assertEquals(ChangeLifecycleMutationResultState.REJECTED, result.state());
        assertEquals("No ACTIVE snapshot is available for lifecycle evaluation", result.reason());
    }

    @Test
    void aTransitionTheLifecycleDoesNotAllowIsRejectedWithItsEvaluation() {
        ChangeLifecycleMutationResult result = service().apply(
                command("skip", 0, ChangeLifecycleState.COMPLETED, Optional.empty(), true),
                ChangeLifecycleMutationPolicy.strict());

        assertEquals(ChangeLifecycleMutationResultState.REJECTED, result.state());
        assertTrue(result.reason().startsWith("Lifecycle transition is not eligible for mutation: "), result.reason());
        assertTrue(store.attempts.isEmpty());
    }

    @Test
    void theStoreKeepsTheLastWordOnCasAndIdempotency() {
        store.outcome = ChangeLifecycleMutationPersistenceState.CONFLICT;
        ChangeLifecycleMutationResult conflict = service().apply(
                command("race", 0, ChangeLifecycleState.PROPOSED, Optional.empty(), true),
                ChangeLifecycleMutationPolicy.strict());
        assertEquals(ChangeLifecycleMutationResultState.CONFLICT, conflict.state());
        assertTrue(conflict.audit().isEmpty());
        assertEquals("Concurrent writer advanced the lifecycle revision", conflict.reason());

        store.outcome = ChangeLifecycleMutationPersistenceState.ALREADY_APPLIED;
        ChangeLifecycleMutationResult already = service().apply(
                command("race-2", 0, ChangeLifecycleState.PROPOSED, Optional.empty(), true),
                ChangeLifecycleMutationPolicy.strict());
        assertEquals(ChangeLifecycleMutationResultState.ALREADY_APPLIED, already.state());
        assertTrue(already.audit().isPresent());
    }

    @Test
    void theDefaultConstructionAppliesTheForwardOnlyPolicy() {
        ControlledChangeLifecycleMutationService defaults =
                new ControlledChangeLifecycleMutationService(evaluation, store, writeCapability);

        ChangeLifecycleMutationResult result = defaults.apply(
                command("defaults", 0, ChangeLifecycleState.PROPOSED, Optional.empty(), true),
                ChangeLifecycleMutationPolicy.strict());

        assertEquals(ChangeLifecycleMutationResultState.APPLIED, result.state());
        assertTrue(ChangeLifecycleMutationPolicy.strict().confirmationRequired());
    }

    @Test
    void aCommandRefusesAnAbandonmentReasonOutsideAbandonmentAndABlankActor() {
        assertEquals("abandonment reason is only valid when targetState is ABANDONED",
                assertThrows(IllegalArgumentException.class, () -> command(
                        "reason", 0, ChangeLifecycleState.PROPOSED,
                        Optional.of(ChangeAbandonmentReason.DUPLICATE), true)).getMessage());
        assertEquals("actor must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new ChangeLifecycleMutationCommand(ChangeLifecycleMutationId.generate(),
                        new ChangeLifecycleIdempotencyKey("actor"), projectId, changeId,
                        ChangeLifecycleRevision.initial(), ChangeLifecycleState.PROPOSED, Optional.empty(), true,
                        "  ", NOW)).getMessage());
    }

    @Test
    void aPersistenceResultCarriesEvidenceUnlessItIsAConflict() {
        assertEquals("APPLIED requires persisted state and audit", assertThrows(IllegalArgumentException.class,
                () -> new ChangeLifecycleMutationPersistenceResult(ChangeLifecycleMutationPersistenceState.APPLIED,
                        Optional.empty(), Optional.empty(), "applied")).getMessage());
        assertEquals("reason must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new ChangeLifecycleMutationPersistenceResult(ChangeLifecycleMutationPersistenceState.CONFLICT,
                        Optional.empty(), Optional.empty(), " ")).getMessage());

        store.outcome = ChangeLifecycleMutationPersistenceState.APPLIED;
        ChangeLifecycleMutationResult applied = service().apply(
                command("evidence", 0, ChangeLifecycleState.PROPOSED, Optional.empty(), true),
                ChangeLifecycleMutationPolicy.strict());
        assertEquals("CONFLICT must not fabricate an audit record", assertThrows(IllegalArgumentException.class,
                () -> new ChangeLifecycleMutationPersistenceResult(ChangeLifecycleMutationPersistenceState.CONFLICT,
                        applied.lifecycleState(), applied.audit(), "conflict")).getMessage());
    }

    @Test
    void aWriteCapabilityObservationThatAllowsNamesItsProvider() {
        ChangeWriteCapabilityObservation denied = ChangeWriteCapabilityObservation.denied(" No provider ");
        assertEquals("No provider", denied.reason());
        assertTrue(denied.providerId().isEmpty());
        assertEquals("writeAllowed requires an explicit providerId", assertThrows(IllegalArgumentException.class,
                () -> new ChangeWriteCapabilityObservation(true, Optional.empty(), "allowed")).getMessage());
        assertEquals("reason must not be blank", assertThrows(IllegalArgumentException.class,
                () -> ChangeWriteCapabilityObservation.allowed(WRITER, "")).getMessage());
    }

    private ControlledChangeLifecycleMutationService service() {
        return new ControlledChangeLifecycleMutationService(
                evaluation, store, writeCapability, ChangeLifecyclePolicy.forwardOnly(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ChangeLifecycleMutationCommand command(
            String key,
            long expectedRevision,
            ChangeLifecycleState target,
            Optional<ChangeAbandonmentReason> reason,
            boolean confirmed) {
        return new ChangeLifecycleMutationCommand(
                ChangeLifecycleMutationId.generate(),
                new ChangeLifecycleIdempotencyKey(key),
                projectId,
                changeId,
                new ChangeLifecycleRevision(expectedRevision),
                target,
                reason,
                confirmed,
                " maintainer ",
                NOW);
    }

    private static Provenance provenance() {
        return new Provenance(WRITER, Optional.of("1"), SourceLocator.file("changes/c.md"),
                Optional.of("c"), Optional.of("rev"), EVIDENCE);
    }

    private final class MutationStore implements ChangeLifecycleMutationStore {
        private final Map<ChangeLifecycleIdempotencyKey, ChangeLifecycleMutationAuditRecord> audits =
                new LinkedHashMap<>();
        private final List<ChangeLifecycleMutationAttempt> attempts = new ArrayList<>();
        private ChangeLifecycleOperationalState state;
        private ChangeLifecycleMutationPersistenceState outcome = ChangeLifecycleMutationPersistenceState.APPLIED;

        @Override
        public Optional<ChangeLifecycleOperationalState> findState(ProjectSpecificationId project, ChangeId change) {
            return Optional.ofNullable(state);
        }

        @Override
        public Optional<ChangeLifecycleMutationAuditRecord> findByIdempotencyKey(
                ProjectSpecificationId project,
                ChangeLifecycleIdempotencyKey idempotencyKey) {
            return Optional.ofNullable(audits.get(idempotencyKey));
        }

        @Override
        public ChangeLifecycleMutationAuditPage listAudit(
                ProjectSpecificationId project, ChangeId change, PageRequest pageRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChangeLifecycleMutationPersistenceResult apply(ChangeLifecycleMutationAttempt attempt) {
            attempts.add(attempt);
            if (outcome == ChangeLifecycleMutationPersistenceState.CONFLICT) {
                return new ChangeLifecycleMutationPersistenceResult(outcome, Optional.ofNullable(state),
                        Optional.empty(), "Concurrent writer advanced the lifecycle revision");
            }
            ChangeLifecycleRevision next = attempt.expectedRevision().next();
            ChangeLifecycleMutationAuditRecord audit = new ChangeLifecycleMutationAuditRecord(
                    attempt.mutationId(), attempt.idempotencyKey(), attempt.commandFingerprint(),
                    attempt.projectId(), attempt.changeId(), attempt.fromState(), attempt.targetState(),
                    attempt.targetAbandonmentReason(), attempt.expectedRevision(), next, attempt.actor(),
                    attempt.providerId(), attempt.reason(), attempt.appliedAt());
            ChangeLifecycle lifecycle = attempt.targetAbandonmentReason()
                    .map(reason -> ChangeLifecycle.abandoned(attempt.changeId(), reason))
                    .orElseGet(() -> ChangeLifecycle.of(attempt.changeId(), attempt.targetState()));
            state = new ChangeLifecycleOperationalState(attempt.projectId(), lifecycle, next,
                    Optional.of(attempt.appliedAt()), Optional.of(attempt.mutationId()));
            audits.put(attempt.idempotencyKey(), audit);
            return new ChangeLifecycleMutationPersistenceResult(outcome, Optional.of(state), Optional.of(audit),
                    "Lifecycle mutation persisted");
        }
    }

    private final class ActiveSnapshot implements SpecificationKnowledgeStore {
        private boolean active = true;

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
        @Override public Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId id) {
            ChangeProposal change = new ChangeProposal(changeId, projectId, Optional.of("change"), "Change", "Intent",
                    List.of(), List.of(), List.of(), provenance());
            return Optional.of(new SnapshotBusinessContent(id, SpecificationVersionId.generate(), List.of(), List.of(),
                    List.of(change), List.of(), List.of(), List.of(), List.of(),
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

    private static final class NoLinks implements TraceabilityStore {
        @Override public List<TraceabilityLink> outgoing(KnowledgeSnapshotId id, TraceabilityEntityRef source,
                Set<TraceabilityRelationType> relationTypes) {
            return List.of();
        }

        @Override public List<TraceabilityLink> incoming(KnowledgeSnapshotId id, TraceabilityEntityRef target,
                Set<TraceabilityRelationType> relationTypes) {
            return List.of();
        }

        @Override public void putLink(KnowledgeSnapshotId id, TraceabilityLink link) { throw new UnsupportedOperationException(); }
        @Override public void putLinks(KnowledgeSnapshotId id, List<TraceabilityLink> batch) { throw new UnsupportedOperationException(); }
        @Override public Optional<TraceabilityLink> findLink(KnowledgeSnapshotId id, TraceabilityLinkId linkId) { throw new UnsupportedOperationException(); }
    }
}
