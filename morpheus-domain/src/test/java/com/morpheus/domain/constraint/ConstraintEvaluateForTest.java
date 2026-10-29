package com.morpheus.domain.constraint;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConstraintEvaluateForTest {

    @Test
    void unknownApplicabilityNeverBecomesBlocked() {
        var evaluation = legacyConstraint().evaluateFor(ChangeLifecycleState.VERIFYING);

        assertEquals(ConstraintEvaluationState.UNKNOWN, evaluation.state());
    }

    @Test
    void warningViolationDoesNotBlockWhenPolicyIsNonBlocking() {
        Constraint constraint = explicit(
                ConstraintSeverity.WARNING,
                ConstraintSatisfaction.VIOLATED,
                ConstraintBlockingPolicy.nonBlocking());

        var evaluation = constraint.evaluateFor(ChangeLifecycleState.VERIFYING);

        assertEquals(ConstraintEvaluationState.NON_BLOCKING, evaluation.state());
    }

    @Test
    void criticalSeverityDoesNotBlockOutsideExplicitTarget() {
        Constraint constraint = explicit(
                ConstraintSeverity.CRITICAL,
                ConstraintSatisfaction.VIOLATED,
                ConstraintBlockingPolicy.blockWhenViolated(List.of(ChangeLifecycleState.COMPLETED)));

        var evaluation = constraint.evaluateFor(ChangeLifecycleState.VERIFYING);

        assertEquals(ConstraintEvaluationState.NON_BLOCKING, evaluation.state());
    }

    @Test
    void targetedSatisfiedConstraintDoesNotBlock() {
        Constraint constraint = explicit(
                ConstraintSeverity.CRITICAL,
                ConstraintSatisfaction.SATISFIED,
                ConstraintBlockingPolicy.blockWhenViolated(List.of(ChangeLifecycleState.VERIFYING)));

        var evaluation = constraint.evaluateFor(ChangeLifecycleState.VERIFYING);

        assertEquals(ConstraintEvaluationState.NON_BLOCKING, evaluation.state());
    }

    @Test
    void targetedUnknownSatisfactionRemainsUnknown() {
        Constraint constraint = new Constraint(
                ConstraintId.generate(),
                ChangeId.generate(),
                "Review must pass",
                ConstraintApplicability.APPLICABLE,
                ConstraintSeverity.CRITICAL,
                ConstraintSatisfaction.UNKNOWN,
                ConstraintBlockingPolicy.blockWhenViolated(List.of(ChangeLifecycleState.VERIFYING)),
                List.of(),
                provenance());

        var evaluation = constraint.evaluateFor(ChangeLifecycleState.VERIFYING);

        assertEquals(ConstraintEvaluationState.UNKNOWN, evaluation.state());
    }

    @Test
    void targetedViolatedConstraintBlocksWithEvidenceAndReason() {
        Constraint constraint = explicit(
                ConstraintSeverity.ERROR,
                ConstraintSatisfaction.VIOLATED,
                ConstraintBlockingPolicy.blockWhenViolated(List.of(ChangeLifecycleState.VERIFYING)));

        var evaluation = constraint.evaluateFor(ChangeLifecycleState.VERIFYING);

        assertEquals(ConstraintEvaluationState.BLOCKING, evaluation.state());
        assertEquals(constraint.supportingEvidenceIds(), evaluation.supportingEvidenceIds());
        assertEquals(constraint.provenance().evidenceId(), evaluation.sourceEvidenceId());
        assertEquals("Constraint is violated and explicitly blocks lifecycle state VERIFYING", evaluation.reason());
    }

    private Constraint legacyConstraint() {
        return Constraint.withUnknownSemantics(ConstraintId.generate(), ChangeId.generate(), "Constraint", provenance());
    }

    private Constraint explicit(
            ConstraintSeverity severity,
            ConstraintSatisfaction satisfaction,
            ConstraintBlockingPolicy policy) {
        return new Constraint(
                ConstraintId.generate(),
                ChangeId.generate(),
                "Review must pass",
                ConstraintApplicability.APPLICABLE,
                severity,
                satisfaction,
                policy,
                List.of(EvidenceId.generate()),
                provenance());
    }

    private Provenance provenance() {
        return new Provenance(
                new ProviderId("test"),
                Optional.empty(),
                SourceLocator.file("constraints.md"),
                Optional.empty(),
                Optional.empty(),
                EvidenceId.generate());
    }
}
