package com.morpheus.domain.constraint;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.evidence.EvidenceId;

import java.util.List;
import java.util.Objects;

/** Explainable provider-neutral evaluation of one constraint for one lifecycle target. */
public record ConstraintEvaluation(
        ConstraintId constraintId,
        ChangeId changeId,
        ChangeLifecycleState targetState,
        ConstraintEvaluationState state,
        ConstraintApplicability applicability,
        ConstraintSeverity severity,
        ConstraintSatisfaction satisfaction,
        ConstraintBlockingPolicy blockingPolicy,
        String reason,
        List<EvidenceId> supportingEvidenceIds,
        EvidenceId sourceEvidenceId) {

    public ConstraintEvaluation {
        Objects.requireNonNull(constraintId, "constraintId");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(applicability, "applicability");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(satisfaction, "satisfaction");
        Objects.requireNonNull(blockingPolicy, "blockingPolicy");
        Objects.requireNonNull(reason, "reason");
        reason = reason.trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        supportingEvidenceIds = List.copyOf(Objects.requireNonNull(supportingEvidenceIds, "supportingEvidenceIds"));
        Objects.requireNonNull(sourceEvidenceId, "sourceEvidenceId");
        if (state == ConstraintEvaluationState.BLOCKING
                && satisfaction != ConstraintSatisfaction.VIOLATED) {
            throw new IllegalArgumentException("BLOCKING evaluation requires VIOLATED satisfaction");
        }
    }
}
