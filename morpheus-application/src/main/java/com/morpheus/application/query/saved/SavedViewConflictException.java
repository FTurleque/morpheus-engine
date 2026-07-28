package com.morpheus.application.query.saved;

/** Explicit stale-revision or identity conflict; never converted to silent last-write-wins. */
public final class SavedViewConflictException extends IllegalStateException {
    public SavedViewConflictException(String message) {
        super(message);
    }
}
