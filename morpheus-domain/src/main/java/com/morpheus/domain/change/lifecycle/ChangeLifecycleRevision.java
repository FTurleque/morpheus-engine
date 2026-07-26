package com.morpheus.domain.change.lifecycle;

/** Monotone optimistic-concurrency revision for mutable operational lifecycle state. */
public record ChangeLifecycleRevision(long value) implements Comparable<ChangeLifecycleRevision> {
    public ChangeLifecycleRevision {
        if (value < 0) {
            throw new IllegalArgumentException("lifecycle revision must be non-negative");
        }
    }

    public static ChangeLifecycleRevision initial() {
        return new ChangeLifecycleRevision(0);
    }

    public ChangeLifecycleRevision next() {
        if (value == Long.MAX_VALUE) {
            throw new IllegalStateException("lifecycle revision overflow");
        }
        return new ChangeLifecycleRevision(value + 1);
    }

    @Override
    public int compareTo(ChangeLifecycleRevision other) {
        return Long.compare(value, other.value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
