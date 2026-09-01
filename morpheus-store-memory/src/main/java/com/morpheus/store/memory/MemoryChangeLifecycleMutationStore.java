package com.morpheus.store.memory;

import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationAttempt;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationAuditRecord;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPersistenceResult;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPersistenceState;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationStore;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleOperationalState;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** In-memory reference implementation of M17 lifecycle CAS/idempotency/audit semantics. */
public final class MemoryChangeLifecycleMutationStore implements ChangeLifecycleMutationStore {
    private final SpecificationKnowledgeStore projects;
    private final Map<StateKey, ChangeLifecycleOperationalState> states = new HashMap<>();
    private final Map<IdempotencyKey, ChangeLifecycleMutationAuditRecord> byIdempotency = new HashMap<>();
    private final Map<ChangeLifecycleMutationId, ChangeLifecycleMutationAuditRecord> byMutationId = new HashMap<>();

    public MemoryChangeLifecycleMutationStore(SpecificationKnowledgeStore projects) {
        this.projects = Objects.requireNonNull(projects, "projects");
    }

    @Override
    public synchronized Optional<ChangeLifecycleOperationalState> findState(
            ProjectSpecificationId projectId,
            ChangeId changeId) {
        return Optional.ofNullable(states.get(new StateKey(projectId, changeId)));
    }

    @Override
    public synchronized Optional<ChangeLifecycleMutationAuditRecord> findByIdempotencyKey(
            ProjectSpecificationId projectId,
            ChangeLifecycleIdempotencyKey idempotencyKey) {
        return Optional.ofNullable(byIdempotency.get(new IdempotencyKey(projectId, idempotencyKey)));
    }

    @Override
    public synchronized List<ChangeLifecycleMutationAuditRecord> listAudit(
            ProjectSpecificationId projectId,
            ChangeId changeId) {
        return byMutationId.values().stream()
                .filter(item -> item.projectId().equals(projectId) && item.changeId().equals(changeId))
                .sorted((left, right) -> {
                    int revision = left.toRevision().compareTo(right.toRevision());
                    return revision != 0 ? revision : left.mutationId().compareTo(right.mutationId());
                })
                .toList();
    }

    @Override
    public synchronized ChangeLifecycleMutationPersistenceResult apply(ChangeLifecycleMutationAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        requireProject(attempt.projectId());
        IdempotencyKey idempotencyKey = new IdempotencyKey(attempt.projectId(), attempt.idempotencyKey());
        ChangeLifecycleMutationAuditRecord existing = byIdempotency.get(idempotencyKey);
        if (existing != null) {
            if (!existing.commandFingerprint().equals(attempt.commandFingerprint())) {
                return conflict(current(attempt), "Idempotency key already belongs to a different command");
            }
            return new ChangeLifecycleMutationPersistenceResult(
                    ChangeLifecycleMutationPersistenceState.ALREADY_APPLIED,
                    Optional.of(stateFrom(existing)),
                    Optional.of(existing),
                    "Idempotent lifecycle mutation was already applied");
        }
        ChangeLifecycleMutationAuditRecord sameMutationId = byMutationId.get(attempt.mutationId());
        if (sameMutationId != null) {
            return conflict(current(attempt), "Mutation id already exists with another idempotency key");
        }

        ChangeLifecycleOperationalState current = current(attempt);
        if (!current.revision().equals(attempt.expectedRevision())) {
            return conflict(Optional.of(current),
                    "Expected revision " + attempt.expectedRevision() + " does not match " + current.revision());
        }
        if (current.lifecycle().state() != attempt.fromState()) {
            return conflict(Optional.of(current),
                    "Expected lifecycle state " + attempt.fromState() + " does not match " + current.lifecycle().state());
        }

        ChangeLifecycle lifecycle = targetLifecycle(attempt);
        var nextRevision = current.revision().next();
        ChangeLifecycleOperationalState next = new ChangeLifecycleOperationalState(
                attempt.projectId(),
                lifecycle,
                nextRevision,
                Optional.of(attempt.appliedAt()),
                Optional.of(attempt.mutationId()));
        ChangeLifecycleMutationAuditRecord audit = new ChangeLifecycleMutationAuditRecord(
                attempt.mutationId(),
                attempt.idempotencyKey(),
                attempt.commandFingerprint(),
                attempt.projectId(),
                attempt.changeId(),
                attempt.fromState(),
                attempt.targetState(),
                attempt.targetAbandonmentReason(),
                current.revision(),
                nextRevision,
                attempt.actor(),
                attempt.providerId(),
                attempt.reason(),
                attempt.appliedAt());

        states.put(new StateKey(attempt.projectId(), attempt.changeId()), next);
        byIdempotency.put(idempotencyKey, audit);
        byMutationId.put(attempt.mutationId(), audit);
        return new ChangeLifecycleMutationPersistenceResult(
                ChangeLifecycleMutationPersistenceState.APPLIED,
                Optional.of(next),
                Optional.of(audit),
                "Lifecycle mutation applied");
    }

    private void requireProject(ProjectSpecificationId projectId) {
        if (projects.findProject(projectId).isEmpty()) {
            throw new KnowledgeStoreException("project not found for lifecycle mutation: " + projectId);
        }
    }

    private ChangeLifecycleOperationalState current(ChangeLifecycleMutationAttempt attempt) {
        return states.getOrDefault(
                new StateKey(attempt.projectId(), attempt.changeId()),
                ChangeLifecycleOperationalState.initial(attempt.projectId(), attempt.changeId()));
    }

    private ChangeLifecycleMutationPersistenceResult conflict(
            ChangeLifecycleOperationalState current,
            String reason) {
        return conflict(Optional.of(current), reason);
    }

    private ChangeLifecycleMutationPersistenceResult conflict(
            Optional<ChangeLifecycleOperationalState> current,
            String reason) {
        return new ChangeLifecycleMutationPersistenceResult(
                ChangeLifecycleMutationPersistenceState.CONFLICT,
                current,
                Optional.empty(),
                reason);
    }

    private ChangeLifecycleOperationalState stateFrom(ChangeLifecycleMutationAuditRecord audit) {
        ChangeLifecycle lifecycle = audit.targetState() == ChangeLifecycleState.ABANDONED
                ? ChangeLifecycle.abandoned(audit.changeId(), audit.targetAbandonmentReason().orElseThrow())
                : ChangeLifecycle.of(audit.changeId(), audit.targetState());
        return new ChangeLifecycleOperationalState(
                audit.projectId(),
                lifecycle,
                audit.toRevision(),
                Optional.of(audit.appliedAt()),
                Optional.of(audit.mutationId()));
    }

    private ChangeLifecycle targetLifecycle(ChangeLifecycleMutationAttempt attempt) {
        return attempt.targetState() == ChangeLifecycleState.ABANDONED
                ? ChangeLifecycle.abandoned(attempt.changeId(), attempt.targetAbandonmentReason().orElseThrow())
                : ChangeLifecycle.of(attempt.changeId(), attempt.targetState());
    }

    private record StateKey(ProjectSpecificationId projectId, ChangeId changeId) {
        private StateKey {
            Objects.requireNonNull(projectId, "projectId");
            Objects.requireNonNull(changeId, "changeId");
        }
    }

    private record IdempotencyKey(ProjectSpecificationId projectId, ChangeLifecycleIdempotencyKey key) {
        private IdempotencyKey {
            Objects.requireNonNull(projectId, "projectId");
            Objects.requireNonNull(key, "key");
        }
    }
}
