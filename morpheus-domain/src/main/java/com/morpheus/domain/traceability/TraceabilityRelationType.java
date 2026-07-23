package com.morpheus.domain.traceability;

import java.util.Optional;

/** Controlled MORPHEUS traceability relation taxonomy. */
public enum TraceabilityRelationType {
    REFINES(
            TraceabilitySemanticClass.STRUCTURAL,
            TraceabilityTransitivityPolicy.CONTEXTUAL,
            "REFINED_BY"),
    DERIVES_FROM(
            TraceabilitySemanticClass.STRUCTURAL,
            TraceabilityTransitivityPolicy.CONTEXTUAL,
            "DERIVES"),
    CONSTRAINS(
            TraceabilitySemanticClass.CONSTRAINT,
            TraceabilityTransitivityPolicy.NON_TRANSITIVE,
            "CONSTRAINED_BY"),
    SATISFIES(
            TraceabilitySemanticClass.REALIZATION,
            TraceabilityTransitivityPolicy.NON_TRANSITIVE,
            "SATISFIED_BY"),
    IMPLEMENTS(
            TraceabilitySemanticClass.REALIZATION,
            TraceabilityTransitivityPolicy.NON_TRANSITIVE,
            "IMPLEMENTED_BY"),
    VALIDATES(
            TraceabilitySemanticClass.VERIFICATION,
            TraceabilityTransitivityPolicy.NON_TRANSITIVE,
            "VALIDATED_BY"),
    VERIFIED_BY(
            TraceabilitySemanticClass.VERIFICATION,
            TraceabilityTransitivityPolicy.NON_TRANSITIVE,
            "VERIFIES"),
    DEPENDS_ON(
            TraceabilitySemanticClass.DEPENDENCY,
            TraceabilityTransitivityPolicy.CONTEXTUAL,
            "DEPENDENCY_OF"),
    AFFECTS(
            TraceabilitySemanticClass.IMPACT,
            TraceabilityTransitivityPolicy.NON_TRANSITIVE,
            "AFFECTED_BY"),
    DECIDED_BY(
            TraceabilitySemanticClass.DECISION,
            TraceabilityTransitivityPolicy.NON_TRANSITIVE,
            "DECIDES"),
    SUPERSEDES(
            TraceabilitySemanticClass.HISTORY,
            TraceabilityTransitivityPolicy.CONTEXTUAL,
            "SUPERSEDED_BY"),
    LINKS_TO_CODE(
            TraceabilitySemanticClass.EXTERNAL,
            TraceabilityTransitivityPolicy.NON_TRANSITIVE,
            "CODE_LINKED_FROM"),
    LINKS_TO_TEST(
            TraceabilitySemanticClass.EXTERNAL,
            TraceabilityTransitivityPolicy.NON_TRANSITIVE,
            "TEST_LINKED_FROM"),
    RELATED_TO(
            TraceabilitySemanticClass.WEAK_ASSOCIATION,
            TraceabilityTransitivityPolicy.NON_TRANSITIVE,
            "RELATED_TO");

    private final TraceabilitySemanticClass semanticClass;
    private final TraceabilityTransitivityPolicy transitivityPolicy;
    private final Optional<String> inverseQueryName;

    TraceabilityRelationType(
            TraceabilitySemanticClass semanticClass,
            TraceabilityTransitivityPolicy transitivityPolicy,
            String inverseQueryName) {
        this.semanticClass = semanticClass;
        this.transitivityPolicy = transitivityPolicy;
        this.inverseQueryName = Optional.ofNullable(inverseQueryName);
    }

    public TraceabilitySemanticClass semanticClass() {
        return semanticClass;
    }

    public TraceabilityTransitivityPolicy transitivityPolicy() {
        return transitivityPolicy;
    }

    /** Query-facing inverse label only; it is never another persisted relation type. */
    public Optional<String> inverseQueryName() {
        return inverseQueryName;
    }
}
