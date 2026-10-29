package com.morpheus.domain.constraint;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.morpheus.domain.change.lifecycle.ChangeLifecycleState.IMPLEMENTING;
import static com.morpheus.domain.change.lifecycle.ChangeLifecycleState.VERIFYING;
import static com.morpheus.domain.constraint.ConstraintApplicability.APPLICABLE;
import static com.morpheus.domain.constraint.ConstraintApplicability.NOT_APPLICABLE;
import static com.morpheus.domain.constraint.ConstraintBlockingMode.BLOCK_WHEN_VIOLATED;
import static com.morpheus.domain.constraint.ConstraintBlockingMode.NON_BLOCKING;
import static com.morpheus.domain.constraint.ConstraintSatisfaction.SATISFIED;
import static com.morpheus.domain.constraint.ConstraintSatisfaction.VIOLATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a constraint blocks a lifecycle state, over every valid combination of applicability, blocking mode,
 * satisfaction and a targeted or untargeted state. The table holds the state, the reason text that leaves the product,
 * and the two answers the orchestration state reads. A reason text in it is a public answer: the CLI, the MCP tools
 * and the HTTP routes return it.
 *
 * <p>A policy that blocks when violated targets {@code VERIFYING}; {@code IMPLEMENTING} is the untargeted state. A
 * not-applicable constraint cannot declare a blocking policy, so those three combinations do not exist.</p>
 */
class ConstraintBlockingDecisionTest {

    private static final ConstraintEvaluationState UNKNOWN_STATE = ConstraintEvaluationState.UNKNOWN;

