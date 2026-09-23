package com.morpheus.application.traceability;

/**
 * A path search exhausted a traversal budget before it found its target, so it cannot tell whether a path exists.
 * The message is the truncation reason, in the vocabulary of {@link TraceabilitySubgraph#truncationReason()}.
 */
public final class TraceabilityTraversalBudgetException extends RuntimeException {
    public TraceabilityTraversalBudgetException(String truncationReason) {
        super(truncationReason);
    }
}
