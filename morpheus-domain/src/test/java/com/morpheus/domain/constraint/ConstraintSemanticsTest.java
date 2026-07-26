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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintSemanticsTest {

    @Test
    void legacyConstraintKeepsSemanticsUnknown() {
        Constraint constraint = new Constraint(
                ConstraintId.generate(), ChangeId.generate(), "Security review required", provenance());

        assertEquals(ConstraintApplicability.UNKNOWN, constraint.applicability());
        assertEquals(ConstraintSeverity.UNKNOWN, constraint.severity());
        assertEquals(ConstraintSatisfaction.UNKNOWN, constraint.satisfaction());
        assertEquals(ConstraintBlockingMode.UNKNOWN, constraint.blockingPolicy().mode());
        assertTrue(constraint.supportingEvidenceIds().isEmpty());
        assertFalse(constraint.hasExplicitSemantics());
    }

    @Test
    void blockWhenViolatedRequiresLifecycleTarget() {
        assertThrows(IllegalArgumentException.class, () -> new ConstraintBlockingPolicy(
                ConstraintBlockingMode.BLOCK_WHEN_VIOLATED, List.of()));
    }

    @Test
    void nonBlockingPolicyCannotSmuggleLifecycleTarget() {
        assertThrows(IllegalArgumentException.class, () -> new ConstraintBlockingPolicy(
                ConstraintBlockingMode.NON_BLOCKING, List.of(ChangeLifecycleState.VERIFYING)));
    }

    @Test
    void unknownPolicyCannotSmuggleLifecycleTarget() {
        assertThrows(IllegalArgumentException.class, () -> new ConstraintBlockingPolicy(
                ConstraintBlockingMode.UNKNOWN, List.of(ChangeLifecycleState.VERIFYING)));
    }

    @Test
    void explicitSatisfiedOrViolatedStateRequiresSupportingEvidence() {
        for (ConstraintSatisfaction satisfaction : List.of(
                ConstraintSatisfaction.SATISFIED,
                ConstraintSatisfaction.VIOLATED)) {
            assertThrows(IllegalArgumentException.class, () -> new Constraint(
                    ConstraintId.generate(),
                    ChangeId.generate(),
                    "Constraint",
                    ConstraintApplicability.APPLICABLE,
                    ConstraintSeverity.WARNING,
                    satisfaction,
                    ConstraintBlockingPolicy.nonBlocking(),
                    List.of(),
                    provenance()));
        }
    }

    @Test
    void canonicalizesSupportingEvidenceAndRejectsDuplicates() {
        EvidenceId first = EvidenceId.generate();
        EvidenceId second = EvidenceId.generate();
        Constraint constraint = new Constraint(
                ConstraintId.generate(),
                ChangeId.generate(),
                "Constraint",
                ConstraintApplicability.APPLICABLE,
                ConstraintSeverity.ERROR,
                ConstraintSatisfaction.VIOLATED,
                ConstraintBlockingPolicy.blockWhenViolated(List.of(ChangeLifecycleState.VERIFYING)),
                List.of(second, first),
                provenance());

        assertEquals(List.of(first, second).stream().sorted().toList(), constraint.supportingEvidenceIds());
        assertTrue(constraint.hasExplicitSemantics());

        assertThrows(IllegalArgumentException.class, () -> new Constraint(
                ConstraintId.generate(),
                ChangeId.generate(),
                "Constraint",
                ConstraintApplicability.APPLICABLE,
                ConstraintSeverity.ERROR,
                ConstraintSatisfaction.VIOLATED,
                ConstraintBlockingPolicy.blockWhenViolated(List.of(ChangeLifecycleState.VERIFYING)),
                List.of(first, first),
                provenance()));
    }

    @Test
    void notApplicableConstraintCannotDeclareBlockingPolicy() {
        assertThrows(IllegalArgumentException.class, () -> new Constraint(
                ConstraintId.generate(),
                ChangeId.generate(),
                "Constraint",
                ConstraintApplicability.NOT_APPLICABLE,
                ConstraintSeverity.CRITICAL,
                ConstraintSatisfaction.UNKNOWN,
                ConstraintBlockingPolicy.blockWhenViolated(List.of(ChangeLifecycleState.VERIFYING)),
                List.of(),
                provenance()));
    }

    @Test
    void blockingEvaluationRequiresViolatedSatisfaction() {
        assertThrows(IllegalArgumentException.class, () -> new ConstraintEvaluation(
                ConstraintId.generate(),
                ChangeId.generate(),
                ChangeLifecycleState.VERIFYING,
                ConstraintEvaluationState.BLOCKING,
                ConstraintApplicability.APPLICABLE,
                ConstraintSeverity.CRITICAL,
                ConstraintSatisfaction.UNKNOWN,
                ConstraintBlockingPolicy.blockWhenViolated(List.of(ChangeLifecycleState.VERIFYING)),
                "invalid",
                List.of(),
                EvidenceId.generate()));
    }

    private static Provenance provenance() {
        return new Provenance(
                new ProviderId("test"),
                Optional.empty(),
                SourceLocator.file("constraints.md"),
                Optional.empty(),
                Optional.empty(),
                EvidenceId.generate());
    }
}