    private static final List<Row> TABLE = List.of(
            new Row(APPLICABLE, NON_BLOCKING, SATISFIED, VERIFYING,
                    ConstraintEvaluationState.NON_BLOCKING, "Constraint policy is explicitly non-blocking",
                    true, false),
            new Row(APPLICABLE, NON_BLOCKING, SATISFIED, IMPLEMENTING,
                    ConstraintEvaluationState.NON_BLOCKING, "Constraint policy is explicitly non-blocking",
                    true, false),
            new Row(APPLICABLE, NON_BLOCKING, VIOLATED, VERIFYING,
                    ConstraintEvaluationState.NON_BLOCKING, "Constraint policy is explicitly non-blocking",
                    true, false),
            new Row(APPLICABLE, NON_BLOCKING, VIOLATED, IMPLEMENTING,
                    ConstraintEvaluationState.NON_BLOCKING, "Constraint policy is explicitly non-blocking",
                    true, false),
            new Row(APPLICABLE, NON_BLOCKING, ConstraintSatisfaction.UNKNOWN, VERIFYING,
                    ConstraintEvaluationState.NON_BLOCKING, "Constraint policy is explicitly non-blocking",
                    true, false),
            new Row(APPLICABLE, NON_BLOCKING, ConstraintSatisfaction.UNKNOWN, IMPLEMENTING,
                    ConstraintEvaluationState.NON_BLOCKING, "Constraint policy is explicitly non-blocking",
                    true, false),
            new Row(APPLICABLE, BLOCK_WHEN_VIOLATED, SATISFIED, VERIFYING,
                    ConstraintEvaluationState.NON_BLOCKING, "Constraint is satisfied", true, false),
            new Row(APPLICABLE, BLOCK_WHEN_VIOLATED, SATISFIED, IMPLEMENTING,
                    ConstraintEvaluationState.NON_BLOCKING, "Constraint blocking policy does not target lifecycle state IMPLEMENTING",
                    true, false),
            new Row(APPLICABLE, BLOCK_WHEN_VIOLATED, VIOLATED, VERIFYING,
                    ConstraintEvaluationState.BLOCKING, "Constraint is violated and explicitly blocks lifecycle state VERIFYING",
                    true, true),
            new Row(APPLICABLE, BLOCK_WHEN_VIOLATED, VIOLATED, IMPLEMENTING,
                    ConstraintEvaluationState.NON_BLOCKING, "Constraint blocking policy does not target lifecycle state IMPLEMENTING",
                    true, true),
            new Row(APPLICABLE, BLOCK_WHEN_VIOLATED, ConstraintSatisfaction.UNKNOWN, VERIFYING,
                    UNKNOWN_STATE, "Constraint satisfaction is unknown for targeted blocking policy", false, false),
            new Row(APPLICABLE, BLOCK_WHEN_VIOLATED, ConstraintSatisfaction.UNKNOWN, IMPLEMENTING,
                    ConstraintEvaluationState.NON_BLOCKING, "Constraint blocking policy does not target lifecycle state IMPLEMENTING",
                    false, false),
            new Row(APPLICABLE, ConstraintBlockingMode.UNKNOWN, SATISFIED, VERIFYING,
                    UNKNOWN_STATE, "Constraint blocking policy is unknown", false, false),
            new Row(APPLICABLE, ConstraintBlockingMode.UNKNOWN, SATISFIED, IMPLEMENTING,
                    UNKNOWN_STATE, "Constraint blocking policy is unknown", false, false),
            new Row(APPLICABLE, ConstraintBlockingMode.UNKNOWN, VIOLATED, VERIFYING,
                    UNKNOWN_STATE, "Constraint blocking policy is unknown", false, false),
            new Row(APPLICABLE, ConstraintBlockingMode.UNKNOWN, VIOLATED, IMPLEMENTING,
                    UNKNOWN_STATE, "Constraint blocking policy is unknown", false, false),
            new Row(APPLICABLE, ConstraintBlockingMode.UNKNOWN, ConstraintSatisfaction.UNKNOWN, VERIFYING,
                    UNKNOWN_STATE, "Constraint blocking policy is unknown", false, false),
            new Row(APPLICABLE, ConstraintBlockingMode.UNKNOWN, ConstraintSatisfaction.UNKNOWN, IMPLEMENTING,
                    UNKNOWN_STATE, "Constraint blocking policy is unknown", false, false),
            new Row(NOT_APPLICABLE, NON_BLOCKING, SATISFIED, VERIFYING,
                    ConstraintEvaluationState.NOT_APPLICABLE, "Constraint is explicitly not applicable", true, false),
            new Row(NOT_APPLICABLE, NON_BLOCKING, SATISFIED, IMPLEMENTING,
                    ConstraintEvaluationState.NOT_APPLICABLE, "Constraint is explicitly not applicable", true, false),
            new Row(NOT_APPLICABLE, NON_BLOCKING, VIOLATED, VERIFYING,
                    ConstraintEvaluationState.NOT_APPLICABLE, "Constraint is explicitly not applicable", true, false),
            new Row(NOT_APPLICABLE, NON_BLOCKING, VIOLATED, IMPLEMENTING,
                    ConstraintEvaluationState.NOT_APPLICABLE, "Constraint is explicitly not applicable", true, false),
            new Row(NOT_APPLICABLE, NON_BLOCKING, ConstraintSatisfaction.UNKNOWN, VERIFYING,
                    ConstraintEvaluationState.NOT_APPLICABLE, "Constraint is explicitly not applicable", true, false),
            new Row(NOT_APPLICABLE, NON_BLOCKING, ConstraintSatisfaction.UNKNOWN, IMPLEMENTING,
                    ConstraintEvaluationState.NOT_APPLICABLE, "Constraint is explicitly not applicable", true, false),
            new Row(NOT_APPLICABLE, ConstraintBlockingMode.UNKNOWN, SATISFIED, VERIFYING,
                    ConstraintEvaluationState.NOT_APPLICABLE, "Constraint is explicitly not applicable", true, false),
            new Row(NOT_APPLICABLE, ConstraintBlockingMode.UNKNOWN, SATISFIED, IMPLEMENTING,
                    ConstraintEvaluationState.NOT_APPLICABLE, "Constraint is explicitly not applicable", true, false),
            new Row(NOT_APPLICABLE, ConstraintBlockingMode.UNKNOWN, VIOLATED, VERIFYING,
                    ConstraintEvaluationState.NOT_APPLICABLE, "Constraint is explicitly not applicable", true, false),
            new Row(NOT_APPLICABLE, ConstraintBlockingMode.UNKNOWN, VIOLATED, IMPLEMENTING,
                    ConstraintEvaluationState.NOT_APPLICABLE, "Constraint is explicitly not applicable", true, false),
            new Row(NOT_APPLICABLE, ConstraintBlockingMode.UNKNOWN, ConstraintSatisfaction.UNKNOWN, VERIFYING,
                    ConstraintEvaluationState.NOT_APPLICABLE, "Constraint is explicitly not applicable", true, false),
            new Row(NOT_APPLICABLE, ConstraintBlockingMode.UNKNOWN, ConstraintSatisfaction.UNKNOWN, IMPLEMENTING,
                    ConstraintEvaluationState.NOT_APPLICABLE, "Constraint is explicitly not applicable", true, false),
            new Row(ConstraintApplicability.UNKNOWN, NON_BLOCKING, SATISFIED, VERIFYING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, NON_BLOCKING, SATISFIED, IMPLEMENTING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, NON_BLOCKING, VIOLATED, VERIFYING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, NON_BLOCKING, VIOLATED, IMPLEMENTING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, NON_BLOCKING, ConstraintSatisfaction.UNKNOWN, VERIFYING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, NON_BLOCKING, ConstraintSatisfaction.UNKNOWN, IMPLEMENTING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, BLOCK_WHEN_VIOLATED, SATISFIED, VERIFYING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, BLOCK_WHEN_VIOLATED, SATISFIED, IMPLEMENTING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, BLOCK_WHEN_VIOLATED, VIOLATED, VERIFYING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, BLOCK_WHEN_VIOLATED, VIOLATED, IMPLEMENTING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, BLOCK_WHEN_VIOLATED, ConstraintSatisfaction.UNKNOWN, VERIFYING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, BLOCK_WHEN_VIOLATED, ConstraintSatisfaction.UNKNOWN, IMPLEMENTING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, ConstraintBlockingMode.UNKNOWN, SATISFIED, VERIFYING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, ConstraintBlockingMode.UNKNOWN, SATISFIED, IMPLEMENTING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, ConstraintBlockingMode.UNKNOWN, VIOLATED, VERIFYING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, ConstraintBlockingMode.UNKNOWN, VIOLATED, IMPLEMENTING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, ConstraintBlockingMode.UNKNOWN, ConstraintSatisfaction.UNKNOWN, VERIFYING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false),
            new Row(ConstraintApplicability.UNKNOWN, ConstraintBlockingMode.UNKNOWN, ConstraintSatisfaction.UNKNOWN, IMPLEMENTING,
                    UNKNOWN_STATE, "Constraint applicability is unknown", false, false));

