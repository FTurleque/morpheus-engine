package com.morpheus.application.constraint;

import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintApplicability;
import com.morpheus.domain.constraint.ConstraintBlockingMode;
import com.morpheus.domain.constraint.ConstraintEvaluation;
import com.morpheus.domain.constraint.ConstraintEvaluationState;
import com.morpheus.domain.constraint.ConstraintSatisfaction;

import java.util.Objects;

/** Pure deterministic M16 evaluator. It never interprets constraint text or severity as executable policy. */
public final class ConstraintPolicyEvaluationService {

    public ConstraintEvaluation evaluate(Constraint constraint, ChangeLifecycleState targetState) {
        Objects.requireNonNull(constraint, "constraint");
        Objects.requireNonNull(targetState, "targetState");

        ConstraintEvaluationState state;
        String reason;

        if (constraint.applicability() == ConstraintApplicability.NOT_APPLICABLE) {
            state = ConstraintEvaluationState.NOT_APPLICABLE;
            reason = "Constraint is explicitly not applicable";
        } else if (constraint.applicability() == ConstraintApplicability.UNKNOWN) {
            state = ConstraintEvaluationState.UNKNOWN;
            reason = "Constraint applicability is unknown";
        } else if (constraint.blockingPolicy().mode() == ConstraintBlockingMode.UNKNOWN) {
            state = ConstraintEvaluationState.UNKNOWN;
            reason = "Constraint blocking policy is unknown";
        } else if (constraint.blockingPolicy().mode() == ConstraintBlockingMode.NON_BLOCKING) {
            state = ConstraintEvaluationState.NON_BLOCKING;
            reason = "Constraint policy is explicitly non-blocking";
        } else if (!constraint.blockingPolicy().targets(targetState)) {
            state = ConstraintEvaluationState.NON_BLOCKING;
            reason = "Constraint blocking policy does not target lifecycle state " + targetState;
        } else if (constraint.satisfaction() == ConstraintSatisfaction.UNKNOWN) {
            state = ConstraintEvaluationState.UNKNOWN;
            reason = "Constraint satisfaction is unknown for targeted blocking policy";
        } else if (constraint.satisfaction() == ConstraintSatisfaction.SATISFIED) {
            state = ConstraintEvaluationState.NON_BLOCKING;
            reason = "Constraint is satisfied";
        } else {
            state = ConstraintEvaluationState.BLOCKING;
            reason = "Constraint is violated and explicitly blocks lifecycle state " + targetState;
        }

        return new ConstraintEvaluation(
                constraint.id(),
                constraint.changeId(),
                targetState,
                state,
                constraint.applicability(),
                constraint.severity(),
                constraint.satisfaction(),
                constraint.blockingPolicy(),
                reason,
                constraint.supportingEvidenceIds(),
                constraint.provenance().evidenceId());
    }
}
