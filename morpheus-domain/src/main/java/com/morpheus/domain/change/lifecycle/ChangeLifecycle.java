package com.morpheus.domain.change.lifecycle;

import com.morpheus.domain.change.ChangeId;

import java.util.Objects;
import java.util.Optional;

/** Business lifecycle observation kept separate from normalized change content and temporal projection. */
public record ChangeLifecycle(
        ChangeId changeId,
        ChangeLifecycleState state,
        Optional<ChangeAbandonmentReason> abandonmentReason) {

    public ChangeLifecycle {
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(state, "state");
        abandonmentReason = Objects.requireNonNull(abandonmentReason, "abandonmentReason");

        if (state == ChangeLifecycleState.ABANDONED && abandonmentReason.isEmpty()) {
            throw new IllegalArgumentException("ABANDONED lifecycle requires an abandonment reason");
        }
        if (state != ChangeLifecycleState.ABANDONED && abandonmentReason.isPresent()) {
            throw new IllegalArgumentException("abandonment reason is only valid for ABANDONED lifecycle");
        }
    }

    public static ChangeLifecycle of(ChangeId changeId, ChangeLifecycleState state) {
        return new ChangeLifecycle(changeId, state, Optional.empty());
    }

    public static ChangeLifecycle abandoned(ChangeId changeId, ChangeAbandonmentReason reason) {
        return new ChangeLifecycle(changeId, ChangeLifecycleState.ABANDONED, Optional.of(reason));
    }
}
