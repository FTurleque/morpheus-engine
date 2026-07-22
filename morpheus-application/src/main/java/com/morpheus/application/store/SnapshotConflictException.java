package com.morpheus.application.store;

/** Raised when snapshot activation would violate predecessor/current-state invariants. */
public final class SnapshotConflictException extends KnowledgeStoreException {
    public SnapshotConflictException(String message) {
        super(message);
    }
}
