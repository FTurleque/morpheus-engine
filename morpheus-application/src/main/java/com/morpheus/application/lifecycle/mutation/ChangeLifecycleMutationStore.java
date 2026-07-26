package com.morpheus.application.lifecycle.mutation;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.List;
import java.util.Optional;

/** Persistence port for operational lifecycle state, CAS, idempotency and append-only audit. */
public interface ChangeLifecycleMutationStore {
    Optional<ChangeLifecycleOperationalState> findState(ProjectSpecificationId projectId, ChangeId changeId);

    Optional<ChangeLifecycleMutationAuditRecord> findByIdempotencyKey(
            ProjectSpecificationId projectId,
            ChangeLifecycleIdempotencyKey idempotencyKey);

    List<ChangeLifecycleMutationAuditRecord> listAudit(ProjectSpecificationId projectId, ChangeId changeId);

    ChangeLifecycleMutationPersistenceResult apply(ChangeLifecycleMutationAttempt attempt);
}
