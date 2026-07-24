package com.morpheus.application.orchestration;

import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;

import java.util.Objects;
import java.util.Optional;

/** Explicit lifecycle observation used for orchestration analysis; it is never inferred from snapshot structure. */
public record ChangeLifecycleObservation(
        Optional<ChangeLifecycleState> state,
        Optional<ChangeAbandonmentReason> abandonmentReason,
        ChangeLifecycleObservationSource source) {

    public ChangeLifecycleObservation {
        state = Objects.requireNonNull(state, "state");
        abandonmentReason = Objects.requireNonNull(abandonmentReason, "abandonmentReason");
        Objects.requireNonNull(source, "source");

        if (source == ChangeLifecycleObservationSource.UNAVAILABLE && state.isPresent()) {
            throw new IllegalArgumentException("UNAVAILABLE lifecycle source must not contain a state");
        }
        if (source == ChangeLifecycleObservationSource.CALLER_SUPPLIED && state.isEmpty()) {
            throw new IllegalArgumentException("CALLER_SUPPLIED lifecycle source requires a state");
        }
        if (state.orElse(null) == ChangeLifecycleState.ABANDONED && abandonmentReason.isEmpty()) {
            throw new IllegalArgumentException("ABANDONED lifecycle observation requires an abandonment reason");
        }
        if (state.orElse(null) != ChangeLifecycleState.ABANDONED && abandonmentReason.isPresent()) {
            throw new IllegalArgumentException("abandonment reason is only valid for ABANDONED lifecycle observation");
        }
    }

    public static ChangeLifecycleObservation unavailable() {
        return new ChangeLifecycleObservation(Optional.empty(), Optional.empty(), ChangeLifecycleObservationSource.UNAVAILABLE);
    }

    public static ChangeLifecycleObservation callerSupplied(
            ChangeLifecycleState state,
            Optional<ChangeAbandonmentReason> abandonmentReason) {
        return new ChangeLifecycleObservation(
                Optional.of(Objects.requireNonNull(state, "state")),
                Objects.requireNonNull(abandonmentReason, "abandonmentReason"),
                ChangeLifecycleObservationSource.CALLER_SUPPLIED);
    }
}
