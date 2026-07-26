package com.morpheus.domain.constraint;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.provenance.Provenance;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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

    /** Compatibility constructor for pre-M16 providers and callers. No semantics are inferred from text. */
    public Constraint(
            ConstraintId id,
            ChangeId changeId,
            String statement,
            Provenance provenance) {
        this(
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

    public boolean hasExplicitSemantics() {
        return applicability != ConstraintApplicability.UNKNOWN
                || severity != ConstraintSeverity.UNKNOWN
                || satisfaction != ConstraintSatisfaction.UNKNOWN
                || blockingPolicy.mode() != ConstraintBlockingMode.UNKNOWN;
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
