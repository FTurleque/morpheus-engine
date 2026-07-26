package com.morpheus.application.lifecycle.mutation;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleRevision;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeLifecycleMutationContractTest {
    private static final Instant T0 = Instant.parse("2026-07-26T16:00:00Z");

    @Test
    void virtualInitialStateIsDraftRevisionZeroWithoutMutationMetadata() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        ChangeId changeId = ChangeId.generate();
        ChangeLifecycleOperationalState state = ChangeLifecycleOperationalState.initial(projectId, changeId);

        assertEquals(ChangeLifecycleState.DRAFT, state.lifecycle().state());
        assertEquals(0, state.revision().value());
        assertTrue(state.updatedAt().isEmpty());
        assertTrue(state.lastMutationId().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleOperationalState(
                projectId,
                ChangeLifecycle.of(changeId, ChangeLifecycleState.DRAFT),
                ChangeLifecycleRevision.initial(),
                Optional.of(T0),
                Optional.of(ChangeLifecycleMutationId.generate())));
    }

    @Test
    void appliedResultRequiresStateAndExactlyOneAppliedAuditShape() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        ChangeId changeId = ChangeId.generate();
        ChangeLifecycleMutationId mutationId = ChangeLifecycleMutationId.generate();
        ChangeLifecycleIdempotencyKey key = new ChangeLifecycleIdempotencyKey("m17-app-test");
        ChangeLifecycleOperationalState state = new ChangeLifecycleOperationalState(
                projectId,
                ChangeLifecycle.of(changeId, ChangeLifecycleState.PROPOSED),
                new ChangeLifecycleRevision(1),
                Optional.of(T0),
                Optional.of(mutationId));
        ChangeLifecycleMutationAuditRecord audit = new ChangeLifecycleMutationAuditRecord(
                mutationId,
                key,
                "fingerprint",
                projectId,
                changeId,
                ChangeLifecycleState.DRAFT,
                ChangeLifecycleState.PROPOSED,
                Optional.empty(),
                ChangeLifecycleRevision.initial(),
                new ChangeLifecycleRevision(1),
                "test",
                new ProviderId("fixture"),
                "allowed",
                T0);

        var result = new ChangeLifecycleMutationResult(
                ChangeLifecycleMutationResultState.APPLIED,
                Optional.of(state),
                Optional.of(audit),
                "applied");
        ChangeLifecycleMutationResultView view = ChangeLifecycleMutationResultView.from(result);
        assertEquals("APPLIED", view.state());
        assertEquals(1, view.lifecycleState().orElseThrow().revision());
        assertEquals("fixture", view.audit().orElseThrow().providerId());

        assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleMutationResult(
                ChangeLifecycleMutationResultState.APPLIED,
                Optional.empty(),
                Optional.empty(),
                "invalid"));
        assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleMutationResult(
                ChangeLifecycleMutationResultState.CONFLICT,
                Optional.of(state),
                Optional.of(audit),
                "invalid"));
    }

    @Test
    void auditRevisionMustAdvanceExactlyOnce() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        ChangeId changeId = ChangeId.generate();
        assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleMutationAuditRecord(
                ChangeLifecycleMutationId.generate(),
                new ChangeLifecycleIdempotencyKey("bad-audit"),
                "fingerprint",
                projectId,
                changeId,
                ChangeLifecycleState.DRAFT,
                ChangeLifecycleState.PROPOSED,
                Optional.empty(),
                ChangeLifecycleRevision.initial(),
                new ChangeLifecycleRevision(2),
                "test",
                new ProviderId("fixture"),
                "invalid",
                T0));
    }
}
