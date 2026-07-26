package com.morpheus.domain.change.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangeLifecycleMutationPrimitivesTest {
    @Test
    void revisionStartsAtZeroAndAdvancesMonotonically() {
        ChangeLifecycleRevision initial = ChangeLifecycleRevision.initial();
        assertEquals(0, initial.value());
        assertEquals(1, initial.next().value());
        assertEquals(2, initial.next().next().value());
        assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleRevision(-1));
        assertThrows(IllegalStateException.class, () -> new ChangeLifecycleRevision(Long.MAX_VALUE).next());
    }

    @Test
    void idempotencyKeyIsExplicitNormalizedAndBounded() {
        assertEquals("retry-1", new ChangeLifecycleIdempotencyKey("  retry-1  ").value());
        assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleIdempotencyKey("   "));
        assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleIdempotencyKey("x".repeat(201)));
    }

    @Test
    void mutationIdentityRoundTripsThroughCanonicalString() {
        ChangeLifecycleMutationId id = ChangeLifecycleMutationId.generate();
        assertEquals(id, ChangeLifecycleMutationId.parse(id.toString()));
    }
}
