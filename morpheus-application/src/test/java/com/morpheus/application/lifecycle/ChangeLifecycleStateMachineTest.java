package com.morpheus.application.lifecycle;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeLifecycleStateMachineTest {
    private final ChangeLifecycleStateMachine machine = new ChangeLifecycleStateMachine();

    @Test
    void acceptsTheNominalLifecycle() {
        ChangeId changeId = ChangeId.generate();
        ChangeLifecycle lifecycle = ChangeLifecycle.of(changeId, ChangeLifecycleState.DRAFT);
        ChangeLifecycleFacts facts = ChangeLifecycleFacts.permissive();
        ChangeLifecyclePolicy policy = ChangeLifecyclePolicy.forwardOnly();

        for (ChangeLifecycleState target : new ChangeLifecycleState[]{
                ChangeLifecycleState.PROPOSED,
                ChangeLifecycleState.SPECIFIED,
                ChangeLifecycleState.DESIGNED,
                ChangeLifecycleState.PLANNED,
                ChangeLifecycleState.IMPLEMENTING,
                ChangeLifecycleState.VERIFYING,
                ChangeLifecycleState.COMPLETED,
                ChangeLifecycleState.ARCHIVED}) {
            var decision = machine.evaluate(ChangeLifecycleTransitionRequest.to(lifecycle, target, facts, policy));
            assertTrue(decision.allowed(), lifecycle.state() + " -> " + target);
            lifecycle = decision.target().orElseThrow();
        }

        assertEquals(ChangeLifecycleState.ARCHIVED, lifecycle.state());
    }

    @Test
    void proposedToSpecifiedReportsAllMissingPrerequisites() {
        ChangeLifecycleFacts missing = new ChangeLifecycleFacts(
                false, false, false, true, true, true, false, false, false);

        var decision = evaluate(ChangeLifecycleState.PROPOSED, ChangeLifecycleState.SPECIFIED, missing);

        assertFalse(decision.allowed());
        assertEquals(
                java.util.List.of(
                        ChangeLifecycleBlocker.MISSING_REQUIREMENTS,
                        ChangeLifecycleBlocker.UNKNOWN_CRITICAL_CONSTRAINTS,
                        ChangeLifecycleBlocker.MISSING_ACCEPTANCE_CRITERIA),
                decision.blockers());
    }

    @Test
    void specifiedCanSkipDesignOnlyWhenDesignIsNotRequiredAndPlanExists() {
        ChangeLifecycleFacts skipAllowed = new ChangeLifecycleFacts(
                true, true, true, false, false, true, false, false, false);
        assertTrue(evaluate(ChangeLifecycleState.SPECIFIED, ChangeLifecycleState.PLANNED, skipAllowed).allowed());

        ChangeLifecycleFacts designRequired = new ChangeLifecycleFacts(
                true, true, true, true, true, true, false, false, false);
        assertEquals(
                java.util.List.of(ChangeLifecycleBlocker.DESIGN_REQUIRED),
                evaluate(ChangeLifecycleState.SPECIFIED, ChangeLifecycleState.PLANNED, designRequired).blockers());

        ChangeLifecycleFacts noPlan = new ChangeLifecycleFacts(
                true, true, true, false, false, false, false, false, false);
        assertEquals(
                java.util.List.of(ChangeLifecycleBlocker.MISSING_PLAN),
                evaluate(ChangeLifecycleState.SPECIFIED, ChangeLifecycleState.PLANNED, noPlan).blockers());
    }

    @Test
    void specifiedToDesignedRequiresDesignDecisionsWhenDesignIsRequired() {
        ChangeLifecycleFacts missingDesign = new ChangeLifecycleFacts(
                true, true, true, true, false, true, false, false, false);

        var decision = evaluate(ChangeLifecycleState.SPECIFIED, ChangeLifecycleState.DESIGNED, missingDesign);

        assertFalse(decision.allowed());
        assertEquals(java.util.List.of(ChangeLifecycleBlocker.MISSING_DESIGN), decision.blockers());
    }

    @Test
    void knownBlockerPreventsImplementation() {
        ChangeLifecycleFacts blocked = new ChangeLifecycleFacts(
                true, true, true, true, true, true, true, false, false);

        var decision = evaluate(ChangeLifecycleState.PLANNED, ChangeLifecycleState.IMPLEMENTING, blocked);

        assertFalse(decision.allowed());
        assertEquals(java.util.List.of(ChangeLifecycleBlocker.KNOWN_BLOCKER), decision.blockers());
    }

    @Test
    void failedOrUnverifiedBlockingAcceptanceCriteriaPreventCompletion() {
        ChangeLifecycleFacts blocked = new ChangeLifecycleFacts(
                true, true, true, true, true, true, false, true, true);

        var decision = evaluate(ChangeLifecycleState.VERIFYING, ChangeLifecycleState.COMPLETED, blocked);

        assertFalse(decision.allowed());
        assertEquals(
                java.util.List.of(
                        ChangeLifecycleBlocker.BLOCKING_ACCEPTANCE_CRITERION_FAILED,
                        ChangeLifecycleBlocker.BLOCKING_ACCEPTANCE_CRITERION_UNVERIFIED),
                decision.blockers());
    }

    @Test
    void backwardRevisionRequiresExplicitPolicy() {
        ChangeLifecycle source = ChangeLifecycle.of(ChangeId.generate(), ChangeLifecycleState.VERIFYING);
        var denied = machine.evaluate(ChangeLifecycleTransitionRequest.to(
                source,
                ChangeLifecycleState.IMPLEMENTING,
                ChangeLifecycleFacts.permissive(),
                ChangeLifecyclePolicy.forwardOnly()));
        assertEquals(java.util.List.of(ChangeLifecycleBlocker.BACKWARD_TRANSITION_DISABLED), denied.blockers());

        var allowed = machine.evaluate(ChangeLifecycleTransitionRequest.to(
                source,
                ChangeLifecycleState.IMPLEMENTING,
                ChangeLifecycleFacts.permissive(),
                ChangeLifecyclePolicy.revisionsAllowed()));
        assertTrue(allowed.allowed());
    }

    @Test
    void completedReopenRequiresItsExceptionalPermission() {
        ChangeLifecycle source = ChangeLifecycle.of(ChangeId.generate(), ChangeLifecycleState.COMPLETED);
        var denied = machine.evaluate(ChangeLifecycleTransitionRequest.to(
                source,
                ChangeLifecycleState.VERIFYING,
                ChangeLifecycleFacts.permissive(),
                ChangeLifecyclePolicy.revisionsAllowed()));
        assertEquals(java.util.List.of(ChangeLifecycleBlocker.COMPLETED_REOPEN_DISABLED), denied.blockers());

        var allowed = machine.evaluate(ChangeLifecycleTransitionRequest.to(
                source,
                ChangeLifecycleState.VERIFYING,
                ChangeLifecycleFacts.permissive(),
                ChangeLifecyclePolicy.revisionsAndCompletedReopenAllowed()));
        assertTrue(allowed.allowed());
    }

    @Test
    void abandonmentRequiresReasonAndPreservesIt() {
        ChangeLifecycle source = ChangeLifecycle.of(ChangeId.generate(), ChangeLifecycleState.IMPLEMENTING);
        var missingReason = machine.evaluate(ChangeLifecycleTransitionRequest.to(
                source,
                ChangeLifecycleState.ABANDONED,
                ChangeLifecycleFacts.permissive(),
                ChangeLifecyclePolicy.forwardOnly()));
        assertEquals(java.util.List.of(ChangeLifecycleBlocker.ABANDONMENT_REASON_REQUIRED), missingReason.blockers());

        var withReason = machine.evaluate(new ChangeLifecycleTransitionRequest(
                source,
                ChangeLifecycleState.ABANDONED,
                ChangeLifecycleFacts.permissive(),
                ChangeLifecyclePolicy.forwardOnly(),
                Optional.of(ChangeAbandonmentReason.NOT_FEASIBLE)));
        assertTrue(withReason.allowed());
        assertEquals(ChangeAbandonmentReason.NOT_FEASIBLE,
                withReason.target().orElseThrow().abandonmentReason().orElseThrow());
    }

    @Test
    void abandonedChangeCanReopenAsProposed() {
        ChangeLifecycle source = ChangeLifecycle.abandoned(ChangeId.generate(), ChangeAbandonmentReason.OBSOLETE);

        var decision = machine.evaluate(ChangeLifecycleTransitionRequest.to(
                source,
                ChangeLifecycleState.PROPOSED,
                ChangeLifecycleFacts.permissive(),
                ChangeLifecyclePolicy.forwardOnly()));

        assertTrue(decision.allowed());
        assertEquals(ChangeLifecycleState.PROPOSED, decision.target().orElseThrow().state());
        assertTrue(decision.target().orElseThrow().abandonmentReason().isEmpty());
    }

    @Test
    void archivedChangeDoesNotReopenImplicitly() {
        ChangeLifecycle source = ChangeLifecycle.of(ChangeId.generate(), ChangeLifecycleState.ARCHIVED);

        var decision = machine.evaluate(ChangeLifecycleTransitionRequest.to(
                source,
                ChangeLifecycleState.PROPOSED,
                ChangeLifecycleFacts.permissive(),
                ChangeLifecyclePolicy.revisionsAndCompletedReopenAllowed()));

        assertFalse(decision.allowed());
        assertEquals(java.util.List.of(ChangeLifecycleBlocker.ARCHIVED_REOPEN_NOT_ALLOWED), decision.blockers());
    }

    @Test
    void completedLifecycleAndCompletedTaskDoNotPromoteTemporalState() {
        ChangeId changeId = ChangeId.generate();
        ChangeLifecycle verifying = ChangeLifecycle.of(changeId, ChangeLifecycleState.VERIFYING);
        var completion = machine.evaluate(ChangeLifecycleTransitionRequest.to(
                verifying,
                ChangeLifecycleState.COMPLETED,
                ChangeLifecycleFacts.permissive(),
                ChangeLifecyclePolicy.forwardOnly()));
        ChangeLifecycle completed = completion.target().orElseThrow();

        EntityVersion<ChangeLifecycle> occurrence = new EntityVersion<>(
                EntityVersionId.generate(),
                changeId.value(),
                SpecificationVersionId.generate(),
                TemporalState.PROPOSED,
                completed);
        assertEquals(ChangeLifecycleState.COMPLETED, occurrence.content().state());
        assertEquals(TemporalState.PROPOSED, occurrence.temporalState());

        ImplementationTask task = new ImplementationTask(
                TaskId.generate(),
                changeId,
                Optional.of("task-1"),
                "Implementation done",
                true,
                provenance());
        ChangeLifecycle stillDraft = ChangeLifecycle.of(changeId, ChangeLifecycleState.DRAFT);
        assertTrue(task.completed());
        assertEquals(ChangeLifecycleState.DRAFT, stillDraft.state());
    }

    private ChangeLifecycleTransitionDecision evaluate(
            ChangeLifecycleState from,
            ChangeLifecycleState to,
            ChangeLifecycleFacts facts) {
        return machine.evaluate(ChangeLifecycleTransitionRequest.to(
                ChangeLifecycle.of(ChangeId.generate(), from),
                to,
                facts,
                ChangeLifecyclePolicy.forwardOnly()));
    }

    private Provenance provenance() {
        return new Provenance(
                new ProviderId("test-provider"),
                Optional.of("1.0"),
                SourceLocator.file("test/change.md"),
                Optional.of("change:test"),
                Optional.empty(),
                EvidenceId.generate());
    }
}
