package com.morpheus.domain.change.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of a lifecycle transition: allowed towards a target lifecycle, or blocked by machine-readable blockers.
 *
 * <p>The family is closed. Only the domain builds one, from {@link ChangeLifecycleTransitionRequest#decide()} or
 * {@link #blockedByAConstraint(Optional)}.</p>
 */
public sealed interface ChangeLifecycleTransitionDecision
        permits ChangeLifecycleTransitionDecision.Allowed, ChangeLifecycleTransitionDecision.Blocked {

    boolean allowed();

    List<ChangeLifecycleBlocker> blockers();

    Optional<ChangeLifecycle> target();

    /** A blocked transition that a caller can unblock by naming why the change is abandoned. */
    default boolean awaitsAnAbandonmentReason() {
        return blockers().contains(ChangeLifecycleBlocker.ABANDONMENT_REASON_REQUIRED);
    }

    /**
     * The transition blocked by a violated blocking constraint: the lifecycle blockers of the decision, when there is
     * one, then {@link ChangeLifecycleBlocker#BLOCKING_CONSTRAINT} once.
     */
    static Blocked blockedByAConstraint(Optional<ChangeLifecycleTransitionDecision> lifecycleDecision) {
        List<ChangeLifecycleBlocker> blockers = new ArrayList<>(
                Objects.requireNonNull(lifecycleDecision, "lifecycleDecision")
                        .map(ChangeLifecycleTransitionDecision::blockers)
                        .orElse(List.of()));
        if (!blockers.contains(ChangeLifecycleBlocker.BLOCKING_CONSTRAINT)) {
            blockers.add(ChangeLifecycleBlocker.BLOCKING_CONSTRAINT);
        }
        return new Blocked(blockers);
    }

    record Allowed(ChangeLifecycle lifecycle) implements ChangeLifecycleTransitionDecision {
        public Allowed {
            Objects.requireNonNull(lifecycle, "lifecycle");
        }

        @Override
        public boolean allowed() {
            return true;
        }

        @Override
        public List<ChangeLifecycleBlocker> blockers() {
            return List.of();
        }

        @Override
        public Optional<ChangeLifecycle> target() {
            return Optional.of(lifecycle);
        }
    }

    record Blocked(List<ChangeLifecycleBlocker> blockers) implements ChangeLifecycleTransitionDecision {
        public Blocked {
            blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
            if (blockers.isEmpty()) {
                throw new IllegalArgumentException("a blocked transition requires at least one blocker");
            }
        }

        @Override
        public boolean allowed() {
            return false;
        }

        @Override
        public Optional<ChangeLifecycle> target() {
            return Optional.empty();
        }
    }
}
