package com.morpheus.domain.constraint;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.provenance.Provenance;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Provider-neutral constraint attached to a normalized change proposal with explicit M16 semantics. */
public record Constraint(
        ConstraintId id,
        ChangeId changeId,
        String statement,
        ConstraintApplicability applicability,
        ConstraintSeverity severity,
        ConstraintSatisfaction satisfaction,
        ConstraintBlockingPolicy blockingPolicy,
        List<EvidenceId> supportingEvidenceIds,
        Provenance provenance) {

    public Constraint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(changeId, "changeId");
        statement = requireNonBlank(statement, "statement");
        Objects.requireNonNull(applicability, "applicability");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(satisfaction, "satisfaction");
        Objects.requireNonNull(blockingPolicy, "blockingPolicy");
        supportingEvidenceIds = canonicalEvidenceIds(supportingEvidenceIds);
        Objects.requireNonNull(provenance, "provenance");

        if (satisfaction != ConstraintSatisfaction.UNKNOWN && supportingEvidenceIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "supporting evidence is required for constraint satisfaction state " + satisfaction);
        }
        if (applicability == ConstraintApplicability.NOT_APPLICABLE
                && blockingPolicy.mode() == ConstraintBlockingMode.BLOCK_WHEN_VIOLATED) {
            throw new IllegalArgumentException("NOT_APPLICABLE constraint cannot declare a blocking policy");
        }
    }

    /**
     * A constraint of which the source states only the text: applicability, severity, satisfaction and blocking
     * policy are unknown and no evidence supports it. Nothing is inferred from the statement.
     */
    public static Constraint withUnknownSemantics(
            ConstraintId id,
            ChangeId changeId,
            String statement,
            Provenance provenance) {
        return new Constraint(
                id,
                changeId,
                statement,
                ConstraintApplicability.UNKNOWN,
                ConstraintSeverity.UNKNOWN,
                ConstraintSatisfaction.UNKNOWN,
                ConstraintBlockingPolicy.unknown(),
                List.of(),
                provenance);
    }

    public boolean hasExplicitSemantics() {
        return applicability != ConstraintApplicability.UNKNOWN
                || severity != ConstraintSeverity.UNKNOWN
                || satisfaction != ConstraintSatisfaction.UNKNOWN
                || blockingPolicy.mode() != ConstraintBlockingMode.UNKNOWN;
    }

    /** Whether the source states both the applicability and the severity of this constraint. */
    public boolean hasKnownApplicabilityAndSeverity() {
        return applicability != ConstraintApplicability.UNKNOWN && severity != ConstraintSeverity.UNKNOWN;
    }

    /**
     * Whether this constraint blocks the target state, and why. {@code UNKNOWN} is never decided as blocking: an unknown
     * applicability, an unknown blocking mode, or an unknown satisfaction for a targeted state leaves the evaluation
     * unknown (ADR-0119, ADR-0093). Severity plays no part.
     */
    public ConstraintEvaluation evaluateFor(ChangeLifecycleState targetState) {
        Objects.requireNonNull(targetState, "targetState");
        Verdict verdict = policyVerdict().orElseGet(() -> targetedVerdict(targetState));
        return new ConstraintEvaluation(
                id,
                changeId,
                targetState,
                verdict.state(),
                applicability,
                severity,
                satisfaction,
                blockingPolicy,
                verdict.reason(),
                supportingEvidenceIds,
                provenance.evidenceId());
    }

    /**
     * Whether the blocking of this constraint is known whatever the target state: its policy decides on its own, or it
     * blocks when violated and its satisfaction is stated. A known constraint has no unknown evaluation for any state.
     * It is a different question from {@link #evaluateFor}: for a state the policy does not target, an evaluation is
     * non-blocking even when the satisfaction is unknown, where this answer stays unknown.
     */
    public boolean hasKnownBlocking() {
        return policyVerdict()
                .map(verdict -> verdict.state() != ConstraintEvaluationState.UNKNOWN)
                .orElse(satisfaction != ConstraintSatisfaction.UNKNOWN);
    }

    /**
     * Whether this is an applicable constraint whose policy blocks when violated and which is violated, whatever the
     * states its policy targets. A constraint that evaluates as blocking for some state always answers yes.
     */
    public boolean isViolatedExplicitBlockingPolicy() {
        return policyVerdict().isEmpty() && satisfaction == ConstraintSatisfaction.VIOLATED;
    }

    /** The answer of the applicability and the blocking mode alone, when they give one before the target state. */
    private Optional<Verdict> policyVerdict() {
        if (applicability == ConstraintApplicability.NOT_APPLICABLE) {
            return Optional.of(new Verdict(ConstraintEvaluationState.NOT_APPLICABLE,
                    "Constraint is explicitly not applicable"));
        }
        if (applicability == ConstraintApplicability.UNKNOWN) {
            return Optional.of(new Verdict(ConstraintEvaluationState.UNKNOWN, "Constraint applicability is unknown"));
        }
        if (blockingPolicy.mode() == ConstraintBlockingMode.UNKNOWN) {
            return Optional.of(new Verdict(ConstraintEvaluationState.UNKNOWN, "Constraint blocking policy is unknown"));
        }
        if (blockingPolicy.mode() == ConstraintBlockingMode.NON_BLOCKING) {
            return Optional.of(new Verdict(ConstraintEvaluationState.NON_BLOCKING,
                    "Constraint policy is explicitly non-blocking"));
        }
        return Optional.empty();
    }

    private Verdict targetedVerdict(ChangeLifecycleState targetState) {
        if (!blockingPolicy.targets(targetState)) {
            return new Verdict(ConstraintEvaluationState.NON_BLOCKING,
                    "Constraint blocking policy does not target lifecycle state " + targetState);
        }
        if (satisfaction == ConstraintSatisfaction.UNKNOWN) {
            return new Verdict(ConstraintEvaluationState.UNKNOWN,
                    "Constraint satisfaction is unknown for targeted blocking policy");
        }
        if (satisfaction == ConstraintSatisfaction.SATISFIED) {
            return new Verdict(ConstraintEvaluationState.NON_BLOCKING, "Constraint is satisfied");
        }
        return new Verdict(ConstraintEvaluationState.BLOCKING,
                "Constraint is violated and explicitly blocks lifecycle state " + targetState);
    }

    private record Verdict(ConstraintEvaluationState state, String reason) {
    }

    private static List<EvidenceId> canonicalEvidenceIds(List<EvidenceId> evidenceIds) {
        Objects.requireNonNull(evidenceIds, "supportingEvidenceIds");
        List<EvidenceId> copy = evidenceIds.stream()
                .peek(item -> Objects.requireNonNull(item, "supportingEvidenceIds item"))
                .sorted()
                .toList();
        Set<EvidenceId> seen = new HashSet<>();
        for (EvidenceId evidenceId : copy) {
            if (!seen.add(evidenceId)) {
                throw new IllegalArgumentException("duplicate supporting evidence identity: " + evidenceId);
            }
        }
        return List.copyOf(copy);
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