    @Test
    void everyCombinationIsDecidedAsTheTableSays() {
        assertEquals(48, TABLE.size(), "24 valid constraints, each read for a targeted and an untargeted state");
        for (Row row : TABLE) {
            Constraint constraint = constraint(row.applicability(), row.mode(), row.satisfaction());
            ConstraintEvaluation evaluation = constraint.evaluateFor(row.target());
            assertEquals(row.state(), evaluation.state(), row::toString);
            assertEquals(row.reason(), evaluation.reason(), row::toString);
            assertEquals(row.known(), constraint.hasKnownBlocking(), row::toString);
            assertEquals(row.counted(), constraint.isViolatedExplicitBlockingPolicy(), row::toString);
        }
    }

    @Test
    void anUnknownInputTheDecisionNeededIsNeverBlocking() {
        int needed = 0;
        for (Row row : TABLE) {
            boolean targeted = row.mode() == BLOCK_WHEN_VIOLATED && row.target() == VERIFYING;
            boolean neededAnUnknown = row.applicability() == ConstraintApplicability.UNKNOWN
                    || row.mode() == ConstraintBlockingMode.UNKNOWN && row.applicability() == APPLICABLE
                    || targeted && row.applicability() == APPLICABLE
                            && row.satisfaction() == ConstraintSatisfaction.UNKNOWN;
            if (neededAnUnknown) {
                needed++;
                Constraint constraint = constraint(row.applicability(), row.mode(), row.satisfaction());
                assertEquals(UNKNOWN_STATE, constraint.evaluateFor(row.target()).state(), row::toString);
            }
        }
        assertEquals(25, needed, "the rows whose decision needed an unknown input");
    }

