package com.morpheus.application.identity;

/** Raised when explicit identity continuity cannot be established from previously known evidence. */
public final class IdentityContinuityException extends RuntimeException {
    public IdentityContinuityException(String message) {
        super(message);
    }
}
