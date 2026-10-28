package com.morpheus.application.lifecycle.mutation;

import com.morpheus.application.query.PageRequest;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.Optional;

/** Persistence port for operational lifecycle state, CAS, idempotency and append-only audit. */
public interface ChangeLifecycleMutationStore {
    Optional<ChangeLifecycleOperationalState> findState(ProjectSpecificationId projectId, ChangeId changeId);

    Optional<ChangeLifecycleMutationAuditRecord> findByIdempotencyKey(
            ProjectSpecificationId projectId,
            ChangeLifecycleIdempotencyKey idempotencyKey);

    /**
     * One page of the change's audit, by revision then mutation identity, with the number of records the change has.
     * The journal only grows: the port offers no read of it whole.
     */
    ChangeLifecycleMutationAuditPage listAudit(ProjectSpecificationId projectId, ChangeId changeId, PageRequest pageRequest);

    ChangeLifecycleMutationPersistenceResult apply(ChangeLifecycleMutationAttempt attempt);
}
