package com.morpheus.domain.change.lifecycle;

import java.util.Objects;

/** Caller-owned stable key used to make lifecycle mutation retries idempotent. */
public record ChangeLifecycleIdempotencyKey(String value) implements Comparable<ChangeLifecycleIdempotencyKey> {
    public ChangeLifecycleIdempotencyKey {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
        if (value.length() > 200) {
            throw new IllegalArgumentException("idempotency key must not exceed 200 characters");
        }
    }

    @Override
    public int compareTo(ChangeLifecycleIdempotencyKey other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
