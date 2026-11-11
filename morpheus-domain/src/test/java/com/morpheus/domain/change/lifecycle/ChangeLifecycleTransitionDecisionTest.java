package com.morpheus.domain.change.lifecycle;

import com.morpheus.domain.change.ChangeId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeLifecycleTransitionDecisionTest {

    @Test
    void anAllowedTransitionCarriesItsTargetAndNoBlocker() {
        ChangeLifecycle proposed = ChangeLifecycle.of(ChangeId.generate(), ChangeLifecycleState.PROPOSED);
        ChangeLifecycleTransitionDecision allowed = new ChangeLifecycleTransitionDecision.Allowed(proposed);

        assertTrue(allowed.allowed());
        assertEquals(List.of(), allowed.blockers());
        assertEquals(Optional.of(proposed), allowed.target());
        assertFalse(allowed.awaitsAnAbandonmentReason());
    }

    @Test
    void aBlockedTransitionNeedsABlocker() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> new ChangeLifecycleTransitionDecision.Blocked(List.of()));
        assertEquals("a blocked transition requires at least one blocker", refused.getMessage());
    }

    @Test
    void aMissingAbandonmentReasonIsTheOnlyBlockerThatAwaitsInput() {
        assertTrue(new ChangeLifecycleTransitionDecision.Blocked(
                List.of(ChangeLifecycleBlocker.ABANDONMENT_REASON_REQUIRED)).awaitsAnAbandonmentReason());
        ChangeLifecycleTransitionDecision missingPlan = new ChangeLifecycleTransitionDecision.Blocked(
                List.of(ChangeLifecycleBlocker.MISSING_PLAN));
        assertFalse(missingPlan.awaitsAnAbandonmentReason());
        assertEquals(Optional.empty(), missingPlan.target());
    }

    @Test
    void aBlockingConstraintAddsItsBlockerOnceAfterTheLifecycleBlockers() {
        assertEquals(List.of(ChangeLifecycleBlocker.BLOCKING_CONSTRAINT),
                ChangeLifecycleTransitionDecision.blockedByAConstraint(Optional.empty()).blockers());

        ChangeLifecycleTransitionDecision allowed = new ChangeLifecycleTransitionDecision.Allowed(
                ChangeLifecycle.of(ChangeId.generate(), ChangeLifecycleState.PLANNED));
        assertEquals(List.of(ChangeLifecycleBlocker.BLOCKING_CONSTRAINT),
                ChangeLifecycleTransitionDecision.blockedByAConstraint(Optional.of(allowed)).blockers());

        ChangeLifecycleTransitionDecision blocked = new ChangeLifecycleTransitionDecision.Blocked(
                List.of(ChangeLifecycleBlocker.MISSING_PLAN, ChangeLifecycleBlocker.BLOCKING_CONSTRAINT));
        assertEquals(List.of(ChangeLifecycleBlocker.MISSING_PLAN, ChangeLifecycleBlocker.BLOCKING_CONSTRAINT),
                ChangeLifecycleTransitionDecision.blockedByAConstraint(Optional.of(blocked)).blockers());
    }
}
