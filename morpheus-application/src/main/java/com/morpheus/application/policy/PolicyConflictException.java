package com.morpheus.application.policy;

/** Explicit conflict for stale policy revisions or duplicate identities. */
public final class PolicyConflictException extends RuntimeException {
    public PolicyConflictException(String message) {
        super(message);
    }
}