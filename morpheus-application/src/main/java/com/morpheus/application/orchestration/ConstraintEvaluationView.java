package com.morpheus.application.orchestration;

import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.ConstraintApplicability;
import com.morpheus.domain.constraint.ConstraintEvaluation;
import com.morpheus.domain.constraint.ConstraintEvaluationState;
import com.morpheus.domain.constraint.ConstraintSatisfaction;
import com.morpheus.domain.constraint.ConstraintSeverity;

import java.util.List;
import java.util.Objects;

/** JSON-safe application projection of one explicit M16 constraint evaluation. */
public record ConstraintEvaluationView(
        String constraintId,
        String changeId,
        ChangeLifecycleState targetState,
        ConstraintEvaluationState state,
        ConstraintApplicability applicability,
        ConstraintSeverity severity,
        ConstraintSatisfaction satisfaction,
        String blockingMode,
        List<String> blockingTargets,
        String reason,
        List<String> supportingEvidenceIds,
        String sourceEvidenceId) {

    public ConstraintEvaluationView {
        constraintId = requireNonBlank(constraintId, "constraintId");
        changeId = requireNonBlank(changeId, "changeId");
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(applicability, "applicability");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(satisfaction, "satisfaction");
        blockingMode = requireNonBlank(blockingMode, "blockingMode");
        blockingTargets = List.copyOf(Objects.requireNonNull(blockingTargets, "blockingTargets"));
        reason = requireNonBlank(reason, "reason");
        supportingEvidenceIds = List.copyOf(Objects.requireNonNull(supportingEvidenceIds, "supportingEvidenceIds"));
        sourceEvidenceId = requireNonBlank(sourceEvidenceId, "sourceEvidenceId");
    }

    public static ConstraintEvaluationView from(ConstraintEvaluation evaluation) {
        Objects.requireNonNull(evaluation, "evaluation");
        return new ConstraintEvaluationView(
                evaluation.constraintId().toString(),
                evaluation.changeId().toString(),
                evaluation.targetState(),
                evaluation.state(),
                evaluation.applicability(),
                evaluation.severity(),
                evaluation.satisfaction(),
                evaluation.blockingPolicy().mode().name(),
                evaluation.blockingPolicy().lifecycleTargets().stream()
                        .map(Enum::name)
                        .sorted()
                        .toList(),
                evaluation.reason(),
                evaluation.supportingEvidenceIds().stream()
                        .map(Object::toString)
                        .sorted()
                        .toList(),
                evaluation.sourceEvidenceId().toString());
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
