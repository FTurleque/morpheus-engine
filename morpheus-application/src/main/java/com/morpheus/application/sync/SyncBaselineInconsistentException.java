package com.morpheus.application.sync;

/**
 * Signals that snapshot publication may already have succeeded while the follow-up sync baseline could not be
 * persisted after bounded reconciliation. Callers must inspect current published/sync state before retrying.
 */
public final class SyncBaselineInconsistentException extends IllegalStateException {
    public SyncBaselineInconsistentException(String message, Throwable cause) {
        super(message, cause);
    }
}