    @Test
    void aConstraintWhoseBlockingIsKnownHasNoUnknownEvaluationForAnyState() {
        for (Constraint constraint : everyValidConstraint()) {
            if (!constraint.hasKnownBlocking()) continue;
            for (ChangeLifecycleState state : ChangeLifecycleState.values()) {
                assertNotEquals(UNKNOWN_STATE, constraint.evaluateFor(state).state(),
                        () -> constraint + " for " + state);
            }
        }
    }

    @Test
    void aConstraintThatBlocksSomeStateIsCountedAsAViolatedExplicitBlockingPolicy() {
        int blocking = 0;
        for (Constraint constraint : everyValidConstraint()) {
            for (ChangeLifecycleState state : ChangeLifecycleState.values()) {
                if (constraint.evaluateFor(state).state() == ConstraintEvaluationState.BLOCKING) {
                    blocking++;
                    assertTrue(constraint.isViolatedExplicitBlockingPolicy(), () -> constraint + " for " + state);
                }
            }
        }
        assertTrue(blocking > 0, "the combinations must include a blocking one, or the property holds of nothing");
    }

    @Test
    void applicabilityAndSeverityAreKnownOnlyWhenBothAreStated() {
        assertTrue(constraint(APPLICABLE, NON_BLOCKING, SATISFIED).hasKnownApplicabilityAndSeverity());
        assertFalse(constraint(ConstraintApplicability.UNKNOWN, NON_BLOCKING, SATISFIED)
                .hasKnownApplicabilityAndSeverity());
        Constraint unknownSeverity = new Constraint(ConstraintId.generate(), ChangeId.generate(), "Review must pass",
                APPLICABLE, ConstraintSeverity.UNKNOWN, SATISFIED, ConstraintBlockingPolicy.nonBlocking(),
                List.of(EvidenceId.generate()), provenance());
        assertFalse(unknownSeverity.hasKnownApplicabilityAndSeverity());
    }

    private static List<Constraint> everyValidConstraint() {
        List<Constraint> constraints = new ArrayList<>();
        for (ConstraintApplicability applicability : ConstraintApplicability.values()) {
            for (ConstraintBlockingMode mode : ConstraintBlockingMode.values()) {
                if (applicability == NOT_APPLICABLE && mode == BLOCK_WHEN_VIOLATED) continue;
                for (ConstraintSatisfaction satisfaction : ConstraintSatisfaction.values()) {
                    constraints.add(constraint(applicability, mode, satisfaction));
                }
            }
        }
        assertEquals(24, constraints.size());
        return constraints;
    }

    private static Constraint constraint(
            ConstraintApplicability applicability, ConstraintBlockingMode mode, ConstraintSatisfaction satisfaction) {
        ConstraintBlockingPolicy policy = switch (mode) {
            case UNKNOWN -> ConstraintBlockingPolicy.unknown();
            case NON_BLOCKING -> ConstraintBlockingPolicy.nonBlocking();
            case BLOCK_WHEN_VIOLATED -> ConstraintBlockingPolicy.blockWhenViolated(List.of(VERIFYING));
        };
        List<EvidenceId> evidence = satisfaction == ConstraintSatisfaction.UNKNOWN
                ? List.of()
                : List.of(EvidenceId.generate());
        return new Constraint(ConstraintId.generate(), ChangeId.generate(), "Review must pass", applicability,
                ConstraintSeverity.WARNING, satisfaction, policy, evidence, provenance());
    }

    private static Provenance provenance() {
        return new Provenance(new ProviderId("test"), Optional.empty(), SourceLocator.file("constraints.md"),
                Optional.empty(), Optional.empty(), EvidenceId.generate());
    }


    private record Row(
            ConstraintApplicability applicability, ConstraintBlockingMode mode, ConstraintSatisfaction satisfaction,
            ChangeLifecycleState target, ConstraintEvaluationState state, String reason, boolean known,
            boolean counted) {
    }
}
