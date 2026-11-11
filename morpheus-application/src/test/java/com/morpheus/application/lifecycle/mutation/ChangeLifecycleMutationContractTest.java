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
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

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
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleOperationalState(
                projectId,
                ChangeLifecycle.of(changeId, ChangeLifecycleState.DRAFT),
                ChangeLifecycleRevision.initial(),
                Optional.of(T0),
                Optional.of(ChangeLifecycleMutationId.generate())));
        assertEquals("revision 0 is a virtual initial state and cannot have mutation metadata", failure.getMessage());
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

        IllegalArgumentException appliedWithoutEvidence = assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleMutationResult(
                ChangeLifecycleMutationResultState.APPLIED,
                Optional.empty(),
                Optional.empty(),
                "invalid"));
        assertEquals("APPLIED requires lifecycle state and audit", appliedWithoutEvidence.getMessage());
        IllegalArgumentException conflictWithAudit = assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleMutationResult(
                ChangeLifecycleMutationResultState.CONFLICT,
                Optional.of(state),
                Optional.of(audit),
                "invalid"));
        assertEquals("CONFLICT must not fabricate an applied audit record", conflictWithAudit.getMessage());
    }

    @Test
    void auditRevisionMustAdvanceExactlyOnce() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        ChangeId changeId = ChangeId.generate();
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleMutationAuditRecord(
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
        assertEquals("audit toRevision must equal fromRevision + 1", failure.getMessage());
    }

    @Test
    void appliedAbandonmentPersistenceRequiresExplicitReason() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        ChangeId changeId = ChangeId.generate();
        ChangeLifecycleMutationId mutationId = ChangeLifecycleMutationId.generate();
        ChangeLifecycleIdempotencyKey key = new ChangeLifecycleIdempotencyKey("abandoned-missing-reason");
        ProviderId providerId = new ProviderId("fixture");

        IllegalArgumentException attemptFailure = assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleMutationAttempt(
                mutationId,
                key,
                "fingerprint",
                projectId,
                changeId,
                ChangeLifecycleState.DRAFT,
                ChangeLifecycleState.ABANDONED,
                Optional.empty(),
                ChangeLifecycleRevision.initial(),
                "test",
                providerId,
                "allowed",
                T0));
        assertEquals("ABANDONED persistence attempt requires an abandonment reason", attemptFailure.getMessage());

        IllegalArgumentException auditFailure = assertThrows(IllegalArgumentException.class, () -> new ChangeLifecycleMutationAuditRecord(
                mutationId,
                key,
                "fingerprint",
                projectId,
                changeId,
                ChangeLifecycleState.DRAFT,
                ChangeLifecycleState.ABANDONED,
                Optional.empty(),
                ChangeLifecycleRevision.initial(),
                new ChangeLifecycleRevision(1),
                "test",
                providerId,
                "allowed",
                T0));
        assertEquals("ABANDONED audit record requires an abandonment reason", auditFailure.getMessage());
    }

    /**
     * The partition is spelled out here independently of the {@code switch} that implements it, so a state moved
     * to the wrong side is a failing test and not a reviewed diff.
     */
    @Test
    void onlyAppliedAndAlreadyAppliedAreSuccessful() {
        Set<ChangeLifecycleMutationResultState> successful = EnumSet.noneOf(ChangeLifecycleMutationResultState.class);
        for (ChangeLifecycleMutationResultState state : ChangeLifecycleMutationResultState.values()) {
            if (state.successful()) {
                successful.add(state);
            }
        }

        assertEquals(
                EnumSet.of(ChangeLifecycleMutationResultState.APPLIED, ChangeLifecycleMutationResultState.ALREADY_APPLIED),
                successful);
    }
}
