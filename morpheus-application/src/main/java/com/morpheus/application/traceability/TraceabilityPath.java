package com.morpheus.application.traceability;

import com.morpheus.domain.traceability.TraceabilityEntityRef;

import java.util.List;
import java.util.Objects;

/** Deterministic shortest path composed only of persisted traceability links. */
public record TraceabilityPath(
        TraceabilityEntityRef start,
        TraceabilityEntityRef target,
        List<TraceabilityPathStep> steps) {

    public TraceabilityPath {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(target, "target");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));

        if (steps.isEmpty()) {
            if (!start.equals(target)) {
                throw new IllegalArgumentException("empty path is valid only when start equals target");
            }
        } else {
            if (!steps.getFirst().from().equals(start)) {
                throw new IllegalArgumentException("first path step must start at the path start entity");
            }
            if (!steps.getLast().into().equals(target)) {
                throw new IllegalArgumentException("last path step must end at the path target entity");
            }
            for (int index = 1; index < steps.size(); index++) {
                if (!steps.get(index - 1).into().equals(steps.get(index).from())) {
                    throw new IllegalArgumentException("path steps must form a contiguous chain");
                }
            }
        }
    }
}
