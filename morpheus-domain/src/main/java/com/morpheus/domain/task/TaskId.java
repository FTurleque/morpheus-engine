package com.morpheus.domain.task;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of a normalized implementation task. */
public record TaskId(DomainIdentity value) implements Comparable<TaskId> {
    public TaskId {
        Objects.requireNonNull(value, "value");
    }

    public static TaskId generate() {
        return new TaskId(DomainIdentity.generate());
    }

    public static TaskId parse(String value) {
        return new TaskId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(TaskId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
