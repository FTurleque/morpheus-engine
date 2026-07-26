package com.morpheus.application.lifecycle.mutation;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleRevision;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Mutable operational lifecycle state, deliberately separate from published snapshots. */
public record ChangeLifecycleOperationalState(
        ProjectSpecificationId projectId,
        ChangeLifecycle lifecycle,
        ChangeLifecycleRevision revision,
        Optional<Instant> updatedAt,
        Optional<ChangeLifecycleMutationId> lastMutationId) {

    public ChangeLifecycleOperationalState {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(revision, "revision");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        lastMutationId = Objects.requireNonNull(lastMutationId, "lastMutationId");
        if (revision.value() == 0 && (updatedAt.isPresent() || lastMutationId.isPresent())) {
            throw new IllegalArgumentException("revision 0 is a virtual initial state and cannot have mutation metadata");
        }
        if (revision.value() > 0 && (updatedAt.isEmpty() || lastMutationId.isEmpty())) {
            throw new IllegalArgumentException("persisted lifecycle state requires update metadata");
        }
    }

    public static ChangeLifecycleOperationalState initial(ProjectSpecificationId projectId, ChangeId changeId) {
        return new ChangeLifecycleOperationalState(
                projectId,
                ChangeLifecycle.of(changeId, com.morpheus.domain.change.lifecycle.ChangeLifecycleState.DRAFT),
                ChangeLifecycleRevision.initial(),
                Optional.empty(),
                Optional.empty());
    }
}
