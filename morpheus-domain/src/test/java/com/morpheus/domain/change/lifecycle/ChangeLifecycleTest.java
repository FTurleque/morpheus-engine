package com.morpheus.domain.change.lifecycle;

import com.morpheus.domain.change.ChangeId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangeLifecycleTest {

    @Test
    void exposesExactlyTheTenCanonicalStates() {
        assertEquals(
                Arrays.asList(
                        ChangeLifecycleState.DRAFT,
                        ChangeLifecycleState.PROPOSED,
                        ChangeLifecycleState.SPECIFIED,
                        ChangeLifecycleState.DESIGNED,
                        ChangeLifecycleState.PLANNED,
                        ChangeLifecycleState.IMPLEMENTING,
                        ChangeLifecycleState.VERIFYING,
                        ChangeLifecycleState.COMPLETED,
                        ChangeLifecycleState.ARCHIVED,
                        ChangeLifecycleState.ABANDONED),
                Arrays.asList(ChangeLifecycleState.values()));
    }

    @Test
    void abandonedLifecycleRequiresStructuredReason() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycle(
                ChangeId.generate(),
                ChangeLifecycleState.ABANDONED,
                Optional.empty()));
        assertEquals("ABANDONED lifecycle requires an abandonment reason", failure.getMessage());
    }

    @Test
    void nonAbandonedLifecycleRejectsAbandonmentReason() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycle(
                ChangeId.generate(),
                ChangeLifecycleState.PROPOSED,
                Optional.of(ChangeAbandonmentReason.OBSOLETE)));
        assertEquals("abandonment reason is only valid for ABANDONED lifecycle", failure.getMessage());
    }

    @Test
    void stateChangesDoNotChangeLogicalChangeIdentity() {
        ChangeId changeId = ChangeId.generate();
        assertEquals(changeId, ChangeLifecycle.of(changeId, ChangeLifecycleState.DRAFT).changeId());
        assertEquals(changeId, ChangeLifecycle.of(changeId, ChangeLifecycleState.COMPLETED).changeId());
    }
}
