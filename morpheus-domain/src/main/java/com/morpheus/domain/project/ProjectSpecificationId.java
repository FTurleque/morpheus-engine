package com.morpheus.domain.project;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** MORPHEUS-owned identity of one project specification scope. */
public record ProjectSpecificationId(DomainIdentity value) implements Comparable<ProjectSpecificationId> {
    public ProjectSpecificationId {
        Objects.requireNonNull(value, "value");
    }

    public static ProjectSpecificationId generate() {
        return new ProjectSpecificationId(DomainIdentity.generate());
    }

    public static ProjectSpecificationId parse(String value) {
        return new ProjectSpecificationId(DomainIdentity.parse(value));
    }

    @Override
    public int compareTo(ProjectSpecificationId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
