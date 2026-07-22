package com.morpheus.application.snapshot;

/** Raised when snapshot validation itself fails unexpectedly after the candidate entered VALIDATING. */
public final class SnapshotValidationException extends RuntimeException {
    public SnapshotValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
