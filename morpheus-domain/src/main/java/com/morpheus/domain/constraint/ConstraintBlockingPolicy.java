package com.morpheus.domain.constraint;

import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Provider-neutral blocking policy with explicit lifecycle targets. */
public record ConstraintBlockingPolicy(
        ConstraintBlockingMode mode,
        List<ChangeLifecycleState> targetStates) {

    public ConstraintBlockingPolicy {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(targetStates, "targetStates");
        List<ChangeLifecycleState> copy = targetStates.stream()
                .peek(item -> Objects.requireNonNull(item, "targetStates item"))
                .sorted()
                .toList();
        Set<ChangeLifecycleState> seen = new HashSet<>();
        for (ChangeLifecycleState state : copy) {
            if (!seen.add(state)) {
                throw new IllegalArgumentException("duplicate lifecycle target: " + state);
            }
        }
        targetStates = List.copyOf(copy);

        if (mode == ConstraintBlockingMode.BLOCK_WHEN_VIOLATED && targetStates.isEmpty()) {
            throw new IllegalArgumentException("BLOCK_WHEN_VIOLATED requires at least one lifecycle target");
        }
        if (mode != ConstraintBlockingMode.BLOCK_WHEN_VIOLATED && !targetStates.isEmpty()) {
            throw new IllegalArgumentException(mode + " cannot declare lifecycle targets");
        }
    }

    public static ConstraintBlockingPolicy unknown() {
        return new ConstraintBlockingPolicy(ConstraintBlockingMode.UNKNOWN, List.of());
    }

    public static ConstraintBlockingPolicy nonBlocking() {
        return new ConstraintBlockingPolicy(ConstraintBlockingMode.NON_BLOCKING, List.of());
    }

    public static ConstraintBlockingPolicy blockWhenViolated(List<ChangeLifecycleState> targetStates) {
        return new ConstraintBlockingPolicy(ConstraintBlockingMode.BLOCK_WHEN_VIOLATED, targetStates);
    }

    public boolean targets(ChangeLifecycleState state) {
        return targetStates.contains(Objects.requireNonNull(state, "state"));
    }
}
