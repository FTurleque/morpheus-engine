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
        assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycle(
                ChangeId.generate(),
                ChangeLifecycleState.ABANDONED,
                Optional.empty()));
    }

    @Test
    void nonAbandonedLifecycleRejectsAbandonmentReason() {
        assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycle(
                ChangeId.generate(),
                ChangeLifecycleState.PROPOSED,
                Optional.of(ChangeAbandonmentReason.OBSOLETE)));
    }

    @Test
    void stateChangesDoNotChangeLogicalChangeIdentity() {
        ChangeId changeId = ChangeId.generate();
        assertEquals(changeId, ChangeLifecycle.of(changeId, ChangeLifecycleState.DRAFT).changeId());
        assertEquals(changeId, ChangeLifecycle.of(changeId, ChangeLifecycleState.COMPLETED).changeId());
    }
}
