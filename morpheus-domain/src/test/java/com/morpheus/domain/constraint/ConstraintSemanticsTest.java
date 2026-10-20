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
    void aConstraintWithUnknownSemanticsIsUnknownOnEveryAxisAndCarriesWhatItWasGiven() {
        ConstraintId id = ConstraintId.generate();
        ChangeId changeId = ChangeId.generate();
        Provenance provenance = provenance();

        Constraint constraint = Constraint.withUnknownSemantics(id, changeId, "Security review required", provenance);

        assertEquals(ConstraintApplicability.UNKNOWN, constraint.applicability());
        assertEquals(ConstraintSeverity.UNKNOWN, constraint.severity());
        assertEquals(ConstraintSatisfaction.UNKNOWN, constraint.satisfaction());
        assertEquals(ConstraintBlockingMode.UNKNOWN, constraint.blockingPolicy().mode());
        assertTrue(constraint.supportingEvidenceIds().isEmpty());
        assertFalse(constraint.hasExplicitSemantics());
        assertEquals(
                new Constraint(
                        id,
                        changeId,
                        "Security review required",
                        ConstraintApplicability.UNKNOWN,
                        ConstraintSeverity.UNKNOWN,
                        ConstraintSatisfaction.UNKNOWN,
                        ConstraintBlockingPolicy.unknown(),
                        List.of(),
                        provenance),
                constraint);
    }

    @Test
    void blockWhenViolatedRequiresLifecycleTarget() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new ConstraintBlockingPolicy(
                ConstraintBlockingMode.BLOCK_WHEN_VIOLATED, List.of()));
        assertEquals("BLOCK_WHEN_VIOLATED requires at least one lifecycle target", failure.getMessage());
    }

    @Test
    void nonBlockingPolicyCannotSmuggleLifecycleTarget() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new ConstraintBlockingPolicy(
                ConstraintBlockingMode.NON_BLOCKING, List.of(ChangeLifecycleState.VERIFYING)));
        assertEquals("NON_BLOCKING cannot declare lifecycle targets", failure.getMessage());
    }

    @Test
    void unknownPolicyCannotSmuggleLifecycleTarget() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new ConstraintBlockingPolicy(
                ConstraintBlockingMode.UNKNOWN, List.of(ChangeLifecycleState.VERIFYING)));
        assertEquals("UNKNOWN cannot declare lifecycle targets", failure.getMessage());
    }

    @Test
    void explicitSatisfiedOrViolatedStateRequiresSupportingEvidence() {
        for (ConstraintSatisfaction satisfaction : List.of(
                ConstraintSatisfaction.SATISFIED,
                ConstraintSatisfaction.VIOLATED)) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new Constraint(
                    ConstraintId.generate(),
                    ChangeId.generate(),
                    "Constraint",
                    ConstraintApplicability.APPLICABLE,
                    ConstraintSeverity.WARNING,
                    satisfaction,
                    ConstraintBlockingPolicy.nonBlocking(),
                    List.of(),
                    provenance()));
            assertEquals("supporting evidence is required for constraint satisfaction state " + satisfaction, failure.getMessage());
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

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new Constraint(
                ConstraintId.generate(),
                ChangeId.generate(),
                "Constraint",
                ConstraintApplicability.APPLICABLE,
                ConstraintSeverity.ERROR,
                ConstraintSatisfaction.VIOLATED,
                ConstraintBlockingPolicy.blockWhenViolated(List.of(ChangeLifecycleState.VERIFYING)),
                List.of(first, first),
                provenance()));
        assertTrue(failure.getMessage().contains("duplicate supporting evidence identity"), failure.getMessage());
    }

    @Test
    void notApplicableConstraintCannotDeclareBlockingPolicy() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new Constraint(
                ConstraintId.generate(),
                ChangeId.generate(),
                "Constraint",
                ConstraintApplicability.NOT_APPLICABLE,
                ConstraintSeverity.CRITICAL,
                ConstraintSatisfaction.UNKNOWN,
                ConstraintBlockingPolicy.blockWhenViolated(List.of(ChangeLifecycleState.VERIFYING)),
                List.of(),
                provenance()));
        assertEquals("NOT_APPLICABLE constraint cannot declare a blocking policy", failure.getMessage());
    }

    @Test
    void blockingEvaluationRequiresViolatedSatisfaction() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new ConstraintEvaluation(
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
        assertEquals("BLOCKING evaluation requires VIOLATED satisfaction", failure.getMessage());
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
