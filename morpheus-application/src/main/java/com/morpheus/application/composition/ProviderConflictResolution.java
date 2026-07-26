package com.morpheus.application.composition;

/** Explicit outcome for a divergent logical entity contributed by multiple providers. */
public enum ProviderConflictResolution {
    RESOLVED_BY_PRECEDENCE,
    UNRESOLVED_EQUAL_PRECEDENCE
}
