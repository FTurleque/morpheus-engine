package com.morpheus.domain.version;

import com.morpheus.domain.project.ProjectSpecificationId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Logical version of one project's specification content, distinct from a technical snapshot. */
public record SpecificationVersion(
        SpecificationVersionId id,
        ProjectSpecificationId projectId,
        Optional<Long> sequence,
        Optional<String> providerVersion,
        Optional<String> sourceRevision,
        Instant createdAt,
        Optional<SpecificationVersionId> predecessor) {

    public SpecificationVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectId, "projectId");
        sequence = Objects.requireNonNull(sequence, "sequence");
        providerVersion = normalizeOptional(providerVersion, "providerVersion");
        sourceRevision = normalizeOptional(sourceRevision, "sourceRevision");
        Objects.requireNonNull(createdAt, "createdAt");
        predecessor = Objects.requireNonNull(predecessor, "predecessor");

        sequence.ifPresent(value -> {
            if (value <= 0) {
                throw new IllegalArgumentException("sequence must be greater than zero");
            }
        });

        predecessor.ifPresent(value -> {
            if (value.equals(id)) {
                throw new IllegalArgumentException("a specification version cannot be its own predecessor");
            }
        });
    }

    private static Optional<String> normalizeOptional(Optional<String> value, String name) {
        return Objects.requireNonNull(value, name)
                .map(String::trim)
                .filter(candidate -> !candidate.isEmpty());
    }
}
