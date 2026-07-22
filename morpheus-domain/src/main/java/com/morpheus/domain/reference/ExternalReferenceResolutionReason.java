package com.morpheus.domain.reference;

public enum ExternalReferenceResolutionReason {
    NOT_ATTEMPTED,
    NO_RESOLVER,
    RESOLVED,
    TARGET_NOT_FOUND,
    TARGET_UNAVAILABLE,
    TARGET_REMOVED
}
