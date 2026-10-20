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
        IllegalArgumentException negativeFailure = assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleRevision(-1));
        assertEquals("lifecycle revision must be non-negative", negativeFailure.getMessage());
        IllegalStateException overflowFailure = assertThrows(IllegalStateException.class, () -> new ChangeLifecycleRevision(Long.MAX_VALUE).next());
        assertEquals("lifecycle revision overflow", overflowFailure.getMessage());
    }

    @Test
    void idempotencyKeyIsExplicitNormalizedAndBounded() {
        assertEquals("retry-1", new ChangeLifecycleIdempotencyKey("  retry-1  ").value());
        IllegalArgumentException blankFailure = assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleIdempotencyKey("   "));
        assertEquals("idempotency key must not be blank", blankFailure.getMessage());
        IllegalArgumentException tooLongFailure = assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleIdempotencyKey("x".repeat(201)));
        assertEquals("idempotency key must not exceed 200 characters", tooLongFailure.getMessage());
    }

    @Test
    void mutationIdentityRoundTripsThroughCanonicalString() {
        ChangeLifecycleMutationId id = ChangeLifecycleMutationId.generate();
        assertEquals(id, ChangeLifecycleMutationId.parse(id.toString()));
    }
}
