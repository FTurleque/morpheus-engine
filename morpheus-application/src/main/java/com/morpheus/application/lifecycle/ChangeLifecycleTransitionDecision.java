package com.morpheus.application.lifecycle;

import com.morpheus.domain.change.lifecycle.ChangeLifecycle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic transition decision with machine-readable blockers. */
public record ChangeLifecycleTransitionDecision(
        boolean allowed,
        List<ChangeLifecycleBlocker> blockers,
        Optional<ChangeLifecycle> target) {

    public ChangeLifecycleTransitionDecision {
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        target = Objects.requireNonNull(target, "target");
        if (allowed && (!blockers.isEmpty() || target.isEmpty())) {
            throw new IllegalArgumentException("allowed transition requires target and no blockers");
        }
        if (!allowed && (blockers.isEmpty() || target.isPresent())) {
            throw new IllegalArgumentException("blocked transition requires blockers and no target");
        }
    }

    public static ChangeLifecycleTransitionDecision allowed(ChangeLifecycle target) {
        return new ChangeLifecycleTransitionDecision(true, List.of(), Optional.of(target));
    }

    public static ChangeLifecycleTransitionDecision blocked(List<ChangeLifecycleBlocker> blockers) {
        return new ChangeLifecycleTransitionDecision(false, blockers, Optional.empty());
    }
}
